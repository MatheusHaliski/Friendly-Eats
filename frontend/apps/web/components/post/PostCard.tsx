'use client';

import type { PostCardView } from '@friendly-eats/api';
import Link from 'next/link';
import { IconComment, IconLike, IconShare } from '@/components/icons';
import { countsText, postHref } from '@/lib/post-format';
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

interface Props {
  post: PostCardView;
  /** Feed de alertas (e qualquer feed em rules.feedsWithoutFrame) nunca mostra moldura. */
  framed?: boolean;
  motion: boolean;
  headingLevel?: 'h2' | 'h3';
  /** Prévia do estúdio: o título não vira link (o post ainda não existe). */
  linkTitle?: boolean;
}

/**
 * Card do post (RF14) dentro da moldura (RF16): a arte fica só na faixa externa e o container opaco traz autor,
 * bolinha, selo do tipo, foto com texto alternativo, preço, alergênicos e os chips. Nenhum texto sobre a arte.
 */
export function PostCard({ post, framed = true, motion, headingLevel = 'h3', linkTitle = true }: Props) {
  const headingId = `post-${post.id}`;
  const H = headingLevel;
  const isAlert = post.kind.code === 'ALERTA_SANITARIO';
  return (
    <PostFrame
      background={post.background}
      framed={framed && !isAlert}
      border={post.kind.border}
      expired={post.expired}
      seed={post.id}
      motion={motion}
      labelledBy={headingId}
      testId="card-post"
    >
      {isAlert && <HazardBand author={post.author} host={post.alert?.sourceHost ?? null} />}
      <AuthorLine author={post.author} publishedAt={post.publishedAt} />
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
      <H id={headingId} className="text-lg font-bold leading-snug">
        {linkTitle ? (
          <Link href={postHref(post)} className="underline decoration-1 hover:decoration-2">
            {post.title}
          </Link>
        ) : (
          post.title
        )}
      </H>
      {post.mediaUrl && (
        // eslint-disable-next-line @next/next/no-img-element -- foto pública do bucket, já tratada (sem EXIF)
        <img src={post.mediaUrl} alt={post.altText ?? ''} loading="lazy" className="aspect-[4/3] w-full rounded-md border border-line object-cover" />
      )}
      {post.excerpt && <p className="whitespace-pre-line">{post.excerpt}</p>}
      {post.alert && <AlertDetails alert={post.alert} compact />}
      <TaxonomyLine cuisine={post.cuisine} foodFormat={post.foodFormat} dietaryStyles={post.dietaryStyles} />
      <PriceLine price={post.price} />
      <PeriodLine startsAt={post.startsAt} endsAt={post.endsAt} label={post.kind.code === 'EVENTO' ? 'Quando' : 'Vigência'} />
      <PlaceLine place={post.establishment} />
      <AllergenList allergens={post.allergens} warnings={post.warnings} />
      <p className="flex flex-wrap items-center gap-x-4 gap-y-1 text-sm text-muted">
        <span className="sr-only">{isAlert ? countsText(post.counts).split(', ').at(-1) : countsText(post.counts)}</span>
        {!isAlert && (
          <span className="inline-flex items-center gap-1" aria-hidden="true">
            <IconLike size={16} /> {post.counts.likes}
          </span>
        )}
        {!isAlert && (
          <span className="inline-flex items-center gap-1" aria-hidden="true">
            <IconComment size={16} /> {post.counts.comments}
          </span>
        )}
        <span className="inline-flex items-center gap-1" aria-hidden="true">
          <IconShare size={16} /> {post.counts.shares}
        </span>
      </p>
    </PostFrame>
  );
}
