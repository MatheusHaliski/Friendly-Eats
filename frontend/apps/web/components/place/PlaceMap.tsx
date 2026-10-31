'use client';

import 'leaflet/dist/leaflet.css';
import type { Circle, LeafletMouseEvent, Map as LeafletMap, Marker } from 'leaflet';
import { useEffect, useRef } from 'react';

/** Tiles do OpenStreetMap (sem chave paga), sempre com a atribuição que a licença ODbL pede. */
const TILES = 'https://tile.openstreetmap.org/{z}/{x}/{y}.png';
const ATTRIBUTION = '&copy; <a href="https://www.openstreetmap.org/copyright">colaboradores do OpenStreetMap</a>';
/** Raio do círculo do endereço aproximado: metade da diagonal da célula de 0,005° (≈ 550 m) da API. */
const APPROX_RADIUS_M = 400;

/**
 * Pino em SVG (sem as imagens padrão do Leaflet, que o bundler não serve): cor da marca com contorno preto e miolo
 * branco dos tokens que não mudam com o tema, porque os tiles do mapa são sempre claros.
 */
const PIN_HTML =
  '<svg width="32" height="42" viewBox="0 0 32 42" aria-hidden="true" focusable="false"><path d="M16 1C7.7 1 1 7.6 1 15.8 1 27 16 41 16 41s15-14 15-25.2C31 7.6 24.3 1 16 1z" style="fill:var(--fe-color-brand);stroke:var(--fe-color-qr-dark);stroke-width:2"/><circle cx="16" cy="15.5" r="5.5" style="fill:var(--fe-color-qr-light);stroke:var(--fe-color-qr-dark);stroke-width:2"/></svg>';

/** Valor de um token (o Leaflet pinta o círculo por atributo SVG, que não aceita var()). */
function token(name: string): string {
  return getComputedStyle(document.documentElement).getPropertyValue(name).trim();
}

interface Props {
  lat: number;
  lng: number;
  /** Nome acessível do mapa ("Mapa: Sabor Mineiro, Savassi"). */
  label: string;
  /** Endereço aproximado: círculo em vez de pino. */
  approximate?: boolean;
  /** Pino arrastável (formulário do dono); clicar no mapa também move o pino. */
  onMove?: (lat: number, lng: number) => void;
  /** Recebe a função que lê o centro do mapa (botão "pôr o pino no centro", alternativa ao arrasto). */
  onReady?: (center: () => { lat: number; lng: number }) => void;
  className?: string;
}

/**
 * Mapa do lugar com Leaflet. Carregado só no navegador; o pino arrastável tem alternativas sem arrasto (clique no
 * mapa, os campos de latitude e longitude e o botão do centro), como pede a WCAG 2.2 (2.5.7).
 */
export function PlaceMap({ lat, lng, label, approximate = false, onMove, onReady, className }: Props) {
  const box = useRef<HTMLDivElement>(null);
  const map = useRef<LeafletMap | null>(null);
  const marker = useRef<Marker | Circle | null>(null);
  const moveRef = useRef(onMove);
  useEffect(() => {
    moveRef.current = onMove;
  }, [onMove]);
  const editable = !!onMove;

  useEffect(() => {
    let cancelled = false;
    void import('leaflet').then((L) => {
      if (cancelled || !box.current || map.current) return;
      const m = L.map(box.current, { zoomControl: false, scrollWheelZoom: false, keyboard: true }).setView([lat, lng], approximate ? 15 : 17);
      L.control.zoom({ zoomInTitle: 'Aproximar', zoomOutTitle: 'Afastar' }).addTo(m);
      m.attributionControl.setPrefix('<a href="https://leafletjs.com">Leaflet</a>');
      L.tileLayer(TILES, { attribution: ATTRIBUTION, maxZoom: 19, referrerPolicy: 'strict-origin-when-cross-origin' }).addTo(m);
      if (approximate) {
        marker.current = L.circle([lat, lng], { radius: APPROX_RADIUS_M, color: token('--fe-color-brand'), weight: 2, fillOpacity: 0.15 }).addTo(m);
      } else {
        const icon = L.divIcon({ html: PIN_HTML, className: 'fe-map-pin', iconSize: [32, 42], iconAnchor: [16, 41] });
        const mk = L.marker([lat, lng], { icon, draggable: editable, keyboard: false, alt: '' }).addTo(m);
        mk.on('dragend', () => {
          const p = mk.getLatLng();
          moveRef.current?.(round(p.lat), round(p.lng));
        });
        marker.current = mk;
      }
      if (editable) {
        m.on('click', (ev: LeafletMouseEvent) => moveRef.current?.(round(ev.latlng.lat), round(ev.latlng.lng)));
      }
      onReady?.(() => {
        const c = m.getCenter();
        return { lat: round(c.lat), lng: round(c.lng) };
      });
      map.current = m;
    });
    return () => {
      cancelled = true;
      map.current?.remove();
      map.current = null;
      marker.current = null;
    };
    // o mapa é criado uma vez; mudanças do ponto vêm pelo efeito abaixo
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [approximate, editable]);

  useEffect(() => {
    const mk = marker.current;
    if (!mk || !map.current) return;
    const current = mk.getLatLng();
    if (current.lat !== lat || current.lng !== lng) {
      mk.setLatLng([lat, lng]);
      map.current.panTo([lat, lng]);
    }
  }, [lat, lng]);

  return (
    <div
      ref={box}
      role="group"
      aria-label={label}
      data-testid="mapa-lugar"
      className={className ?? 'h-56 w-full overflow-hidden rounded-md border border-line'}
    />
  );
}

function round(v: number): number {
  return Math.round(v * 1e6) / 1e6;
}
