import type { ChipView } from '@friendly-eats/api';
import type { CSSProperties } from 'react';
import { CHIP_ICONS } from './icons';

/** Chip-pílula de culinária: pictograma + rótulo + cor do tema, nunca só a cor (DESIGN_SYSTEM.md, seção 4). */
export function CuisineChip({ chip }: { chip: ChipView }) {
  const Icon = CHIP_ICONS[chip.lucideIcon];
  const style = { '--chip-dark': chip.darkHex, '--chip-light': chip.lightHex } as CSSProperties;
  return (
    <span className="fe-chip" style={style} data-chip-code={chip.code}>
      {Icon && <Icon size={16} strokeWidth={2.25} />}
      <span className="sr-only">Culinária: </span>
      {chip.label}
    </span>
  );
}

export function chipStyle(chip: Pick<ChipView, 'darkHex' | 'lightHex'>): CSSProperties {
  return { '--chip-dark': chip.darkHex, '--chip-light': chip.lightHex } as CSSProperties;
}
