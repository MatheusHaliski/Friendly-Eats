'use client';

import type { EstablishmentCard, EstablishmentView, RatingView } from '@friendly-eats/api';
import { useQuery } from '@tanstack/react-query';
import Link from 'next/link';
import { useId, type ReactNode } from 'react';
import { AccountBadge, VerifiedSeal } from '@/components/AccountBadge';
import { CuisineChip } from '@/components/CuisineChip';
import { Dialog } from '@/components/Dialog';
import { Notice } from '@/components/Form';
import { IconClose, IconExternal, IconGlutenFree, IconLactoseFree, IconPlace } from '@/components/icons';
import { PriceTag } from '@/components/PriceTag';
import { RatingInline } from '@/components/RatingSummary';
import { api } from '@/lib/api';
import { CROSS_CONTACT_WARNING, dietaryText, needsCrossContactWarning } from '@/lib/dietary';
import { formatDistance } from '@/lib/format';
import { placeKey } from '@/lib/queries';

/** O que a nota quer dizer, conforme o estado (taxonomia, seção 10.2). */
export function ratingMeaning(r: RatingView): string {
  if (r.state === 'RATING_NONE') return 'Ninguém avaliou ainda: não há nota.';
  if (r.state === 'RATING_FEW') return 'Poucas avaliações: a média só aparece a partir de 5, para uma opinião isolada não virar a nota do lugar.';
  return `Média de ${r.count} ${r.count === 1 ? 'avaliação' : 'avaliações'} de quem esteve lá, de 1 (péssimo) a 5 (excelente).`;
}

/** O que a faixa de preço significa: a escala nacional por pessoa (taxonomia, seção 9). */
export function priceMeaning(card: EstablishmentCard): string {
  const p = card.price;
  if (p.level === 'PRICE_UNKNOWN') return 'O lugar não informou o preço e ainda não há avaliações suficientes para estimar.';
  if (p.level === 'PRICE_NA') return 'Preço não se aplica a este tipo de lugar.';
  return 'Gasto por pessoa na escala nacional: $ até R$\u00a040 · $$ de R$\u00a040 a R$\u00a080 · $$$ de R$\u00a080 a R$\u00a0160 · $$$$ acima de R$\u00a0160. Os cifrões cheios marcam o nível.';
}

/** Endereço público: completo só quando a visibilidade deixa (senão, bairro e cidade). */
export function publicAddress(place: EstablishmentView | undefined, card: EstablishmentCard): { lines: string[]; full: boolean } {
  const loc = place?.location;
  const cityLine = [card.location.city, card.location.uf].filter(Boolean).join(' – ');
  if (loc && loc.street && !loc.approximate && !card.location.approximate) {
    const street = `${loc.street}${loc.streetNumber ? `, ${loc.streetNumber}` : ''}${loc.complement ? ` – ${loc.complement}` : ''}`;
    return { lines: [street, loc.neighborhood, cityLine].filter((x): x is string => !!x), full: true };
  }
  return { lines: [card.location.neighborhood, cityLine].filter((x): x is string => !!x), full: false };
}

/** Link "Como chegar" (mapa externo), só com o endereço completo e público. */
export function directionsUrl(lines: string[]): string {
  return `https://www.google.com/maps/dir/?api=1&destination=${encodeURIComponent(lines.join(', '))}`;
}

interface Props {
  card: EstablishmentCard | null;
  /** Por que o lugar aparece nesta lista (filtros, distância). */
  reasons: string[];
  onClose: () => void;
}

/**
 * Modal explicativo do lugar (Explorar e aba Restaurantes do feed): o que cada informação do cartão quer dizer, o
 * endereço público e o caminho para a página do lugar. Foco preso, Esc fecha e o foco volta ao cartão (Dialog).
 */
export function PlaceDialog({ card, reasons, onClose }: Props) {
  const uid = useId();
  const id = card ? String(card.id) : '';
  const place = useQuery({ queryKey: placeKey(id), queryFn: () => api.establishments.get(id), enabled: !!card });
  if (!card) return null;
  const c = place.data?.classification;
  const address = publicAddress(place.data, card);
  const cuisineLine = [card.primaryCuisine?.label, card.primaryFormat?.label].filter(Boolean).join(' · ');
  return (
    <Dialog open onClose={onClose} labelledBy={`${uid}-titulo`} describedBy={`${uid}-tipo`}>
      <div className="grid gap-4" data-testid="modal-lugar">
        <div className="flex items-start justify-between gap-3">
          <div className="grid gap-1">
            <h2 id={`${uid}-titulo`} className="flex flex-wrap items-center gap-1.5 text-2xl font-extrabold">
              {card.name}
              {place.data?.owner?.verified && <VerifiedSeal />}
            </h2>
            <p id={`${uid}-tipo`} className="text-muted">
              {card.primaryType.label} · {card.group.label}
            </p>
          </div>
          <button type="button" className="fe-btn fe-btn-sm" onClick={onClose} aria-label="Fechar">
            <IconClose size={18} />
          </button>
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

        <dl className="grid gap-3">
          {cuisineLine && (
            <Item term="Culinária e formato">
              <p>{cuisineLine}</p>
            </Item>
          )}
          <Item term="Preço">
            <p>
              <PriceTag price={card.price} />
            </p>
            <p className="text-sm text-muted">{priceMeaning(card)}</p>
          </Item>
          <Item term="Nota">
            <RatingInline rating={card.rating} size={16} />
            <p className="text-sm text-muted">{ratingMeaning(card.rating)}</p>
          </Item>
          {c && c.dietaryOffers.length > 0 && (
            <Item term="Estilos alimentares">
              <ul className="grid gap-1">
                {c.dietaryOffers.map((d) => (
                  <li key={d.code} className="flex items-center gap-1.5">
                    {d.code === 'SEM_GLUTEN' && <IconGlutenFree size={16} />}
                    {d.code === 'SEM_LACTOSE' && <IconLactoseFree size={16} />}
                    {dietaryText(d.code, d.label, d.level)}
                  </li>
                ))}
              </ul>
              <p className="text-sm text-muted">
                {c.dietaryOffers.some((d) => needsCrossContactWarning(d.code)) ? CROSS_CONTACT_WARNING : 'Informação declarada pelo estabelecimento'}
              </p>
            </Item>
          )}
          <Item term="Endereço">
            <address className="grid not-italic">
              {address.lines.map((l, i) => (
                <span key={l} className={i === address.lines.length - 1 ? 'flex items-center gap-1.5' : undefined}>
                  {i === address.lines.length - 1 && <IconPlace size={16} />}
                  {l}
                </span>
              ))}
            </address>
            {!address.full && <p className="text-sm text-muted">Endereço aproximado: bairro e cidade (o lugar não mostra o endereço completo).</p>}
          </Item>
          {place.data?.owner && (
            <Item term="Responsável">
              <p className="flex flex-wrap items-center gap-x-2 gap-y-1">
                <span className="font-semibold">{place.data.owner.name}</span>
                <AccountBadge code={place.data.owner.category.code} label={place.data.owner.category.label} />
                {place.data.owner.verified ? <span className="text-sm text-muted">Conta verificada: é quem diz ser (não indica qualidade).</span> : null}
              </p>
            </Item>
          )}
          <Item term="Por que aparece aqui">
            <ul className="grid list-disc gap-0.5 pl-5" data-testid="motivos">
              {card.distanceKm !== null && <li>Fica a {formatDistance(card.distanceKm)} de você.</li>}
              {reasons.map((r) => (
                <li key={r}>{r}</li>
              ))}
            </ul>
          </Item>
        </dl>

        {place.isPending && (
          <p role="status" className="text-sm text-muted">
            Carregando os detalhes…
          </p>
        )}
        {place.isError && (
          <Notice tone="info">
            <p>Não foi possível carregar os detalhes agora; a página do lugar tem tudo.</p>
          </Notice>
        )}

        <div className="flex flex-wrap gap-2">
          <Link href={`/lugares/${card.id}`} className="fe-btn fe-btn-primary">
            Ver página do lugar
          </Link>
          {address.full && (
            <a href={directionsUrl(address.lines)} target="_blank" rel="noopener noreferrer" className="fe-btn">
              <IconExternal size={18} />
              Como chegar
              <span className="sr-only">(abre o mapa em outra aba)</span>
            </a>
          )}
          <button type="button" className="fe-btn" onClick={onClose}>
            Fechar
          </button>
        </div>
      </div>
    </Dialog>
  );
}

function Item({ term, children }: { term: string; children: ReactNode }) {
  return (
    <div className="grid gap-1 border-t border-line pt-3">
      <dt className="font-bold">{term}</dt>
      <dd className="grid gap-1">{children}</dd>
    </div>
  );
}
