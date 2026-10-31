'use client';

import { isApiError, type EstablishmentView } from '@friendly-eats/api';
import { useQuery } from '@tanstack/react-query';
import Link from 'next/link';
import { useEffect, type ReactNode } from 'react';
import { VerifyAgeRequired } from '@/components/age/AlcoholNotice';
import { AccountBadge, VerifiedSeal } from '@/components/AccountBadge';
import { CuisineChip } from '@/components/CuisineChip';
import { Notice } from '@/components/Form';
import { IconGlutenFree, IconLactoseFree, IconLogin, IconPlace } from '@/components/icons';
import { PriceTag } from '@/components/PriceTag';
import { RatingPanel } from '@/components/RatingSummary';
import { api } from '@/lib/api';
import { CROSS_CONTACT_WARNING, dietaryText, needsCrossContactWarning } from '@/lib/dietary';
import { placeKey, useTaxonomy } from '@/lib/queries';
import { DIMENSIONS } from '@/lib/review';
import { loginHref } from '@/lib/safe-redirect';
import { MyReviewArea } from './MyReviewArea';
import { PlacePosts } from './PlacePosts';
import { ReviewList } from './ReviewList';

/** Página do estabelecimento (RF6, RF7, RF8). */
export function PlaceView({ id }: { id: string }) {
  const place = useQuery({ queryKey: placeKey(id), queryFn: () => api.establishments.get(id) });
  const name = place.data?.name;
  useEffect(() => {
    if (name) document.title = `${name} · Friendly Eats`;
  }, [name]);

  if (place.isPending) {
    return (
      <p role="status" className="text-muted">
        Carregando o lugar…
      </p>
    );
  }
  if (place.isError) {
    const e = place.error;
    if (isApiError(e, 401)) {
      // lugar 18+ (bebida alcoólica) para quem não entrou
      return (
        <div className="grid max-w-xl gap-4">
          <h1 className="text-3xl font-extrabold">Conteúdo para maiores de 18 anos</h1>
          <Notice tone="info">
            <p>{e.detail}</p>
          </Notice>
          <p>
            <Link href={loginHref(`/lugares/${id}`)} className="fe-btn fe-btn-primary">
              <IconLogin size={18} />
              Entrar e verificar a idade
            </Link>
          </p>
        </div>
      );
    }
    if (isApiError(e, 403) && e.problem.ageVerificationRequired) {
      // RF17: adulto sem a idade verificada
      return <VerifyAgeRequired detail={e.detail} />;
    }
    if (isApiError(e, 404)) {
      return (
        <div className="grid max-w-xl gap-4">
          <h1 className="text-3xl font-extrabold">Lugar não encontrado</h1>
          <p>{e.detail}</p>
          <p>
            <Link href="/" className="underline">
              Voltar para o Explorar
            </Link>
          </p>
        </div>
      );
    }
    return (
      <Notice tone="error" live>
        <p>Não foi possível carregar o lugar. {e instanceof Error ? e.message : ''}</p>
        <p>
          <button type="button" className="fe-btn fe-btn-sm" onClick={() => place.refetch()}>
            Tentar de novo
          </button>
        </p>
      </Notice>
    );
  }
  return <PlaceContent place={place.data} />;
}

function PlaceContent({ place }: { place: EstablishmentView }) {
  const taxonomy = useTaxonomy();
  const dimensions = taxonomy.data?.reviewDimensions ?? [...DIMENSIONS];
  const c = place.classification;
  return (
    <article aria-labelledby="titulo-lugar" className="grid gap-8">
      <header className="grid gap-3">
        <p className="text-sm font-semibold uppercase tracking-wide text-muted">{place.group.label}</p>
        <h1 id="titulo-lugar" className="text-3xl font-extrabold tracking-tight sm:text-4xl">
          {place.name}
        </h1>
        <p className="text-lg">
          {place.primaryType.label}
          {place.secondaryTypes.length > 0 && (
            <span className="text-muted"> · também {place.secondaryTypes.map((t) => t.label.toLowerCase()).join(', ')}</span>
          )}
        </p>
        <div className="flex flex-wrap items-center gap-2">
          {c.chip && <CuisineChip chip={c.chip} />}
          {place.adultsOnly && (
            <span className="fe-tag">
              18+<span className="sr-only"> (só para maiores de 18 anos)</span>
            </span>
          )}
          {place.status === 'CLOSED' && <span className="fe-tag">Fechado</span>}
        </div>
        {place.published === false && (
          <Notice tone="info">
            <p className="font-semibold">Esta página está fora do ar: só você a vê.</p>
            {place.publishBlockers && place.publishBlockers.length > 0 && (
              <ul className="list-disc pl-5">
                {place.publishBlockers.map((b) => (
                  <li key={b}>{b}</li>
                ))}
              </ul>
            )}
          </Notice>
        )}
        {place.description && <p className="max-w-3xl">{place.description}</p>}
      </header>

      <div className="grid gap-8 lg:grid-cols-[1fr_20rem] lg:items-start">
        <div className="grid gap-8">
          <section aria-labelledby="titulo-nota" className="fe-card grid gap-4 p-5">
            <h2 id="titulo-nota" className="text-xl font-bold">
              Nota
            </h2>
            <RatingPanel rating={place.rating} dimensions={dimensions} />
          </section>

          <MyReviewArea place={place} />

          <ReviewList placeId={String(place.id)} dimensions={dimensions} />

          <PlacePosts placeId={String(place.id)} />
        </div>

        <aside aria-label="Informações do lugar" className="grid gap-4">
          <InfoSection title="Classificação">
            <dl className="grid gap-2">
              {c.primaryCuisine && (
                <Item term="Culinária">
                  {[c.primaryCuisine, ...c.secondaryCuisines].map((x) => x.label).join(', ')}
                </Item>
              )}
              {c.primaryFormat && (
                <Item term="Tipo de comida">{[c.primaryFormat, ...c.secondaryFormats].map((x) => x.label).join(', ')}</Item>
              )}
              {c.approach && <Item term="Abordagem">{c.approach.label}</Item>}
              {c.dietaryOffers.length > 0 && (
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
                  <p className="mt-1 text-sm text-muted">
                    {c.dietaryOffers.some((d) => needsCrossContactWarning(d.code))
                      ? CROSS_CONTACT_WARNING
                      : 'Informação declarada pelo estabelecimento'}
                  </p>
                </Item>
              )}
            </dl>
          </InfoSection>

          {place.price.level !== 'PRICE_NA' && (
            <InfoSection title="Preço por pessoa">
              <p data-testid="preco">
                <PriceTag price={place.price} />
              </p>
            </InfoSection>
          )}

          <InfoSection title="Local">
            <address className="grid gap-1 not-italic">
              {place.location.street && (
                <span>
                  {place.location.street}
                  {place.location.streetNumber ? `, ${place.location.streetNumber}` : ''}
                  {place.location.complement ? ` – ${place.location.complement}` : ''}
                </span>
              )}
              {place.location.neighborhood && <span>{place.location.neighborhood}</span>}
              <span className="flex items-center gap-1.5">
                <IconPlace size={16} />
                {[place.location.city, place.location.uf].filter(Boolean).join(' – ')}
              </span>
              {place.location.cep && <span className="text-sm text-muted">CEP {place.location.cep.replace(/^(\d{5})(\d{3})$/, '$1-$2')}</span>}
            </address>
            {place.location.approximate && (
              <p className="mt-2 text-sm">
                <strong>Localização aproximada</strong>
                <span className="text-muted"> · endereço aproximado: bairro e cidade</span>
              </p>
            )}
          </InfoSection>

          {place.owner && (
            <InfoSection title="Responsável">
              <p className="flex flex-wrap items-center gap-x-2 gap-y-1">
                <AccountBadge code={place.owner.category.code} label={place.owner.category.label} />
              </p>
              <p className="flex flex-wrap items-center gap-1.5">
                <Link href={`/u/${place.owner.id}`} className="font-semibold underline">
                  {place.owner.name}
                </Link>
                {place.owner.verified && <VerifiedSeal />}
                <span className="text-muted">@{place.owner.handle}</span>
              </p>
            </InfoSection>
          )}
        </aside>
      </div>
    </article>
  );
}

function InfoSection({ title, children }: { title: string; children: ReactNode }) {
  const id = `info-${title.toLowerCase().normalize('NFD').replace(/[^a-z]+/g, '-')}`;
  return (
    <section aria-labelledby={id} className="fe-card grid gap-2 p-4">
      <h2 id={id} className="font-bold">
        {title}
      </h2>
      {children}
    </section>
  );
}

function Item({ term, children }: { term: string; children: ReactNode }) {
  return (
    <div className="grid gap-0.5">
      <dt className="text-sm text-muted">{term}</dt>
      <dd>{children}</dd>
    </div>
  );
}
