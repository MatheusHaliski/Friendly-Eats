'use client';

import type { PlaceRefView } from '@friendly-eats/api';
import { useQuery } from '@tanstack/react-query';
import { useState } from 'react';
import { FieldErrors } from '@/components/Form';
import { IconSearch } from '@/components/icons';
import { ownsPlaces } from '@/lib/account-types';
import { api } from '@/lib/api';

interface Props {
  author: string;
  value: number | null;
  onChange: (id: number | null) => void;
  required: boolean;
  /** Lugar que o post já cita (edição). */
  current: PlaceRefView | null;
  errors?: string[];
}

/**
 * Lugar citado no post: Estabelecimento e Varejo escolhem entre os próprios lugares ("Meus lugares"); as demais
 * contas buscam pelo nome entre os lugares no ar.
 */
export function PlacePicker({ author, value, onChange, required, current, errors }: Props) {
  const owner = ownsPlaces(author);
  const mine = useQuery({ queryKey: ['meus-lugares'], queryFn: () => api.me.establishments(), enabled: owner });
  const [q, setQ] = useState('');
  const [term, setTerm] = useState('');
  const search = useQuery({
    queryKey: ['busca-lugar-post', term],
    queryFn: ({ signal }) => api.establishments.search({ q: term, size: 6 }, signal),
    enabled: !owner && term.length > 1,
  });
  const errId = 'post-place-erro';
  const options: Array<{ id: number; name: string; detail: string }> = owner
    ? (mine.data ?? []).map((p) => ({
        id: p.id,
        name: p.name,
        detail: [p.primaryType.label, p.status === 'CLOSED' ? 'Fechado' : null, p.published === false ? 'fora do ar' : null].filter(Boolean).join(' · '),
      }))
    : (search.data?.items ?? []).map((c) => ({ id: c.id, name: c.name, detail: [c.primaryType.label, c.location.city].filter(Boolean).join(' · ') }));
  if (current && !options.some((o) => o.id === current.id)) {
    options.unshift({ id: current.id, name: current.name, detail: [current.city, current.uf].filter(Boolean).join(' – ') });
  }

  return (
    <fieldset className="grid gap-2" aria-describedby={errors?.length ? errId : undefined} id="post-place">
      <legend className="font-semibold">
        Lugar{required ? <span className="font-normal text-muted"> (obrigatório)</span> : <span className="font-normal text-muted"> (opcional)</span>}
      </legend>
      <p className="text-sm text-muted">
        {owner ? 'Escolha um dos seus lugares.' : 'Busque o lugar pelo nome para citar no post.'}
      </p>
      {!owner && (
        <div className="flex max-w-lg gap-2">
          <label htmlFor="post-place-busca" className="sr-only">
            Nome do lugar
          </label>
          <input
            id="post-place-busca"
            className="fe-input"
            type="search"
            value={q}
            onChange={(e) => setQ(e.target.value)}
            onKeyDown={(e) => {
              if (e.key === 'Enter') {
                e.preventDefault();
                setTerm(q.trim());
              }
            }}
            placeholder="ex.: Sabor Mineiro"
            autoComplete="off"
          />
          <button type="button" className="fe-btn" onClick={() => setTerm(q.trim())}>
            <IconSearch size={18} />
            Buscar
          </button>
        </div>
      )}
      {owner && mine.isPending && <p className="text-sm text-muted">Carregando os seus lugares…</p>}
      {owner && mine.data?.length === 0 && <p className="text-sm text-muted">Você ainda não cadastrou lugares.</p>}
      {!owner && search.data && search.data.items.length === 0 && <p className="text-sm text-muted">Nenhum lugar com esse nome.</p>}
      {(options.length > 0 || !required) && (
        <div className="grid gap-1.5" role="radiogroup" aria-label="Lugares">
          {!required && (
            <label className="flex min-h-11 items-center gap-2">
              <input type="radio" name="post-lugar" className="size-5" checked={value === null} onChange={() => onChange(null)} />
              Nenhum lugar
            </label>
          )}
          {options.map((o) => (
            <label key={o.id} className="flex min-h-11 items-center gap-2">
              <input type="radio" name="post-lugar" className="size-5" checked={value === o.id} onChange={() => onChange(o.id)} />
              <span>
                <span className="font-semibold">{o.name}</span>
                {o.detail && <span className="text-sm text-muted"> · {o.detail}</span>}
              </span>
            </label>
          ))}
        </div>
      )}
      <FieldErrors id={errId} errors={errors} />
    </fieldset>
  );
}
