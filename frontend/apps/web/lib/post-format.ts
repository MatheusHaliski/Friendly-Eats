// Formatação dos posts (RF14): tempo relativo no card ("há 3 min"), data absoluta no fuso de quem vê na página do
// post, vigência, alergênicos por extenso e os links da tela a partir dos caminhos da API.
import type { AllergenView, AuthorView, PostCardView, PostDetailView } from '@friendly-eats/api';
import { plural } from './format';

const absolute = new Intl.DateTimeFormat('pt-BR', { day: 'numeric', month: 'long', year: 'numeric', hour: '2-digit', minute: '2-digit' });
const shortDate = new Intl.DateTimeFormat('pt-BR', { day: 'numeric', month: 'short' });
const shortDateYear = new Intl.DateTimeFormat('pt-BR', { day: 'numeric', month: 'short', year: 'numeric' });
const dayTime = new Intl.DateTimeFormat('pt-BR', { weekday: 'short', day: 'numeric', month: 'short', hour: '2-digit', minute: '2-digit' });
const monthYear = new Intl.DateTimeFormat('pt-BR', { month: 'long', year: 'numeric' });

/** "agora", "há 3 min", "há 2 h", "ontem", "há 4 dias" e, depois de uma semana, "12 de out.". */
export function formatRelative(iso: string, now: Date = new Date()): string {
  const d = new Date(iso);
  if (Number.isNaN(d.getTime())) return '';
  const s = Math.round((now.getTime() - d.getTime()) / 1000);
  if (s < 60) return 'agora';
  const min = Math.floor(s / 60);
  if (min < 60) return `há ${min} min`;
  const h = Math.floor(min / 60);
  if (h < 24) return `há ${h} h`;
  const days = Math.floor(h / 24);
  if (days === 1) return 'ontem';
  if (days < 7) return `há ${days} dias`;
  return (d.getFullYear() === now.getFullYear() ? shortDate : shortDateYear).format(d);
}

/** "25 de outubro de 2026 às 11:20", no fuso de quem vê. */
export function formatDateTime(iso: string): string {
  const d = new Date(iso);
  return Number.isNaN(d.getTime()) ? '' : absolute.format(d);
}

/** "outubro de 2026". */
export function formatMonthYear(iso: string): string {
  const d = new Date(iso);
  return Number.isNaN(d.getTime()) ? '' : monthYear.format(d);
}

/** Vigência da promoção ou horário do evento: "sáb., 31 de out., 09:00 até sáb., 31 de out., 16:00". */
export function formatPeriod(startsAt: string | null, endsAt: string | null): string | null {
  if (!startsAt || !endsAt) return null;
  return `${dayTime.format(new Date(startsAt))} até ${dayTime.format(new Date(endsAt))}`;
}

/** "Contém: Ovos, Leite · Pode conter: Amendoim" (o nível sempre por extenso, nunca só a cor). */
export function allergenGroups(allergens: AllergenView[]): Array<{ level: string; label: string; items: string[] }> {
  const groups = new Map<string, { level: string; label: string; items: string[] }>();
  for (const a of allergens) {
    const g = groups.get(a.level) ?? { level: a.level, label: a.levelLabel, items: [] };
    g.items.push(a.label);
    groups.set(a.level, g);
  }
  return [...groups.values()].sort((x, y) => (x.level === 'CONTEM' ? -1 : y.level === 'CONTEM' ? 1 : 0));
}

export function countsText(counts: { likes: number; comments: number; shares: number }): string {
  return [plural(counts.likes, 'curtida', 'curtidas'), plural(counts.comments, 'comentário', 'comentários'), plural(counts.shares, 'compartilhamento', 'compartilhamentos')].join(', ');
}

// ------------------------------------------------------------ links da tela (a API manda os caminhos dela)

export const postHref = (p: Pick<PostCardView | PostDetailView, 'id'>) => `/posts/${encodeURIComponent(p.id)}`;

/** Perfil público do autor; null quando a conta foi excluída ou está fora do ar (sem link). */
export function profileHref(a: Pick<AuthorView, 'id' | 'profilePath'>): string | null {
  return a.profilePath && a.id != null ? `/u/${a.id}` : null;
}

export const placeHref = (id: number) => `/lugares/${id}`;

/** O texto de um alerta sanitário começa pela fonte: "Fonte: Anvisa (www.gov.br)". */
export function alertSource(author: AuthorView, host: string | null): string {
  return host ? `Fonte: ${author.name} (${host})` : `Fonte: ${author.name}`;
}
