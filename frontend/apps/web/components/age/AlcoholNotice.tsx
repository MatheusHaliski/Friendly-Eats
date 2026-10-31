'use client';

import Link from 'next/link';
import { IconAgeVerify } from '@/components/icons';
import { useAgeVerification, useSession } from '@/lib/queries';
import { loginHref } from '@/lib/safe-redirect';
import { alcoholNotice } from '@/lib/titular';

export const ALCOHOL_HIDDEN = 'Conteúdo de bebida alcoólica aparece só depois de verificar a idade';
export const AGE_SETTINGS = '/configuracoes/idade';

/**
 * Aviso onde o conteúdo de bebida some (feed, busca, página do lugar): versão sem esse conteúdo até a verificação de
 * idade (RF17; Decreto 12.880/2026, art. 19). Visitante vai para o login e volta à verificação.
 */
export function AlcoholHiddenNotice({ className = '' }: { className?: string }) {
  const session = useSession();
  const age = useAgeVerification(!!session.data);
  if (session.isPending) return null;
  const kind = alcoholNotice(session.data, age.data);
  if (kind === 'none') return null;
  const href = kind === 'login' ? loginHref(AGE_SETTINGS) : AGE_SETTINGS;
  return (
    <div className={`flex flex-wrap items-center gap-x-3 gap-y-2 rounded-md border-2 border-line-strong p-3 ${className}`} data-testid="aviso-bebida">
      <IconAgeVerify size={20} className="flex-none" />
      <p className="min-w-0 flex-1">
        {ALCOHOL_HIDDEN}.{kind === 'login' ? ' Entre na sua conta para verificar.' : ''}
      </p>
      <Link href={href} className="fe-btn fe-btn-sm">
        Verificar a idade
      </Link>
    </div>
  );
}

/** Página de post 18+ ou de lugar com foco em bebida para quem ainda não verificou a idade (403 da API). */
export function VerifyAgeRequired({ detail }: { detail: string }) {
  return (
    <div className="grid max-w-xl gap-4">
      <h1 className="text-3xl font-extrabold">Verifique a sua idade para ver</h1>
      <div className="flex items-start gap-2 rounded-md border-2 border-line-strong p-3">
        <IconAgeVerify size={20} className="mt-0.5 flex-none" />
        <div className="grid gap-1">
          <p>{detail}</p>
          <p className="text-sm text-muted">
            A verificação é feita por um fornecedor especializado. Guardamos só o resultado, nunca a imagem do documento.
          </p>
        </div>
      </div>
      <p>
        <Link href={AGE_SETTINGS} className="fe-btn fe-btn-primary">
          <IconAgeVerify size={18} />
          Verificar a idade
        </Link>
      </p>
    </div>
  );
}
