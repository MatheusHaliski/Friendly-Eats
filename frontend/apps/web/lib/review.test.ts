import { describe, expect, it } from 'vitest';
import { dimensionsFor, keepDimensions } from './review';

describe('dimensões por modo (ReviewDimension.java)', () => {
  it('ambiente só no local e entrega só no delivery', () => {
    expect(dimensionsFor('DINE_IN').map((d) => d.code)).toEqual(['COMIDA', 'ATENDIMENTO', 'AMBIENTE', 'CUSTO_BENEFICIO']);
    expect(dimensionsFor('TAKEOUT').map((d) => d.code)).toEqual(['COMIDA', 'ATENDIMENTO', 'CUSTO_BENEFICIO']);
    expect(dimensionsFor('DELIVERY').map((d) => d.code)).toEqual(['COMIDA', 'CUSTO_BENEFICIO', 'ENTREGA']);
    expect(dimensionsFor(null)).toEqual([]);
  });

  it('trocar o modo tira as dimensões que não valem', () => {
    expect(keepDimensions({ COMIDA: 5, AMBIENTE: 4, ATENDIMENTO: 3 }, 'DELIVERY')).toEqual({ COMIDA: 5 });
  });
});
