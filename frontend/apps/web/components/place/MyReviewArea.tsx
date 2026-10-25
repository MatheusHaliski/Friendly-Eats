'use client';

import { isApiError, type EstablishmentView, type ReviewView } from '@friendly-eats/api';
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import Link from 'next/link';
import { useRouter } from 'next/navigation';
import { useRef, useState, type ReactNode } from 'react';
import { Notice } from '@/components/Form';
import { IconDelete, IconEdit, IconLogin, IconSuccess } from '@/components/icons';
import { canReview } from '@/lib/account-types';
import { api } from '@/lib/api';
import { myReviewKey, placeKey, useSession, useTaxonomy } from '@/lib/queries';
import { DIMENSIONS } from '@/lib/review';
import { loginHref } from '@/lib/safe-redirect';
import { ReviewForm } from './ReviewForm';
import { ReviewItem } from './ReviewList';

/**
 * "Avaliar" (RF8): quem não entrou vê o convite para entrar; organização e dono veem por que não avaliam;
 * os demais veem a própria avaliação (GET …/reviews/me) com editar e apagar, ou o formulário.
 */
export function MyReviewArea({ place }: { place: EstablishmentView }) {
  const id = String(place.id);
  const session = useSession();
  const user = session.data;
  const router = useRouter();
  const queryClient = useQueryClient();
  const taxonomy = useTaxonomy();
  const dimensions = taxonomy.data?.reviewDimensions ?? [...DIMENSIONS];
  const [editing, setEditing] = useState(false);
  const [confirmDelete, setConfirmDelete] = useState(false);
  const [message, setMessage] = useState<string | null>(null);
  const [deleteError, setDeleteError] = useState<string | null>(null);
  const headingRef = useRef<HTMLHeadingElement>(null);
  const voltar = `/lugares/${id}`;

  const eligible = !!user && canReview(user.category.code) && place.owner?.id !== user.id;
  const mine = useQuery({
    queryKey: myReviewKey(id),
    enabled: eligible,
    queryFn: async (): Promise<ReviewView | null> => {
      try {
        return await api.establishments.myReview(id);
      } catch (e) {
        if (isApiError(e, 404)) return null;
        throw e;
      }
    },
  });

  const refreshPlace = () => {
    void queryClient.invalidateQueries({ queryKey: placeKey(id) });
  };

  const remove = useMutation({
    mutationFn: () => api.establishments.deleteMyReview(id),
    onSuccess: () => {
      queryClient.setQueryData(myReviewKey(id), null);
      setConfirmDelete(false);
      setMessage('Sua avaliação foi apagada.');
      refreshPlace();
      headingRef.current?.focus();
    },
    onError: (e) => {
      if (isApiError(e, 401)) {
        router.push(loginHref(voltar));
        return;
      }
      if (isApiError(e, 404)) {
        queryClient.setQueryData(myReviewKey(id), null);
        setConfirmDelete(false);
        return;
      }
      setDeleteError(isApiError(e) ? e.detail : 'Não foi possível apagar agora. Tente de novo.');
    },
  });

  let body: ReactNode;
  if (session.isPending) {
    body = <p className="text-muted">Carregando…</p>;
  } else if (!user) {
    body = (
      <div className="grid gap-3">
        <p>Entre na sua conta para dar a sua nota a este lugar.</p>
        <p>
          <Link href={loginHref(voltar)} className="fe-btn fe-btn-primary">
            <IconLogin size={18} />
            Entrar para avaliar
          </Link>
        </p>
      </div>
    );
  } else if (!canReview(user.category.code)) {
    body = (
      <Notice>
        <p>Contas de organização não avaliam lugares. Avalie com a sua conta pessoal.</p>
      </Notice>
    );
  } else if (place.owner?.id === user.id) {
    body = (
      <Notice>
        <p>Você não pode avaliar o próprio estabelecimento.</p>
      </Notice>
    );
  } else if (mine.isPending) {
    body = <p className="text-muted">Carregando a sua avaliação…</p>;
  } else if (mine.isError && isApiError(mine.error, 401)) {
    body = (
      <p>
        <Link href={loginHref(voltar)} className="fe-btn fe-btn-primary">
          <IconLogin size={18} />
          Entrar para avaliar
        </Link>
      </p>
    );
  } else if (mine.data && !editing) {
    const review = mine.data;
    body = (
      <div className="grid gap-4">
        <ReviewItem review={review} dimensions={dimensions} />
        {deleteError && (
          <Notice tone="error" live>
            <p>{deleteError}</p>
          </Notice>
        )}
        {confirmDelete ? (
          <div className="flex flex-wrap items-center gap-2" role="group" aria-label="Confirmar exclusão">
            <p className="w-full font-semibold">Apagar a sua avaliação? Esta ação não pode ser desfeita.</p>
            <button type="button" className="fe-btn fe-btn-primary" onClick={() => remove.mutate()} disabled={remove.isPending}>
              <IconDelete size={18} />
              {remove.isPending ? 'Apagando…' : 'Sim, apagar'}
            </button>
            <button type="button" className="fe-btn" onClick={() => setConfirmDelete(false)}>
              Cancelar
            </button>
          </div>
        ) : (
          <div className="flex flex-wrap gap-2">
            <button
              type="button"
              className="fe-btn"
              onClick={() => {
                setMessage(null);
                setEditing(true);
              }}
            >
              <IconEdit size={18} />
              Editar avaliação
            </button>
            <button
              type="button"
              className="fe-btn"
              onClick={() => {
                setDeleteError(null);
                setConfirmDelete(true);
              }}
            >
              <IconDelete size={18} />
              Apagar avaliação
            </button>
          </div>
        )}
      </div>
    );
  } else {
    body = (
      <ReviewForm
        placeId={id}
        initial={mine.data ?? null}
        dimensions={dimensions}
        visitModes={taxonomy.data?.visitModes}
        onCancel={mine.data ? () => setEditing(false) : undefined}
        onSaved={(review, created) => {
          queryClient.setQueryData(myReviewKey(id), review);
          setEditing(false);
          setMessage(created ? 'Sua avaliação foi publicada.' : 'Sua avaliação foi atualizada.');
          refreshPlace();
          headingRef.current?.focus();
        }}
      />
    );
  }

  const hasOwn = eligible && !!mine.data;
  return (
    <section aria-labelledby="titulo-avaliar" className="fe-card grid gap-4 p-5">
      <h2 id="titulo-avaliar" ref={headingRef} tabIndex={-1} className="text-xl font-bold outline-none">
        {hasOwn && !editing ? 'Sua avaliação' : editing ? 'Editar a sua avaliação' : 'Avaliar'}
      </h2>
      <p role="status" className={message ? 'flex items-center gap-2 font-semibold' : 'sr-only'}>
        {message && <IconSuccess size={20} className="text-success" />}
        {message ?? ''}
      </p>
      {body}
    </section>
  );
}
