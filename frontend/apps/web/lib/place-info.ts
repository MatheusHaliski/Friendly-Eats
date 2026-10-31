// Textos da página do lugar (RF11) que não vêm prontos da API: fuso, faixas do dia e links de mapa.
import type { OpenState, RangeView } from '@friendly-eats/api';

const BRASILIA = new Set([
  'America/Sao_Paulo',
  'America/Fortaleza',
  'America/Recife',
  'America/Maceio',
  'America/Bahia',
  'America/Belem',
  'America/Araguaina',
]);
const AMAZONAS = new Set(['America/Manaus', 'America/Cuiaba', 'America/Campo_Grande', 'America/Porto_Velho', 'America/Boa_Vista']);

/** Fuso do lugar em palavras: os horários da página estão nele, não no de quem olha. */
export function timeZoneLabel(zone: string): string {
  if (BRASILIA.has(zone)) return 'horário de Brasília';
  if (AMAZONAS.has(zone)) return 'horário do Amazonas (1 hora a menos que Brasília)';
  if (zone === 'America/Rio_Branco') return 'horário do Acre (2 horas a menos que Brasília)';
  if (zone === 'America/Noronha') return 'horário de Fernando de Noronha (1 hora a mais que Brasília)';
  return zone;
}

/** "11:30–15:00 e 18:00–02:00 (passa da meia-noite)"; "00:00–00:00" vira "24 horas". */
export function rangesText(ranges: RangeView[]): string {
  if (ranges.length === 1 && ranges[0]!.opens === '00:00' && ranges[0]!.closes === '00:00' && !ranges[0]!.overnight) return '24 horas';
  const parts = ranges.map((r) => `${r.opens}–${r.closes === '00:00' ? '24:00' : r.closes}${r.overnight ? ' (passa da meia-noite)' : ''}`);
  if (parts.length <= 1) return parts.join('');
  return `${parts.slice(0, -1).join(', ')} e ${parts.at(-1)}`;
}

/** Abre ou não a página com o tom de "aberto": só o texto diz o estado, o tom é reforço. */
export function openTone(state: OpenState): 'open' | 'closed' | 'unknown' {
  if (state === 'OPEN') return 'open';
  if (state === 'UNKNOWN' || state === 'MAYBE_CLOSED') return 'unknown';
  return 'closed';
}

/** Mapa do OpenStreetMap no ponto público (o aproximado já vem arredondado da API). */
export function osmUrl(lat: number, lng: number, zoom = 17): string {
  return `https://www.openstreetmap.org/?mlat=${lat}&mlon=${lng}#map=${zoom}/${lat}/${lng}`;
}
