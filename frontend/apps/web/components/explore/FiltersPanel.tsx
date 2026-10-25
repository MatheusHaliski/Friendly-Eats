'use client';

import type { CuisineChipCode, PriceLevelCode, SearchSort, TaxonomyCatalog } from '@friendly-eats/api';
import { useId, useState, type ReactNode } from 'react';
import { chipStyle } from '@/components/CuisineChip';
import { Notice } from '@/components/Form';
import { CHIP_ICONS, IconNearMe, IconStar } from '@/components/icons';
import {
  MACRO_TO_CHIP,
  MIN_RATING_OPTIONS,
  RADII_KM,
  roundCoords,
  type Coords,
  type ExploreFilters,
} from '@/lib/explore-params';
import { formatDecimal } from '@/lib/format';

interface Props {
  filters: ExploreFilters;
  taxonomy: TaxonomyCatalog | undefined;
  taxonomyError: boolean;
  coords: Coords | null;
  onCoords: (c: Coords | null) => void;
  onChange: (patch: Partial<ExploreFilters>) => void;
  onClear: () => void;
  canClear: boolean;
}

function Group({ legend, children, hint }: { legend: string; children: ReactNode; hint?: ReactNode }) {
  return (
    <fieldset className="grid gap-2 border-t border-line pt-4">
      <legend className="float-left mb-1 w-full font-bold">{legend}</legend>
      {hint}
      {children}
    </fieldset>
  );
}

function toggle<T>(list: T[], value: T): T[] {
  return list.includes(value) ? list.filter((v) => v !== value) : [...list, value];
}

const PRICE_CODES: PriceLevelCode[] = ['PRICE_1', 'PRICE_2', 'PRICE_3', 'PRICE_4'];
const PRICE_CLASS = ['text-price-1', 'text-price-2', 'text-price-3', 'text-price-4'];

/** Painel de filtros do Explorar; cada mudança vai direto para a URL. */
export function FiltersPanel({ filters, taxonomy, taxonomyError, coords, onCoords, onChange, onClear, canClear }: Props) {
  const uid = useId();
  const cuisineGroups = (taxonomy?.cuisineChips ?? [])
    .filter((chip) => Object.values(MACRO_TO_CHIP).includes(chip.code))
    .map((chip) => ({
      chip,
      cuisines: (taxonomy?.cuisines ?? []).filter((c) => MACRO_TO_CHIP[c.macro] === chip.code),
    }));

  return (
    <form className="fe-card grid gap-4 p-4" aria-label="Filtros" onSubmit={(e) => e.preventDefault()}>
      <div className="flex items-center justify-between gap-2">
        <h2 className="text-lg font-bold">Filtros</h2>
        {canClear && (
          <button type="button" className="fe-btn fe-btn-sm" onClick={onClear}>
            Limpar filtros
          </button>
        )}
      </div>

      {taxonomyError && (
        <Notice tone="error">
          <p>Não foi possível carregar as opções de filtro. Recarregue a página.</p>
        </Notice>
      )}

      <Group legend="Culinária">
        <div className="flex flex-wrap gap-2">
          <span className="relative inline-flex">
            <input
              type="radio"
              className="fe-visually-hidden-input"
              id={`${uid}-chip-todas`}
              name="chip"
              checked={filters.chip === null}
              onChange={() => onChange({ chip: null })}
            />
            <label htmlFor={`${uid}-chip-todas`} className="fe-pill">
              Todas
            </label>
          </span>
          {(taxonomy?.cuisineChips ?? []).map((chip) => {
            const Icon = CHIP_ICONS[chip.lucideIcon];
            const id = `${uid}-chip-${chip.code}`;
            return (
              <span key={chip.code} className="relative inline-flex">
                <input
                  type="radio"
                  className="fe-visually-hidden-input"
                  id={id}
                  name="chip"
                  value={chip.code}
                  checked={filters.chip === chip.code}
                  onChange={() => onChange({ chip: chip.code as CuisineChipCode })}
                />
                <label htmlFor={id} className="fe-pill" data-chip={chip.code} style={chipStyle(chip)}>
                  {Icon && <Icon size={16} />}
                  {chip.label}
                </label>
              </span>
            );
          })}
        </div>
        <div className="grid gap-1">
          <label htmlFor={`${uid}-culinaria`} className="text-sm font-semibold">
            Culinária específica
          </label>
          <p id={`${uid}-culinaria-dica`} className="text-sm text-muted">
            Inclui as regionais: Brasileira traz Mineira, Baiana e as outras.
          </p>
          <select
            id={`${uid}-culinaria`}
            className="fe-input"
            aria-describedby={`${uid}-culinaria-dica`}
            value={filters.cuisine[0] ?? ''}
            onChange={(e) => onChange({ cuisine: e.target.value ? [e.target.value] : [] })}
          >
            <option value="">Qualquer culinária</option>
            {cuisineGroups.map(({ chip, cuisines }) => (
              <optgroup key={chip.code} label={chip.label}>
                {cuisines.map((c) => (
                  <option key={c.code} value={c.code}>
                    {c.label}
                  </option>
                ))}
              </optgroup>
            ))}
          </select>
        </div>
      </Group>

      <Group legend="Tipo de comida">
        <label htmlFor={`${uid}-formato`} className="sr-only">
          Tipo de comida
        </label>
        <select
          id={`${uid}-formato`}
          className="fe-input"
          value={filters.format[0] ?? ''}
          onChange={(e) => onChange({ format: e.target.value ? [e.target.value] : [] })}
        >
          <option value="">Qualquer tipo de comida</option>
          {(taxonomy?.foodFamilies ?? []).map((fam) => (
            <optgroup key={fam.code} label={fam.label}>
              {(taxonomy?.formats ?? [])
                .filter((f) => f.family === fam.code)
                .map((f) => (
                  <option key={f.code} value={f.code}>
                    {f.label}
                  </option>
                ))}
            </optgroup>
          ))}
        </select>
      </Group>

      <Group legend="Faixa de preço por pessoa">
        <div className="grid gap-1">
          {PRICE_CODES.map((code, i) => {
            const item = taxonomy?.priceLevels.find((p) => p.code === code);
            const id = `${uid}-preco-${code}`;
            return (
              <div key={code} className="flex min-h-11 items-center gap-2">
                <input
                  type="checkbox"
                  id={id}
                  className="size-6 flex-none"
                  checked={filters.price.includes(code)}
                  onChange={() => onChange({ price: toggle(filters.price, code) })}
                />
                <label htmlFor={id} className="grid grid-cols-[3.5rem_1fr] items-baseline gap-x-1">
                  <span aria-hidden="true" className={`font-bold tracking-wider ${PRICE_CLASS[i]}`}>
                    {'$'.repeat(i + 1)}
                  </span>
                  <span>
                    {item?.label ?? code}
                    {item?.rangeLabel ? <span className="block text-sm text-muted">{item.rangeLabel}</span> : null}
                  </span>
                </label>
              </div>
            );
          })}
        </div>
      </Group>

      <Group legend="Nota mínima">
        <div className="grid gap-1">
          {[null, ...MIN_RATING_OPTIONS].map((v) => {
            const id = `${uid}-nota-${v ?? 'qualquer'}`;
            return (
              <div key={id} className="flex min-h-11 items-center gap-2">
                <input
                  type="radio"
                  id={id}
                  name="nota-minima"
                  className="size-6 flex-none"
                  checked={filters.minRating === v}
                  onChange={() => onChange({ minRating: v })}
                />
                <label htmlFor={id} className="inline-flex items-center gap-1.5">
                  {v === null ? (
                    'Qualquer nota'
                  ) : (
                    <>
                      <IconStar size={16} className="text-star" fill="currentColor" />
                      {formatDecimal(v).replace(',0', '')} ou mais
                    </>
                  )}
                </label>
              </div>
            );
          })}
        </div>
        <p className="text-sm text-muted">Só lugares com nota média (5 avaliações ou mais).</p>
      </Group>

      <Group legend="Estilo alimentar">
        <div className="grid grid-cols-2 gap-x-3 gap-y-1">
          {(taxonomy?.dietaryStyles ?? []).map((s) => {
            const id = `${uid}-estilo-${s.code}`;
            return (
              <div key={s.code} className="flex min-h-11 items-center gap-2">
                <input
                  type="checkbox"
                  id={id}
                  className="size-6 flex-none"
                  checked={filters.dietary.includes(s.code)}
                  onChange={() => onChange({ dietary: toggle(filters.dietary, s.code) })}
                />
                <label htmlFor={id}>{s.label}</label>
              </div>
            );
          })}
        </div>
        <p className="text-sm text-muted">Informação declarada pelo estabelecimento.</p>
      </Group>

      <NearMe filters={filters} coords={coords} onCoords={onCoords} onChange={onChange} />

      <Group legend="Ordenar">
        <label htmlFor={`${uid}-ordem`} className="sr-only">
          Ordenar por
        </label>
        <select
          id={`${uid}-ordem`}
          className="fe-input"
          value={filters.sort === 'DISTANCE' && !coords ? 'RELEVANCE' : filters.sort}
          onChange={(e) => onChange({ sort: e.target.value as SearchSort })}
        >
          <option value="RELEVANCE">Relevância</option>
          <option value="DISTANCE" disabled={!coords}>
            {coords ? 'Distância' : 'Distância (use sua localização)'}
          </option>
          <option value="RATING">Melhor nota</option>
        </select>
      </Group>
    </form>
  );
}

type GeoState = { kind: 'idle' } | { kind: 'locating' } | { kind: 'error'; message: string };

function geoErrorMessage(err: GeolocationPositionError | null): string {
  if (!err) return 'Este navegador não informa a localização.';
  if (err.code === err.PERMISSION_DENIED) return 'Você não permitiu o uso da localização. Busque pelo nome ou pelos filtros.';
  if (err.code === err.TIMEOUT) return 'A localização demorou demais. Tente de novo.';
  return 'Não foi possível descobrir sua localização agora.';
}

/** "Perto de mim": pede a localização só no clique e não guarda a coordenada (taxonomia, seção 11.4). */
function NearMe({
  filters,
  coords,
  onCoords,
  onChange,
}: Pick<Props, 'filters' | 'coords' | 'onCoords' | 'onChange'>) {
  const uid = useId();
  const [geo, setGeo] = useState<GeoState>({ kind: 'idle' });
  const locate = () => {
    if (typeof navigator === 'undefined' || !navigator.geolocation) {
      setGeo({ kind: 'error', message: geoErrorMessage(null) });
      return;
    }
    setGeo({ kind: 'locating' });
    navigator.geolocation.getCurrentPosition(
      (pos) => {
        setGeo({ kind: 'idle' });
        onCoords(roundCoords(pos.coords.latitude, pos.coords.longitude));
      },
      (err) => setGeo({ kind: 'error', message: geoErrorMessage(err) }),
      { enableHighAccuracy: false, timeout: 15_000, maximumAge: 60_000 },
    );
  };
  return (
    <Group legend="Perto de mim">
      <p id={`${uid}-aviso`} className="text-sm">
        A sua localização é usada só nesta busca e não é guardada.
      </p>
      {coords ? (
        <>
          <p className="flex items-center gap-1.5 text-sm font-semibold">
            <IconNearMe size={16} />
            Usando a sua localização
          </p>
          <label htmlFor={`${uid}-raio`} className="text-sm font-semibold">
            Raio
          </label>
          <select
            id={`${uid}-raio`}
            className="fe-input"
            value={filters.radiusKm}
            onChange={(e) => onChange({ radiusKm: Number(e.target.value) })}
          >
            {RADII_KM.map((r) => (
              <option key={r} value={r}>
                {r} km{r === 5 ? ' (padrão)' : ''}
              </option>
            ))}
          </select>
          <button
            type="button"
            className="fe-btn fe-btn-sm"
            onClick={() => {
              onCoords(null);
              if (filters.sort === 'DISTANCE') onChange({ sort: 'RELEVANCE' });
            }}
          >
            Parar de usar a localização
          </button>
        </>
      ) : (
        <button type="button" className="fe-btn" onClick={locate} aria-describedby={`${uid}-aviso`} disabled={geo.kind === 'locating'}>
          <IconNearMe size={18} />
          {geo.kind === 'locating' ? 'Buscando sua localização…' : 'Usar minha localização'}
        </button>
      )}
      <p role="status" className="text-sm">
        {geo.kind === 'error' ? geo.message : ''}
      </p>
    </Group>
  );
}
