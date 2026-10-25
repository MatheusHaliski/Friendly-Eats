import type { DimensionItem, RatingView } from '@friendly-eats/api';
import { formatDecimal, plural } from '@/lib/format';
import { IconStar } from './icons';
import { Stars } from './Stars';

/**
 * Nota agregada nos três estados (taxonomia, seção 10.2): sem avaliação (texto, sem estrelas), poucas (só a
 * contagem) e com nota (estrelas + "4,3 · 128 avaliações"). As estrelas são decorativas; o conjunto tem o rótulo.
 */
export function RatingInline({ rating, size = 18 }: { rating: RatingView; size?: number }) {
  if (rating.state === 'RATING_NONE') return <span className="text-muted">Sem avaliações ainda</span>;
  if (rating.state === 'RATING_FEW' || rating.average === null) {
    return <span className="text-muted">{plural(rating.count, 'avaliação', 'avaliações')}, ainda sem nota média</span>;
  }
  return (
    <span role="img" aria-label={rating.accessibleLabel} className="inline-flex flex-wrap items-center gap-x-2">
      <Stars average={rating.average} size={size} />
      <span>
        <strong className="font-bold">{rating.averageLabel ?? formatDecimal(rating.average)}</strong>
        {' · '}
        {plural(rating.count, 'avaliação', 'avaliações')}
      </span>
    </span>
  );
}

/** Bloco da página do lugar: número grande, estrelas, histograma de 5 a 1 e médias por dimensão. */
export function RatingPanel({ rating, dimensions }: { rating: RatingView; dimensions: DimensionItem[] }) {
  if (rating.state !== 'RATED' || rating.average === null) {
    return (
      <p className="text-lg" data-testid="rating-state">
        <RatingInline rating={rating} />
      </p>
    );
  }
  const max = Math.max(1, ...Object.values(rating.histogram).map((n) => n ?? 0));
  const dims = dimensions.filter((d) => rating.dimensions[d.code] !== undefined);
  return (
    <div className="grid gap-6 sm:grid-cols-[auto_1fr]" data-testid="rating-state">
      <div className="flex flex-col gap-1" role="img" aria-label={rating.accessibleLabel}>
        <span className="text-5xl font-extrabold leading-none">{rating.averageLabel}</span>
        <Stars average={rating.average} size={22} />
        <span className="text-muted">de 5 · {plural(rating.count, 'avaliação', 'avaliações')}</span>
      </div>
      <div className="grid gap-4">
        <ul className="grid gap-1" aria-label="Distribuição das notas">
          {(['5', '4', '3', '2', '1'] as const).map((star) => {
            const n = rating.histogram[star] ?? 0;
            const pct = rating.count ? Math.round((n / rating.count) * 100) : 0;
            return (
              <li key={star} className="grid grid-cols-[2.5rem_1fr_2.5rem] items-center gap-2 text-sm">
                <span className="inline-flex items-center gap-0.5" aria-hidden="true">
                  {star}
                  <IconStar size={14} className="text-star" fill="currentColor" />
                </span>
                <span className="h-2.5 overflow-hidden rounded-full bg-sunken" aria-hidden="true">
                  <span className="block h-full rounded-full bg-fg" style={{ width: `${(n / max) * 100}%` }} />
                </span>
                <span className="text-right tabular-nums" aria-hidden="true">
                  {n}
                </span>
                <span className="sr-only">
                  {star} {star === '1' ? 'estrela' : 'estrelas'}: {plural(n, 'avaliação', 'avaliações')} ({pct}%)
                </span>
              </li>
            );
          })}
        </ul>
        {dims.length > 0 && (
          <dl className="grid grid-cols-2 gap-x-6 gap-y-1 text-sm sm:grid-cols-3">
            {dims.map((d) => (
              <div key={d.code} className="flex justify-between gap-2 border-b border-line py-1">
                <dt className="text-muted">{d.label}</dt>
                <dd className="font-semibold tabular-nums">{formatDecimal(rating.dimensions[d.code]!)}</dd>
              </div>
            ))}
          </dl>
        )}
      </div>
    </div>
  );
}
