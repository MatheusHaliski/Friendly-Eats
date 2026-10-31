'use client';

// Moderação (RF13) e segurança entre usuários (RF19): chaves de cache, ganchos e textos comuns.
import type { ReasonView, ReportTargetType } from '@friendly-eats/api';
import { useQuery } from '@tanstack/react-query';
import { api } from './api';
import { useSession } from './queries';

export const REASONS_KEY = ['denuncia', 'motivos'] as const;
export const ROLES_KEY = ['eu', 'papeis'] as const;
export const MY_REPORTS_KEY = ['eu', 'denuncias'] as const;
export const MY_DECISIONS_KEY = ['eu', 'decisoes'] as const;
export const SAFETY_KEY = ['eu', 'seguranca'] as const;
export const integrityKey = (placeId: string) => ['lugar', placeId, 'integridade'] as const;
export const relationKey = (userId: number) => ['eu', 'relacao', userId] as const;
export const casesKey = (status: string, page: number) => ['moderacao', 'casos', status, page] as const;
export const caseKey = (id: number) => ['moderacao', 'caso', id] as const;

/** Motivos fechados (GET /api/v1/reports/reasons): mudam pouco, ficam 1 h no cache. */
export function useReasons() {
  return useQuery({ queryKey: REASONS_KEY, queryFn: () => api.reports.reasons(), staleTime: 60 * 60_000 });
}

/** Só para quem entrou: o app mostra "Moderação" a quem tem o papel. */
export function useRoles() {
  const session = useSession();
  return useQuery({ queryKey: ROLES_KEY, queryFn: () => api.reports.roles(), enabled: !!session.data, staleTime: 5 * 60_000 });
}

/** Alertas ao consumidor e congelamento da página do lugar (público). */
export function useIntegrity(placeId: string) {
  return useQuery({ queryKey: integrityKey(placeId), queryFn: () => api.reports.integrity(placeId), retry: false });
}

export function reasonsFor(all: ReasonView[] | undefined, target: ReportTargetType, ownerContest = false): ReasonView[] {
  return (all ?? []).filter((r) => (ownerContest ? r.ownerContest : r.targets.includes(target)));
}

/** Rótulo do alvo no texto ("este post", "este comentário"…). */
export const TARGET_TEXT: Record<ReportTargetType, string> = {
  POST: 'este post',
  COMMENT: 'este comentário',
  REVIEW: 'esta avaliação',
  PROFILE: 'este perfil',
  ESTABLISHMENT: 'esta página de lugar',
};

/** /denunciar com o alvo já preenchido (canal sem conta). */
export function publicReportHref(type: ReportTargetType, id: string | number): string {
  return `/denunciar?tipo=${encodeURIComponent(type)}&id=${encodeURIComponent(String(id))}`;
}

const dateTime = new Intl.DateTimeFormat('pt-BR', {
  day: 'numeric',
  month: 'short',
  year: 'numeric',
  hour: '2-digit',
  minute: '2-digit',
  timeZone: 'America/Sao_Paulo',
});

export function formatWhen(iso: string | null | undefined): string {
  if (!iso) return '';
  const d = new Date(iso);
  return Number.isNaN(d.getTime()) ? '' : dateTime.format(d);
}

/**
 * Link colado no formulário público → alvo. Aceita /posts/{uuid}, /p/{uuid}, /u/{id} e /lugares/{id}; comentário e
 * avaliação chegam pelo botão "Denunciar" (tipo e id já preenchidos).
 */
export function parseTargetLink(link: string): { type: ReportTargetType; id: string } | null {
  const path = link.trim().replace(/^https?:\/\/[^/]+/i, '');
  const post = /^\/(?:posts|p)\/([0-9a-f-]{36})(?:[/?#].*)?$/i.exec(path);
  if (post) return { type: 'POST', id: post[1]!.toLowerCase() };
  const profile = /^\/u\/(\d+)(?:[/?#].*)?$/.exec(path);
  if (profile) return { type: 'PROFILE', id: profile[1]! };
  const place = /^\/lugares\/(\d+)(?:[/?#].*)?$/.exec(path);
  if (place) return { type: 'ESTABLISHMENT', id: place[1]! };
  return null;
}
