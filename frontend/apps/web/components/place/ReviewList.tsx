'use client';

import type { DimensionItem, ReviewView } from '@friendly-eats/api';
import { keepPreviousData, useQuery } from '@tanstack/react-query';
import { useState, type ReactNode } from 'react';
import { AccountBadge } from '@/components/AccountBadge';
import { Notice } from '@/components/Form';
import { IconAppeal } from '@/components/icons';
import { ContentActions } from '@/components/moderation/ContentActions';
import { ContestDialog } from '@/components/moderation/ContestDialog';
import { Pagination } from '@/components/Pagination';
import { starOptionLabel } from '@/components/StarRatingInput';
import { Stars } from '@/components/Stars';
import { api } from '@/lib/api';
import { formatCents, formatDate, plural } from '@/lib/format';
import { reviewsKey, useSession } from '@/lib/queries';

const PAGE_SIZE = 10;

/** Selo escrito do vínculo declarado (RF13): a cor nunca é o único sinal. */
const DISCLOSURE_TEXT: Record<string, string> = {
  REVIEW_INCENTIVIZED: 'Declarou cortesia ou convite · pesa menos na nota',
  REVIEW_CONFLICT: 'Declarou vínculo com o lugar · fora da nota',
};

/**
 * Uma avaliação: nota (estrelas decorativas + "4 – Bom"), modo, dimensões, gasto e texto; o selo do vínculo declarado
 * e, para o autor, a situação na moderação (RF13). {@code actions}: menu "…" (denunciar ou contestar).
 */
export function ReviewItem({
  review,
  dimensions,
  headingLevel = 3,
  actions,
}: {
  review: ReviewView;
  dimensions: DimensionItem[];
  headingLevel?: 3 | 4;
  actions?: ReactNode;
}) {
  const H = `h${headingLevel}` as 'h3';
  const dims = dimensions.filter((d) => review.dimensions[d.code] !== undefined);
  return (
    <article className="grid gap-2" aria-label={`Avaliação de ${review.author.name}`}>
      <div className="flex flex-wrap items-center gap-x-3 gap-y-1">
        <H className="font-bold">{review.author.name}</H>
        <span className="text-sm text-muted">@{review.author.handle}</span>
        <span className="text-sm">
          <AccountBadge code={review.author.category.code} label={review.author.category.label} />
        </span>
        {actions && <span className="ml-auto">{actions}</span>}
      </div>
      {review.status === 'UNDER_REVIEW' && (
        <Notice>
          <p>Em análise pela moderação: fica fora da lista e da nota até a decisão. Você recebe o resultado em Configurações → Denúncias e decisões.</p>
        </Notice>
      )}
      {review.status === 'REMOVED' && (
        <Notice tone="error">
          <p>Removida pela moderação. O motivo e o recurso estão em Configurações → Denúncias e decisões.</p>
        </Notice>
      )}
      {DISCLOSURE_TEXT[review.trust] && <p className="text-sm font-semibold">{DISCLOSURE_TEXT[review.trust]}</p>}
      <p className="flex flex-wrap items-center gap-x-2 gap-y-1">
        <span role="img" aria-label={`Nota ${review.rating} de 5: ${review.ratingLabel}`} className="inline-flex items-center gap-2">
          <Stars average={review.rating} size={18} />
          <strong>{starOptionLabel(review.rating)}</strong>
        </span>
        <span className="text-sm text-muted">
          · {review.visitMode.label} · {formatDate(review.updatedAt)}
          {review.edited ? ' · editada' : ''}
        </span>
      </p>
      {dims.length > 0 && (
        <ul className="flex flex-wrap gap-x-4 gap-y-1 text-sm" aria-label="Notas por dimensão">
          {dims.map((d) => (
            <li key={d.code}>
              <span className="text-muted">{d.label}:</span> <strong>{review.dimensions[d.code]}</strong> de 5
            </li>
          ))}
        </ul>
      )}
      {review.spentPerPersonCents !== null && (
        <p className="text-sm text-muted">Gastou {formatCents(review.spentPerPersonCents)} por pessoa</p>
      )}
      {review.body && <p className="whitespace-pre-line">{review.body}</p>}
    </article>
  );
}

/**
 * Lista paginada pública (GET /api/v1/establishments/{id}/reviews), mais recentes primeiro. Cada avaliação tem o menu
 * "…": quem visita denuncia; o dono do lugar contesta (RC-SEG-08), sem ver dados de quem avaliou.
 */
export function ReviewList({ placeId, dimensions, ownerId = null }: { placeId: string; dimensions: DimensionItem[]; ownerId?: number | null }) {
  const [page, setPage] = useState(0);
  const session = useSession();
  const isOwner = ownerId != null && session.data?.id === ownerId;
  const q = useQuery({
    queryKey: reviewsKey(placeId, page),
    queryFn: () => api.establishments.reviews(placeId, page, PAGE_SIZE),
    placeholderData: keepPreviousData,
  });
  return (
    <section aria-labelledby="titulo-avaliacoes" className="grid gap-4">
      <div className="flex flex-wrap items-baseline justify-between gap-2">
        <h2 id="titulo-avaliacoes" className="text-xl font-bold">
          Avaliações
        </h2>
        {q.data && <p className="text-muted">{plural(q.data.total, 'avaliação', 'avaliações')}</p>}
      </div>
      {q.isPending && <p className="text-muted">Carregando avaliações…</p>}
      {q.isError && (
        <Notice tone="error">
          <p>Não foi possível carregar as avaliações. {q.error instanceof Error ? q.error.message : ''}</p>
        </Notice>
      )}
      {q.data && q.data.items.length === 0 && <p className="text-muted">Ainda ninguém avaliou este lugar.</p>}
      {q.data && q.data.items.length > 0 && (
        <ol className="grid gap-4" data-testid="lista-avaliacoes" aria-busy={q.isFetching}>
          {q.data.items.map((r) => (
            <li key={r.id} className="fe-card p-4">
              <ReviewItem
                review={r}
                dimensions={dimensions}
                actions={
                  isOwner ? (
                    <ContestAction placeId={placeId} reviewId={r.id} author={r.author.name} />
                  ) : session.data?.handle !== r.author.handle ? (
                    <ContentActions targetType="REVIEW" targetId={r.id} context={`a avaliação de ${r.author.name}`} />
                  ) : null
                }
              />
            </li>
          ))}
        </ol>
      )}
      {q.data && (
        <Pagination
          page={q.data.page}
          size={q.data.size}
          total={q.data.total}
          onPage={setPage}
          label="Páginas de avaliações"
        />
      )}
    </section>
  );
}

/** Dono do lugar: "Contestar avaliação" no menu "…" (RF13). */
function ContestAction({ placeId, reviewId, author }: { placeId: string; reviewId: number; author: string }) {
  const [open, setOpen] = useState(false);
  return (
    <>
      <ContentActions
        targetType="REVIEW"
        targetId={reviewId}
        context={`a avaliação de ${author}`}
        canReport={false}
        extra={[{ key: 'contestar', label: 'Contestar avaliação', icon: <IconAppeal size={16} />, onSelect: () => setOpen(true) }]}
      />
      <ContestDialog open={open} onClose={() => setOpen(false)} placeId={placeId} reviewId={reviewId} author={author} />
    </>
  );
}
