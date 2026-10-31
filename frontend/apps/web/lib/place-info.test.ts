import { describe, expect, it } from 'vitest';
import { openTone, osmUrl, rangesText, timeZoneLabel } from './place-info';

describe('place-info', () => {
  it('diz o fuso do lugar em palavras', () => {
    expect(timeZoneLabel('America/Sao_Paulo')).toBe('horário de Brasília');
    expect(timeZoneLabel('America/Recife')).toBe('horário de Brasília');
    expect(timeZoneLabel('America/Manaus')).toContain('1 hora a menos');
    expect(timeZoneLabel('America/Noronha')).toContain('1 hora a mais');
  });

  it('escreve as faixas do dia', () => {
    expect(rangesText([{ opens: '11:30', closes: '15:00', overnight: false }])).toBe('11:30–15:00');
    expect(
      rangesText([
        { opens: '11:30', closes: '15:00', overnight: false },
        { opens: '18:00', closes: '02:00', overnight: true },
      ]),
    ).toBe('11:30–15:00 e 18:00–02:00 (passa da meia-noite)');
    expect(rangesText([{ opens: '18:00', closes: '00:00', overnight: false }])).toBe('18:00–24:00');
    expect(rangesText([{ opens: '00:00', closes: '00:00', overnight: false }])).toBe('24 horas');
  });

  it('tom e mapa', () => {
    expect(openTone('OPEN')).toBe('open');
    expect(openTone('MAYBE_CLOSED')).toBe('unknown');
    expect(openTone('TEMPORARILY_CLOSED')).toBe('closed');
    expect(osmUrl(-19.9, -43.9)).toBe('https://www.openstreetmap.org/?mlat=-19.9&mlon=-43.9#map=17/-19.9/-43.9');
  });
});
