// Formatação pt-BR: vírgula decimal, reais e datas.

const decimal1 = new Intl.NumberFormat('pt-BR', { minimumFractionDigits: 1, maximumFractionDigits: 1 });
const brl = new Intl.NumberFormat('pt-BR', { style: 'currency', currency: 'BRL' });
const dateFmt = new Intl.DateTimeFormat('pt-BR', { day: 'numeric', month: 'long', year: 'numeric', timeZone: 'America/Sao_Paulo' });

/** 4.25 → "4,3" (uma casa e vírgula, como o averageLabel da API). */
export function formatDecimal(n: number): string {
  return decimal1.format(n);
}

/** 4590 → "R$ 45,90". */
export function formatCents(cents: number): string {
  return brl.format(cents / 100).replace(/ /g, ' ');
}

/** "1.2" km → "1,2 km"; abaixo de 1 km, em metros arredondados de 50 em 50. */
export function formatDistance(km: number): string {
  if (km < 1) return `${Math.max(50, Math.round((km * 1000) / 50) * 50)} m`;
  return `${decimal1.format(km)} km`;
}

export function formatDate(iso: string): string {
  const d = new Date(iso);
  return Number.isNaN(d.getTime()) ? '' : dateFmt.format(d);
}

/**
 * Valor digitado em reais → centavos. Aceita "45", "45,9", "45,90", "R$ 1.234,50" e "45.90".
 * Devolve null para vazio e NaN para texto que não é valor.
 */
export function parseReaisToCents(input: string): number | null {
  const s = input.replace(/R\$|\s/g, '');
  if (s === '') return null;
  let normalized: string;
  if (/^\d{1,3}(\.\d{3})+(,\d{1,2})?$/.test(s)) {
    normalized = s.replace(/\./g, '').replace(',', '.');
  } else if (/^\d+(,\d{1,2})?$/.test(s)) {
    normalized = s.replace(',', '.');
  } else if (/^\d+(\.\d{1,2})?$/.test(s)) {
    normalized = s;
  } else {
    return Number.NaN;
  }
  return Math.round(Number(normalized) * 100);
}

/** Centavos → texto editável no campo ("45,90"). */
export function centsToInput(cents: number | null | undefined): string {
  if (cents === null || cents === undefined) return '';
  return (cents / 100).toFixed(2).replace('.', ',');
}

export function plural(n: number, um: string, varios: string): string {
  return `${n.toLocaleString('pt-BR')} ${n === 1 ? um : varios}`;
}

/** Idade completa em anos numa data de referência (AAAA-MM-DD). */
export function ageOn(dateOfBirth: string, today: Date = new Date()): number | null {
  const m = /^(\d{4})-(\d{2})-(\d{2})$/.exec(dateOfBirth);
  if (!m) return null;
  const [y, mo, d] = [Number(m[1]), Number(m[2]), Number(m[3])];
  let age = today.getFullYear() - y;
  if (today.getMonth() + 1 < mo || (today.getMonth() + 1 === mo && today.getDate() < d)) age -= 1;
  return age;
}
