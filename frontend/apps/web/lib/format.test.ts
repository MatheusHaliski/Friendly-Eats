import { describe, expect, it } from 'vitest';
import { ageOn, centsToInput, formatCents, formatDecimal, formatDistance, parseReaisToCents, plural } from './format';

describe('formatação pt-BR', () => {
  it('uma casa decimal com vírgula', () => {
    expect(formatDecimal(4.25)).toBe('4,3');
    expect(formatDecimal(4)).toBe('4,0');
  });

  it('reais e distância', () => {
    expect(formatCents(4590)).toBe('R$ 45,90');
    expect(formatDistance(1.2)).toBe('1,2 km');
    expect(formatDistance(0.43)).toBe('450 m');
  });

  it('plural', () => {
    expect(plural(1, 'avaliação', 'avaliações')).toBe('1 avaliação');
    expect(plural(1280, 'avaliação', 'avaliações')).toBe('1.280 avaliações');
  });
});

describe('parseReaisToCents', () => {
  it.each([
    ['45', 4500],
    ['45,9', 4590],
    ['45,90', 4590],
    ['R$ 1.234,50', 123450],
    ['45.90', 4590],
    ['0', 0],
  ])('%s → %d centavos', (input, cents) => {
    expect(parseReaisToCents(input)).toBe(cents);
  });

  it('vazio é null e texto inválido é NaN', () => {
    expect(parseReaisToCents('  ')).toBeNull();
    expect(parseReaisToCents('quarenta')).toBeNaN();
    expect(parseReaisToCents('4,567')).toBeNaN();
  });

  it('volta para o campo', () => {
    expect(centsToInput(4590)).toBe('45,90');
    expect(centsToInput(null)).toBe('');
  });
});

describe('ageOn', () => {
  it('conta o aniversário', () => {
    const hoje = new Date(2026, 9, 25);
    expect(ageOn('2010-10-25', hoje)).toBe(16);
    expect(ageOn('2010-10-26', hoje)).toBe(15);
    expect(ageOn('', hoje)).toBeNull();
  });
});
