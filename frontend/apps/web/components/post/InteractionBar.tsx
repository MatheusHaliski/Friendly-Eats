'use client';

import { isApiError, type PostDetailView, type ShareChannelCode } from '@friendly-eats/api';
import { useMutation, useQueryClient } from '@tanstack/react-query';
import { useRouter } from 'next/navigation';
import { useId, useState } from 'react';
import { IconComment, IconCopy, IconExternal, IconLike, IconSave, IconSaved, IconShare } from '@/components/icons';
import { api } from '@/lib/api';
import { plural } from '@/lib/format';
import { patchPostEverywhere, useVocabulary } from '@/lib/post-queries';
import { useSession } from '@/lib/queries';
import { loginHref } from '@/lib/safe-redirect';
import { canShareNatively, copyText, shareTarget } from '@/lib/share';

/**
 * Curtir, comentar, compartilhar e salvar (RF12): botões com rótulo por extenso, aria-pressed nos que alternam e o
 * resultado anunciado num aria-live. Alerta sanitário: só compartilhar e salvar.
 */
export function InteractionBar({ post }: { post: PostDetailView }) {
  const uid = useId();
  const router = useRouter();
  const qc = useQueryClient();
  const session = useSession();
  const vocabulary = useVocabulary();
  const [status, setStatus] = useState('');
  const [shareOpen, setShareOpen] = useState(false);
  const logged = !!session.data;
  const here = `/posts/${post.id}`;
  const it = post.interactions;

  const fail = (e: unknown) => {
    if (isApiError(e, 401)) router.push(loginHref(here));
    else setStatus(isApiError(e) ? e.detail : 'Não foi possível concluir agora. Tente de novo.');
  };
  const requireLogin = () => {
    router.push(loginHref(here));
    return false;
  };

  const like = useMutation({
    mutationFn: () => (post.viewer.liked ? api.posts.unlike(post.id) : api.posts.like(post.id)),
    onSuccess: (r) => {
      patchPostEverywhere(
        qc,
        post.id,
        (p) => ({ ...p, viewer: { ...p.viewer, liked: r.liked }, counts: { ...p.counts, likes: r.likes } }),
        (c) => ({ ...c, viewer: { ...c.viewer, liked: r.liked }, counts: { ...c.counts, likes: r.likes } }),
      );
      setStatus(`${r.liked ? 'Você curtiu.' : 'Curtida desfeita.'} ${plural(r.likes, 'curtida', 'curtidas')}.`);
    },
    onError: fail,
  });

  const save = useMutation({
    mutationFn: () => (post.viewer.saved ? api.posts.unsave(post.id) : api.posts.save(post.id)),
    onSuccess: (r) => {
      patchPostEverywhere(
        qc,
        post.id,
        (p) => ({ ...p, viewer: { ...p.viewer, saved: r.saved } }),
        (c) => ({ ...c, viewer: { ...c.viewer, saved: r.saved } }),
      );
      setStatus(r.saved ? 'Post salvo. Só você vê os seus salvos.' : 'Post tirado dos salvos.');
    },
    onError: fail,
  });

  const count = useMutation({
    mutationFn: (channel: ShareChannelCode) => api.posts.share(post.id, channel),
    onSuccess: (r) => {
      patchPostEverywhere(
        qc,
        post.id,
        (p) => ({ ...p, counts: { ...p.counts, shares: r.shares } }),
        (c) => ({ ...c, counts: { ...c.counts, shares: r.shares } }),
      );
    },
  });
  /** Conta o compartilhamento (só com sessão e post aberto); o visitante compartilha sem contar. */
  const counted = (channel: ShareChannelCode) => {
    if (logged && it.shares) count.mutate(channel);
  };

  const channels = (vocabulary.data?.shareChannels ?? []).filter((c) => c.code !== 'NATIVE' || canShareNatively());
  const shareTitle = post.title;

  return (
    <div className="grid gap-3">
      <div className="flex flex-wrap items-center gap-2" role="group" aria-label="Interações">
        {it.likes && (
          <button
            type="button"
            className="fe-btn"
            aria-pressed={post.viewer.liked}
            disabled={like.isPending}
            onClick={() => (logged ? like.mutate() : requireLogin())}
            data-testid="curtir"
          >
            <IconLike size={18} fill={post.viewer.liked ? 'currentColor' : 'none'} />
            <span>{post.viewer.liked ? 'Curtido' : 'Curtir'}</span>
            <span className="text-muted">
              <span className="sr-only">, </span>
              {plural(post.counts.likes, 'curtida', 'curtidas')}
            </span>
          </button>
        )}
        {it.comments && (
          <a href="#comentarios" className="fe-btn" data-testid="ir-comentarios">
            <IconComment size={18} />
            <span>Comentar</span>
            <span className="text-muted">
              <span className="sr-only">, </span>
              {plural(post.counts.comments, 'comentário', 'comentários')}
            </span>
          </a>
        )}
        <button
          type="button"
          className="fe-btn"
          aria-expanded={shareOpen}
          aria-controls={`${uid}-compartilhar`}
          onClick={() => setShareOpen((v) => !v)}
          data-testid="compartilhar"
        >
          <IconShare size={18} />
          <span>Compartilhar</span>
          <span className="text-muted">
            <span className="sr-only">, </span>
            {plural(post.counts.shares, 'compartilhamento', 'compartilhamentos')}
          </span>
        </button>
        <button
          type="button"
          className="fe-btn"
          aria-pressed={post.viewer.saved}
          disabled={save.isPending}
          onClick={() => (logged ? save.mutate() : requireLogin())}
          data-testid="salvar"
        >
          {post.viewer.saved ? <IconSaved size={18} /> : <IconSave size={18} />}
          <span>{post.viewer.saved ? 'Salvo' : 'Salvar'}</span>
        </button>
      </div>
      {!it.likes && post.kind.code === 'ALERTA_SANITARIO' && (
        <p className="text-sm text-muted" data-testid="aviso-alerta">
          {it.commentNotice ?? 'Alerta sanitário não recebe curtidas nem comentários: compartilhe ou salve.'}
        </p>
      )}

      {shareOpen && (
        <div id={`${uid}-compartilhar`} className="fe-card grid gap-3 p-4" data-testid="folha-compartilhar">
          <p className="font-bold">Compartilhar</p>
          <ul className="flex flex-wrap gap-2">
            {channels.map((ch) => {
              const code = ch.code as ShareChannelCode;
              const target = shareTarget(code, post.shareUrl, shareTitle);
              if (code === 'COPY_LINK') {
                return (
                  <li key={code}>
                    <button
                      type="button"
                      className="fe-btn fe-btn-sm"
                      onClick={async () => {
                        const ok = await copyText(post.shareUrl);
                        setStatus(ok ? 'Link copiado.' : 'Não deu para copiar: selecione o link abaixo e copie.');
                        if (ok) counted(code);
                      }}
                    >
                      <IconCopy size={16} />
                      {ch.label}
                    </button>
                  </li>
                );
              }
              if (code === 'NATIVE') {
                return (
                  <li key={code}>
                    <button
                      type="button"
                      className="fe-btn fe-btn-sm"
                      onClick={async () => {
                        try {
                          await navigator.share({ title: shareTitle, url: post.shareUrl });
                          counted(code);
                        } catch {
                          // a pessoa fechou a folha do sistema
                        }
                      }}
                    >
                      <IconShare size={16} />
                      {ch.label}
                    </button>
                  </li>
                );
              }
              return target ? (
                <li key={code}>
                  <a href={target} target="_blank" rel="noopener noreferrer" className="fe-btn fe-btn-sm" onClick={() => counted(code)}>
                    <IconExternal size={16} />
                    {ch.label}
                    <span className="sr-only">(abre em outra aba)</span>
                  </a>
                </li>
              ) : null;
            })}
          </ul>
          <div className="grid gap-1">
            <label htmlFor={`${uid}-link`} className="text-sm font-semibold">
              Link do post
            </label>
            <input id={`${uid}-link`} className="fe-input" readOnly value={post.shareUrl} onFocus={(e) => e.currentTarget.select()} />
            <p className="text-sm text-muted">O link não diz quem compartilhou.</p>
          </div>
        </div>
      )}
      <p role="status" aria-live="polite" className="text-sm font-semibold" data-testid="status-interacao">
        {status}
      </p>
    </div>
  );
}
