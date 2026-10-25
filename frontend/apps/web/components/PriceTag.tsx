import type { PriceView } from '@friendly-eats/api';

const LEVEL_COUNT: Record<string, number> = { PRICE_1: 1, PRICE_2: 2, PRICE_3: 3, PRICE_4: 4 };
const LEVEL_COLOR: Record<string, string> = {
  PRICE_1: 'text-price-1',
  PRICE_2: 'text-price-2',
  PRICE_3: 'text-price-3',
  PRICE_4: 'text-price-4',
};

/** "informado pelo estabelecimento" ou "segundo N avaliações". */
export function priceSourceText(price: PriceView): string | null {
  if (price.source === 'PRICE_SRC_DECLARED') return 'informado pelo estabelecimento';
  if (price.source === 'PRICE_SRC_REVIEWS') return `segundo ${price.reportCount} ${price.reportCount === 1 ? 'avaliação' : 'avaliações'}`;
  return null;
}

/**
 * Cifrões cheios + vazados (a contagem é o sinal; a cor só reforça), com nível, faixa e origem sempre visíveis.
 * O leitor de tela ouve o accessibleLabel da API em vez de "dólar dólar" (DESIGN_SYSTEM.md, seção 5).
 */
export function PriceTag({ price, compact = false }: { price: PriceView; compact?: boolean }) {
  if (price.level === 'PRICE_NA') return null;
  if (price.level === 'PRICE_UNKNOWN') {
    return <span className="text-muted">Preço não informado</span>;
  }
  const filled = LEVEL_COUNT[price.level] ?? 0;
  const source = priceSourceText(price);
  const parts = [price.label, price.rangeLabel, compact ? null : source].filter(Boolean);
  return (
    <span role="img" aria-label={price.accessibleLabel} className="inline-flex items-baseline gap-x-2">
      <span className={`flex-none font-bold tracking-wider ${LEVEL_COLOR[price.level] ?? ''}`} data-testid="price-glyphs">
        {Array.from({ length: 4 }, (_, i) =>
          i < filled ? (
            <span key={i} data-dollar="cheio">
              $
            </span>
          ) : (
            <span key={i} data-dollar="vazado" className="fe-dollar-empty">
              $
            </span>
          ),
        )}
      </span>
      <span>{parts.join(' · ')}</span>
    </span>
  );
}
