'use client';

import { isApiError, type RelatedUserView, type SafetyView } from '@friendly-eats/api';
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import Link from 'next/link';
import { useId, useState, type FormEvent } from 'react';
import { Notice, useDeferredFocus } from '@/components/Form';
import { IconBlock, IconMute, IconSupport } from '@/components/icons';
import { Avatar } from '@/components/post/PostBits';
import { api } from '@/lib/api';
import { formatDate } from '@/lib/format';
import { SAFETY_KEY } from '@/lib/moderation';
import { profileHref } from '@/lib/post-format';
import { CheckRow, ResultNotice, type Result } from '@/components/settings/PreferenceControls';

/** Configurações → Privacidade → "Bloqueados, silenciados e palavras" (RF19). */
export function SafetySection() {
  const uid = useId();
  const safety = useQuery({ queryKey: SAFETY_KEY, queryFn: () => api.safety.mine() });
  return (
    <section aria-labelledby={`${uid}-titulo`} className="fe-card grid gap-5 p-5" data-testid="seguranca-usuarios">
      <h2 id={`${uid}-titulo`} className="text-xl font-bold">
        Bloqueados, silenciados e palavras
      </h2>
      <p className="text-muted">
        Quem você bloqueia não vê o seu perfil nem os seus posts e não comenta; você também deixa de ver a pessoa. Quem você silencia some do seu feed e
        dos comentários, sem saber. Nenhum dos dois recebe aviso.{' '}
        <Link href="/seguranca" className="inline-flex items-center gap-1 font-semibold underline">
          <IconSupport size={16} />
          Sofreu intimidação?
        </Link>
      </p>
      {safety.isPending && <p className="text-muted">Carregando…</p>}
      {safety.isError && (
        <Notice tone="error">
          <p>Não foi possível carregar. Recarregue a página.</p>
        </Notice>
      )}
      {safety.data && (
        <>
          <People title="Bloqueados" kind="block" people={safety.data.blocked} empty="Você não bloqueou ninguém." />
          <People title="Silenciados" kind="mute" people={safety.data.muted} empty="Você não silenciou ninguém." />
          <Words data={safety.data} />
        </>
      )}
    </section>
  );
}

function People({ title, kind, people, empty }: { title: string; kind: 'block' | 'mute'; people: RelatedUserView[]; empty: string }) {
  const uid = useId();
  const qc = useQueryClient();
  const [status, setStatus] = useState('');
  const undo = useMutation({
    mutationFn: (userId: number) => (kind === 'block' ? api.safety.unblock(userId) : api.safety.unmute(userId)),
    onSuccess: (_r, userId) => {
      const name = people.find((p) => p.user.id === userId)?.user.name ?? 'A pessoa';
      setStatus(kind === 'block' ? `${name} foi desbloqueada.` : `${name} voltou a aparecer para você.`);
      void qc.invalidateQueries({ queryKey: SAFETY_KEY });
      void qc.invalidateQueries({ queryKey: ['eu', 'relacao', userId] });
    },
    onError: (e) => setStatus(isApiError(e) ? e.detail : 'Não foi possível desfazer agora.'),
  });
  const Icon = kind === 'block' ? IconBlock : IconMute;
  return (
    <div className="grid gap-2">
      <h3 id={`${uid}-titulo`} className="flex items-center gap-2 font-bold">
        <Icon size={18} />
        {title} <span className="font-normal text-muted">({people.length})</span>
      </h3>
      {people.length === 0 ? (
        <p className="text-muted">{empty}</p>
      ) : (
        <ul className="grid gap-2" aria-labelledby={`${uid}-titulo`}>
          {people.map((p) => {
            const href = profileHref(p.user);
            return (
              <li key={p.user.id ?? p.user.name} className="flex flex-wrap items-center gap-3">
                <Avatar name={p.user.name} url={p.user.avatarUrl} size={32} />
                <span className="grid min-w-0 flex-1">
                  {href ? (
                    <Link href={href} className="font-semibold underline">
                      {p.user.name}
                    </Link>
                  ) : (
                    <span className="font-semibold">{p.user.name}</span>
                  )}
                  <span className="text-sm text-muted">desde {formatDate(p.since)}</span>
                </span>
                <button type="button" className="fe-btn fe-btn-sm" disabled={undo.isPending} onClick={() => p.user.id != null && undo.mutate(p.user.id)}>
                  {kind === 'block' ? 'Desbloquear' : 'Deixar de silenciar'}
                  <span className="sr-only"> {p.user.name}</span>
                </button>
              </li>
            );
          })}
        </ul>
      )}
      <p role="status" className="text-sm font-semibold">
        {status}
      </p>
    </div>
  );
}

function Words({ data }: { data: SafetyView }) {
  const uid = useId();
  const qc = useQueryClient();
  const [text, setText] = useState(data.words.join('\n'));
  const [result, setResult] = useState<Result>(null);
  const [resultRef, focusResult] = useDeferredFocus<HTMLDivElement>();

  const save = useMutation({
    mutationFn: (words: string[]) => api.safety.setWords(words),
    onSuccess: (view) => {
      qc.setQueryData(SAFETY_KEY, view);
      setText(view.words.join('\n'));
      setResult({ tone: 'success', text: `Palavras filtradas salvas (${view.words.length}).` });
    },
    onError: (e) => {
      setResult({ tone: 'error', text: isApiError(e, 422) && e.errors.length ? e.errors.join(' ') : isApiError(e) ? e.detail : 'Não foi possível salvar agora.' });
      focusResult();
    },
  });
  const toggle = useMutation({
    mutationFn: (on: boolean) => api.safety.setDefaultFilter(on),
    onSuccess: (view) => {
      qc.setQueryData(SAFETY_KEY, view);
      setResult({ tone: 'success', text: view.defaultFilter.enabled ? 'Filtro de ofensas ligado.' : 'Filtro de ofensas desligado.' });
    },
    onError: (e) => {
      setResult({ tone: 'error', text: isApiError(e) ? e.detail : 'Não foi possível mudar agora.' });
      focusResult();
    },
  });

  function submit(e: FormEvent) {
    e.preventDefault();
    setResult(null);
    const words = text
      .split(/[\n,]/)
      .map((w) => w.trim())
      .filter(Boolean);
    save.mutate(words);
  }

  return (
    <form noValidate onSubmit={submit} className="grid gap-3" aria-labelledby={`${uid}-titulo`}>
      <h3 id={`${uid}-titulo`} className="font-bold">
        Palavras filtradas
      </h3>
      <CheckRow
        id={`${uid}-padrao`}
        label={`Filtro de ofensas (${data.defaultFilter.size} palavras e expressões comuns de intimidação)`}
        hint={data.defaultFilter.locked ? 'Para menores de 16 anos fica ligado; só o responsável pode alterar (ECA Digital).' : 'Esconde comentários ofensivos nos seus posts.'}
        checked={data.defaultFilter.enabled}
        onChange={(on) => toggle.mutate(on)}
        locked={data.defaultFilter.locked}
      />
      <div className="grid gap-1">
        <label htmlFor={`${uid}-lista`} className="font-semibold">
          Suas palavras
        </label>
        <p id={`${uid}-dica`} className="text-sm text-muted">
          Uma por linha (ou separadas por vírgula), até {data.maxWords}. Comentários nos seus posts com elas ficam ocultos para todo mundo; quem
          escreveu ainda vê o seu. Acentos, maiúsculas e trocas como &quot;1d10t4&quot; contam igual.
        </p>
        <textarea id={`${uid}-lista`} className="fe-input min-h-28" value={text} onChange={(e) => setText(e.target.value)} aria-describedby={`${uid}-dica`} />
      </div>
      <ResultNotice result={result} innerRef={resultRef} />
      <div>
        <button type="submit" className="fe-btn fe-btn-primary" disabled={save.isPending}>
          {save.isPending ? 'Salvando…' : 'Salvar palavras'}
        </button>
      </div>
    </form>
  );
}
