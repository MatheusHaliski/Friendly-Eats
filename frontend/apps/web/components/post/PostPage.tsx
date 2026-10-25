'use client';

import { isApiError, type PostDetailView } from '@friendly-eats/api';
import Link from 'next/link';
import { useEffect } from 'react';
import { Notice } from '@/components/Form';
import { IconLogin } from '@/components/icons';
import { formatDateTime } from '@/lib/post-format';
import { useMotionAllowed, usePost } from '@/lib/post-queries';
import { loginHref } from '@/lib/safe-redirect';
import { AuthorActions } from './AuthorActions';
import { Comments } from './Comments';
import { InteractionBar } from './InteractionBar';
import {
  AlertDetails,
  AllergenList,
  AuthorLine,
  ClassificationTags,
  HazardBand,
  KindBadge,
  PeriodLine,
  PlaceLine,
  PriceLine,
  TaxonomyLine,
} from './PostBits';
import { PostFrame } from './PostFrame';

/** Página do post (RF14) com as interações (RF12) e as ações do autor (RF9). */
export function PostPage({ id }: { id: string }) {
  const post = usePost(id);
  const title = post.data?.title;
  useEffect(() => {
    if (title) document.title = `${title} · Friendly Eats`;
  }, [title]);

  if (post.isPending) {
    return (
      <p role="status" className="text-muted">
        Carregando o post…
      </p>
    );
  }
  if (post.isError) {
    const e = post.error;
    if (isApiError(e, 401)) {
      return (
        <div className="grid max-w-xl gap-4">
          <h1 className="text-3xl font-extrabold">Conteúdo para maiores de 18 anos</h1>
          <Notice>
            <p>{e.detail}</p>
          </Notice>
          <p>
            <Link href={loginHref(`/posts/${id}`)} className="fe-btn fe-btn-primary">
              <IconLogin size={18} />
              Entrar
            </Link>
          </p>
        </div>
      );
    }
    if (isApiError(e, 404)) {
      return (
        <div className="grid max-w-xl gap-4">
          <h1 className="text-3xl font-extrabold">Post não encontrado</h1>
          <p>Ele pode ter sido apagado, ser de um perfil privado ou o endereço estar errado.</p>
          <p>
            <Link href="/feed" className="underline">
              Voltar para o feed
            </Link>
          </p>
        </div>
      );
    }
    return (
      <Notice tone="error" live>
        <p>Não foi possível carregar o post. {e instanceof Error ? e.message : ''}</p>
        <p>
          <button type="button" className="fe-btn fe-btn-sm" onClick={() => post.refetch()}>
            Tentar de novo
          </button>
        </p>
      </Notice>
    );
  }
  return <PostContent post={post.data} />;
}

function PostContent({ post }: { post: PostDetailView }) {
  const motion = useMotionAllowed();
  const isAlert = post.kind.code === 'ALERTA_SANITARIO';
  const edited = post.updatedAt && post.updatedAt !== post.publishedAt;
  return (
    <div className="grid max-w-3xl gap-6">
      <p>
        <Link href={isAlert ? '/feed?aba=alertas' : '/feed'} className="inline-flex min-h-6 items-center underline">
          Voltar para o feed
        </Link>
      </p>
      <article aria-labelledby="titulo-post" className="grid gap-4">
        <PostFrame
          as="div"
          background={post.background}
          framed={!isAlert}
          border={post.kind.border}
          expired={post.expired}
          seed={post.id}
          motion={motion}
          testId="post"
        >
          {isAlert && <HazardBand author={post.author} host={post.alert?.sourceHost ?? null} />}
          <AuthorLine author={post.author} publishedAt={post.publishedAt} absolute />
          <div className="flex flex-wrap items-center gap-2">
            <KindBadge kind={post.kind} />
            <ClassificationTags
              ageRating={post.ageRating}
              ageRatingLabel={post.ageRatingLabel}
              advertisingLabel={post.advertisingLabel}
              expired={post.expired}
              status={post.status}
            />
          </div>
          <h1 id="titulo-post" className="text-2xl font-extrabold leading-tight sm:text-3xl">
            {post.title}
          </h1>
          {post.mediaUrl && (
            // eslint-disable-next-line @next/next/no-img-element -- foto pública do bucket, já tratada (sem EXIF)
            <img src={post.mediaUrl} alt={post.altText ?? ''} className="w-full rounded-md border border-line object-cover" />
          )}
          {post.body && <p className="whitespace-pre-line text-lg">{post.body}</p>}
          {post.alert && <AlertDetails alert={post.alert} />}
          <TaxonomyLine cuisine={post.cuisine} foodFormat={post.foodFormat} dietaryStyles={post.dietaryStyles} />
          <PriceLine price={post.price} />
          <PeriodLine startsAt={post.startsAt} endsAt={post.endsAt} label={post.kind.code === 'EVENTO' ? 'Quando' : 'Vigência'} />
          <PlaceLine place={post.establishment} city={post.city} />
          {post.recipe && <Recipe recipe={post.recipe} />}
          <AllergenList allergens={post.allergens} warnings={post.warnings} notice={post.allergenNotice} />
          {!post.allergens.length && !post.warnings.length && post.allergenNotice && <p className="text-sm text-muted">{post.allergenNotice}</p>}
          {post.expired && post.expiresAt && (
            <p className="text-sm text-muted">Encerrado em {formatDateTime(post.expiresAt)}: saiu do feed e fica só aqui e no perfil.</p>
          )}
          {edited && <p className="text-sm text-muted">Editado em {formatDateTime(post.updatedAt)}.</p>}
        </PostFrame>
        {post.status === 'ARCHIVED' && (
          <Notice>
            <p>Post arquivado: fora do feed, só para leitura. Não recebe curtidas, comentários nem compartilhamentos.</p>
          </Notice>
        )}
        <InteractionBar post={post} />
      </article>
      {post.viewer.isAuthor && <AuthorActions post={post} />}
      {/* alerta sanitário não recebe comentários: o aviso fica junto dos botões */}
      {!isAlert && <Comments post={post} />}
    </div>
  );
}

function Recipe({ recipe }: { recipe: NonNullable<PostDetailView['recipe']> }) {
  return (
    <section aria-labelledby="titulo-receita" className="grid gap-3" data-testid="receita">
      <h2 id="titulo-receita" className="text-xl font-bold">
        Receita
      </h2>
      {(recipe.servings || recipe.prepMinutes) && (
        <p className="text-sm text-muted">
          {[recipe.servings ? `Rende ${recipe.servings} ${recipe.servings === 1 ? 'porção' : 'porções'}` : null, recipe.prepMinutes ? `Preparo: ${recipe.prepMinutes} min` : null]
            .filter(Boolean)
            .join(' · ')}
        </p>
      )}
      <h3 className="font-bold">Ingredientes</h3>
      <ul className="grid list-disc gap-1 pl-6">
        {recipe.ingredients.map((i) => (
          <li key={i}>{i}</li>
        ))}
      </ul>
      <h3 className="font-bold">Modo de preparo</h3>
      <ol className="grid list-decimal gap-1 pl-6">
        {recipe.steps.map((s) => (
          <li key={s}>{s}</li>
        ))}
      </ol>
    </section>
  );
}
