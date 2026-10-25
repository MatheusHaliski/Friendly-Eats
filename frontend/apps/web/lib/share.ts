// Folha de compartilhamento do post (RF12): os canais vêm do vocabulário (ShareChannel). O link é o público do
// post ({public-base-url}/p/{id}), sem dizer quem compartilhou.
import type { ShareChannelCode } from '@friendly-eats/api';

/** Endereço que abre o app ou o site do canal; null para os que não abrem página (copiar, folha do sistema). */
export function shareTarget(channel: ShareChannelCode, url: string, title: string): string | null {
  const u = encodeURIComponent(url);
  const t = encodeURIComponent(title);
  switch (channel) {
    case 'WHATSAPP':
      return `https://wa.me/?text=${encodeURIComponent(`${title} ${url}`)}`;
    case 'TELEGRAM':
      return `https://t.me/share/url?url=${u}&text=${t}`;
    case 'X':
      return `https://x.com/intent/post?url=${u}&text=${t}`;
    case 'EMAIL':
      return `mailto:?subject=${t}&body=${u}`;
    default:
      return null;
  }
}

/** A folha do sistema (Web Share API) só aparece onde existe. */
export function canShareNatively(): boolean {
  return typeof navigator !== 'undefined' && typeof navigator.share === 'function';
}

/** Copia o texto; devolve false quando o navegador não deixa (a tela mostra o link para copiar à mão). */
export async function copyText(text: string): Promise<boolean> {
  try {
    await navigator.clipboard.writeText(text);
    return true;
  } catch {
    return false;
  }
}
