'use client';

import { keepPreviousData, useQuery } from '@tanstack/react-query';
import { useRouter, useSearchParams } from 'next/navigation';
import { useMemo, useOptimistic, useState, useTransition } from 'react';
import type { EstablishmentCard as Card } from '@friendly-eats/api';
import { EstablishmentCard } from '@/components/EstablishmentCard';
import { Notice } from '@/components/Form';
import { IconSearch } from '@/components/icons';
import { Pagination } from '@/components/Pagination';
import { api } from '@/lib/api';
import {
  filterReasons,
  filtersToSearch,
  hasActiveFilters,
  parseFilters,
  toSearchParams,
  type Coords,
  type ExploreFilters,
} from '@/lib/explore-params';
import { plural } from '@/lib/format';
import { useTaxonomy } from '@/lib/queries';
import { FiltersPanel } from './FiltersPanel';
import { PlaceDialog } from './PlaceDialog';

interface Props {
  /** Rota da página ("/" no Explorar, "/feed" na aba Restaurantes). */
  basePath: string;
  /** Parâmetros da página que ficam na URL junto com os filtros (ex.: aba=restaurantes). */
  keep?: Record<string, string>;
}

/**
 * Busca de lugares (RF10) com os filtros na URL: usada no Explorar e na aba Restaurantes do feed. Clicar num cartão
 * abre o modal explicativo; a página do lugar fica no botão do modal e no próprio link do cartão.
 */
export function PlaceSearch({ basePath, keep }: Props) {
  const router = useRouter();
  const sp = useSearchParams();
  const search = sp.toString();
  const filters = useMemo(() => parseFilters(new URLSearchParams(search)), [search]);
  const taxonomy = useTaxonomy();
  // "Perto de mim": a coordenada fica só na memória desta página, nunca na URL nem no armazenamento
  const [coords, setCoords] = useState<Coords | null>(null);
  const [filtersOpen, setFiltersOpen] = useState(false);
  const [opened, setOpened] = useState<Card | null>(null);
  const keepKey = JSON.stringify(keep ?? {});
  const hrefFor = (f: ExploreFilters) => {
    const sp = new URLSearchParams(JSON.parse(keepKey) as Record<string, string>);
    new URLSearchParams(filtersToSearch(f)).forEach((v, k) => sp.append(k, v));
    const q = sp.toString();
    return `${basePath}${q ? `?${q}` : ''}`;
  };

  const params = useMemo(() => toSearchParams(filters, coords), [filters, coords]);
  const results = useQuery({
    queryKey: ['busca', params],
    queryFn: ({ signal }) => api.establishments.search(params, signal),
    placeholderData: keepPreviousData,
  });

  // o controle muda na hora; a URL (fonte da verdade, compartilhável) chega logo depois
  const [shown, setShown] = useOptimistic(filters);
  const [, startTransition] = useTransition();
  const go = (next: ExploreFilters) =>
    startTransition(() => {
      setShown(next);
      router.replace(hrefFor(next), { scroll: false });
    });
  const update = (patch: Partial<ExploreFilters>) => go({ ...shown, page: 0, ...patch });
  // ligar ou desligar "perto de mim" muda o conjunto de resultados: volta para a primeira página
  const changeCoords = (c: Coords | null) => {
    setCoords(c);
    if (shown.page !== 0) go({ ...shown, page: 0 });
  };
  const clear = () => {
    setCoords(null);
    go(parseFilters(new URLSearchParams()));
  };

  const data = results.data;
  const activeCount = [
    shown.chip,
    shown.cuisine.length,
    shown.format.length,
    shown.price.length,
    shown.minRating,
    shown.dietary.length,
    coords,
  ].filter(Boolean).length;

  return (
    <div className="grid gap-6">
      <div className="grid gap-3">
        <form
          key={filters.q}
          role="search"
          aria-label="Buscar lugares por nome"
          className="flex max-w-2xl gap-2"
          onSubmit={(e) => {
            e.preventDefault();
            const q = String(new FormData(e.currentTarget).get('q') ?? '');
            update({ q });
          }}
        >
          <label htmlFor="busca-nome" className="sr-only">
            Nome do lugar
          </label>
          <input
            id="busca-nome"
            name="q"
            type="search"
            className="fe-input"
            placeholder="Nome do lugar, ex.: Sabor Mineiro"
            defaultValue={filters.q}
            autoComplete="off"
            enterKeyHint="search"
          />
          <button type="submit" className="fe-btn fe-btn-primary">
            <IconSearch size={18} />
            Buscar
          </button>
        </form>
      </div>

      <div className="grid gap-6 lg:grid-cols-[19rem_1fr] lg:items-start">
        <div className="grid gap-3">
          <button
            type="button"
            className="fe-btn lg:hidden"
            aria-expanded={filtersOpen}
            aria-controls="painel-filtros"
            onClick={() => setFiltersOpen((v) => !v)}
          >
            {filtersOpen ? 'Esconder filtros' : 'Mostrar filtros'}
            {activeCount > 0 ? ` (${activeCount} ${activeCount === 1 ? 'ativo' : 'ativos'})` : ''}
          </button>
          <div id="painel-filtros" className={`${filtersOpen ? 'block' : 'hidden'} lg:block`}>
            <FiltersPanel
              filters={shown}
              taxonomy={taxonomy.data}
              taxonomyError={taxonomy.isError}
              coords={coords}
              onCoords={changeCoords}
              onChange={update}
              onClear={clear}
              canClear={hasActiveFilters(shown) || coords !== null}
            />
          </div>
        </div>

        <section aria-labelledby="titulo-resultados" className="grid gap-4">
          <div className="flex flex-wrap items-baseline justify-between gap-2">
            <h2 id="titulo-resultados" className="text-xl font-bold">
              Resultados
            </h2>
            <p role="status" className="text-muted" data-testid="contagem">
              {results.isPending
                ? 'Buscando lugares…'
                : data
                  ? `${plural(data.total, 'lugar encontrado', 'lugares encontrados')}${coords && data.radiusKm ? ` num raio de ${String(data.radiusKm).replace('.', ',')} km` : ''}`
                  : ''}
            </p>
          </div>

          {results.isError && (
            <Notice tone="error" live>
              <p>Não foi possível buscar agora. {results.error instanceof Error ? results.error.message : ''}</p>
              <p>
                <button type="button" className="fe-btn fe-btn-sm" onClick={() => results.refetch()}>
                  Tentar de novo
                </button>
              </p>
            </Notice>
          )}

          {data && data.items.length === 0 && (
            <div className="fe-card grid gap-2 p-6">
              <p className="font-semibold">Nenhum lugar encontrado com esses filtros.</p>
              <p className="text-muted">Tente outro nome, um raio maior ou menos filtros.</p>
              {(hasActiveFilters(filters) || coords) && (
                <p>
                  <button type="button" className="fe-btn fe-btn-sm" onClick={clear}>
                    Limpar filtros
                  </button>
                </p>
              )}
            </div>
          )}

          {data && data.items.length > 0 && (
            <ul className="grid gap-4 sm:grid-cols-2 xl:grid-cols-3" aria-busy={results.isFetching} data-testid="lista-lugares">
              {data.items.map((card) => (
                <li key={card.id}>
                  <EstablishmentCard card={card} onOpen={setOpened} />
                </li>
              ))}
            </ul>
          )}

          {data && (
            <Pagination
              page={data.page}
              size={data.size}
              total={data.total}
              label="Páginas de resultados"
              hrefFor={(page) => hrefFor({ ...filters, page })}
            />
          )}
        </section>
      </div>
      <PlaceDialog card={opened} reasons={filterReasons(shown, taxonomy.data, coords !== null)} onClose={() => setOpened(null)} />
    </div>
  );
}
