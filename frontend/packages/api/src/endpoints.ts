// Um método por rota da API usada pelo web (controllers em src/main/java/com/friendlyeats/api/).
import { createClient, type ApiClient, type ClientOptions } from './client.ts';
import type {
  AccountTypeCode,
  AgeAppealView,
  AgeStartView,
  AgeVerificationView,
  DataExportView,
  GuardianRequestView,
  MinorLinkView,
  PrivacyContactView,
  ProtectionCode,
  SupervisedView,
  SupervisionView,
  CommentCreatedView,
  CommentPageView,
  ConsentList,
  ConsentPurpose,
  ConsentView,
  DeletionRequest,
  DeletionView,
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

    backgrounds: {
      /** Catálogo do estúdio de moldura (RF16, ETag + max-age 1 h: o navegador revalida). */
      catalog: () => client.get<FrameCatalog>('/api/v1/backgrounds/catalog'),
      recommendations: (category: AccountTypeCode, feed: 'POST' | 'ALERT' = 'POST', media: 'TEXT' | 'IMAGE' = 'TEXT') =>
        client.get<FrameRecommendation[]>('/api/v1/backgrounds/recommendations', { query: { category, feed, media } }),
    },

    // ============================================================ RF17 · idade verificada e responsável
    // (AgeVerificationController, GuardianController)
    age: {
      status: () => client.get<AgeVerificationView>('/api/v1/me/age-verification'),
      /** 503 sem fornecedor; 403 para quem declarou menos de 18. */
      start: () => client.post<AgeStartView>('/api/v1/me/age-verification/start'),
      /** O servidor pergunta o resultado ao fornecedor; params = o que o fornecedor devolveu na volta. */
      complete: (reference: string, params: Record<string, string> = {}) =>
        client.post<AgeVerificationView>('/api/v1/me/age-verification/complete', { reference, params }),
      appeal: (reason: string) => client.post<AgeAppealView>('/api/v1/me/age-verification/appeal', { reason }),
    },

    guardian: {
      // lado do menor
      status: () => client.get<MinorLinkView>('/api/v1/me/guardian'),
      /** Sem e-mail: reenvia o convite pendente. */
      invite: (guardianEmail?: string) => client.post<MinorLinkView>('/api/v1/me/guardian/invite', { guardianEmail: guardianEmail ?? null }),
      request: (protection: ProtectionCode) => client.post<GuardianRequestView>('/api/v1/me/guardian/requests', { protection }),
      unlink: () => client.delJson<MinorLinkView>('/api/v1/me/guardian'),
      // lado do responsável
      accept: (token: string) => client.post<SupervisedView>('/api/v1/guardian/links/accept', { token }),
      minors: () => client.get<SupervisedView[]>('/api/v1/guardian/minors'),
      panel: (minorId: number | string) => client.get<SupervisionView>(`/api/v1/guardian/minors/${seg(minorId)}`),
      decide: (minorId: number | string, requestId: number, approve: boolean) =>
        client.post<SupervisionView>(`/api/v1/guardian/minors/${seg(minorId)}/requests/${seg(requestId)}`, { approve }),
      setProtection: (minorId: number | string, protection: ProtectionCode, allowed: boolean) =>
        client.put<SupervisionView>(`/api/v1/guardian/minors/${seg(minorId)}/protections/${seg(protection)}`, { allowed }).then((r) => r.data),
      setSensitiveConsent: (minorId: number | string, granted: boolean) =>
        client.put<SupervisionView>(`/api/v1/guardian/minors/${seg(minorId)}/sensitive-consent`, { granted }).then((r) => r.data),
      unlinkMinor: (minorId: number | string) => client.del(`/api/v1/guardian/minors/${seg(minorId)}`),
    },

    // ============================================================ RF18 · central do titular (PrivacyController)
    privacy: {
      /** Público: encarregado, direitos e prazos. */
      contact: () => client.get<PrivacyContactView>('/api/v1/legal/privacy-contact'),
      exports: () => client.get<DataExportView[]>('/api/v1/me/data-exports'),
      /** Modo sudo; 429 = já pediu nas últimas 24 h. */
      requestExport: (body: SudoRequest) => client.post<DataExportView>('/api/v1/me/data-exports', body),
    },
  };
}

export type Api = ReturnType<typeof createApi>;

export function createApiWith(options: ClientOptions): Api {
  return createApi(createClient(options));
}
