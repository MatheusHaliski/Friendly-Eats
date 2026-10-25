// Um método por rota da API usada pelo web (controllers em src/main/java/com/friendlyeats/api/).
import { createClient, type ApiClient, type ClientOptions } from './client.ts';
import type {
  EstablishmentView,
  LoginRequest,
  LoginResult,
  RegisterRequest,
  ReviewDraft,
  ReviewPage,
  ReviewView,
  SearchPage,
  SearchParams,
  SessionUserView,
  TaxonomyCatalog,
  TermsView,
} from './types.ts';

export function createApi(client: ApiClient = createClient()) {
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
    },
  };
}

export type Api = ReturnType<typeof createApi>;

export function createApiWith(options: ClientOptions): Api {
  return createApi(createClient(options));
}
