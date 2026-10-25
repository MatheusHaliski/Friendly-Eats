'use client';

import type { FeedPageView, PostCardView } from '@friendly-eats/api';
import type { InfiniteData, UseInfiniteQueryResult } from '@tanstack/react-query';
import type { ReactNode } from 'react';
import { Notice } from '@/components/Form';
import { useMotionAllowed } from '@/lib/post-queries';
import { PostCard } from './PostCard';

interface Props {
  query: UseInfiniteQueryResult<InfiniteData<FeedPageView>>;
  /** Nome da lista para o leitor de tela ("Posts", "Alertas sanitários"…). */
  label: string;
  framed?: boolean;
  empty: ReactNode;
  testId?: string;
  columns?: boolean;
  headingLevel?: 'h2' | 'h3';
}

/** Lista de posts com "Carregar mais" (paginação por cursor, RF14). */
export function PostList({ query, label, framed = true, empty, testId, columns = false, headingLevel }: Props) {
  const motion = useMotionAllowed();
  if (query.isPending) {
    return (
      <p role="status" className="text-muted">
        Carregando {label.toLowerCase()}…
      </p>
    );
  }
  if (query.isError) {
    return (
      <Notice tone="error" live>
        <p>Não foi possível carregar agora. {query.error instanceof Error ? query.error.message : ''}</p>
        <p>
          <button type="button" className="fe-btn fe-btn-sm" onClick={() => query.refetch()}>
            Tentar de novo
          </button>
        </p>
      </Notice>
    );
  }
  const items: PostCardView[] = query.data.pages.flatMap((p) => p.items);
  if (!items.length) return <div className="fe-card grid gap-2 p-6">{empty}</div>;
  return (
    <div className="grid gap-4">
      <ul aria-label={label} className={`grid gap-5 ${columns ? 'md:grid-cols-2' : ''}`} data-testid={testId} aria-busy={query.isFetching}>
        {items.map((p) => (
          <li key={p.id} className="min-w-0">
            <PostCard post={p} framed={framed} motion={motion} headingLevel={headingLevel} />
          </li>
        ))}
      </ul>
      <div className="flex flex-wrap items-center gap-3">
        {query.hasNextPage ? (
          <button type="button" className="fe-btn" onClick={() => query.fetchNextPage()} disabled={query.isFetchingNextPage}>
            {query.isFetchingNextPage ? 'Carregando…' : 'Carregar mais'}
          </button>
        ) : (
          <p className="text-sm text-muted">Fim da lista.</p>
        )}
        <p role="status" className="sr-only">
          {items.length} {items.length === 1 ? 'post carregado' : 'posts carregados'}
        </p>
      </div>
    </div>
  );
}
