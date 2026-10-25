import { describe, expect, it } from 'vitest';
import { filterReasons, filtersToSearch, parseFilters, roundCoords, toSearchParams } from './explore-params';

describe('filtros do Explorar na URL', () => {
  it('ida e volta mantém os filtros e descarta lixo', () => {
    const sp = new URLSearchParams('q=pão&chip=AMERICAS&price=PRICE_2&price=PRICE_9&minRating=4.5&dietary=VEGANO&radiusKm=10&sort=RATING&page=2&cuisine=<script>');
    const f = parseFilters(sp);
    expect(f).toMatchObject({ q: 'pão', chip: 'AMERICAS', price: ['PRICE_2'], minRating: 4.5, dietary: ['VEGANO'], radiusKm: 10, sort: 'RATING', page: 2, cuisine: [] });
    expect(filtersToSearch(f)).toBe('?q=p%C3%A3o&chip=AMERICAS&price=PRICE_2&minRating=4.5&dietary=VEGANO&radiusKm=10&sort=RATING&page=2');
    expect(parseFilters(new URLSearchParams(filtersToSearch(f)))).toEqual(f);
  });

  it('valores padrão não vão para a URL', () => {
    expect(filtersToSearch(parseFilters(new URLSearchParams('radiusKm=7&minRating=9')))).toBe('');
  });

  it('a localização nunca vai para a URL', () => {
    const f = parseFilters(new URLSearchParams('lat=-19.9&lng=-43.9'));
    expect(filtersToSearch(f)).not.toMatch(/lat|lng/);
  });
});

describe('parâmetros da API', () => {
  it('manda o chip como está: o backend aplica a derivação do card', () => {
    const f = parseFilters(new URLSearchParams('chip=VEGETAL&cuisine=MINEIRA'));
    expect(toSearchParams(f, null)).toMatchObject({ chip: ['VEGETAL'], cuisine: ['MINEIRA'] });
    expect(toSearchParams(parseFilters(new URLSearchParams('')), null)).not.toHaveProperty('dietary', expect.anything());
  });

  it('distância e raio só com coordenadas', () => {
    const f = parseFilters(new URLSearchParams('sort=DISTANCE&radiusKm=2'));
    const sem = toSearchParams(f, null);
    expect(sem.sort).toBe('RELEVANCE');
    expect(sem).not.toHaveProperty('lat');
    expect(sem).not.toHaveProperty('radiusKm');
    expect(toSearchParams(f, { lat: -19.9, lng: -43.9 })).toMatchObject({ sort: 'DISTANCE', radiusKm: 2, lat: -19.9, lng: -43.9 });
  });

  it('arredonda a coordenada (~110 m)', () => {
    expect(roundCoords(-19.93612, -43.93456)).toEqual({ lat: -19.936, lng: -43.935 });
  });
});

describe('por que o lugar aparece (modal)', () => {
  it('sem filtros: lista geral, por relevância', () => {
    expect(filterReasons(parseFilters(new URLSearchParams()))).toEqual(['Aparece na lista geral, sem filtros.', 'Ordem: por relevância.']);
  });

  it('filtros em frases, com os rótulos da taxonomia', () => {
    const f = parseFilters(new URLSearchParams('q=sabor&chip=BRASILEIRA&price=PRICE_2&minRating=4.5&dietary=VEGANO&sort=DISTANCE&radiusKm=10'));
    const taxonomy = {
      cuisines: [],
      formats: [],
      dietaryStyles: [{ code: 'VEGANO', label: 'Vegano', group: null }],
      cuisineChips: [{ code: 'BRASILEIRA' as const, label: 'Brasileira', lucideIcon: 'cooking-pot', darkHex: '#000000', lightHex: '#000000' }],
    };
    expect(filterReasons(f, taxonomy, true)).toEqual([
      'O nome combina com a busca “sabor”.',
      'Culinária do grupo Brasileira.',
      'Faixa de preço: $$ Moderado.',
      'Nota 4,5 ou mais.',
      'Estilos alimentares: Vegano.',
      'Dentro do raio de 10 km de onde você está.',
      'Ordem: por distância.',
    ]);
    expect(filterReasons(f, taxonomy, false).at(-1)).toBe('Ordem: por relevância.');
  });
});
