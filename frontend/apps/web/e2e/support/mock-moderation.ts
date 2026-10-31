// API simulada da moderação (RF13) e da segurança entre usuários (RF19): denúncia com protocolo, formulário público,
// contestação do dono, integridade do lugar, minhas denúncias e decisões com recurso, fila do moderador e bloquear,
// silenciar e palavras filtradas. Segue os records de ModerationViews e SafetyViews.
import type {
  CaseDetailView,
  CaseSummaryView,
  IntegrityView,
  MyDecisionView,
  MyReportView,
  ReasonView,
  RelationView,
  SafetyView,
  SessionUserView,
} from '@friendly-eats/api';
import type { Recorded, Reply } from './mock-api';

type Fixture = <T>(name: string) => T;

export interface ModerationHost {
  readonly session: SessionUserView | null;
}

const p = (status: number, detail: string, extra: Record<string, unknown> = {}): Reply => ({
  status,
  body: { title: 'Erro', status, detail, ...extra },
});

const HOW = 'As avaliações entram no ar sem revisão prévia. Um filtro procura sinais de fraude; as suspeitas saem da nota até uma pessoa da moderação decidir.';

export class ModerationState {
  readonly reasons: ReasonView[];
  moderator = false;
  private nextReport = 41;
  /** Integridade por lugar (id → view); sem entrada = sem alertas. */
  readonly integrity = new Map<string, IntegrityView>();
  myReports: MyReportView[] = [];
  myDecisions: MyDecisionView[] = [];
  cases: CaseDetailView[] = [];
  readonly relations = new Map<number, RelationView>();
  safety: SafetyView = {
    blocked: [],
    muted: [],
    words: [],
    defaultFilter: { enabled: false, locked: false, size: 18 },
    maxWords: 100,
  };

  constructor(
    fixture: Fixture,
    private readonly host: ModerationHost,
  ) {
    this.reasons = fixture<ReasonView[]>('motivos-denuncia');
  }

  /** Lugar com alerta ativo e novas avaliações congeladas. */
  freeze(placeId: string): IntegrityView {
    const until = new Date(Date.now() + 5 * 86_400_000).toISOString();
    const alert = {
      id: 7,
      kind: { code: 'ATIVIDADE_SUSPEITA', label: 'Atividade fora do padrão', group: null },
      message: 'Este lugar recebeu muitas avaliações em pouco tempo, várias de contas novas. Elas estão em análise e fora da nota até a decisão.',
      freezeReviews: true,
      startsAt: new Date(Date.now() - 86_400_000).toISOString(),
      endsAt: until,
      liftedAt: null,
      automated: true,
      active: true,
    };
    const view: IntegrityView = { establishmentId: Number(placeId), frozen: true, frozenUntil: until, active: [alert], history: [alert], howWeVerify: HOW };
    this.integrity.set(placeId, view);
    return view;
  }

  /** Um caso aberto na fila e outro em recurso, para a área da moderação. */
  seedCases(): void {
    const base = (id: number, status: CaseSummaryView['status'], extra: Partial<CaseSummaryView> = {}): CaseSummaryView => ({
      id,
      protocol: `CASO-${String(id).padStart(6, '0')}`,
      target: { type: 'COMMENT', typeLabel: 'Comentário', id: '11', path: '/posts/0199a1b2-c3d4-7e5f-8a9b-000000000001#comentarios', excerpt: 'Fui ontem e confirmo: o tutu acaba cedo mesmo!' },
      topReason: { code: 'ASSEDIO', label: 'Assédio ou cyberbullying', group: null },
      priority: { code: 'ALTA', label: 'Alta', level: 2 },
      status,
      reportCount: 2,
      preventive: false,
      authorityReferral: 'NONE',
      dueAt: new Date(Date.now() + 3 * 86_400_000).toISOString(),
      overdue: false,
      createdAt: '2026-10-30T12:00:00Z',
      updatedAt: '2026-10-30T12:00:00Z',
      ...extra,
    });
    const detail = (summary: CaseSummaryView, extra: Partial<CaseDetailView> = {}): CaseDetailView => ({
      summary,
      snapshot: { body: summary.target.excerpt ?? '' },
      currentState: 'No ar',
      targetOwnerId: 205,
      establishmentId: null,
      authorityReference: null,
      authoritySentAt: null,
      reports: [
        {
          id: 40,
          protocol: 'DEN-2026-000040',
          source: { code: 'USER', label: 'Pessoa usuária', group: null },
          reason: { code: 'ASSEDIO', label: 'Assédio ou cyberbullying', group: null },
          details: 'Ataques repetidos nos comentários.',
          reporter: 'Conta #42',
          lowTrust: false,
          childSexualAbuse: false,
          status: 'OPEN',
          createdAt: '2026-10-30T12:00:00Z',
          resolvedAt: null,
        },
      ],
      signals: [],
      decisions: [],
      appeals: [],
      allowedActions: [
        { code: 'KEEP', label: 'Manter', group: null },
        { code: 'HIDE', label: 'Ocultar', group: null },
        { code: 'REMOVE', label: 'Remover', group: null },
        { code: 'WARN', label: 'Avisar o autor', group: null },
      ],
      viewerCanDecide: true,
      viewerBlockedReason: null,
      ...extra,
    });
    this.cases = [
      detail(
        base(1, { code: 'OPEN', label: 'Na fila', group: null }, {
          topReason: { code: 'CRIANCA_EM_RISCO', label: 'Criança ou adolescente em risco', group: null },
          priority: { code: 'CRITICA', label: 'Crítica', level: 0 },
          preventive: true,
          authorityReferral: 'PENDING',
        }),
        { currentState: 'Retirado preventivamente' },
      ),
      detail(base(2, { code: 'OPEN', label: 'Na fila', group: null })),
      detail(base(3, { code: 'IN_APPEAL', label: 'Em recurso', group: null }), {
        decisions: [
          {
            id: 90,
            stage: 'FIRST',
            action: { code: 'HIDE', label: 'Ocultar', group: null },
            policy: { code: 'ASSEDIO', label: 'Assédio ou cyberbullying', group: null },
            statement: 'Humilha uma pessoa identificada.',
            automated: false,
            origin: 'Decisão de uma pessoa da moderação',
            moderatorId: 77,
            createdAt: '2026-10-29T12:00:00Z',
          },
        ],
        appeals: [
          { id: 5, role: 'AUTHOR', reason: 'Era brincadeira entre amigos.', status: 'OPEN', decisionId: 90, resultDecisionId: null, createdAt: '2026-10-30T12:00:00Z', resolvedAt: null },
        ],
      }),
    ];
  }

  handle(rec: Recorded): Reply | null {
    const { method: m, path } = rec;
    const body = (rec.body ?? {}) as Record<string, unknown>;
    const s = this.host.session;

    if (m === 'GET' && path === '/api/v1/reports/reasons') return { status: 200, body: this.reasons };
    const integrity = /^\/api\/v1\/establishments\/(\d+)\/integrity$/.exec(path);
    if (integrity && m === 'GET') {
      return { status: 200, body: this.integrity.get(integrity[1]!) ?? { establishmentId: Number(integrity[1]), frozen: false, frozenUntil: null, active: [], history: [], howWeVerify: HOW } };
    }
    if (m === 'POST' && path === '/api/v1/reports/public') {
      if (!body.name || !body.email) return p(422, 'Informe seu nome.', { errors: ['Informe seu nome (2 a 120 caracteres): a denúncia não pode ser anônima.'] });
      return { status: 201, body: this.receipt() };
    }
    if (m === 'POST' && path === '/api/v1/reports') {
      if (!s) return p(401, 'Entre na sua conta para continuar.');
      const reason = this.reasons.find((r) => r.code === body.reason);
      this.myReports.unshift({
        protocol: `DEN-2026-0000${this.nextReport}`,
        target: { type: body.targetType as MyReportView['target']['type'], typeLabel: 'Post', id: String(body.targetId), path: null, excerpt: null },
        reason: { code: String(body.reason), label: reason?.label ?? '', group: null },
        status: 'RECEBIDA',
        statusLabel: 'Recebida, em análise',
        outcome: null,
        outcomeLabel: null,
        createdAt: new Date().toISOString(),
        resolvedAt: null,
        caseId: 9,
        canAppeal: false,
        appealDeadline: null,
        appealStatus: null,
      });
      return { status: 201, body: this.receipt(body.reason === 'CRIANCA_EM_RISCO') };
    }
    if (m === 'POST' && /^\/api\/v1\/establishments\/\d+\/reviews\/\d+\/contest$/.test(path)) {
      if (!s) return p(401, 'Entre na sua conta para continuar.');
      return { status: 201, body: this.receipt() };
    }

    if (path === '/api/v1/me/roles' && m === 'GET') return s ? { status: 200, body: { moderator: this.moderator } } : p(401, 'Entre.');
    if (path === '/api/v1/me/reports' && m === 'GET') return s ? { status: 200, body: this.myReports } : p(401, 'Entre.');
    if (path === '/api/v1/me/moderation/decisions' && m === 'GET') return s ? { status: 200, body: this.myDecisions } : p(401, 'Entre.');
    const appeal = /^\/api\/v1\/me\/moderation\/cases\/(\d+)\/appeal$/.exec(path);
    if (appeal && m === 'POST') {
      const reason = String(body.reason ?? '');
      if (reason.trim().length < 20) return p(422, 'Explique o recurso em 20 a 2000 caracteres.', { errors: ['Explique o recurso em 20 a 2000 caracteres.'] });
      const view = { id: 3, role: 'AUTHOR' as const, reason, status: 'OPEN' as const, decisionId: 1, resultDecisionId: null, createdAt: new Date().toISOString(), resolvedAt: null };
      this.myDecisions = this.myDecisions.map((d) => (d.caseId === Number(appeal[1]) ? { ...d, canAppeal: false, appeal: view, howToAppeal: 'Seu recurso foi registrado.' } : d));
      return { status: 201, body: view };
    }

    // ---- segurança entre usuários (RF19)
    if (path === '/api/v1/me/safety' && m === 'GET') return s ? { status: 200, body: this.safety } : p(401, 'Entre.');
    if (path === '/api/v1/me/safety' && m === 'PATCH') {
      if (this.safety.defaultFilter.locked && body.defaultFilter === false) return p(403, 'Para menores de 16 anos o filtro de ofensas fica ligado.');
      this.safety = { ...this.safety, defaultFilter: { ...this.safety.defaultFilter, enabled: body.defaultFilter === true } };
      return { status: 200, body: this.safety };
    }
    if (path === '/api/v1/me/filtered-words' && m === 'PUT') {
      const words = [...new Set(((body.words as string[]) ?? []).map((w) => w.trim().toLowerCase().normalize('NFD').replace(/\p{M}/gu, '')))];
      const bad = words.filter((w) => w.length < 2);
      if (bad.length) return p(422, 'Palavra curta.', { errors: bad.map((w) => `"${w}": cada palavra ou expressão tem de 2 a 40 letras.`) });
      this.safety = { ...this.safety, words: words.sort() };
      return { status: 200, body: this.safety };
    }
    const rel = /^\/api\/v1\/me\/(relations|blocks|mutes)\/(\d+)$/.exec(path);
    if (rel) {
      if (!s) return p(401, 'Entre.');
      const id = Number(rel[2]);
      const current = this.relations.get(id) ?? { userId: id, blocked: false, muted: false };
      let next = current;
      if (rel[1] === 'blocks') next = { ...current, blocked: m === 'PUT' };
      if (rel[1] === 'mutes') next = { ...current, muted: m === 'PUT' };
      this.relations.set(id, next);
      return { status: 200, body: next };
    }

    // ---- área da moderação
    if (path.startsWith('/api/v1/moderation/')) {
      if (!s) return p(401, 'Entre.');
      if (!this.moderator) return p(403, 'Só a equipe de moderação acessa esta área.');
      if (path === '/api/v1/moderation/cases' && m === 'GET') {
        const status = rec.search.get('status') ?? 'OPEN';
        const items = this.cases.filter((c) => c.summary.status.code === status).map((c) => c.summary);
        return { status: 200, body: { items, page: 0, size: 20, total: items.length } };
      }
      const one = /^\/api\/v1\/moderation\/cases\/(\d+)(\/[a-z-]+)?$/.exec(path);
      if (one) {
        const c = this.cases.find((x) => x.summary.id === Number(one[1]));
        if (!c) return p(404, 'Caso não encontrado.');
        if (!one[2] && m === 'GET') return { status: 200, body: c };
        if (one[2] === '/decision' && m === 'POST') {
          if (String(body.statement ?? '').trim().length < 20) return p(422, 'Escreva a fundamentação.', { errors: ['Escreva a fundamentação da decisão em 20 a 2000 caracteres: ela vai para o autor.'] });
          const action = c.allowedActions.find((a) => a.code === body.action)!;
          const policy = this.reasons.find((r) => r.code === body.policyReason);
          const updated: CaseDetailView = {
            ...c,
            summary: { ...c.summary, status: { code: 'DECIDED', label: 'Decidido', group: null } },
            currentState: body.action === 'KEEP' || body.action === 'WARN' ? 'No ar' : 'Ocultado pela moderação',
            decisions: [
              ...c.decisions,
              {
                id: 99,
                stage: 'FIRST',
                action,
                policy: policy ? { code: policy.code, label: policy.label, group: null } : null,
                statement: String(body.statement),
                automated: false,
                origin: 'Decisão de uma pessoa da moderação',
                moderatorId: s.id,
                createdAt: new Date().toISOString(),
              },
            ],
            viewerCanDecide: false,
            viewerBlockedReason: 'Caso decidido; aguardando o prazo de recurso.',
          };
          this.cases = this.cases.map((x) => (x.summary.id === c.summary.id ? updated : x));
          return { status: 200, body: updated };
        }
        if (one[2] === '/authority-referral' && m === 'POST') {
          const updated: CaseDetailView = {
            ...c,
            summary: { ...c.summary, authorityReferral: 'SENT' },
            authorityReference: String(body.reference),
            authoritySentAt: new Date().toISOString(),
          };
          this.cases = this.cases.map((x) => (x.summary.id === c.summary.id ? updated : x));
          return { status: 200, body: updated };
        }
      }
      if (path === '/api/v1/moderation/stats' && m === 'GET') {
        return {
          status: 200,
          body: {
            from: '2026-10-01T00:00:00Z',
            to: '2026-10-31T00:00:00Z',
            openCases: 2,
            inAppeal: 1,
            overdue: 0,
            reportsByReason: [{ code: 'ASSEDIO', label: 'Assédio ou cyberbullying', count: 3 }],
            reportsBySource: [{ code: 'USER', label: 'Pessoa usuária', count: 3 }],
            decisionsByAction: [{ code: 'FIRST:HIDE:H', label: 'Decisão · Ocultar · humana', count: 1 }],
            decisionsByPolicy: [{ code: 'ASSEDIO', label: 'Assédio ou cyberbullying', count: 1 }],
            appeals: [{ code: 'OPEN', label: 'Em análise', count: 1 }],
            authorityReferrals: [{ code: 'PENDING', label: 'A comunicar', count: 1 }],
          },
        };
      }
    }
    return null;
  }

  private receipt(child = false) {
    const id = this.nextReport++;
    return {
      protocol: `DEN-2026-0000${id}`,
      status: 'RECEBIDA',
      message: child
        ? 'Recebemos a sua denúncia. O conteúdo saiu do ar na hora enquanto a moderação analisa. Se uma criança corre risco agora, ligue 190 ou disque 100.'
        : 'Recebemos a sua denúncia. Uma pessoa da moderação analisa e você acompanha o resultado pelo protocolo.',
      createdAt: new Date().toISOString(),
      reviewBy: new Date(Date.now() + 3 * 86_400_000).toISOString(),
    };
  }
}
