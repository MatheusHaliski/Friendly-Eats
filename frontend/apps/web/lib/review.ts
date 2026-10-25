// Regras da avaliação no formulário (RF8). Fonte: ReviewDimension.java e VisitMode.java, também servidas em
// GET /api/v1/taxonomy; as constantes abaixo são só o reserva enquanto a taxonomia carrega.
import type { DimensionItem, ReviewDimensionCode, VisitModeCode } from '@friendly-eats/api';

export const VISIT_MODES: ReadonlyArray<{ code: VisitModeCode; label: string }> = [
  { code: 'DINE_IN', label: 'No local' },
  { code: 'TAKEOUT', label: 'Retirada' },
  { code: 'DELIVERY', label: 'Entrega' },
];

export const DIMENSIONS: readonly DimensionItem[] = [
  { code: 'COMIDA', label: 'Comida', visitModes: ['DINE_IN', 'TAKEOUT', 'DELIVERY'] },
  { code: 'ATENDIMENTO', label: 'Atendimento', visitModes: ['DINE_IN', 'TAKEOUT'] },
  { code: 'AMBIENTE', label: 'Ambiente', visitModes: ['DINE_IN'] },
  { code: 'CUSTO_BENEFICIO', label: 'Custo-benefício', visitModes: ['DINE_IN', 'TAKEOUT', 'DELIVERY'] },
  { code: 'ENTREGA', label: 'Entrega', visitModes: ['DELIVERY'] },
];

/** Dimensões que aparecem para o modo escolhido (ambiente só no local, entrega só no delivery). */
export function dimensionsFor(mode: VisitModeCode | null, all: readonly DimensionItem[] = DIMENSIONS): DimensionItem[] {
  if (!mode) return [];
  return all.filter((d) => d.visitModes.includes(mode));
}

/** Tira as dimensões que não valem no modo novo (a API recusaria com 422). */
export function keepDimensions(
  dims: Partial<Record<ReviewDimensionCode, number>>,
  mode: VisitModeCode | null,
  all: readonly DimensionItem[] = DIMENSIONS,
): Partial<Record<ReviewDimensionCode, number>> {
  const allowed = new Set(dimensionsFor(mode, all).map((d) => d.code));
  return Object.fromEntries(Object.entries(dims).filter(([k]) => allowed.has(k as ReviewDimensionCode)));
}

export const MAX_BODY = 2000;
export const MAX_SPENT_CENTS = 500_000;
