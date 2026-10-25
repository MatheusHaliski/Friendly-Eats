// Filtros do Explorar (RF10) ↔ URL ↔ parâmetros de GET /api/v1/establishments.
// A localização da pessoa nunca vai para a URL: lat/lng ficam só na memória da página.
import type { CuisineChipCode, PriceLevelCode, SearchParams, SearchSort, TaxonomyCatalog } from '@friendly-eats/api';

export const RADII_KM = [1, 2, 5, 10, 25] as const;
export const DEFAULT_RADIUS_KM = 5;
export const PAGE_SIZE = 20;
export const MIN_RATING_OPTIONS = [3, 4, 4.5] as const;
const PRICE_CODES: PriceLevelCode[] = ['PRICE_1', 'PRICE_2', 'PRICE_3', 'PRICE_4'];
const SORTS: SearchSort[] = ['RELEVANCE', 'DISTANCE', 'RATING'];
const CHIPS: CuisineChipCode[] = [
  'BRASILEIRA',
  'EUROPEIA',
  'ASIATICA',
  'AMERICAS',
  'ORIENTE_MEDIO_AFRICA',
  'LANCHES_PIZZA',
  'DOCES_CAFE',
  'VEGETAL',
];

export interface ExploreFilters {
  q: string;
  chip: CuisineChipCode | null;
  cuisine: string[];
  format: string[];
  price: PriceLevelCode[];
  minRating: number | null;
  dietary: string[];
  radiusKm: number;
  sort: SearchSort;
  page: number;
}

export interface Coords {
  lat: number;
  lng: number;
}

const CODE = /^[A-Z0-9_]{1,40}$/;

function codes(values: string[]): string[] {
  return [...new Set(values.filter((v) => CODE.test(v)))];
}

export function parseFilters(sp: URLSearchParams): ExploreFilters {
  const chip = sp.get('chip');
  const minRating = Number(sp.get('minRating'));
  const radius = Number(sp.get('radiusKm'));
  const page = Number(sp.get('page'));
  const sort = sp.get('sort') as SearchSort | null;
  return {
    q: (sp.get('q') ?? '').slice(0, 120),
    chip: chip && (CHIPS as string[]).includes(chip) ? (chip as CuisineChipCode) : null,
    cuisine: codes(sp.getAll('cuisine')),
    format: codes(sp.getAll('format')),
    price: sp.getAll('price').filter((p): p is PriceLevelCode => (PRICE_CODES as string[]).includes(p)),
    minRating: Number.isFinite(minRating) && minRating >= 1 && minRating <= 5 ? minRating : null,
    dietary: codes(sp.getAll('dietary')),
    radiusKm: (RADII_KM as readonly number[]).includes(radius) ? radius : DEFAULT_RADIUS_KM,
    sort: sort && SORTS.includes(sort) ? sort : 'RELEVANCE',
    page: Number.isInteger(page) && page > 0 ? page : 0,
  };
}

/** Só o que difere do padrão vai para a URL, para o link ficar curto. */
export function filtersToSearch(f: ExploreFilters): string {
  const sp = new URLSearchParams();
  if (f.q.trim()) sp.set('q', f.q.trim());
  if (f.chip) sp.set('chip', f.chip);
  f.cuisine.forEach((c) => sp.append('cuisine', c));
  f.format.forEach((c) => sp.append('format', c));
  f.price.forEach((c) => sp.append('price', c));
  if (f.minRating !== null) sp.set('minRating', String(f.minRating));
  f.dietary.forEach((c) => sp.append('dietary', c));
  if (f.radiusKm !== DEFAULT_RADIUS_KM) sp.set('radiusKm', String(f.radiusKm));
  if (f.sort !== 'RELEVANCE') sp.set('sort', f.sort);
  if (f.page > 0) sp.set('page', String(f.page));
  const s = sp.toString();
  return s ? `?${s}` : '';
}

export const EMPTY_FILTERS: ExploreFilters = parseFilters(new URLSearchParams());

export function hasActiveFilters(f: ExploreFilters): boolean {
  return filtersToSearch({ ...f, page: 0, sort: 'RELEVANCE', radiusKm: DEFAULT_RADIUS_KM }) !== '';
}

/** Macro da culinária → chip (mesma tabela do CuisineChip.java), para agrupar o seletor de culinária. */
export const MACRO_TO_CHIP: Record<string, CuisineChipCode> = {
  BRASIL: 'BRASILEIRA',
  EUROPA: 'EUROPEIA',
  ASIA: 'ASIATICA',
  AMERICA_LATINA: 'AMERICAS',
  AMERICA_NORTE: 'AMERICAS',
  ORIENTE_MEDIO_NORTE_AFRICA: 'ORIENTE_MEDIO_AFRICA',
  AFRICA: 'ORIENTE_MEDIO_AFRICA',
};

/** Monta os parâmetros da API; distância e raio só com coordenadas. */
export function toSearchParams(f: ExploreFilters, coords: Coords | null): SearchParams {
  const params: SearchParams = {
    q: f.q.trim() || undefined,
    // o chip vai como está: o backend aplica a mesma derivação do card (vegetal só com estilo EXCLUSIVO)
    chip: f.chip ? [f.chip] : undefined,
    cuisine: f.cuisine.length ? f.cuisine : undefined,
    format: f.format.length ? f.format : undefined,
    dietary: f.dietary.length ? f.dietary : undefined,
    price: f.price.length ? f.price : undefined,
    minRating: f.minRating ?? undefined,
    page: f.page,
    size: PAGE_SIZE,
  };
  if (coords) {
    params.lat = coords.lat;
    params.lng = coords.lng;
    params.radiusKm = f.radiusKm;
  }
  params.sort = f.sort === 'DISTANCE' && !coords ? 'RELEVANCE' : f.sort;
  return params;
}

/** Minimização (LGPD): ~110 m de precisão bastam para "perto de mim". */
export function roundCoords(lat: number, lng: number): Coords {
  return { lat: Math.round(lat * 1000) / 1000, lng: Math.round(lng * 1000) / 1000 };
}

const PRICE_TEXT: Record<string, string> = { PRICE_1: '$ Econômico', PRICE_2: '$$ Moderado', PRICE_3: '$$$ Caro', PRICE_4: '$$$$ Muito caro' };
const SORT_TEXT: Record<SearchSort, string> = { RELEVANCE: 'relevância', DISTANCE: 'distância', RATING: 'nota' };

/**
 * "Por que aparece aqui" (modal do lugar): os filtros ativos em frases. Sem filtro, diz que é a lista geral e a
 * ordem. Os rótulos vêm da taxonomia quando ela já carregou.
 */
export function filterReasons(
  f: ExploreFilters,
  taxonomy?: Pick<TaxonomyCatalog, 'cuisines' | 'formats' | 'dietaryStyles' | 'cuisineChips'>,
  near = false,
): string[] {
  const label = (list: Array<{ code: string; label: string }> | undefined, code: string) => list?.find((x) => x.code === code)?.label ?? code;
  const out: string[] = [];
  if (f.q.trim()) out.push(`O nome combina com a busca “${f.q.trim()}”.`);
  if (f.chip) out.push(`Culinária do grupo ${label(taxonomy?.cuisineChips, f.chip)}.`);
  if (f.cuisine.length) out.push(`Culinária: ${f.cuisine.map((c) => label(taxonomy?.cuisines, c)).join(', ')}.`);
  if (f.format.length) out.push(`Tipo de comida: ${f.format.map((c) => label(taxonomy?.formats, c)).join(', ')}.`);
  if (f.price.length) out.push(`Faixa de preço: ${f.price.map((p) => PRICE_TEXT[p] ?? p).join(', ')}.`);
  if (f.minRating !== null) out.push(`Nota ${String(f.minRating).replace('.', ',')} ou mais.`);
  if (f.dietary.length) out.push(`Estilos alimentares: ${f.dietary.map((c) => label(taxonomy?.dietaryStyles, c)).join(', ')}.`);
  if (near) out.push(`Dentro do raio de ${f.radiusKm} km de onde você está.`);
  if (!out.length) out.push('Aparece na lista geral, sem filtros.');
  out.push(`Ordem: por ${SORT_TEXT[f.sort === 'DISTANCE' && !near ? 'RELEVANCE' : f.sort]}.`);
  return out;
}
