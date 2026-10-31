'use client';

import { isApiError, type SessionUserView } from '@friendly-eats/api';
import { useQuery } from '@tanstack/react-query';
import { useRouter } from 'next/navigation';
import { useEffect } from 'react';
import { api } from './api';
import { loginHref } from './safe-redirect';

export const SESSION_KEY = ['sessao'] as const;
export const TAXONOMY_KEY = ['taxonomia'] as const;

/** Quem está logado neste navegador; null quando a API responde 401. */
export async function fetchSession(): Promise<SessionUserView | null> {
  try {
    return await api.auth.session();
  } catch (e) {
    if (isApiError(e, 401)) return null;
    throw e;
  }
}

export function useSession() {
  return useQuery({ queryKey: SESSION_KEY, queryFn: fetchSession, staleTime: 60_000 });
}

/** Vocabulários (GET /api/v1/taxonomy tem Cache-Control de 1 h). */
export function useTaxonomy() {
  return useQuery({ queryKey: TAXONOMY_KEY, queryFn: () => api.taxonomy(), staleTime: 60 * 60_000, gcTime: 60 * 60_000 });
}

export const placeKey = (id: string) => ['lugar', id] as const;
export const reviewsKey = (id: string, page: number) => ['lugar', id, 'avaliacoes', page] as const;
export const myReviewKey = (id: string) => ['lugar', id, 'minha-avaliacao'] as const;

// ------------------------------------------------------------ Configurações (RF2 · RF3 · RF4 · RF5)

export const PROFILE_KEY = ['eu', 'perfil'] as const;
export const PREFERENCES_KEY = ['eu', 'preferencias'] as const;
export const CONSENTS_KEY = ['eu', 'consentimentos'] as const;
export const MFA_KEY = ['eu', '2fa'] as const;
export const SESSIONS_KEY = ['eu', 'sessoes'] as const;

export function useProfile() {
  return useQuery({ queryKey: PROFILE_KEY, queryFn: () => api.me.profile() });
}

export function usePreferences() {
  return useQuery({ queryKey: PREFERENCES_KEY, queryFn: () => api.me.preferences() });
}

export function useConsents() {
  return useQuery({ queryKey: CONSENTS_KEY, queryFn: () => api.me.consents().then((r) => r.items) });
}

// Ao sair de propósito (logout, conta excluída), a sessão vira null antes de a navegação terminar: nesse
// meio-tempo a tela não deve mandar para o login.
let leavingUntil = 0;

export function markLeaving(ms = 5_000): void {
  leavingUntil = Date.now() + ms;
}

/** Sessão obrigatória: sem ela, vai para /entrar?voltar=<path>. */
export function useRequireSession(path: string) {
  const session = useSession();
  const router = useRouter();
  const user = session.data;
  useEffect(() => {
    if (session.isSuccess && !user && Date.now() > leavingUntil) router.replace(loginHref(path));
  }, [session.isSuccess, user, router, path]);
  return session;
}

// ------------------------------------------------------------ RF17 · idade e responsável · RF18 · seus dados

export const AGE_KEY = ['eu', 'idade'] as const;
export const GUARDIAN_KEY = ['eu', 'responsavel'] as const;
export const SUPERVISED_KEY = ['responsavel', 'menores'] as const;
export const supervisionKey = (minorId: string | number) => ['responsavel', 'menores', String(minorId)] as const;
export const EXPORTS_KEY = ['eu', 'exportacoes'] as const;
export const PRIVACY_CONTACT_KEY = ['privacidade', 'contato'] as const;

/** Estado da verificação de idade; só com sessão (o visitante vê sempre a versão sem bebida). */
export function useAgeVerification(enabled = true) {
  return useQuery({ queryKey: AGE_KEY, queryFn: () => api.age.status(), enabled, staleTime: 60_000 });
}

export function useGuardianLink(enabled = true) {
  return useQuery({ queryKey: GUARDIAN_KEY, queryFn: () => api.guardian.status(), enabled });
}

export function useSupervisedMinors(enabled = true) {
  return useQuery({ queryKey: SUPERVISED_KEY, queryFn: () => api.guardian.minors(), enabled });
}

export function useDataExports() {
  return useQuery({
    queryKey: EXPORTS_KEY,
    queryFn: () => api.privacy.exports(),
    // enquanto o arquivo é preparado, confere de novo a cada 5 s
    refetchInterval: (q) => (q.state.data?.some((e) => e.status === 'REQUESTED' || e.status === 'PROCESSING') ? 5_000 : false),
  });
}

export function usePrivacyContact() {
  return useQuery({ queryKey: PRIVACY_CONTACT_KEY, queryFn: () => api.privacy.contact(), staleTime: 60 * 60_000 });
}
