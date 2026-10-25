'use client';

import { isApiError, type SessionUserView } from '@friendly-eats/api';
import { useQuery } from '@tanstack/react-query';
import { api } from './api';

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
