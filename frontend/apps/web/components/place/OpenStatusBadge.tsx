import type { OpenStatusView } from '@friendly-eats/api';
import { openTone } from '@/lib/place-info';
import { IconClosedNow, IconOpen, IconPaused, IconUnknownHours } from './place-icons';

/** "Aberto agora · fecha às 23:00": o texto diz o estado; o ícone (porta aberta ou fechada) e a cor só reforçam. */
export function OpenStatusBadge({ status, size = 18 }: { status: OpenStatusView; size?: number }) {
  const tone = openTone(status.state);
  const Icon =
    status.state === 'TEMPORARILY_CLOSED' ? IconPaused : tone === 'open' ? IconOpen : tone === 'unknown' ? IconUnknownHours : IconClosedNow;
  return (
    <span className="inline-flex items-start gap-1.5" data-testid="aberto-agora" data-state={status.state}>
      <Icon size={size} className={`mt-0.5 flex-none ${tone === 'open' ? 'text-success' : 'text-fg'}`} />
      <span className={tone === 'open' ? 'font-semibold' : undefined}>{status.label}</span>
    </span>
  );
}
