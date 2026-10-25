'use client';

import { useSyncExternalStore } from 'react';
import { effectiveTheme, storeTheme, type ThemeChoice } from '@/lib/theme';
import { IconThemeDark, IconThemeLight } from './icons';

const listeners = new Set<() => void>();

function subscribe(cb: () => void) {
  listeners.add(cb);
  const mq = window.matchMedia('(prefers-color-scheme: dark)');
  mq.addEventListener('change', cb);
  return () => {
    listeners.delete(cb);
    mq.removeEventListener('change', cb);
  };
}

function setTheme(theme: ThemeChoice) {
  document.documentElement.dataset.theme = theme;
  storeTheme(theme);
  listeners.forEach((l) => l());
}

/** Alterna claro/escuro; a escolha fica só no localStorage (a única preferência guardada no navegador). */
export function ThemeToggle() {
  const theme = useSyncExternalStore<ThemeChoice | null>(subscribe, effectiveTheme, () => null);
  const dark = theme === 'dark';
  return (
    <button
      type="button"
      className="fe-btn fe-btn-sm"
      aria-pressed={theme === null ? undefined : dark}
      onClick={() => setTheme(dark ? 'light' : 'dark')}
      data-testid="alternar-tema"
    >
      {dark ? <IconThemeDark size={18} /> : <IconThemeLight size={18} />}
      <span>Tema escuro</span>
    </button>
  );
}
