// Um método por rota da API usada pelo web (controllers em src/main/java/com/friendlyeats/api/).
import { createClient, type ApiClient, type ClientOptions } from './client.ts';
import type {
  AccountTypeCode,
  AlertRequest,
  ConsumerAlertView,
  AppealDecisionRequest,
  AppealView,
  CaseDetailView,
  CasePageView,
  CaseStatusCode,
  ContestRequest,
  DecisionRequest,
  IntegrityView,
  ModerationStatsView,
  MyDecisionView,
  MyReportView,
  PublicReportRequest,
  ReasonView,
  ReceiptView,
  RelationView,
  ReportRequest,
  RolesView,
  SafetyView,
  CommentCreatedView,
  CommentPageView,
  ConsentList,
  ConsentPurpose,
  ConsentView,
  DeletionRequest,
  DeletionView,
  EstablishmentDraft,
  EstablishmentView,
  FeedPageView,
  FeedParams,
  FrameCatalog,
  FrameRecommendation,
  LikeStateView,
  LoginRequest,
  LoginResult,
  MfaSetupView,
  MfaStatusView,
  PasswordChange,
  PostDetailView,
  PostDraft,
  PostPatch,
  PostVocabulary,
  PreferencesPatch,
  PreferencesView,
  ProfileListParams,
  ProfilePageView,
  ProfilePatch,
  ProfileView,
  PublicProfileView,
  RecoveryCodesView,
  RegisterRequest,
  ReviewDraft,
  ReviewPage,
  ReviewView,
  SaveStateView,
  SearchPage,
  SearchParams,
  SessionUserView,
  SessionView,
  ShareChannelCode,
  ShareResultView,
  SudoRequest,
  TaxonomyCatalog,
  TermsView,
  TrendingParams,
  UploadRequest,
  UploadTicket,
} from './types.ts';

const seg = (v: number | string) => encodeURIComponent(String(v));

export function createApi(client: ApiClient = createClient()) {
  const post = (id: string) => `/api/v1/posts/${seg(id)}`;
  return {
    client,

    auth: {
      /** AuthController.session: 401 sem sessão. */
      session: () => client.get<SessionUserView>('/api/v1/auth/session'),
      register: (body: RegisterRequest) => client.post<LoginResult>('/api/v1/auth/register', body),
      login: (body: LoginRequest) => client.post<LoginResult>('/api/v1/auth/login', body),
      /** Código do app autenticador ou de recuperação. */
      loginSecondFactor: (code: string) => client.post<LoginResult>('/api/v1/auth/login/2fa', { code }),
      reactivate: () => client.post<LoginResult>('/api/v1/auth/reactivate'),
      logout: () => client.post<void>('/api/v1/auth/logout'),
    },

    legal: {
      terms: () => client.get<TermsView>('/api/v1/legal/terms'),
    },

    taxonomy: () => client.get<TaxonomyCatalog>('/api/v1/taxonomy'),

    me: {
      verifyEmail: (code: string) => client.post<SessionUserView>('/api/v1/me/verify-email', { code }),
      resendVerification: () => client.post<void>('/api/v1/me/verify-email/resend'),
      establishments: () => client.get<EstablishmentView[]>('/api/v1/me/establishments'),

      // perfil e foto (RF2)
      profile: () => client.get<ProfileView>('/api/v1/me/profile'),
      updateProfile: (patch: ProfilePatch) => client.patch<ProfileView>('/api/v1/me/profile', patch),
      /** objectKey do POST /api/v1/media/uploads, depois do PUT no bucket. */
      setPhoto: (objectKey: string) => client.put<ProfileView>('/api/v1/me/photo', { objectKey }).then((r) => r.data),
      removePhoto: () => client.delJson<ProfileView>('/api/v1/me/photo'),

      // preferências (RF2, RF3)
      preferences: () => client.get<PreferencesView>('/api/v1/me/preferences'),
      updatePreferences: (patch: PreferencesPatch) => client.patch<PreferencesView>('/api/v1/me/preferences', patch),

      // consentimentos (RF3)
      consents: () => client.get<ConsentList>('/api/v1/me/consents'),
      setConsent: (purpose: ConsentPurpose, granted: boolean) =>
        client.put<ConsentView>(`/api/v1/me/consents/${encodeURIComponent(purpose)}`, { granted }).then((r) => r.data),

      // senha, sessões e 2FA (RF4)
      changePassword: (body: PasswordChange) => client.post<{ otherSessionsClosed: number }>('/api/v1/me/password', body),
      sessions: () => client.get<SessionView[]>('/api/v1/me/sessions'),
      endSession: (id: string) => client.del(`/api/v1/me/sessions/${encodeURIComponent(id)}`),
      endOtherSessions: () => client.delJson<{ closed: number }>('/api/v1/me/sessions'),
      mfa: {
        status: () => client.get<MfaStatusView>('/api/v1/me/mfa'),
        /** Passo 1: senha (modo sudo); devolve a chave e a otpauth:// do QR code. */
        start: (body: SudoRequest) => client.post<MfaSetupView>('/api/v1/me/mfa/totp', body),
        /** Passo 2: o código do app liga o 2FA e devolve os 10 códigos de recuperação. */
        confirm: (code: string) => client.post<RecoveryCodesView>('/api/v1/me/mfa/totp/confirm', { code }),
        regenerateCodes: (body: SudoRequest) => client.post<RecoveryCodesView>('/api/v1/me/mfa/recovery-codes', body),
        disable: (body: SudoRequest) => client.post<MfaStatusView>('/api/v1/me/mfa/disable', body),
      },

      // excluir conta (RF5): encerra todas as sessões, inclusive esta
      requestDeletion: (body: DeletionRequest) => client.post<DeletionView>('/api/v1/me/deletion', body),
    },

    media: {
      /** URL pré-assinada para enviar a foto direto ao bucket (RF2). */
      requestUpload: (body: UploadRequest) => client.post<UploadTicket>('/api/v1/media/uploads', body),
    },

    establishments: {
      /** Busca pública (RF10). */
      search: (params: SearchParams, signal?: AbortSignal) =>
        client.get<SearchPage>('/api/v1/establishments', { query: { ...params }, signal }),
      get: (id: number | string) => client.get<EstablishmentView>(`/api/v1/establishments/${encodeURIComponent(String(id))}`),
      /** Cadastro (RF6, RF11): 201 com a página do dono (published e publishBlockers). */
      create: (draft: EstablishmentDraft) => client.post<EstablishmentView>('/api/v1/establishments', draft),
      /** Edição pelo dono (RF6, RF11): bloco ausente não muda; 404 para quem não edita. */
      update: (id: number | string, draft: EstablishmentDraft) =>
        client.patch<EstablishmentView>(`/api/v1/establishments/${seg(id)}`, draft),
      reviews: (id: number | string, page = 0, size = 10) =>
        client.get<ReviewPage>(`/api/v1/establishments/${encodeURIComponent(String(id))}/reviews`, {
          query: { page, size },
        }),
      myReview: (id: number | string) =>
        client.get<ReviewView>(`/api/v1/establishments/${encodeURIComponent(String(id))}/reviews/me`),
      /** 201 cria, 200 substitui. */
      saveMyReview: async (id: number | string, draft: ReviewDraft) => {
        const res = await client.put<ReviewView>(`/api/v1/establishments/${encodeURIComponent(String(id))}/reviews/me`, draft);
        return { review: res.data, created: res.status === 201 };
      },
      deleteMyReview: (id: number | string) =>
        client.del(`/api/v1/establishments/${encodeURIComponent(String(id))}/reviews/me`),
      /** Posts sobre o lugar (RF14): 404/401 como a página do lugar. */
      posts: (id: number | string, params: { kind?: string; cursor?: string; size?: number } = {}, signal?: AbortSignal) =>
        client.get<FeedPageView>(`/api/v1/establishments/${seg(id)}/posts`, { query: { ...params }, signal }),
    },

    posts: {
      /** Vocabulário do formulário (RF9, público); com sessão traz publishableKinds. */
      vocabulary: () => client.get<PostVocabulary>('/api/v1/posts/vocabulary'),
      /** Feed de posts ou de alertas (RF14), por cursor. */
      feed: (params: FeedParams = {}, signal?: AbortSignal) => client.get<FeedPageView>('/api/v1/posts', { query: { ...params }, signal }),
      trending: (params: TrendingParams = {}, signal?: AbortSignal) =>
        client.get<FeedPageView>('/api/v1/posts/trending', { query: { ...params }, signal }),
      /** 401 = post 18+ sem login; 404 = não visível. */
      get: (id: string) => client.get<PostDetailView>(post(id)),
      create: (draft: PostDraft) => client.post<PostDetailView>('/api/v1/posts', draft),
      /** Chave ausente mantém; null limpa (o JSON leva o null). */
      update: (id: string, patch: PostPatch) => client.patch<PostDetailView>(post(id), patch),
      archive: (id: string) => client.post<PostDetailView>(`${post(id)}/archive`),
      restore: (id: string) => client.post<PostDetailView>(`${post(id)}/restore`),
      remove: (id: string) => client.del(post(id)),

      // interações (RF12): PUT/DELETE idempotentes
      like: (id: string) => client.put<LikeStateView>(`${post(id)}/like`).then((r) => r.data),
      unlike: (id: string) => client.delJson<LikeStateView>(`${post(id)}/like`),
      save: (id: string) => client.put<SaveStateView>(`${post(id)}/save`).then((r) => r.data),
      unsave: (id: string) => client.delJson<SaveStateView>(`${post(id)}/save`),
      comments: (id: string, after = 0, limit = 20) =>
        client.get<CommentPageView>(`${post(id)}/comments`, { query: { after, limit } }),
      comment: (id: string, body: string) => client.post<CommentCreatedView>(`${post(id)}/comments`, { body }),
      removeComment: (id: string, commentId: number) => client.del(`${post(id)}/comments/${seg(commentId)}`),
      share: (id: string, channel: ShareChannelCode) => client.post<ShareResultView>(`${post(id)}/shares`, { channel }),
    },

    users: {
      /** Feed de perfis (RF15). */
      list: (params: ProfileListParams = {}, signal?: AbortSignal) =>
        client.get<ProfilePageView>('/api/v1/users', { query: { ...params }, signal }),
      /** 404 = conta fora do ar; perfil privado vem com o mínimo. */
      profile: (id: number | string) => client.get<PublicProfileView>(`/api/v1/users/${seg(id)}/profile`),
      /** 403 = perfil privado para quem não é o titular. */
      posts: (id: number | string, params: { cursor?: string; size?: number } = {}) =>
        client.get<FeedPageView>(`/api/v1/users/${seg(id)}/posts`, { query: { ...params } }),
    },

    // ---- moderação (RF13): denúncia, recurso e integridade do lugar (ReportController) e a área da moderação
    // (ModerationController, só com ROLE_MODERATOR)
    reports: {
      /** Motivos fechados (público). */
      reasons: () => client.get<ReasonView[]>('/api/v1/reports/reasons'),
      /** 201 com o protocolo; 409 já denunciado; 429 limite de ritmo. */
      create: (body: ReportRequest) => client.post<ReceiptView>('/api/v1/reports', body),
      /** Sem conta (STF: canal para não usuários). */
      createPublic: (body: PublicReportRequest) => client.post<ReceiptView>('/api/v1/reports/public', body),
      /** Contestação do dono (RC-SEG-08): a avaliação fica no ar até a decisão. */
      contestReview: (placeId: number | string, reviewId: number, body: ContestRequest) =>
        client.post<ReceiptView>(`/api/v1/establishments/${seg(placeId)}/reviews/${seg(reviewId)}/contest`, body),
      integrity: (placeId: number | string) => client.get<IntegrityView>(`/api/v1/establishments/${seg(placeId)}/integrity`),
      mine: () => client.get<MyReportView[]>('/api/v1/me/reports'),
      myDecisions: () => client.get<MyDecisionView[]>('/api/v1/me/moderation/decisions'),
      appeal: (caseId: number, reason: string) => client.post<AppealView>(`/api/v1/me/moderation/cases/${seg(caseId)}/appeal`, { reason }),
      roles: () => client.get<RolesView>('/api/v1/me/roles'),
    },

    moderation: {
      queue: (status: CaseStatusCode = 'OPEN', page = 0, size = 20) =>
        client.get<CasePageView>('/api/v1/moderation/cases', { query: { status, page, size } }),
      get: (id: number) => client.get<CaseDetailView>(`/api/v1/moderation/cases/${seg(id)}`),
      decide: (id: number, body: DecisionRequest) => client.post<CaseDetailView>(`/api/v1/moderation/cases/${seg(id)}/decision`, body),
      decideAppeal: (id: number, body: AppealDecisionRequest) =>
        client.post<CaseDetailView>(`/api/v1/moderation/cases/${seg(id)}/appeal-decision`, body),
      authorityReferral: (id: number, reference: string) =>
        client.post<CaseDetailView>(`/api/v1/moderation/cases/${seg(id)}/authority-referral`, { reference }),
      createAlert: (placeId: number, body: AlertRequest) => client.post<ConsumerAlertView>(`/api/v1/moderation/establishments/${seg(placeId)}/alerts`, body),
      liftAlert: (placeId: number, alertId: number) =>
        client.post<ConsumerAlertView>(`/api/v1/moderation/establishments/${seg(placeId)}/alerts/${seg(alertId)}/lift`),
      stats: (days = 30) => client.get<ModerationStatsView>('/api/v1/moderation/stats', { query: { days } }),
    },

    // ---- segurança entre usuários (RF19, SafetyController): PUT/DELETE idempotentes
    safety: {
      mine: () => client.get<SafetyView>('/api/v1/me/safety'),
      setDefaultFilter: (defaultFilter: boolean) => client.patch<SafetyView>('/api/v1/me/safety', { defaultFilter }),
      setWords: (words: string[]) => client.put<SafetyView>('/api/v1/me/filtered-words', { words }).then((r) => r.data),
      relation: (userId: number) => client.get<RelationView>(`/api/v1/me/relations/${seg(userId)}`),
      block: (userId: number) => client.put<RelationView>(`/api/v1/me/blocks/${seg(userId)}`).then((r) => r.data),
      unblock: (userId: number) => client.delJson<RelationView>(`/api/v1/me/blocks/${seg(userId)}`),
      mute: (userId: number) => client.put<RelationView>(`/api/v1/me/mutes/${seg(userId)}`).then((r) => r.data),
      unmute: (userId: number) => client.delJson<RelationView>(`/api/v1/me/mutes/${seg(userId)}`),
    },

    backgrounds: {
      /** Catálogo do estúdio de moldura (RF16, ETag + max-age 1 h: o navegador revalida). */
      catalog: () => client.get<FrameCatalog>('/api/v1/backgrounds/catalog'),
      recommendations: (category: AccountTypeCode, feed: 'POST' | 'ALERT' = 'POST', media: 'TEXT' | 'IMAGE' = 'TEXT') =>
        client.get<FrameRecommendation[]>('/api/v1/backgrounds/recommendations', { query: { category, feed, media } }),
    },
  };
}

export type Api = ReturnType<typeof createApi>;

export function createApiWith(options: ClientOptions): Api {
  return createApi(createClient(options));
}
