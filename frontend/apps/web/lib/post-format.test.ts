import { describe, expect, it } from 'vitest';
import { allergenGroups, alertSource, countsText, formatDateTime, formatPeriod, formatRelative, profileHref } from './post-format';
import { shareTarget } from './share';

describe('tempo do post', () => {
  const now = new Date('2026-10-25T15:00:00Z');
  it('relativo no card', () => {
    expect(formatRelative('2026-10-25T14:59:30Z', now)).toBe('agora');
    expect(formatRelative('2026-10-25T14:57:00Z', now)).toBe('há 3 min');
    expect(formatRelative('2026-10-25T12:30:00Z', now)).toBe('há 2 h');
    expect(formatRelative('2026-10-24T12:00:00Z', now)).toBe('ontem');
    expect(formatRelative('2026-10-21T12:00:00Z', now)).toBe('há 4 dias');
    expect(formatRelative('2026-10-12T12:00:00Z', now)).toMatch(/^12 de out/);
    expect(formatRelative('2025-10-12T12:00:00Z', now)).toMatch(/2025$/);
  });

  it('absoluto e vigência no fuso de quem vê', () => {
    expect(formatDateTime('2026-10-25T14:20:00Z')).toMatch(/^25 de outubro de 2026 às \d{2}:20$/);
    expect(formatPeriod('2026-10-31T12:00:00Z', '2026-10-31T19:00:00Z')).toMatch(/^sáb\., 31 de out\., \d{2}:00 até sáb\., 31 de out\., \d{2}:00$/);
    expect(formatPeriod(null, '2026-10-31T19:00:00Z')).toBeNull();
  });
});

describe('alergênicos, contadores e links', () => {
  it('agrupa por nível com o rótulo por extenso, "Contém" primeiro', () => {
    expect(
      allergenGroups([
        { code: 'AMENDOIM', label: 'Amendoim', level: 'PODE_CONTER', levelLabel: 'Pode conter' },
        { code: 'LEITE', label: 'Leite', level: 'CONTEM', levelLabel: 'Contém' },
        { code: 'OVOS', label: 'Ovos', level: 'CONTEM', levelLabel: 'Contém' },
      ]),
    ).toEqual([
      { level: 'CONTEM', label: 'Contém', items: ['Leite', 'Ovos'] },
      { level: 'PODE_CONTER', label: 'Pode conter', items: ['Amendoim'] },
    ]);
  });

  it('contadores em português, com singular', () => {
    expect(countsText({ likes: 1, comments: 0, shares: 2 })).toBe('1 curtida, 0 comentários, 2 compartilhamentos');
  });

  it('autor sem profilePath (conta excluída) não vira link', () => {
    expect(profileHref({ id: 310, profilePath: '/api/v1/users/310/profile' })).toBe('/u/310');
    expect(profileHref({ id: null, profilePath: null })).toBeNull();
    expect(alertSource({ id: 700, name: 'Anvisa' } as never, 'www.gov.br')).toBe('Fonte: Anvisa (www.gov.br)');
  });

  it('canais de compartilhamento abrem o site certo com o link público', () => {
    const url = 'https://friendlyeats.app/p/abc';
    expect(shareTarget('WHATSAPP', url, 'PF')).toBe('https://wa.me/?text=PF%20https%3A%2F%2Ffriendlyeats.app%2Fp%2Fabc');
    expect(shareTarget('TELEGRAM', url, 'PF')).toBe('https://t.me/share/url?url=https%3A%2F%2Ffriendlyeats.app%2Fp%2Fabc&text=PF');
    expect(shareTarget('X', url, 'PF')).toContain('x.com/intent/post?url=');
    expect(shareTarget('EMAIL', url, 'PF')).toBe('mailto:?subject=PF&body=https%3A%2F%2Ffriendlyeats.app%2Fp%2Fabc');
    expect(shareTarget('COPY_LINK', url, 'PF')).toBeNull();
  });
});
