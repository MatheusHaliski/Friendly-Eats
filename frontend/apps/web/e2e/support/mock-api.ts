// API simulada para os testes ponta a ponta: intercepta /api/v1/** no navegador (page.route) e responde com as
// fixtures JSON de e2e/fixtures, que seguem os records Java. Guarda estado (sessão, CSRF, a própria avaliação) e
// registra cada pedido para os testes conferirem parâmetros, corpo e cabeçalhos.
import { readFileSync } from 'node:fs';
import path from 'node:path';
import { fileURLToPath } from 'node:url';
import type { BrowserContext, Page, Route } from '@playwright/test';
import type {
  EstablishmentView,
  ReviewDraft,
  ReviewPage,
  ReviewView,
  SearchPage,
  SessionUserView,
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
  private overrides: Array<{ method: string; pattern: RegExp; handler: Handler }> = [];

  constructor(
    private readonly page: Page,
    private readonly context: BrowserContext,
  ) {}

  async install(): Promise<void> {
    await this.page.route('**/api/v1/**', (route) => this.handle(route));
  }

  login(kind: 'comensal' | 'estabelecimento' = 'comensal', patch: Partial<SessionUserView> = {}): SessionUserView {
    this.session = { ...fixture<SessionUserView>(`sessao-${kind}`), ...patch };
    return this.session;
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
    if (m === 'GET' && p === '/api/v1/me/establishments') {
      if (!this.session) return problem(401, 'Entre na sua conta para continuar.');
      return { status: 200, body: ['ESTABELECIMENTO', 'VAREJO'].includes(this.session.category.code) ? fixture('meus-lugares') : [] };
    }
    return problem(404, `Rota não simulada: ${m} ${p}`);
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
