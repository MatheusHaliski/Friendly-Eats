// API simulada para os testes ponta a ponta: intercepta /api/v1/** no navegador (page.route) e responde com as
// fixtures JSON de e2e/fixtures, que seguem os records Java. Guarda estado (sessão, CSRF, a própria avaliação) e
// registra cada pedido para os testes conferirem parâmetros, corpo e cabeçalhos.
import { readFileSync } from 'node:fs';
import path from 'node:path';
import { fileURLToPath } from 'node:url';
import type { BrowserContext, Page, Route } from '@playwright/test';
import { PostsState } from './mock-posts';
import type {
  ConsentList,
  ConsentView,
  EstablishmentView,
  MfaSetupView,
  PreferencesPatch,
  PreferencesView,
  ProfilePatch,
  ProfileView,
  ReviewDraft,
  ReviewPage,
  ReviewView,
  SearchPage,
  SessionUserView,
  SessionView,
  TaxonomyCatalog,
} from '@friendly-eats/api';

const FIXTURES = fileURLToPath(new URL('../fixtures/', import.meta.url));

export function fixture<T>(name: string): T {
  return JSON.parse(readFileSync(path.join(FIXTURES, `${name}.json`), 'utf8')) as T;
}

export interface Recorded {
  method: string;
  path: string;
  search: URLSearchParams;
  headers: Record<string, string>;
  body: unknown;
}

export interface Reply {
  status: number;
  body?: unknown;
  headers?: Record<string, string>;
}

type Handler = (req: Recorded, api: MockApi) => Reply | Promise<Reply>;

export const SENHA_CERTA = 'uma frase longa e segura';
/** Código do app autenticador que a API simulada aceita (no lugar do TOTP de verdade). */
export const CODIGO_2FA = '123456';
/** Origem da URL pré-assinada do bucket (outra origem, como o Garage/S3 de verdade). */
export const BUCKET_ORIGIN = 'http://media.localhost:3900';
/** Onde as fotos já tratadas ficam públicas (PublicLinks.mediaUrl). */
export const MEDIA_PUBLIC = '/media-teste/';
const AVATAR_PNG = fileURLToPath(new URL('../fixtures/avatar.png', import.meta.url));
const PRATO_PNG = fileURLToPath(new URL('../fixtures/prato.png', import.meta.url));
/** Foto de prato para os testes de publicar (o mesmo PNG servido nas fotos dos posts). */
export const PRATO_FIXTURE = PRATO_PNG;

/** Estado da conta logada: perfil, preferências, consentimentos, 2FA e sessões (Configurações, RF2 a RF5). */
export interface AccountState {
  profile: ProfileView;
  preferences: PreferencesView;
  consents: ConsentView[];
  mfa: { enabled: boolean; enabledAt: string | null; codes: string[]; setup: boolean };
  sessions: SessionView[];
  uploads: Set<string>;
  bucketPuts: Array<{ url: string; method: string; headers: Record<string, string>; size: number }>;
}

export function problem(status: number, detail: string, extra: Record<string, unknown> = {}): Reply {
  const titles: Record<number, string> = {
    401: 'Login necessário',
    403: 'Sem permissão',
    404: 'Não encontrado',
    409: 'Ação não disponível agora',
    422: 'Revise os dados',
    429: 'Muitas tentativas',
  };
  return { status, body: { type: 'about:blank', title: titles[status] ?? 'Erro', status, detail, ...extra } };
}

const VISIT_LABEL: Record<string, string> = { DINE_IN: 'No local', TAKEOUT: 'Retirada', DELIVERY: 'Entrega' };
const STAR_LABEL = ['Péssimo', 'Ruim', 'Regular', 'Bom', 'Excelente'];

export class MockApi {
  readonly requests: Recorded[] = [];
  session: SessionUserView | null = null;
  csrfToken = `csrf-${Math.random().toString(36).slice(2)}`;
  readonly myReviews = new Map<string, ReviewView>();
  private accountState: AccountState | null = null;
  /** Posts, comentários, perfis e moldura (RF9 · RF12 · RF14 · RF15 · RF16). */
  readonly posts: PostsState = this.makePosts();
  private overrides: Array<{ method: string; pattern: RegExp; handler: Handler }> = [];

  constructor(
    private readonly page: Page,
    private readonly context: BrowserContext,
  ) {}

  private makePosts(): PostsState {
    // eslint-disable-next-line @typescript-eslint/no-this-alias -- o estado dos posts lê a sessão atual
    const host = this;
    return new PostsState(fixture, {
      get session() {
        return host.session;
      },
      uploads: () => (host.session ? host.account.uploads : null),
    });
  }

  async install(): Promise<void> {
    await this.page.route('**/api/v1/**', (route) => this.handle(route));
    await this.page.route(`${BUCKET_ORIGIN}/**`, (route) => this.bucket(route));
    await this.page.route(`**${MEDIA_PUBLIC}**`, (route) =>
      route.fulfill({
        status: 200,
        headers: { 'Content-Type': 'image/png' },
        body: readFileSync(route.request().url().includes('/posts/') ? PRATO_PNG : AVATAR_PNG),
      }),
    );
  }

  login(kind: 'comensal' | 'estabelecimento' = 'comensal', patch: Partial<SessionUserView> = {}): SessionUserView {
    this.session = { ...fixture<SessionUserView>(`sessao-${kind}`), ...patch };
    this.accountState = null;
    return this.session;
  }

  /** Estado das Configurações, criado a partir da sessão no primeiro uso; os testes podem mudar antes de abrir a tela. */
  get account(): AccountState {
    if (!this.session) throw new Error('login() antes de usar account');
    if (!this.accountState) {
      const s = this.session;
      const minor = s.minor;
      const profile: ProfileView = {
        ...fixture<ProfileView>('perfil-comensal'),
        id: s.id,
        name: s.name,
        handle: s.handle,
        email: s.email,
        category: s.category,
        categoryVerification: s.categoryVerification,
      };
      if (s.category.code !== 'COMENSAL') profile.typeDetails = {};
      if (minor) profile.typeDetails = { ...profile.typeDetails, guardianEmail: 'mae@example.com' };
      const prefs = fixture<PreferencesView>('preferencias');
      const preferences: PreferencesView = minor
        ? { ...prefs, profilePrivate: true, whoCanComment: 'FOLLOWERS', usageReminderMinutes: 60, minorProtectionsLocked: true }
        : prefs;
      // menor de 16: localização, feed personalizado e marketing travados; o sensível vai até os 18 (LGPD Art. 14)
      const lockedForMinor = new Set(['LOCATION_TRACKING', 'PERSONALIZED_FEED', 'MARKETING', 'SENSITIVE_PROFILE_DATA']);
      const consents = fixture<ConsentList>('consentimentos').items.map((c) => ({ ...c, lockedForMinor: minor && lockedForMinor.has(c.purpose) }));
      this.accountState = {
        profile,
        preferences,
        consents,
        mfa: { enabled: s.mfaEnabled, enabledAt: s.mfaEnabled ? '2026-09-01T12:00:00Z' : null, codes: s.mfaEnabled ? ['abcde-fghjk'] : [], setup: false },
        sessions: fixture<SessionView[]>('sessoes'),
        uploads: new Set(),
        bucketPuts: [],
      };
    }
    return this.accountState;
  }

  /** PUT na URL pré-assinada: confere o método e o Content-Type assinado, como o S3. */
  private async bucket(route: Route): Promise<void> {
    const req = route.request();
    const cors = {
      'Access-Control-Allow-Origin': '*',
      'Access-Control-Allow-Methods': 'PUT',
      'Access-Control-Allow-Headers': 'Content-Type',
    };
    if (req.method() === 'OPTIONS') {
      await route.fulfill({ status: 204, headers: cors });
      return;
    }
    const url = new URL(req.url());
    const key = url.pathname.replace(/^\/fe-media\//, '');
    const headers = await req.allHeaders();
    const size = req.postDataBuffer()?.length ?? 0;
    this.account.bucketPuts.push({ url: req.url(), method: req.method(), headers, size });
    if (req.method() !== 'PUT' || !url.searchParams.has('X-Amz-Signature')) {
      await route.fulfill({ status: 403, headers: cors, body: '<Error><Code>AccessDenied</Code></Error>' });
      return;
    }
    this.account.uploads.add(key);
    await route.fulfill({ status: 200, headers: { ...cors, ETag: '"teste"' }, body: '' });
  }

  /** Resposta específica de um teste; tem prioridade sobre as padrão. */
  on(method: string, pattern: RegExp, handler: Handler | Reply): void {
    this.overrides.unshift({ method, pattern, handler: typeof handler === 'function' ? handler : () => handler });
  }

  find(method: string, pathPattern: RegExp): Recorded[] {
    return this.requests.filter((r) => r.method === method && pathPattern.test(r.path));
  }

  last(method: string, pathPattern: RegExp): Recorded | undefined {
    return this.find(method, pathPattern).at(-1);
  }

  private async handle(route: Route): Promise<void> {
    const req = route.request();
    const url = new URL(req.url());
    let body: unknown = undefined;
    try {
      body = req.postDataJSON();
    } catch {
      body = req.postData();
    }
    const rec: Recorded = {
      method: req.method(),
      path: url.pathname,
      search: url.searchParams,
      headers: await req.allHeaders(),
      body,
    };
    this.requests.push(rec);
    const reply = await this.dispatch(rec, url.origin);
    await route.fulfill({
      status: reply.status,
      headers: {
        'Content-Type': reply.status >= 400 ? 'application/problem+json' : 'application/json',
        ...(reply.headers ?? {}),
      },
      body: reply.body === undefined ? '' : JSON.stringify(reply.body),
    });
  }

  private async dispatch(rec: Recorded, origin: string): Promise<Reply> {
    const write = !['GET', 'HEAD'].includes(rec.method);
    if (write && rec.headers['x-xsrf-token'] !== this.csrfToken) {
      return { status: 403, body: { title: 'Sessão desatualizada', status: 403, detail: 'Token CSRF ausente ou inválido. Recarregue a página e tente de novo.' } };
    }
    const o = this.overrides.find((x) => x.method === rec.method && x.pattern.test(rec.path));
    if (o) return o.handler(rec, this);
    return this.defaults(rec, origin);
  }

  private async defaults(rec: Recorded, origin: string): Promise<Reply> {
    const { method: m, path: p } = rec;
    const placeMatch = /^\/api\/v1\/establishments\/(\d+)(\/reviews(\/me)?)?$/.exec(p);

    if (m === 'GET' && p === '/api/v1/auth/csrf') {
      await this.context.addCookies([{ name: 'XSRF-TOKEN', value: this.csrfToken, url: origin }]);
      return { status: 200, body: { headerName: 'X-XSRF-TOKEN', token: `mascarado-${this.csrfToken}` } };
    }
    if (m === 'GET' && p === '/api/v1/auth/session') {
      return this.session ? { status: 200, body: this.session } : problem(401, 'Entre na sua conta para continuar.');
    }
    if (m === 'POST' && p === '/api/v1/auth/login') {
      const b = rec.body as { email?: string; password?: string };
      if (b?.password === SENHA_CERTA) {
        const user = this.login('comensal', { email: b.email ?? 'julia@example.com' });
        await this.rotateCsrf();
        return { status: 200, body: { status: 'AUTHENTICATED', user, deletionScheduledFor: null, message: null } };
      }
      return problem(401, 'E-mail ou senha incorretos.');
    }
    if (m === 'POST' && p === '/api/v1/auth/logout') {
      this.session = null;
      await this.rotateCsrf();
      return { status: 204 };
    }
    if (m === 'POST' && p === '/api/v1/auth/register') {
      const b = rec.body as { name: string; handle: string; email: string; userType: string };
      const labels: Record<string, string> = { COMENSAL: 'Comensal', ESTABELECIMENTO: 'Estabelecimento', CHEF: 'Chef' };
      this.session = {
        id: 77,
        name: b.name,
        handle: b.handle,
        email: b.email,
        category: { code: b.userType as SessionUserView['category']['code'], label: labels[b.userType] ?? b.userType, color: '#48A8FF' },
        categoryVerification: ['ESTABELECIMENTO', 'VAREJO', 'OFICIAL'].includes(b.userType) ? 'PENDING' : 'NOT_REQUIRED',
        accountStatus: 'PENDING_VERIFICATION',
        emailVerified: false,
        mfaEnabled: false,
        minor: false,
        termsUpToDate: true,
      };
      await this.rotateCsrf();
      return {
        status: 201,
        body: { status: 'AUTHENTICATED', user: this.session, deletionScheduledFor: null, message: `Enviamos um código de 6 dígitos para ${b.email}.` },
      };
    }
    if (m === 'GET' && p === '/api/v1/legal/terms') return { status: 200, body: fixture('termos') };
    if (m === 'GET' && p === '/api/v1/taxonomy') return { status: 200, body: fixture('taxonomia'), headers: { 'Cache-Control': 'max-age=3600, public' } };

    if (m === 'GET' && p === '/api/v1/establishments') return { status: 200, body: this.search(rec.search) };

    const postsReply = this.posts.handle(rec);
    if (postsReply) return postsReply;

    if (placeMatch) {
      const [, id, reviews, me] = placeMatch;
      const place = this.place(id!);
      if (!place) return problem(404, 'Estabelecimento não encontrado.');
      if (place.adultsOnly) {
        if (!this.session) return problem(401, 'Entre na sua conta para ver: conteúdo de bebida alcoólica é só para maiores de 18.');
        if (this.session.minor) return problem(404, 'Estabelecimento não encontrado.');
      }
      if (!reviews && m === 'GET') return { status: 200, body: place };
      if (reviews && !me && m === 'GET') {
        const page = id === '1' ? fixture<ReviewPage>('avaliacoes-1') : { ...fixture<ReviewPage>('avaliacoes-vazia'), rating: place.rating };
        return { status: 200, body: page };
      }
      if (me) return this.myReview(m, id!, rec.body as ReviewDraft);
    }

    if (m === 'POST' && p === '/api/v1/me/verify-email') {
      if (!this.session) return problem(401, 'Entre na sua conta para continuar.');
      if (this.session.emailVerified) return problem(409, 'Seu e-mail já está confirmado.');
      if ((rec.body as { code?: string })?.code !== '123456') return problem(422, 'Código incorreto. Restam 4 tentativas.', { errors: ['Código incorreto. Restam 4 tentativas.'] });
      this.session = { ...this.session, emailVerified: true, accountStatus: 'ACTIVE' };
      return { status: 200, body: this.session };
    }
    if (m === 'POST' && p === '/api/v1/me/verify-email/resend') {
      return this.session ? { status: 202 } : problem(401, 'Entre na sua conta para continuar.');
    }
    if (p.startsWith('/api/v1/me/') || p === '/api/v1/media/uploads') {
      const reply = this.accountRoute(rec);
      if (reply) return reply;
    }
    if (m === 'GET' && p === '/api/v1/me/establishments') {
      if (!this.session) return problem(401, 'Entre na sua conta para continuar.');
      return { status: 200, body: ['ESTABELECIMENTO', 'VAREJO'].includes(this.session.category.code) ? fixture('meus-lugares') : [] };
    }
    return problem(404, `Rota não simulada: ${m} ${p}`);
  }

  /** Modo sudo (StepUp): senha e, com 2FA, o código do app ou um de recuperação. */
  private sudo(password: unknown, code: unknown): Reply | null {
    const mfa = this.account.mfa;
    const okCode = !mfa.enabled || code === CODIGO_2FA || (typeof code === 'string' && mfa.codes.includes(code));
    if (password === SENHA_CERTA && okCode) return null;
    return problem(403, mfa.enabled ? 'Senha ou código do app autenticador incorretos.' : 'Senha incorreta.');
  }

  private accountRoute(rec: Recorded): Reply | null {
    const { method: m, path: p } = rec;
    if (!this.session) return problem(401, 'Entre na sua conta para continuar.');
    const acc = this.account;
    const body = (rec.body ?? {}) as Record<string, unknown>;

    // ---- perfil e foto (RF2)
    if (p === '/api/v1/me/profile' && m === 'GET') return { status: 200, body: acc.profile };
    if (p === '/api/v1/me/profile' && m === 'PATCH') return this.patchProfile(body as ProfilePatch);
    if (p === '/api/v1/media/uploads' && m === 'POST') {
      if (this.session.accountStatus !== 'ACTIVE') return problem(403, 'Sua conta precisa estar ativa para enviar fotos. Confirme seu e-mail ou fale com o suporte.');
      const errors: string[] = [];
      if (body.kind !== 'IMAGE') errors.push('Diga o tipo de mídia: IMAGE.');
      if (!['image/jpeg', 'image/png'].includes(String(body.contentType))) errors.push('Envie a foto em JPEG ou PNG.');
      if (typeof body.sizeBytes !== 'number' || body.sizeBytes < 1 || body.sizeBytes > 10 * 1024 * 1024) errors.push('A foto vai até 10 MB.');
      if (errors.length) return problem(422, errors[0]!, { title: 'Revise a foto', errors });
      const ext = body.contentType === 'image/png' ? 'png' : 'jpg';
      const objectKey = `uploads/${this.session.id}/0199${acc.uploads.size + 1}.${ext}`;
      return {
        status: 201,
        body: {
          objectKey,
          uploadUrl: `${BUCKET_ORIGIN}/fe-media/${objectKey}?X-Amz-Algorithm=AWS4-HMAC-SHA256&X-Amz-Signature=teste`,
          method: 'PUT',
          headers: { 'Content-Type': String(body.contentType) },
          expiresAt: new Date(Date.now() + 15 * 60_000).toISOString(),
          maxBytes: 10 * 1024 * 1024,
        },
      };
    }
    if (p === '/api/v1/me/photo' && m === 'PUT') {
      const key = String(body.objectKey ?? '');
      if (!acc.uploads.has(key)) return problem(422, 'Envie a foto de novo.', { errors: ['Envie a foto de novo.'] });
      acc.uploads.delete(key);
      const publicKey = `avatars/0199-${Date.now()}.png`;
      acc.profile = { ...acc.profile, avatarObjectKey: publicKey, avatarUrl: `${MEDIA_PUBLIC}${publicKey}` };
      return { status: 200, body: acc.profile };
    }
    if (p === '/api/v1/me/photo' && m === 'DELETE') {
      acc.profile = { ...acc.profile, avatarObjectKey: null, avatarUrl: null };
      return { status: 200, body: acc.profile };
    }

    // ---- preferências (RF2, RF3)
    if (p === '/api/v1/me/preferences' && m === 'GET') return { status: 200, body: acc.preferences };
    if (p === '/api/v1/me/preferences' && m === 'PATCH') return this.patchPreferences(body as PreferencesPatch);

    // ---- consentimentos (RF3)
    if (p === '/api/v1/me/consents' && m === 'GET') return { status: 200, body: { items: acc.consents } };
    const consent = /^\/api\/v1\/me\/consents\/([A-Z_]+)$/.exec(p);
    if (consent && m === 'PUT') {
      const item = acc.consents.find((c) => c.purpose === consent[1]);
      if (!item) return problem(404, 'Finalidade desconhecida.');
      if (item.lockedForMinor) {
        return problem(403, item.sensitive ? 'Dados sensíveis do perfil só a partir dos 18 anos, ou com o consentimento do responsável (LGPD Art. 14).' : 'Para menores de 16 anos este uso fica desligado (ECA Digital).');
      }
      const updated: ConsentView = { ...item, granted: body.granted === true, updatedAt: new Date().toISOString() };
      acc.consents = acc.consents.map((c) => (c.purpose === item.purpose ? updated : c));
      if (item.purpose === 'SENSITIVE_PROFILE_DATA' && !updated.granted) acc.profile = { ...acc.profile, dietaryStyles: [] };
      return { status: 200, body: updated };
    }

    // ---- senha e sessões (RF4)
    if (p === '/api/v1/me/password' && m === 'POST') {
      const denied = this.sudo(body.currentPassword, body.code);
      if (denied) return denied;
      if (typeof body.newPassword !== 'string' || body.newPassword.length < 12) {
        const msg = 'A senha precisa ter pelo menos 12 caracteres. Uma frase com várias palavras funciona bem.';
        return problem(422, msg, { errors: [msg] });
      }
      const closed = acc.sessions.filter((s) => !s.current).length;
      acc.sessions = acc.sessions.filter((s) => s.current);
      return { status: 200, body: { otherSessionsClosed: closed } };
    }
    if (p === '/api/v1/me/sessions' && m === 'GET') return { status: 200, body: acc.sessions };
    if (p === '/api/v1/me/sessions' && m === 'DELETE') {
      const closed = acc.sessions.filter((s) => !s.current).length;
      acc.sessions = acc.sessions.filter((s) => s.current);
      return { status: 200, body: { closed } };
    }
    const one = /^\/api\/v1\/me\/sessions\/([\w-]+)$/.exec(p);
    if (one && m === 'DELETE') {
      if (!acc.sessions.some((s) => s.id === one[1] && !s.current)) return { status: 404 };
      acc.sessions = acc.sessions.filter((s) => s.id !== one[1]);
      return { status: 204 };
    }

    // ---- 2FA (RF4)
    const status = () => ({ enabled: acc.mfa.enabled, enabledAt: acc.mfa.enabledAt, recoveryCodesLeft: acc.mfa.codes.length });
    const newCodes = () => {
      acc.mfa.codes = Array.from({ length: 10 }, (_, i) => `k${i}m2p-${(i + 3).toString().repeat(2)}x7q`.slice(0, 11));
      return { codes: acc.mfa.codes, generatedAt: new Date().toISOString() };
    };
    if (p === '/api/v1/me/mfa' && m === 'GET') return { status: 200, body: status() };
    if (p === '/api/v1/me/mfa/totp' && m === 'POST') {
      const denied = this.sudo(body.password, body.code);
      if (denied) return denied;
      if (acc.mfa.enabled) return problem(409, 'O 2FA já está ativo. Desative antes de configurar outro aparelho.');
      acc.mfa.setup = true;
      return { status: 200, body: fixture<MfaSetupView>('2fa-configuracao') };
    }
    if (p === '/api/v1/me/mfa/totp/confirm' && m === 'POST') {
      if (!acc.mfa.setup) return problem(422, 'A configuração expirou. Comece de novo.', { errors: ['A configuração expirou. Comece de novo.'] });
      if (body.code !== CODIGO_2FA) {
        const msg = 'Código incorreto. Confira a hora do celular e tente de novo.';
        return problem(422, msg, { errors: [msg] });
      }
      acc.mfa = { ...acc.mfa, enabled: true, enabledAt: new Date().toISOString(), setup: false };
      this.session = { ...this.session, mfaEnabled: true };
      return { status: 200, body: newCodes() };
    }
    if (p === '/api/v1/me/mfa/recovery-codes' && m === 'POST') {
      const denied = this.sudo(body.password, body.code);
      if (denied) return denied;
      if (!acc.mfa.enabled) return problem(409, 'Ative o 2FA primeiro.');
      return { status: 200, body: newCodes() };
    }
    if (p === '/api/v1/me/mfa/disable' && m === 'POST') {
      const denied = this.sudo(body.password, body.code);
      if (denied) return denied;
      acc.mfa = { enabled: false, enabledAt: null, codes: [], setup: false };
      this.session = { ...this.session, mfaEnabled: false };
      return { status: 200, body: status() };
    }

    // ---- excluir conta (RF5)
    if (p === '/api/v1/me/deletion' && m === 'POST') {
      if (body.confirm !== true) return problem(422, 'Confirme que entendeu as consequências da exclusão.', { errors: ['Confirme que entendeu as consequências da exclusão.'] });
      const denied = this.sudo(body.password, body.code);
      if (denied) return denied;
      this.session = null;
      return {
        status: 202,
        body: { accountStatus: 'PENDING_DELETION', deletionScheduledFor: new Date(Date.now() + 30 * 86_400_000).toISOString(), graceDays: 30 },
      };
    }
    return null;
  }

  private patchProfile(patch: ProfilePatch): Reply {
    const acc = this.account;
    const errors: string[] = [];
    const next: ProfileView = { ...acc.profile };
    if (patch.name != null) {
      if (patch.name.trim().length < 2 || patch.name.trim().length > 120) errors.push('Informe um nome de exibição com 2 a 120 caracteres.');
      else next.name = patch.name.trim();
    }
    if (patch.handle != null) {
      if (patch.handle === 'emuso') return problem(409, 'Esse @usuário já está em uso. Tente outro.');
      if (!/^[a-z0-9._]{3,30}$/.test(patch.handle)) errors.push('O @usuário deve ter 3 a 30 caracteres: letras minúsculas, números, ponto ou sublinhado.');
      else next.handle = patch.handle;
    }
    if (patch.bio != null) {
      if (patch.bio.length > 150) errors.push('A bio tem no máximo 150 caracteres.');
      else next.bio = patch.bio || null;
    }
    if (patch.pronouns != null) next.pronouns = patch.pronouns;
    if (patch.links != null) {
      if (patch.links.length > 5 || patch.links.some((l) => !/^https:\/\/\S{4,200}$/.test(l))) errors.push('Até 5 links, cada um começando com https://');
      else next.links = patch.links;
    }
    if (patch.countryOfResidence != null) next.countryOfResidence = patch.countryOfResidence || null;
    if (patch.stateOfResidence != null) next.stateOfResidence = patch.stateOfResidence || null;
    if (patch.cityOfResidence != null) next.cityOfResidence = patch.cityOfResidence || null;
    if (patch.recoveryEmail != null) next.recoveryEmail = patch.recoveryEmail || null;
    if (patch.cellphoneNumber != null) {
      if (patch.cellphoneNumber && !/^\+[1-9]\d{7,14}$/.test(patch.cellphoneNumber)) errors.push('Celular no formato internacional, ex.: +5541999990000.');
      else next.cellphoneNumber = patch.cellphoneNumber || null;
    }
    if (patch.typeDetails != null) next.typeDetails = patch.typeDetails;
    const taxonomy = fixture<TaxonomyCatalog>('taxonomia');
    if (patch.favoriteCuisines != null) {
      const codes = [...new Set(patch.favoriteCuisines.map((c) => c.toUpperCase()))];
      const unknown = codes.filter((c) => !taxonomy.cuisines.some((x) => x.code === c));
      if (codes.length > 5) errors.push('Escolha até 5 culinárias favoritas.');
      else if (unknown.length) errors.push(`Culinária desconhecida: ${unknown.join(', ')}.`);
      else next.favoriteCuisines = codes;
    }
    if (patch.dietaryStyles != null) {
      const codes = [...new Set(patch.dietaryStyles.map((c) => c.toUpperCase()))];
      const granted = acc.consents.find((c) => c.purpose === 'SENSITIVE_PROFILE_DATA')?.granted;
      if (codes.length && !granted) {
        return problem(403, 'Para guardar estilos alimentares, ligue antes o consentimento de dados sensíveis do perfil (LGPD Art. 11).');
      }
      const unknown = codes.filter((c) => !taxonomy.dietaryStyles.some((x) => x.code === c));
      if (unknown.length) errors.push(`Estilo alimentar desconhecido: ${unknown.join(', ')}.`);
      else next.dietaryStyles = codes;
    }
    if (errors.length) return problem(422, errors[0]!, { errors });
    acc.profile = next;
    if (this.session) this.session = { ...this.session, name: next.name, handle: next.handle };
    return { status: 200, body: next };
  }

  private patchPreferences(patch: PreferencesPatch): Reply {
    const acc = this.account;
    const minor = acc.preferences.minorProtectionsLocked;
    if (minor && (patch.profilePrivate === false || patch.sensitiveContent === 'ALLOW' || patch.whoCanComment === 'EVERYONE')) {
      return problem(403, 'Para menores de 16 anos essa proteção fica ligada; só o responsável pode alterá-la (ECA Digital).');
    }
    if (patch.clearUsageReminder && minor) return problem(403, 'Para menores de 16 anos o lembrete de tempo de uso fica ligado.');
    if (patch.fontScale != null && (patch.fontScale < 85 || patch.fontScale > 150)) {
      return problem(422, 'O tamanho da fonte vai de 85% a 150%.', { errors: ['O tamanho da fonte vai de 85% a 150%.'] });
    }
    const { clearUsageReminder, notifications, ...rest } = patch;
    const defined = Object.fromEntries(Object.entries(rest).filter(([, v]) => v !== undefined && v !== null));
    acc.preferences = {
      ...acc.preferences,
      ...defined,
      usageReminderMinutes: clearUsageReminder ? null : (patch.usageReminderMinutes ?? acc.preferences.usageReminderMinutes),
      notifications: { ...acc.preferences.notifications, ...(notifications ?? {}) },
    };
    return { status: 200, body: acc.preferences };
  }

  place(id: string): EstablishmentView | null {
    try {
      return fixture<EstablishmentView>(`lugar-${id}`);
    } catch {
      const card = fixture<SearchPage>('busca').items.find((c) => String(c.id) === id);
      if (!card) return null;
      // lugar sem fixture própria: monta a página a partir do card da busca
      const base = fixture<EstablishmentView>('lugar-2');
      return {
        ...base,
        id: card.id,
        name: card.name,
        primaryType: card.primaryType,
        group: card.group,
        price: card.price,
        rating: card.rating,
        adultsOnly: card.adultsOnly,
        status: card.status,
        location: {
          ...base.location,
          city: card.location.city,
          uf: card.location.uf,
          neighborhood: card.location.neighborhood,
          approximate: card.location.approximate,
          street: card.location.approximate ? null : base.location.street,
          streetNumber: card.location.approximate ? null : base.location.streetNumber,
        },
        classification: { ...base.classification, chip: card.chip, primaryCuisine: card.primaryCuisine, primaryFormat: card.primaryFormat },
      };
    }
  }

  private search(sp: URLSearchParams): SearchPage {
    const page = fixture<SearchPage>('busca');
    const q = sp.get('q')?.toLowerCase();
    let items = page.items;
    if (q) items = items.filter((c) => c.name.toLowerCase().includes(q));
    if (!this.session || this.session.minor) items = items.filter((c) => !c.adultsOnly);
    const lat = sp.get('lat');
    if (lat) items = items.map((c, i) => ({ ...c, distanceKm: Math.round((0.4 + i * 1.3) * 10) / 10 }));
    const removed = page.items.length - items.length;
    return {
      ...page,
      items,
      page: Number(sp.get('page') ?? 0),
      total: q ? items.length : page.total - removed,
      radiusKm: lat ? Number(sp.get('radiusKm') ?? 5) : null,
    };
  }

  private myReview(method: string, id: string, draft: ReviewDraft): Reply {
    if (!this.session) return problem(401, 'Entre na sua conta para continuar.');
    const current = this.myReviews.get(id);
    if (method === 'GET') return current ? { status: 200, body: current } : problem(404, 'Você não avaliou este lugar.');
    if (method === 'DELETE') {
      if (!current) return problem(404, 'Você não avaliou este lugar.');
      this.myReviews.delete(id);
      return { status: 204 };
    }
    if (method === 'PUT') {
      const errors: string[] = [];
      if (!draft.rating || draft.rating < 1 || draft.rating > 5) errors.push('Dê uma nota inteira de 1 (Péssimo) a 5 (Excelente).');
      if (!draft.visitMode) errors.push('Preencha "visitMode".');
      if (errors.length) return problem(422, errors[0]!, { errors });
      const now = new Date().toISOString();
      const review: ReviewView = {
        id: current?.id ?? 500,
        rating: draft.rating!,
        ratingLabel: STAR_LABEL[draft.rating! - 1]!,
        visitMode: { code: draft.visitMode!, label: VISIT_LABEL[draft.visitMode!]!, group: null },
        dimensions: draft.dimensions ?? {},
        spentPerPersonCents: draft.spentPerPersonCents,
        body: draft.body,
        trust: 'REVIEW_UNVERIFIED',
        edited: !!current,
        author: { name: this.session.name, handle: this.session.handle, category: this.session.category },
        createdAt: current?.createdAt ?? now,
        updatedAt: now,
      };
      this.myReviews.set(id, review);
      return { status: current ? 200 : 201, body: review };
    }
    return problem(404, 'Rota não simulada');
  }

  /** Login e logout trocam o token CSRF, como o Spring Security faz. */
  private async rotateCsrf(): Promise<void> {
    this.csrfToken = `csrf-${Math.random().toString(36).slice(2)}`;
    await this.context.clearCookies({ name: 'XSRF-TOKEN' });
  }
}
