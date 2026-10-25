'use client';

// Consultas do feed, da página do post, dos perfis e do estúdio de moldura (RF9 · RF12 · RF14 · RF15 · RF16).
import type {
  AccountTypeCode,
  FeedPageView,
  PostCardView,
  PostDetailView,
  PostKindCode,
  PreferencesView,
  ProfileListParams,
} from '@friendly-eats/api';
import { useInfiniteQuery, useQuery, type InfiniteData, type QueryClient } from '@tanstack/react-query';
import { useSyncExternalStore } from 'react';
import { api } from './api';
import { PREFERENCES_KEY, useSession } from './queries';

export const PAGE_SIZE = 10;

export const vocabularyKey = (userId: number | null) => ['posts', 'vocabulario', userId] as const;
export const feedKey = (feed: 'POST' | 'ALERT', kind: string) => ['posts', 'feed', feed, kind] as const;
export const trendingKey = (feed: 'POST' | 'ALERT', category: string) => ['posts', 'em-alta', feed, category] as const;
export const postKey = (id: string) => ['post', id] as const;
export const commentsKey = (id: string) => ['post', id, 'comentarios'] as const;
export const placePostsKey = (id: string) => ['lugar', id, 'posts'] as const;
export const profilesKey = (params: ProfileListParams) => ['perfis', params] as const;
export const publicProfileKey = (id: string) => ['perfil', id] as const;
export const profilePostsKey = (id: string) => ['perfil', id, 'posts'] as const;
export const CATALOG_KEY = ['molduras', 'catalogo'] as const;

/** Vocabulário do formulário; com sessão traz os tipos que a conta publica (por isso a chave leva a conta). */
export function useVocabulary() {
  const session = useSession();
  const userId = session.data?.id ?? null;
  return useQuery({
    queryKey: vocabularyKey(userId),
    queryFn: () => api.posts.vocabulary(),
    enabled: !session.isPending,
    staleTime: 10 * 60_000,
  });
}

function nextCursor(last: FeedPageView): string | undefined {
  return last.nextCursor ?? undefined;
}

export function useFeed(feed: 'POST' | 'ALERT', kind: PostKindCode | '') {
  return useInfiniteQuery({
    queryKey: feedKey(feed, kind),
    queryFn: ({ pageParam, signal }) =>
      api.posts.feed({ feed, kind: kind || undefined, cursor: pageParam, size: PAGE_SIZE }, signal),
    initialPageParam: undefined as string | undefined,
    getNextPageParam: nextCursor,
  });
}

export function useTrending(feed: 'POST' | 'ALERT', category: AccountTypeCode | '') {
  return useQuery({
    queryKey: trendingKey(feed, category),
    queryFn: ({ signal }) => api.posts.trending({ feed, category: category || undefined, size: 5 }, signal),
    staleTime: 60_000,
  });
}

export function usePost(id: string, enabled = true) {
  return useQuery({ queryKey: postKey(id), queryFn: () => api.posts.get(id), enabled: enabled && !!id });
}

export function useComments(id: string, enabled = true) {
  return useInfiniteQuery({
    queryKey: commentsKey(id),
    queryFn: ({ pageParam }) => api.posts.comments(id, pageParam, 20),
    initialPageParam: 0,
    getNextPageParam: (last) => last.nextAfter ?? undefined,
    enabled,
  });
}

export function usePlacePosts(id: string) {
  return useInfiniteQuery({
    queryKey: placePostsKey(id),
    queryFn: ({ pageParam, signal }) => api.establishments.posts(id, { cursor: pageParam, size: 6 }, signal),
    initialPageParam: undefined as string | undefined,
    getNextPageParam: nextCursor,
  });
}

export function useProfiles(params: ProfileListParams) {
  return useInfiniteQuery({
    queryKey: profilesKey(params),
    queryFn: ({ pageParam, signal }) => api.users.list({ ...params, cursor: pageParam, size: PAGE_SIZE }, signal),
    initialPageParam: undefined as string | undefined,
    getNextPageParam: (last) => last.nextCursor ?? undefined,
  });
}

export function usePublicProfile(id: string) {
  return useQuery({ queryKey: publicProfileKey(id), queryFn: () => api.users.profile(id) });
}

export function useProfilePosts(id: string, enabled: boolean) {
  return useInfiniteQuery({
    queryKey: profilePostsKey(id),
    queryFn: ({ pageParam }) => api.users.posts(id, { cursor: pageParam, size: PAGE_SIZE }),
    initialPageParam: undefined as string | undefined,
    getNextPageParam: nextCursor,
    enabled,
  });
}

/** Catálogo da moldura: a API manda ETag e max-age de 1 h; aqui ele fica 1 h em memória. */
export function useFrameCatalog() {
  return useQuery({ queryKey: CATALOG_KEY, queryFn: () => api.backgrounds.catalog(), staleTime: 60 * 60_000, gcTime: 60 * 60_000 });
}

export function useFrameRecommendations(category: AccountTypeCode | null, media: 'TEXT' | 'IMAGE', enabled: boolean) {
  return useQuery({
    queryKey: ['molduras', 'recomendacoes', category, media] as const,
    queryFn: () => api.backgrounds.recommendations(category!, 'POST', media),
    enabled: enabled && !!category,
    staleTime: 10 * 60_000,
  });
}

// ------------------------------------------------------------ cache: o mesmo post no detalhe e nos feeds

type CardPatch = (card: PostCardView) => PostCardView;

/** Atualiza o post na página dele e em todo feed já carregado (curtidas, salvos, comentários, arquivar). */
export function patchPostEverywhere(qc: QueryClient, id: string, detail: (p: PostDetailView) => PostDetailView, card: CardPatch): void {
  qc.setQueryData<PostDetailView>(postKey(id), (p) => (p ? detail(p) : p));
  const feeds = qc.getQueriesData<InfiniteData<FeedPageView>>({
    predicate: (q) => {
      const k = q.queryKey;
      return (k[0] === 'posts' && k[1] === 'feed') || (k[0] === 'lugar' && k[2] === 'posts') || (k[0] === 'perfil' && k[2] === 'posts');
    },
  });
  for (const [key, data] of feeds) {
    if (!data?.pages) continue;
    qc.setQueryData<InfiniteData<FeedPageView>>(key, {
      ...data,
      pages: data.pages.map((page) => ({ ...page, items: page.items.map((c) => (c.id === id ? card(c) : c)) })),
    });
  }
}

// ------------------------------------------------------------ movimento

function subscribeReducedMotion(cb: () => void): () => void {
  const mq = window.matchMedia('(prefers-reduced-motion: reduce)');
  mq.addEventListener('change', cb);
  return () => mq.removeEventListener('change', cb);
}

/**
 * Pode animar a moldura? Não com "Reduzir movimento" do sistema (prefers-reduced-motion) nem com a preferência
 * {@code reduceMotion} da conta (RF2). Na dúvida (servidor, preferências carregando), não anima.
 */
export function useMotionAllowed(): boolean {
  const systemReduce = useSyncExternalStore(
    subscribeReducedMotion,
    () => window.matchMedia('(prefers-reduced-motion: reduce)').matches,
    () => true,
  );
  const session = useSession();
  const prefs = useQuery<PreferencesView>({
    queryKey: PREFERENCES_KEY,
    queryFn: () => api.me.preferences(),
    enabled: !!session.data,
    staleTime: 5 * 60_000,
  });
  if (systemReduce) return false;
  if (session.isPending) return false;
  if (session.data && (prefs.isPending || prefs.data?.reduceMotion)) return false;
  return true;
}
