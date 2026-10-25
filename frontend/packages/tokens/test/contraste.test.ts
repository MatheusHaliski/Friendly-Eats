// Confere os pares de contraste do docs/DESIGN_SYSTEM.md em cada tema, como o scripts/wcag_contrast.py.
import { describe, expect, it } from 'vitest';
import { tokens, type Tema } from '../dist/tokens.ts';

function canal(c: number): number {
  const s = c / 255;
  return s <= 0.03928 ? s / 12.92 : ((s + 0.055) / 1.055) ** 2.4;
}

function luminancia(hex: string): number {
  const m = /^#([0-9a-f]{6})$/i.exec(hex);
  if (!m?.[1]) throw new Error(`cor inválida: ${hex}`);
  const n = parseInt(m[1], 16);
  return 0.2126 * canal((n >> 16) & 255) + 0.7152 * canal((n >> 8) & 255) + 0.0722 * canal(n & 255);
}

export function contraste(a: string, b: string): number {
  const [x, y] = [luminancia(a), luminancia(b)].sort((p, q) => q - p) as [number, number];
  return (x + 0.05) / (y + 0.05);
}

const TEXTO = [
  'color-text',
  'color-text-muted',
  'color-brand',
  'color-feedback-error',
  'color-feedback-success',
  'color-price-1',
  'color-price-2',
  'color-price-3',
  'color-price-4',
  'color-account-comensal-ink',
  'color-account-criador-ink',
  'color-account-chef-ink',
  'color-account-estabelecimento-ink',
  'color-account-varejo-ink',
  'color-account-comunidade-ink',
  'color-account-oficial-ink',
] as const;

const NAO_TEXTUAL = [
  'color-border-strong',
  'color-focus',
  'color-star-full',
  'color-star-empty',
  'color-price-empty',
  'color-account-oficial-ring',
] as const;

describe.each<Tema>(['light', 'dark'])('tema %s', (tema) => {
  const t = tokens[tema];

  it.each(TEXTO)('%s tem 4,5:1 contra o fundo (WCAG 1.4.3)', (nome) => {
    expect(contraste(t[nome], t['color-bg'])).toBeGreaterThanOrEqual(4.5);
  });

  it.each(NAO_TEXTUAL)('%s tem 3:1 contra o fundo (WCAG 1.4.11)', (nome) => {
    expect(contraste(t[nome], t['color-bg'])).toBeGreaterThanOrEqual(3);
  });

  it('o texto do botão principal tem 4,5:1', () => {
    expect(contraste(t['color-on-primary'], t['color-primary'])).toBeGreaterThanOrEqual(4.5);
  });
});

describe('temas', () => {
  it('claro e escuro têm os mesmos tokens', () => {
    expect(Object.keys(tokens.dark).sort()).toEqual(Object.keys(tokens.light).sort());
  });

  it('o fundo é o do DESIGN_SYSTEM.md', () => {
    expect(tokens.dark['color-bg']).toBe('#17120D');
    expect(tokens.light['color-bg']).toBe('#FFFFFF');
  });

  it('o QR code do 2FA é escuro sobre claro nos dois temas (21:1)', () => {
    expect(contraste(tokens.base['color-qr-dark'], tokens.base['color-qr-light'])).toBeGreaterThanOrEqual(21);
  });

  it('a conta Oficial tem anel no tema escuro (o preto sozinho dá 1,06:1)', () => {
    expect(contraste(tokens.dark['color-account-oficial-fill'], tokens.dark['color-bg'])).toBeLessThan(1.1);
    expect(contraste(tokens.dark['color-account-oficial-ring'], tokens.dark['color-bg'])).toBeGreaterThan(12);
  });
});
