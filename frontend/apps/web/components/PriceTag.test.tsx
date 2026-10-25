import type { PriceView } from '@friendly-eats/api';
import { render, screen } from '@testing-library/react';
import { describe, expect, it } from 'vitest';
import { PriceTag } from './PriceTag';

const base: PriceView = {
  level: 'PRICE_2',
  glyph: '$$',
  label: 'Moderado',
  rangeLabel: 'R$ 40 a R$ 80 por pessoa',
  source: 'PRICE_SRC_REVIEWS',
  reportCount: 23,
  declaredMinCents: null,
  declaredMaxCents: null,
  accessibleLabel: 'Faixa de preço: moderado, R$ 40 a R$ 80 por pessoa, segundo 23 avaliações',
};

describe('PriceTag', () => {
  it('cifrões cheios + vazados, rótulo visível e texto por extenso para o leitor de tela', () => {
    const { container } = render(<PriceTag price={base} />);
    expect(screen.getByRole('img', { name: base.accessibleLabel })).toHaveTextContent('Moderado · R$ 40 a R$ 80 por pessoa · segundo 23 avaliações');
    expect(container.querySelectorAll('[data-dollar="cheio"]')).toHaveLength(2);
    expect(container.querySelectorAll('[data-dollar="vazado"]')).toHaveLength(2);
  });

  it('declarado pelo estabelecimento', () => {
    render(<PriceTag price={{ ...base, source: 'PRICE_SRC_DECLARED', reportCount: 0 }} />);
    expect(screen.getByRole('img')).toHaveTextContent('informado pelo estabelecimento');
  });

  it('sem preço não mostra cifrões vazados; "não se aplica" não aparece', () => {
    const { container, rerender } = render(<PriceTag price={{ ...base, level: 'PRICE_UNKNOWN', glyph: null, label: 'Preço não informado', rangeLabel: null, source: null }} />);
    expect(screen.getByText('Preço não informado')).toBeInTheDocument();
    expect(container.querySelectorAll('[data-dollar]')).toHaveLength(0);
    rerender(<PriceTag price={{ ...base, level: 'PRICE_NA', glyph: null, label: 'Não se aplica', rangeLabel: null, source: null }} />);
    expect(container).toBeEmptyDOMElement();
  });
});
