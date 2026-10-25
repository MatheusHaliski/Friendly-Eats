'use client';

import type { AccountTypeCode, ProfileListParams } from '@friendly-eats/api';
import { Notice } from '@/components/Form';
import { IconSearch } from '@/components/icons';
import { ACCOUNT_TYPES } from '@/lib/account-types';
import { useProfiles } from '@/lib/post-queries';
import { ProfileCard } from './ProfileCard';

interface Props {
  type: string | null;
  city: string;
  name: string;
  go: (changes: Record<string, string | null>) => void;
}

/** Feed de perfis (RF15): tipo de conta, cidade de atuação e nome ou @, com "Carregar mais". */
export function ProfilesPanel({ type, city, name, go }: Props) {
  const validType = ACCOUNT_TYPES.some((t) => t.code === type) ? (type as AccountTypeCode) : undefined;
  const params: ProfileListParams = { type: validType, city: city.trim() || undefined, q: name.trim().slice(0, 80) || undefined };
  const profiles = useProfiles(params);
  const items = profiles.data?.pages.flatMap((p) => p.items) ?? [];
  return (
    <section aria-labelledby="titulo-perfis" className="grid gap-4">
      <h2 id="titulo-perfis" className="sr-only">
        Perfis
      </h2>
      <form
        key={`${validType}-${city}-${name}`}
        role="search"
        aria-label="Filtrar perfis"
        className="fe-card grid gap-3 p-4 sm:grid-cols-[minmax(0,12rem)_minmax(0,1fr)_minmax(0,1fr)_auto] sm:items-end"
        onSubmit={(e) => {
          e.preventDefault();
          const fd = new FormData(e.currentTarget);
          go({ tipoConta: String(fd.get('tipoConta') ?? ''), cidade: String(fd.get('cidade') ?? ''), nome: String(fd.get('nome') ?? '') });
        }}
      >
        <div className="grid gap-1">
          <label htmlFor="perfis-tipo" className="font-semibold">
            Tipo de conta
          </label>
          <select id="perfis-tipo" name="tipoConta" className="fe-input" defaultValue={validType ?? ''}>
            <option value="">Todos</option>
            {ACCOUNT_TYPES.map((t) => (
              <option key={t.code} value={t.code}>
                {t.glyph} {t.label}
              </option>
            ))}
          </select>
        </div>
        <div className="grid gap-1">
          <label htmlFor="perfis-cidade" className="font-semibold">
            Cidade
          </label>
          <input id="perfis-cidade" name="cidade" className="fe-input" defaultValue={city} autoComplete="address-level2" placeholder="ex.: Curitiba" />
        </div>
        <div className="grid gap-1">
          <label htmlFor="perfis-nome" className="font-semibold">
            Nome ou @
          </label>
          <input id="perfis-nome" name="nome" type="search" className="fe-input" defaultValue={name} maxLength={80} autoComplete="off" />
        </div>
        <button type="submit" className="fe-btn fe-btn-primary">
          <IconSearch size={18} />
          Filtrar
        </button>
      </form>
      <p className="text-sm text-muted">
        A cidade é a de atuação declarada por chefs, criadores, comunidades e organizações; a de quem é comensal não aparece.
      </p>
      {profiles.isPending && (
        <p role="status" className="text-muted">
          Carregando perfis…
        </p>
      )}
      {profiles.isError && (
        <Notice tone="error" live>
          <p>Não foi possível carregar os perfis. {profiles.error instanceof Error ? profiles.error.message : ''}</p>
        </Notice>
      )}
      {profiles.data && items.length === 0 && (
        <div className="fe-card grid gap-2 p-6">
          <p className="font-semibold">Nenhum perfil com esses filtros.</p>
        </div>
      )}
      {items.length > 0 && (
        <ul aria-label="Perfis" className="grid gap-3 sm:grid-cols-2 xl:grid-cols-3" data-testid="lista-perfis">
          {items.map((p) => (
            <li key={p.id}>
              <ProfileCard profile={p} />
            </li>
          ))}
        </ul>
      )}
      {profiles.hasNextPage && (
        <p>
          <button type="button" className="fe-btn" onClick={() => profiles.fetchNextPage()} disabled={profiles.isFetchingNextPage}>
            {profiles.isFetchingNextPage ? 'Carregando…' : 'Carregar mais'}
          </button>
        </p>
      )}
    </section>
  );
}
