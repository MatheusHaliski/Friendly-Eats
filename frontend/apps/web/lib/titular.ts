// RF17 · RF18: regras puras das telas de idade, responsável e "Seus dados".
import type { AgeVerificationView, DataExportView, SessionUserView } from '@friendly-eats/api';

/** Que aviso de conteúdo de bebida oculto mostrar: nenhum, "entre e verifique" ou "verifique a idade". */
export type AlcoholNotice = 'none' | 'login' | 'verify';

/**
 * Visitante: entrar e verificar. Conta de adulto (pela data declarada) sem a idade verificada: verificar. Quem já
 * vê o conteúdo e quem declarou menos de 18 (não há o que verificar) não recebem aviso.
 */
export function alcoholNotice(session: SessionUserView | null | undefined, age: AgeVerificationView | undefined): AlcoholNotice {
  if (!session) return 'login';
  if (!age) return 'none';
  if (age.alcoholContentVisible || !age.declaredAdult) return 'none';
  return 'verify';
}

export interface TitularRequest {
  name: string;
  email: string;
  right: string;
  message: string;
}

/** Link mailto: para o pedido do titular ao encarregado (funciona sem conta). */
export function privacyRequestMailto(to: string, r: TitularRequest): string {
  const subject = `Pedido do titular (LGPD): ${r.right}`;
  const body = [
    `Nome: ${r.name}`,
    `E-mail para resposta: ${r.email}`,
    `Direito: ${r.right}`,
    '',
    r.message,
  ].join('\n');
  return `mailto:${encodeURIComponent(to)}?subject=${encodeURIComponent(subject)}&body=${encodeURIComponent(body)}`;
}

/** 1536 → "1,5 KB"; 3_400_000 → "3,2 MB". */
export function formatBytes(n: number | null | undefined): string {
  if (n == null) return '';
  const fmt = new Intl.NumberFormat('pt-BR', { maximumFractionDigits: 1 });
  if (n < 1024) return `${n} bytes`;
  if (n < 1024 * 1024) return `${fmt.format(n / 1024)} KB`;
  return `${fmt.format(n / (1024 * 1024))} MB`;
}

/** Pode pedir outra exportação? Uma por dia, sem contar as que falharam. */
export function nextExportAllowedAt(exports: DataExportView[] | undefined, now: Date = new Date()): Date | null {
  const last = exports?.find((e) => e.status !== 'FAILED');
  if (!last) return null;
  const next = new Date(new Date(last.requestedAt).getTime() + 24 * 60 * 60 * 1000);
  return next > now ? next : null;
}
