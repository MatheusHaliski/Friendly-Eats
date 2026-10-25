'use client';

import { isApiError, type PublicProfileView } from '@friendly-eats/api';
import Link from 'next/link';
import { useEffect, useId, useState } from 'react';
import { AccountBadge, VerifiedSeal } from '@/components/AccountBadge';
import { Notice } from '@/components/Form';
import { IconCopy, IconExternal, IconPlace, IconPrivate } from '@/components/icons';
import { Avatar } from '@/components/post/PostBits';
import { PostList } from '@/components/post/PostList';
import { plural } from '@/lib/format';
import { formatMonthYear } from '@/lib/post-format';
import { useProfilePosts, usePublicProfile } from '@/lib/post-queries';
import { useSession } from '@/lib/queries';
import { copyText } from '@/lib/share';

/** Perfil público (RF15): cabeçalho, links, posts do autor e compartilhar; perfil privado mostra só o mínimo. */
export function ProfileView({ id }: { id: string }) {
  const profile = usePublicProfile(id);
  const name = profile.data?.name;
  useEffect(() => {
    if (name) document.title = `${name} · Friendly Eats`;
  }, [name]);

  if (profile.isPending) {
    return (
      <p role="status" className="text-muted">
        Carregando o perfil…
      </p>
    );
  }
  if (profile.isError) {
    if (isApiError(profile.error, 404)) {
      return (
        <div className="grid max-w-xl gap-4">
          <h1 className="text-3xl font-extrabold">Perfil não encontrado</h1>
          <p>A conta pode não existir mais ou estar fora do ar.</p>
          <p>
            <Link href="/feed?aba=perfis" className="underline">
              Ver outros perfis
            </Link>
          </p>
        </div>
      );
    }
    return (
      <Notice tone="error" live>
        <p>Não foi possível carregar o perfil. {profile.error instanceof Error ? profile.error.message : ''}</p>
      </Notice>
    );
  }
  return <ProfileContent profile={profile.data} />;
}

function ProfileContent({ profile }: { profile: PublicProfileView }) {
  const uid = useId();
  const session = useSession();
  const self = session.data?.id === profile.id;
  // privado para quem não é o titular: o resto vem null e os posts não abrem (403)
  const closed = profile.privateProfile && !self;
  const posts = useProfilePosts(String(profile.id), !closed);
  const [status, setStatus] = useState('');
  const where = [profile.city, profile.uf].filter(Boolean).join(' – ');

  return (
    <div className="grid max-w-3xl gap-8">
      <header className="grid gap-4" aria-labelledby={`${uid}-nome`}>
        <div className="flex flex-wrap items-start gap-4">
          <Avatar name={profile.name} url={profile.avatarUrl} size={88} />
          <div className="grid min-w-0 gap-1">
            <h1 id={`${uid}-nome`} className="flex flex-wrap items-center gap-2 text-3xl font-extrabold">
              {profile.name}
              {profile.verified && <VerifiedSeal />}
            </h1>
            <p className="text-muted">@{profile.handle}</p>
            <p>
              <AccountBadge code={profile.category.code} label={profile.category.label} />
            </p>
            {profile.pronouns && profile.pronouns.length > 0 && <p className="text-sm text-muted">{profile.pronouns.join(', ')}</p>}
          </div>
        </div>
        {closed ? (
          <Notice>
            <p className="flex items-center gap-2 font-semibold">
              <IconPrivate size={18} />
              Perfil privado
            </p>
            <p>Só o nome, o @ e o tipo de conta aparecem. Os posts ficam visíveis só para o titular.</p>
          </Notice>
        ) : (
          <>
            {profile.bio && <p className="max-w-2xl text-lg">{profile.bio}</p>}
            <dl className="flex flex-wrap gap-x-6 gap-y-2 text-sm">
              {where && (
                <div className="flex items-center gap-1.5">
                  <dt>
                    <IconPlace size={16} />
                    <span className="sr-only">Cidade de atuação</span>
                  </dt>
                  <dd>{where}</dd>
                </div>
              )}
              {profile.memberSince && (
                <div className="flex gap-1">
                  <dt className="text-muted">Membro desde</dt>
                  <dd>{formatMonthYear(profile.memberSince)}</dd>
                </div>
              )}
              {profile.postCount != null && (
                <div>
                  <dt className="sr-only">Posts</dt>
                  <dd>{plural(profile.postCount, 'post', 'posts')}</dd>
                </div>
              )}
            </dl>
            {profile.links && profile.links.length > 0 && (
              <ul className="flex flex-wrap gap-2" aria-label="Links">
                {profile.links.map((l) => (
                  <li key={l}>
                    <a href={l} target="_blank" rel="noopener noreferrer nofollow" className="inline-flex min-h-6 items-center gap-1 underline">
                      {l.replace(/^https:\/\//, '')}
                      <IconExternal size={14} />
                      <span className="sr-only">(abre em outra aba)</span>
                    </a>
                  </li>
                ))}
              </ul>
            )}
            {profile.shareUrl && (
              <div className="flex flex-wrap items-center gap-2">
                <button
                  type="button"
                  className="fe-btn fe-btn-sm"
                  onClick={async () => setStatus((await copyText(profile.shareUrl!)) ? 'Link do perfil copiado.' : `Copie o link: ${profile.shareUrl}`)}
                >
                  <IconCopy size={16} />
                  Copiar link do perfil
                </button>
                <span className="text-sm text-muted">{profile.shareUrl}</span>
                <p role="status" className="w-full text-sm font-semibold">
                  {status}
                </p>
              </div>
            )}
          </>
        )}
      </header>

      {!closed && (
        <section aria-labelledby={`${uid}-posts`} className="grid gap-4">
          <h2 id={`${uid}-posts`} className="text-xl font-bold">
            Posts
          </h2>
          {posts.isError && isApiError(posts.error, 403) ? (
            <Notice>
              <p>{posts.error.detail}</p>
            </Notice>
          ) : (
            <PostList query={posts} label={`Posts de ${profile.name}`} testId="posts-perfil" empty={<p className="font-semibold">Nenhum post publicado ainda.</p>} />
          )}
        </section>
      )}
    </div>
  );
}
