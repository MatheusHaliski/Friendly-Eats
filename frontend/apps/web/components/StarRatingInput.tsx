'use client';

import { useId, useState } from 'react';
import { IconStar } from './icons';

export const STAR_LABELS = ['Péssimo', 'Ruim', 'Regular', 'Bom', 'Excelente'] as const;

export function starOptionLabel(value: number): string {
  return `${value} – ${STAR_LABELS[value - 1]}`;
}

interface Props {
  name: string;
  /** Texto do grupo (ex.: "Sua nota" ou "Comida"). */
  legend: string;
  value: number | null;
  onChange: (value: number) => void;
  size?: 'lg' | 'sm';
  id?: string;
  describedBy?: string;
  invalid?: boolean;
  required?: boolean;
}

/**
 * Entrada da nota: grupo de 5 rádios nativos rotulados ("1 – Péssimo" … "5 – Excelente"), setas do teclado, alvo
 * de pelo menos 24×24 px e o rótulo escolhido em texto ao lado (DESIGN_SYSTEM.md, seção 6). Nota inteira, sem meia.
 */
export function StarRatingInput({ name, legend, value, onChange, size = 'lg', id, describedBy, invalid, required }: Props) {
  const autoId = useId();
  const groupId = id ?? `estrelas-${autoId}`;
  const [hover, setHover] = useState<number | null>(null);
  const shown = hover ?? value ?? 0;
  const px = size === 'lg' ? 32 : 24;
  return (
    <div className="grid gap-1">
      <span id={`${groupId}-rotulo`} className={size === 'lg' ? 'font-semibold' : 'text-sm font-semibold'}>
        {legend}
        {required ? <span className="font-normal text-muted"> (obrigatório)</span> : null}
      </span>
      <div className="flex flex-wrap items-center gap-3">
        <div
          id={groupId}
          role="radiogroup"
          aria-labelledby={`${groupId}-rotulo`}
          aria-describedby={describedBy}
          aria-invalid={invalid || undefined}
          aria-required={required || undefined}
          tabIndex={-1}
          className="flex items-center"
          onMouseLeave={() => setHover(null)}
        >
          {[1, 2, 3, 4, 5].map((n) => {
            const inputId = `${groupId}-${n}`;
            return (
              <span key={n} className="relative inline-flex" onMouseEnter={() => setHover(n)}>
                <input
                  type="radio"
                  className="fe-visually-hidden-input"
                  id={inputId}
                  name={name}
                  value={n}
                  checked={value === n}
                  onChange={() => onChange(n)}
                />
                <label
                  htmlFor={inputId}
                  className="inline-flex cursor-pointer items-center justify-center rounded-sm p-1"
                  style={{ minWidth: Math.max(24, px + 8), minHeight: Math.max(24, px + 8) }}
                >
                  <IconStar
                    size={px}
                    className={n <= shown ? 'text-star' : 'text-star-empty'}
                    fill={n <= shown ? 'currentColor' : 'none'}
                  />
                  <span className="sr-only">{starOptionLabel(n)}</span>
                </label>
              </span>
            );
          })}
        </div>
        <span className={size === 'lg' ? 'font-semibold' : 'text-sm'} aria-hidden="true" data-testid={`${name}-escolhida`}>
          {value ? starOptionLabel(value) : 'Sem nota'}
        </span>
      </div>
    </div>
  );
}
