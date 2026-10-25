// Preferência de tema: a única coisa guardada no localStorage (nunca token nem dado da conta).
export type ThemeChoice = 'light' | 'dark';
export const THEME_STORAGE_KEY = 'fe:tema';

export function readStoredTheme(): ThemeChoice | null {
  try {
    const v = window.localStorage.getItem(THEME_STORAGE_KEY);
    return v === 'light' || v === 'dark' ? v : null;
  } catch {
    return null;
  }
}

export function storeTheme(theme: ThemeChoice): void {
  try {
    window.localStorage.setItem(THEME_STORAGE_KEY, theme);
  } catch {
    // modo privado ou armazenamento bloqueado: o tema vale só nesta visita
  }
}

export function effectiveTheme(): ThemeChoice {
  const attr = document.documentElement.dataset.theme;
  if (attr === 'light' || attr === 'dark') return attr;
  return window.matchMedia('(prefers-color-scheme: dark)').matches ? 'dark' : 'light';
}

/** Roda antes da pintura (no <head>) para não piscar o tema errado. */
export const THEME_BOOT_SCRIPT = `(function(){try{var t=localStorage.getItem(${JSON.stringify(THEME_STORAGE_KEY)});if(t==='light'||t==='dark'){document.documentElement.dataset.theme=t;}}catch(e){}})();`;
