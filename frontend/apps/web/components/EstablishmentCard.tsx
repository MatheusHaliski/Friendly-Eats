import type { EstablishmentCard as Card } from '@friendly-eats/api';
import Link from 'next/link';
import { formatDistance } from '@/lib/format';
import { CuisineChip } from './CuisineChip';
import { IconPlace } from './icons';
import { PriceTag } from './PriceTag';
import { RatingInline } from './RatingSummary';

export function placeLine(loc: Card['location']): string {
  return [loc.neighborhood, loc.city && loc.uf ? `${loc.city} – ${loc.uf}` : (loc.city ?? loc.uf)].filter(Boolean).join(', ');
}

/** Card da busca (RF10): nome, tipo, chip, preço, nota, local e distância. */
export function EstablishmentCard({ card }: { card: Card }) {
  const headingId = `lugar-${card.id}`;
  const cuisineOrFormat = card.primaryCuisine?.label ?? card.primaryFormat?.label;
  return (
    <article aria-labelledby={headingId} className="fe-card flex h-full flex-col gap-3 p-4">
      <div className="grid gap-1">
        <h3 id={headingId} className="text-lg font-bold leading-snug">
          <Link href={`/lugares/${card.id}`} className="underline decoration-1 hover:decoration-2">
            {card.name}
          </Link>
        </h3>
        <p className="text-sm text-muted">
          {card.primaryType.label}
          {cuisineOrFormat ? ` · ${cuisineOrFormat}` : ''}
        </p>
      </div>
      <div className="flex flex-wrap items-center gap-2">
        {card.chip && <CuisineChip chip={card.chip} />}
        {card.adultsOnly && (
          <span className="fe-tag">
            18+<span className="sr-only"> (só para maiores de 18 anos)</span>
          </span>
        )}
        {card.status === 'CLOSED' && <span className="fe-tag">Fechado</span>}
      </div>
      <div className="grid gap-1 text-sm">
        <RatingInline rating={card.rating} size={16} />
        <PriceTag price={card.price} compact />
      </div>
      <p className="mt-auto flex items-start gap-1.5 text-sm text-muted">
        <IconPlace size={16} className="mt-0.5 flex-none" />
        <span>
          {placeLine(card.location) || 'Local não informado'}
          {card.location.approximate ? ' · Localização aproximada' : ''}
          {card.distanceKm !== null ? (
            <>
              {' · '}
              <span className="font-semibold text-fg">a {formatDistance(card.distanceKm)}</span>
            </>
          ) : null}
        </span>
      </p>
    </article>
  );
}
