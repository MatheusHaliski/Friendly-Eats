import type { RatingView } from '@friendly-eats/api';
import { render, screen } from '@testing-library/react';
import { describe, expect, it } from 'vitest';
import { DIMENSIONS } from '@/lib/review';
import { RatingInline, RatingPanel } from './RatingSummary';
import { starShapes } from './Stars';

const rated: RatingView = {
  state: 'RATED',
  average: 4.3,
  averageLabel: '4,3',
  count: 128,
  histogram: { '5': 70, '4': 40, '3': 10, '2': 5, '1': 3 },
  dimensions: { COMIDA: 4.5 },
  accessibleLabel: 'Nota 4,3 de 5, com base em 128 avaliações',
};

describe('nota agregada', () => {
  it('RATING_NONE: só o texto, sem estrelas', () => {
    const { container } = render(
      <RatingInline rating={{ ...rated, state: 'RATING_NONE', average: null, averageLabel: null, count: 0, histogram: {}, dimensions: {}, accessibleLabel: 'Sem avaliações ainda' }} />,
    );
    expect(screen.getByText('Sem avaliações ainda')).toBeInTheDocument();
    expect(container.querySelector('svg')).toBeNull();
  });

  it('RATING_FEW: contagem sem média', () => {
    const { container } = render(<RatingInline rating={{ ...rated, state: 'RATING_FEW', average: null, averageLabel: null, count: 3 }} />);
    expect(screen.getByText('3 avaliações, ainda sem nota média')).toBeInTheDocument();
    expect(container.querySelector('svg')).toBeNull();
  });

  it('RATED: número, estrelas decorativas, histograma 5→1 e dimensões', () => {
    render(<RatingPanel rating={rated} dimensions={[...DIMENSIONS]} />);
    expect(screen.getByRole('img', { name: rated.accessibleLabel })).toHaveTextContent('4,3');
    const itens = screen.getByRole('list', { name: 'Distribuição das notas' }).querySelectorAll('li');
    expect([...itens].map((li) => li.textContent?.slice(0, 1))).toEqual(['5', '4', '3', '2', '1']);
    expect(screen.getByText('5 estrelas: 70 avaliações (55%)')).toBeInTheDocument();
    expect(screen.getByText('Comida')).toBeInTheDocument();
    expect(screen.getByText('4,5')).toBeInTheDocument();
  });

  it('meia estrela pela forma, arredondada só como apoio', () => {
    expect(starShapes(4.3)).toEqual(['cheia', 'cheia', 'cheia', 'cheia', 'meia']);
    expect(starShapes(4.2)).toEqual(['cheia', 'cheia', 'cheia', 'cheia', 'vazia']);
    expect(starShapes(1)).toEqual(['cheia', 'vazia', 'vazia', 'vazia', 'vazia']);
  });
});
