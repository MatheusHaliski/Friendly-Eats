// API simulada do RF17 (idade verificada e responsável) e do RF18 (encarregado e "Seus dados"), no formato dos
// records Java (AgeVerificationService, GuardianService, PrivacyContact, DataExportService).
import type {
  AgeStatus,
  AgeVerificationView,
  DataExportView,
  GuardianActionView,
  MinorLinkView,
  PrivacyContactView,
  ProtectionCode,
  ProtectionView,
  SessionUserView,
  SupervisedView,
  SupervisionView,
} from '@friendly-eats/api';
import type { Recorded, Reply } from './mock-api';

type Fixture = <T>(name: string) => T;

export interface TitularHost {
  session: SessionUserView | null;
  /** Modo sudo: null = confere; senão a resposta de erro. */
  sudo(password: unknown, code: unknown): Reply | null;
}

/** Token de convite que a API simulada aceita. */
export const CONVITE_VALIDO = 'convite-de-teste';
export const FAKE_REFERENCE = 'fake-ref-1';

function problem(status: number, detail: string, extra: Record<string, unknown> = {}): Reply {
  const titles: Record<number, string> = { 401: 'Login necessário', 403: 'Sem permissão', 404: 'Não encontrado', 409: 'Conflito', 422: 'Revise os dados', 429: 'Muitas tentativas' };
  return { status, body: { type: 'about:blank', title: titles[status] ?? 'Erro', status, detail, ...extra } };
}

const LABEL: Record<AgeStatus, string> = {
  UNVERIFIED: 'Idade não verificada',
  PENDING: 'Verificação em andamento',
  VERIFIED_ADULT: 'Maior de 18 verificado',
  VERIFIED_MINOR: 'Menor de 18 verificado',
  FAILED: 'Não foi possível verificar',
};

const PROTECTIONS: Array<[ProtectionCode, string, string]> = [
  ['PUBLIC_PROFILE', 'Perfil público', 'Qualquer pessoa vê as publicações no perfil.'],
  ['SENSITIVE_CONTENT', 'Mostrar conteúdo sensível', 'Fotos e textos que podem incomodar aparecem sem aviso.'],
  ['COMMENTS_FROM_EVERYONE', 'Comentários de todo mundo', 'Qualquer conta comenta, não só quem segue.'],
  ['NO_USAGE_REMINDER', 'Sem lembrete de tempo de uso', 'O lembrete de 60 minutos de uso fica desligado.'],
];

export function protections(allowed: ProtectionCode[] = []): ProtectionView[] {
  return PROTECTIONS.map(([code, label, description]) => ({ code, label, description, allowed: allowed.includes(code) }));
}

const now = () => new Date().toISOString();

export class TitularState {
  age: AgeVerificationView | null = null;
  guardian: MinorLinkView | null = null;
  supervised: SupervisionView[] = [];
  exports: DataExportView[] = [];
  /** Responde ao convite como se o fornecedor já tivesse a idade verificada do responsável. */
  private verificationOpen = false;

  constructor(
    private readonly fixture: Fixture,
    private readonly host: TitularHost,
  ) {}

  /** Novo login: padrão = adulto verificado (os testes antigos veem bebida), menor sem verificação. */
  reset(): void {
    const s = this.host.session;
    this.age = s ? this.ageView(s.minor ? 'UNVERIFIED' : 'VERIFIED_ADULT') : null;
    this.guardian = s ? this.linkView(s.minor ? 'PENDING' : 'NONE', s.minor) : null;
    this.supervised = [];
    this.exports = [];
    this.verificationOpen = false;
  }

  /** Muda o estado da verificação (UNVERIFIED para testar o aviso de bebida oculta). */
  setAge(status: AgeStatus): void {
    this.age = this.ageView(status);
  }

  alcoholVisible(): boolean {
    return !!this.host.session && !this.host.session.minor && this.age?.status === 'VERIFIED_ADULT';
  }

  /** O responsável logado supervisiona a conta de exemplo (fixture painel-supervisao). */
  addSupervised(): SupervisionView {
    const panel = this.fixture<SupervisionView>('painel-supervisao');
    this.supervised = [panel];
    return panel;
  }

  ageView(status: AgeStatus): AgeVerificationView {
    const declaredAdult = !this.host.session?.minor;
    const verified = status === 'VERIFIED_ADULT' || status === 'VERIFIED_MINOR';
    return {
      status,
      statusLabel: LABEL[status],
      method: verified ? 'SIMULADO' : null,
      verifiedAt: verified ? '2026-10-20T12:00:00Z' : null,
      validUntil: verified ? '2027-10-20T12:00:00Z' : null,
      alcoholContentVisible: declaredAdult && status === 'VERIFIED_ADULT',
      declaredAdult,
      canStart: declaredAdult && status !== 'VERIFIED_ADULT',
      canAppeal: status === 'VERIFIED_MINOR' || status === 'FAILED',
      providerAvailable: true,
      simulated: true,
      pending: status === 'PENDING' ? { reference: FAKE_REFERENCE, expiresAt: now() } : null,
      appeal: null,
      history: status === 'UNVERIFIED' ? [] : [{ status, statusLabel: LABEL[status], method: 'SIMULADO', note: null, occurredAt: '2026-10-20T12:00:00Z' }],
    };
  }

  private linkView(status: MinorLinkView['status'], required: boolean): MinorLinkView {
    const pending = status === 'PENDING';
    return {
      ...this.fixture<MinorLinkView>('vinculo-menor'),
      required,
      eligible: required,
      status,
      guardianEmail: pending ? 'mae@example.com' : null,
      guardian: null,
      canUnlink: false,
    };
  }

  private action(label: string, action: string, actor: GuardianActionView['actor'] = 'MINOR'): GuardianActionView {
    return { actor, action, label, detail: null, occurredAt: now() };
  }

  handle(rec: Recorded): Reply | null {
    const { method: m, path: p } = rec;
    const body = (rec.body ?? {}) as Record<string, unknown>;
    if (m === 'GET' && p === '/api/v1/legal/privacy-contact') {
      return { status: 200, body: this.fixture<PrivacyContactView>('privacidade-contato') };
    }
    const mine = p.startsWith('/api/v1/me/age-verification') || p.startsWith('/api/v1/me/guardian') || p.startsWith('/api/v1/guardian/') || p === '/api/v1/me/data-exports';
    if (!mine) return null;
    const s = this.host.session;
    if (!s) return problem(401, 'Entre na sua conta para continuar.');
    if (!this.age) this.reset();

    // ---- verificação de idade (RF17)
    if (p === '/api/v1/me/age-verification' && m === 'GET') return { status: 200, body: this.age };
    if (p === '/api/v1/me/age-verification/start' && m === 'POST') {
      if (s.minor) return problem(403, 'Pela data de nascimento informada, você tem menos de 18 anos.');
      if (this.age!.status === 'VERIFIED_ADULT') return problem(409, 'Sua idade já está verificada.');
      this.verificationOpen = true;
      this.age = this.ageView('PENDING');
      return {
        status: 200,
        body: { reference: FAKE_REFERENCE, redirectUrl: `/configuracoes/idade?verificacao=${FAKE_REFERENCE}`, instructions: 'Ambiente de desenvolvimento: escolha o resultado da verificação simulada.', expiresAt: now(), simulated: true },
      };
    }
    if (p === '/api/v1/me/age-verification/complete' && m === 'POST') {
      if (!this.verificationOpen || body.reference !== FAKE_REFERENCE) return problem(409, 'Essa verificação não está mais aberta. Comece de novo.');
      const simulate = (body.params as Record<string, string> | undefined)?.simulate;
      if (!simulate) return { status: 200, body: this.age };
      this.verificationOpen = false;
      const status: AgeStatus = simulate === 'ADULT' ? 'VERIFIED_ADULT' : simulate === 'MINOR' ? 'VERIFIED_MINOR' : 'FAILED';
      const history = [{ status, statusLabel: LABEL[status], method: 'SIMULADO', note: null, occurredAt: now() }, ...this.age!.history];
      this.age = { ...this.ageView(status), history };
      return { status: 200, body: this.age };
    }
    if (p === '/api/v1/me/age-verification/appeal' && m === 'POST') {
      const reason = String(body.reason ?? '').trim();
      if (reason.length < 10) return problem(422, 'Explique o motivo da contestação em 10 a 1000 caracteres.', { errors: ['Explique o motivo da contestação em 10 a 1000 caracteres.'] });
      const appeal = { id: 1, contested: this.age!.status, reason, status: 'OPEN' as const, createdAt: now(), decidedAt: null, decisionNote: null };
      this.age = { ...this.age!, appeal, canAppeal: false, canStart: false };
      return { status: 201, body: appeal };
    }

    // ---- vínculo, lado do menor (RF17)
    if (p === '/api/v1/me/guardian' && m === 'GET') return { status: 200, body: this.guardian };
    if (p === '/api/v1/me/guardian/invite' && m === 'POST') {
      const g = this.guardian!;
      if (!g.eligible) return problem(403, 'O vínculo com o responsável é para contas de menores de 18 anos.');
      const email = typeof body.guardianEmail === 'string' && body.guardianEmail ? body.guardianEmail.toLowerCase() : g.guardianEmail;
      if (!email || !/^[^@\s]+@[^@\s]+\.[^@\s]{2,}$/.test(email)) return problem(422, 'Informe um e-mail válido do responsável, diferente do seu.', { errors: ['Informe um e-mail válido do responsável, diferente do seu.'] });
      this.guardian = { ...g, status: 'PENDING', guardianEmail: email, invitedAt: now(), pendingDays: 0, overdue: false, history: [this.action('Convite enviado ao responsável', 'INVITED'), ...g.history] };
      return { status: 200, body: this.guardian };
    }
    if (p === '/api/v1/me/guardian/requests' && m === 'POST') {
      const g = this.guardian!;
      if (g.status !== 'ACTIVE') return problem(409, 'O pedido vai para o responsável depois que ele aceitar o vínculo.');
      const code = body.protection as ProtectionCode;
      const prot = g.protections.find((x) => x.code === code);
      if (!prot) return problem(404, 'Proteção desconhecida.');
      if (g.requests.some((r) => r.protection === code && r.status === 'PENDING')) return problem(409, 'Esse pedido já está com o seu responsável.');
      const req = { id: g.requests.length + 1, protection: code, label: prot.label, status: 'PENDING' as const, createdAt: now(), decidedAt: null };
      this.guardian = { ...g, requests: [req, ...g.requests], history: [{ ...this.action('Pedido para afrouxar uma proteção', 'REQUESTED'), detail: prot.label }, ...g.history] };
      return { status: 201, body: req };
    }
    if (p === '/api/v1/me/guardian' && m === 'DELETE') {
      if (!this.guardian!.canUnlink) return problem(403, 'Menores de 16 anos não desfazem o vínculo; o responsável pode desfazer pelo painel dele (ECA Digital, art. 24).');
      this.guardian = { ...this.guardian!, status: 'NONE', guardian: null, guardianEmail: null, canUnlink: false };
      return { status: 200, body: this.guardian };
    }

    // ---- painel do responsável (RF17)
    if (p === '/api/v1/guardian/links/accept' && m === 'POST') {
      if (body.token !== CONVITE_VALIDO) return problem(404, 'Convite inválido ou expirado. Peça ao menor para enviar outro em Configurações → Idade e responsável.');
      if (!this.alcoholVisible()) return problem(403, 'Conteúdo de bebida alcoólica aparece só depois de verificar a idade.', { ageVerificationRequired: true });
      return { status: 200, body: this.addSupervised().minor };
    }
    if (p === '/api/v1/guardian/minors' && m === 'GET') return { status: 200, body: this.supervised.map((x) => x.minor) as SupervisedView[] };
    const panel = /^\/api\/v1\/guardian\/minors\/(\d+)(?:\/(requests)\/(\d+)|\/(protections)\/([A-Z_]+)|\/(sensitive-consent))?$/.exec(p);
    if (panel) {
      const idx = this.supervised.findIndex((x) => String(x.minor.minorId) === panel[1]);
      if (idx < 0) return problem(404, 'Você não supervisiona esta conta.');
      let v = this.supervised[idx]!;
      const save = (next: SupervisionView) => {
        const pending = next.requests.filter((r) => r.status === 'PENDING').length;
        v = { ...next, minor: { ...next.minor, pendingRequests: pending } };
        this.supervised[idx] = v;
        return { status: 200, body: v };
      };
      if (m === 'GET' && !panel[2] && !panel[4] && !panel[6]) return { status: 200, body: v };
      if (m === 'DELETE' && !panel[2] && !panel[4] && !panel[6]) {
        this.supervised.splice(idx, 1);
        return { status: 204 };
      }
      if (panel[2] && m === 'POST') {
        const req = v.requests.find((r) => String(r.id) === panel[3] && r.status === 'PENDING');
        if (!req) return problem(404, 'Pedido não encontrado ou já decidido.');
        const approve = body.approve === true;
        return save({
          ...v,
          requests: v.requests.map((r) => (r === req ? { ...r, status: approve ? 'APPROVED' : 'DENIED', decidedAt: now() } : r)),
          protections: approve ? v.protections.map((x) => (x.code === req.protection ? { ...x, allowed: true } : x)) : v.protections,
          history: [{ ...this.action(approve ? 'Pedido aprovado pelo responsável' : 'Pedido negado pelo responsável', approve ? 'APPROVED' : 'DENIED', 'GUARDIAN'), detail: req.label }, ...v.history],
        });
      }
      if (panel[4] && m === 'PUT') {
        const allowed = body.allowed === true;
        return save({
          ...v,
          protections: v.protections.map((x) => (x.code === panel[5] ? { ...x, allowed } : x)),
          history: [this.action(allowed ? 'Proteção desligada pelo responsável' : 'Proteção religada pelo responsável', allowed ? 'PROTECTION_ALLOWED' : 'PROTECTION_RESTORED', 'GUARDIAN'), ...v.history],
        });
      }
      if (panel[6] && m === 'PUT') {
        const granted = body.granted === true;
        return save({
          ...v,
          sensitiveConsent: granted,
          history: [
            this.action(granted ? 'Responsável consentiu os estilos alimentares no perfil' : 'Responsável revogou o consentimento dos estilos alimentares', granted ? 'SENSITIVE_CONSENT_GRANTED' : 'SENSITIVE_CONSENT_REVOKED', 'GUARDIAN'),
            ...v.history,
          ],
        });
      }
    }

    // ---- exportação (RF18)
    if (p === '/api/v1/me/data-exports' && m === 'GET') return { status: 200, body: this.exports };
    if (p === '/api/v1/me/data-exports' && m === 'POST') {
      const denied = this.host.sudo(body.password, body.code);
      if (denied) return denied;
      if (this.exports.some((e) => e.status !== 'FAILED' && Date.now() - Date.parse(e.requestedAt) < 86_400_000)) {
        return { ...problem(429, 'Muitas tentativas. Tente de novo em 1440 minutos ou redefina sua senha.'), headers: { 'Retry-After': '86400' } };
      }
      const requestedAt = now();
      const item: DataExportView = {
        id: '0199-exportacao-1',
        status: 'REQUESTED',
        statusLabel: 'Pedido recebido',
        requestedAt,
        dueAt: new Date(Date.now() + 15 * 86_400_000).toISOString(),
        readyAt: null,
        expiresAt: null,
        sizeBytes: null,
        downloadUrl: null,
      };
      this.exports = [item, ...this.exports];
      return { status: 202, body: item };
    }
    return null;
  }

  /** O job terminou: o pedido mais recente fica pronto com a URL pré-assinada. */
  finishExport(): void {
    const [first, ...rest] = this.exports;
    if (!first) return;
    this.exports = [
      {
        ...first,
        status: 'READY',
        statusLabel: 'Pronto para baixar',
        readyAt: now(),
        expiresAt: new Date(Date.now() + 72 * 3_600_000).toISOString(),
        sizeBytes: 48_213,
        downloadUrl: 'http://media.localhost:3900/fe-media/exports/42/0199-exportacao-1.zip?X-Amz-Algorithm=AWS4-HMAC-SHA256&X-Amz-Signature=teste',
      },
      ...rest,
    ];
  }
}
