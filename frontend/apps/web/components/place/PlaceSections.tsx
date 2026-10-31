'use client';

import type { ActionView, ChargesView, EstablishmentView, FacetSectionView, HoursView, OpenStatusView } from '@friendly-eats/api';
import { useId } from 'react';
import { IconExternal, IconPlace } from '@/components/icons';
import { osmUrl, rangesText, timeZoneLabel } from '@/lib/place-info';
import { directionsUrl } from '@/components/explore/PlaceDialog';
import {
  FACET_ICONS,
  IconCharges,
  IconDelivery,
  IconDirections,
  IconMap,
  IconReserve,
  IconWhatsapp,
} from './place-icons';
import { OpenStatusBadge } from './OpenStatusBadge';
import { PlaceMap } from './PlaceMap';

export function HoursSection({ hours, status }: { hours: HoursView; status: OpenStatusView }) {
  return (
    <div className="grid gap-3">
      <p>
        <OpenStatusBadge status={status} />
      </p>
      {status.note && <p className="text-sm font-semibold">{status.note}</p>}
      {hours.temporaryClosure.closed && (
        <p className="text-sm">
          O estabelecimento está fechado temporariamente
          {hours.temporaryClosure.reopensOn ? `, com volta prevista para ${brDate(hours.temporaryClosure.reopensOn)}` : ', sem data de volta'}.
        </p>
      )}
      {hours.informed ? (
        <dl className="grid gap-1" data-testid="horarios">
          {hours.weekly.map((d) => (
            <div key={d.day} className={`flex flex-wrap justify-between gap-x-3 ${d.today ? 'font-bold' : ''}`}>
              <dt>
                {d.label}
                {d.today && <span> (hoje)</span>}
              </dt>
              <dd>{d.closed ? 'Fechado' : rangesText(d.ranges)}</dd>
            </div>
          ))}
        </dl>
      ) : (
        <p className="text-muted">Horário não informado.</p>
      )}
      {hours.special.length > 0 && (
        <div className="grid gap-1">
          <h3 className="text-sm font-bold">Datas especiais</h3>
          <ul className="grid gap-0.5 text-sm" data-testid="datas-especiais">
            {hours.special.map((s) => (
              <li key={s.date}>
                {s.label}
                {s.note ? ` · ${s.note}` : ''}: {s.closed ? 'fechado' : rangesText(s.ranges)}
              </li>
            ))}
          </ul>
        </div>
      )}
      <p className="text-sm text-muted">Horários no {timeZoneLabel(hours.timeZone)}, informados pelo estabelecimento.</p>
    </div>
  );
}

export function ChargesSection({ charges }: { charges: ChargesView }) {
  return (
    <div className="grid gap-2" data-testid="cobrancas">
      {charges.lines.length === 0 ? (
        <p className="text-muted">O estabelecimento não declarou taxa de serviço, couvert nem diferença por forma de pagamento.</p>
      ) : (
        <ul className="grid gap-2">
          {charges.lines.map((l) => (
            <li key={`${l.code}-${l.text}`} className="flex items-start gap-1.5">
              <IconCharges size={16} className="mt-1 flex-none" />
              <span className="grid">
                <span className="font-semibold">{l.text}</span>
                {l.detail && <span className="text-sm text-muted">{l.detail}</span>}
              </span>
            </li>
          ))}
        </ul>
      )}
      {charges.declaredLabel && <p className="text-sm text-muted">{charges.declaredLabel}.</p>}
      <p className="text-sm text-muted">{charges.notice}</p>
    </div>
  );
}

export function FacetsSection({ sections }: { sections: FacetSectionView[] }) {
  return (
    <div className="grid gap-4 sm:grid-cols-2">
      {sections.map((s) => (
        <section key={s.code} aria-labelledby={`faceta-${s.code}`} className="grid content-start gap-1.5">
          <h3 id={`faceta-${s.code}`} className="font-bold">
            {s.label}
          </h3>
          <ul className="grid gap-1.5">
            {s.items.map((f) => {
              const Icon = FACET_ICONS[f.lucideIcon];
              return (
                <li key={f.code} className="flex items-start gap-1.5" data-testid={`faceta-${f.code}`}>
                  {Icon && <Icon size={16} className="mt-1 flex-none" />}
                  <span className="grid">
                    <span>
                      <span className="font-semibold">{f.label}</span>: {f.valueLabel}
                    </span>
                    <span className="text-xs text-muted">{f.originLabel}</span>
                  </span>
                </li>
              );
            })}
          </ul>
        </section>
      ))}
    </div>
  );
}

/**
 * Botões de ação: só links para fora (reserva, delivery, WhatsApp), em outra aba, sem passar referência
 * (rel noopener noreferrer nofollow). Cada um descreve para onde leva; pedido e pagamento são com o parceiro.
 */
export function ActionButtons({ actions }: { actions: ActionView[] }) {
  const uid = useId();
  if (actions.length === 0) return null;
  return (
    <div className="grid gap-2" data-testid="botoes-acao">
      <ul className="flex flex-wrap gap-2">
        {actions.map((a, i) => {
          const Icon = a.kind === 'RESERVA' ? IconReserve : a.kind === 'DELIVERY' ? IconDelivery : IconWhatsapp;
          return (
            <li key={`${a.provider}-${a.url}`}>
              <a
                href={a.url}
                target="_blank"
                rel="noopener noreferrer nofollow"
                className={`fe-btn ${i === 0 ? 'fe-btn-primary' : ''}`}
                aria-describedby={`${uid}-${i}`}
              >
                <Icon size={18} />
                {a.label}
                {a.preferred && <span className="text-sm font-normal"> · preferido</span>}
                <span className="sr-only"> (abre em outra aba)</span>
              </a>
              <span id={`${uid}-${i}`} className="sr-only">
                {a.exitNotice}
              </span>
            </li>
          );
        })}
      </ul>
      <p className="text-sm text-muted">
        Os botões abrem o site ou o app do parceiro: reserva, pedido e pagamento são feitos com ele, não com o Friendly Eats.
      </p>
    </div>
  );
}

/** Endereço público, mapa (pino ou área aproximada) e os links de mapa externo. */
export function PlaceLocation({ place, directions }: { place: EstablishmentView; directions: string[] | null }) {
  const loc = place.location;
  const point = loc.latitude !== null && loc.longitude !== null ? { lat: loc.latitude, lng: loc.longitude } : null;
  return (
    <div className="grid gap-2">
      <address className="grid gap-1 not-italic">
        {loc.street && (
          <span>
            {loc.street}
            {loc.streetNumber ? `, ${loc.streetNumber}` : ''}
            {loc.complement ? ` – ${loc.complement}` : ''}
          </span>
        )}
        {loc.neighborhood && <span>{loc.neighborhood}</span>}
        <span className="flex items-center gap-1.5">
          <IconPlace size={16} />
          {[loc.city, loc.uf].filter(Boolean).join(' – ')}
        </span>
        {loc.cep && <span className="text-sm text-muted">CEP {loc.cep.replace(/^(\d{5})(\d{3})$/, '$1-$2')}</span>}
      </address>
      {loc.approximate && (
        <p className="text-sm">
          <strong>Localização aproximada</strong>
          <span className="text-muted"> · endereço aproximado: bairro e cidade</span>
        </p>
      )}
      {point && (
        <PlaceMap
          lat={point.lat}
          lng={point.lng}
          approximate={loc.approximate}
          label={`Mapa ${loc.approximate ? 'com a área aproximada' : 'com o local'} de ${place.name}`}
        />
      )}
      <p className="flex flex-wrap gap-2">
        {directions && (
          <a href={directionsUrl(directions)} target="_blank" rel="noopener noreferrer" className="fe-btn fe-btn-sm">
            <IconDirections size={16} />
            Como chegar
            <span className="sr-only"> (abre o mapa em outra aba)</span>
          </a>
        )}
        {point && (
          <a href={osmUrl(point.lat, point.lng, loc.approximate ? 15 : 17)} target="_blank" rel="noopener noreferrer" className="fe-btn fe-btn-sm">
            <IconMap size={16} />
            Ver no OpenStreetMap
            <IconExternal size={14} />
            <span className="sr-only"> (abre em outra aba)</span>
          </a>
        )}
      </p>
    </div>
  );
}

function brDate(iso: string): string {
  const m = /^(\d{4})-(\d{2})-(\d{2})$/.exec(iso);
  return m ? `${m[3]}/${m[2]}/${m[1]}` : iso;
}
