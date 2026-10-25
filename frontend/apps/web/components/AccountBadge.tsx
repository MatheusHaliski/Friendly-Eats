import type { CategoryVerification } from '@friendly-eats/api';
import { accountType } from '@/lib/account-types';
import { IconVerified } from './icons';

/** Bolinha do tipo de conta: cor + glifo + rótulo (a conta Oficial sempre com o anel). */
export function AccountDot({ code }: { code: string }) {
  const info = accountType(code);
  return (
    <span className="inline-flex items-center gap-1" data-type={info.code} aria-hidden="true">
      <span className="fe-dot" data-type={info.code} />
      <span className="fe-glyph">{info.glyph}</span>
    </span>
  );
}

export function AccountBadge({ code, label }: { code: string; label?: string }) {
  const info = accountType(code);
  return (
    <span className="inline-flex items-center gap-1.5">
      <AccountDot code={info.code} />
      <span>{label ?? info.label}</span>
    </span>
  );
}

/** Selo badge-check: só identidade conferida, nunca qualidade (DESIGN_SYSTEM.md, seção 3). */
export function VerifiedSeal() {
  return (
    <span className="inline-flex items-center" title="Conta verificada">
      <IconVerified size={18} className="text-fg" />
      <span className="sr-only">Conta verificada</span>
    </span>
  );
}

export function verificationText(v: CategoryVerification): string | null {
  switch (v) {
    case 'PENDING':
      return 'Verificação em andamento';
    case 'VERIFIED':
      return 'Conta verificada';
    case 'REJECTED':
      return 'Verificação recusada: revise os dados do cadastro';
    default:
      return null;
  }
}
