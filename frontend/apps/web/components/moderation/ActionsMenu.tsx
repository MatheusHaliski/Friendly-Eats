'use client';

import { useEffect, useId, useRef, useState, type ReactNode } from 'react';
import { IconMore } from '@/components/icons';

export interface MenuAction {
  key: string;
  label: string;
  icon?: ReactNode;
  onSelect: () => void;
}

/**
 * Botão "…" com ações secundárias (Denunciar, Bloquear, Silenciar). Padrão de divulgação (WAI-ARIA APG,
 * "Disclosure"): um botão com aria-expanded abre uma lista de botões comuns, alcançáveis por Tab; Esc fecha e devolve
 * o foco. O rótulo diz de quê são as ações ("Mais ações do comentário de Ana").
 */
export function ActionsMenu({ label, actions }: { label: string; actions: MenuAction[] }) {
  const uid = useId();
  const [open, setOpen] = useState(false);
  const buttonRef = useRef<HTMLButtonElement>(null);
  const boxRef = useRef<HTMLDivElement>(null);

  useEffect(() => {
    if (!open) return;
    function onKey(e: KeyboardEvent) {
      if (e.key === 'Escape') {
        setOpen(false);
        buttonRef.current?.focus();
      }
    }
    function onClick(e: MouseEvent) {
      if (!boxRef.current?.contains(e.target as Node)) setOpen(false);
    }
    document.addEventListener('keydown', onKey);
    document.addEventListener('mousedown', onClick);
    return () => {
      document.removeEventListener('keydown', onKey);
      document.removeEventListener('mousedown', onClick);
    };
  }, [open]);

  if (!actions.length) return null;
  return (
    <div ref={boxRef} className="relative inline-flex">
      <button
        ref={buttonRef}
        type="button"
        className="fe-btn fe-btn-sm"
        aria-label={label}
        aria-expanded={open}
        aria-controls={`${uid}-acoes`}
        onClick={() => setOpen((o) => !o)}
      >
        <IconMore size={18} />
      </button>
      <ul
        id={`${uid}-acoes`}
        hidden={!open}
        className="fe-card absolute right-0 top-full z-30 mt-1 grid min-w-48 gap-1 p-1 shadow-lg"
      >
        {actions.map((a) => (
          <li key={a.key}>
            <button
              type="button"
              className="fe-btn fe-btn-sm w-full justify-start border-transparent"
              onClick={() => {
                setOpen(false);
                // o foco volta ao "…" antes de abrir o diálogo, que o devolve para lá ao fechar
                buttonRef.current?.focus();
                a.onSelect();
              }}
            >
              {a.icon}
              {a.label}
            </button>
          </li>
        ))}
      </ul>
    </div>
  );
}
