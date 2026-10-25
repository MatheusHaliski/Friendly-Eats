// Estilos alimentares sempre com rótulo textual ("Opções veganas", "100% vegetariano"), docs/DESIGN_SYSTEM.md seção 4.
import type { DietaryLevel } from '@friendly-eats/api';

const TEXTOS: Record<string, { oferece: string; exclusivo: string }> = {
  VEGANO: { oferece: 'Opções veganas', exclusivo: '100% vegano' },
  VEGETARIANO: { oferece: 'Opções vegetarianas', exclusivo: '100% vegetariano' },
  SEM_GLUTEN: { oferece: 'Opções sem glúten', exclusivo: '100% sem glúten' },
  SEM_LACTOSE: { oferece: 'Opções sem lactose', exclusivo: '100% sem lactose' },
  LOW_CARB: { oferece: 'Opções low carb', exclusivo: '100% low carb' },
  HALAL: { oferece: 'Opções halal', exclusivo: '100% halal' },
  KOSHER: { oferece: 'Opções kosher', exclusivo: '100% kosher' },
  ORGANICO: { oferece: 'Opções orgânicas', exclusivo: '100% orgânico' },
  PESCETARIANO: { oferece: 'Opções pescetarianas', exclusivo: '100% pescetariano' },
  SEM_ACUCAR: { oferece: 'Opções sem açúcar', exclusivo: '100% sem açúcar' },
  CRUDIVORO: { oferece: 'Opções crudívoras', exclusivo: '100% crudívoro' },
};

export function dietaryText(code: string, label: string, level: DietaryLevel): string {
  const t = TEXTOS[code];
  if (!t) return level === 'EXCLUSIVO' ? `100% ${label.toLowerCase()}` : `Opções: ${label.toLowerCase()}`;
  return level === 'EXCLUSIVO' ? t.exclusivo : t.oferece;
}

/** Sem glúten e sem lactose exigem o aviso de contaminação cruzada. */
export function needsCrossContactWarning(code: string): boolean {
  return code === 'SEM_GLUTEN' || code === 'SEM_LACTOSE';
}

export const CROSS_CONTACT_WARNING =
  'Informação declarada pelo estabelecimento; confirme sobre contaminação cruzada';
