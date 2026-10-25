'use client';

import type { EstablishmentView, SessionUserView } from '@friendly-eats/api';
import { useMutation, useQuery } from '@tanstack/react-query';
import Link from 'next/link';
import { useState, type ReactNode } from 'react';
import { AccountBadge, VerifiedSeal, verificationText } from '@/components/AccountBadge';
import { Notice } from '@/components/Form';
import { IconInfo, IconLogout, IconSettings, IconSuccess } from '@/components/icons';
import { ownsPlaces } from '@/lib/account-types';
import { api } from '@/lib/api';
import { useRequireSession } from '@/lib/queries';
import { SETTINGS_SECTIONS } from '@/lib/settings-sections';
import { useLeaveAccount } from '@/lib/use-leave';

const STATUS_TEXT: Record<string, string> = {
  PENDING_VERIFICATION: 'E-mail não confirmado',
  ACTIVE: 'Ativa',
  SUSPENDED: 'Suspensa',
  PENDING_DELETION: 'Em exclusão',
  ANONYMOUS_LGPD_DELETED: 'Eliminada',
};

/** Minha conta (RF4): dados da sessão, sair e "Meus lugares" (RF6) para Estabelecimento e Varejo. */
export function AccountView() {
  const session = useRequireSession('/conta');
  const leave = useLeaveAccount();
  const [logoutError, setLogoutError] = useState<string | null>(null);
  const user = session.data;

  const logout = useMutation({
    mutationFn: () => api.auth.logout(),
    onSuccess: () => leave('/'),
    onError: () => setLogoutError('Não foi possível sair agora. Tente de novo.'),
  });

  if (session.isPending || !user) return <p className="text-muted">Carregando…</p>;

  return (
    <div className="grid max-w-3xl gap-8">
      <div className="flex flex-wrap items-center justify-between gap-4">
        <h1 className="text-3xl font-extrabold">Minha conta</h1>
        <button type="button" className="fe-btn" onClick={() => logout.mutate()} disabled={logout.isPending}>
          <IconLogout size={18} />
          {logout.isPending ? 'Saindo…' : 'Sair'}
        </button>
      </div>
      {logoutError && (
        <Notice tone="error" live>
          <p>{logoutError}</p>
        </Notice>
      )}
      <AccountDetails user={user} />
      <SettingsLinks />
      {ownsPlaces(user.category.code) && <MyPlaces />}
    </div>
  );
}

function AccountDetails({ user }: { user: SessionUserView }) {
  const seal = verificationText(user.categoryVerification);
  return (
    <section aria-labelledby="titulo-dados" className="fe-card grid gap-4 p-5">
      <h2 id="titulo-dados" className="text-xl font-bold">
        Dados da sessão
      </h2>
      <dl className="grid gap-3 sm:grid-cols-2">
        <Row term="Nome">
          <span className="inline-flex items-center gap-1.5">
            {user.name}
            {user.categoryVerification === 'VERIFIED' && <VerifiedSeal />}
          </span>
        </Row>
        <Row term="@usuário">@{user.handle}</Row>
        <Row term="E-mail">
          {user.email}{' '}
          <span className="text-sm text-muted">({user.emailVerified ? 'confirmado' : 'não confirmado'})</span>
        </Row>
        <Row term="Tipo de conta">
          <AccountBadge code={user.category.code} label={user.category.label} />
        </Row>
        <Row term="Situação">{STATUS_TEXT[user.accountStatus] ?? user.accountStatus}</Row>
        {seal && <Row term="Selo">{seal}</Row>}
        <Row term="Verificação em duas etapas">{user.mfaEnabled ? 'Ligada' : 'Desligada'}</Row>
      </dl>
      {!user.emailVerified && (
        <Notice>
          <p>
            Confirme o seu e-mail para avaliar e interagir.{' '}
            <Link href="/confirmar-email" className="font-semibold underline">
              Confirmar e-mail
            </Link>
          </p>
        </Notice>
      )}
      {!user.termsUpToDate && (
        <Notice>
          <p>
            Os Termos de Uso mudaram. Leia a versão nova:{' '}
            <Link href="/termos" className="font-semibold underline">
              Termos de Uso
            </Link>
          </p>
        </Notice>
      )}
      {user.minor && <p className="text-sm text-muted">Conta de menor de 16 anos: as proteções ficam ligadas e só o responsável altera.</p>}
    </section>
  );
}

/** Atalhos para /configuracoes: perfil, exibição, privacidade, segurança e excluir conta. */
function SettingsLinks() {
  return (
    <section aria-labelledby="titulo-configuracoes" className="grid gap-3">
      <h2 id="titulo-configuracoes" className="flex items-center gap-2 text-xl font-bold">
        <IconSettings size={20} />
        Configurações
      </h2>
      <ul className="grid gap-2 sm:grid-cols-2">
        {SETTINGS_SECTIONS.map((s) => (
          <li key={s.slug}>
            <Link href={s.href} className="fe-card flex h-full flex-col gap-0.5 p-4 no-underline hover:bg-sunken">
              <span className="font-bold underline">{s.title}</span>
              <span className="text-sm text-muted">{s.description}</span>
            </Link>
          </li>
        ))}
      </ul>
    </section>
  );
}

function Row({ term, children }: { term: string; children: ReactNode }) {
  return (
    <div className="grid gap-0.5">
      <dt className="text-sm text-muted">{term}</dt>
      <dd>{children}</dd>
    </div>
  );
}

function MyPlaces() {
  const places = useQuery({ queryKey: ['meus-lugares'], queryFn: () => api.me.establishments() });
  return (
    <section aria-labelledby="titulo-lugares" className="grid gap-4">
      <h2 id="titulo-lugares" className="text-xl font-bold">
        Meus lugares
      </h2>
      {places.isPending && <p className="text-muted">Carregando…</p>}
      {places.isError && (
        <Notice tone="error">
          <p>Não foi possível carregar os seus lugares.</p>
        </Notice>
      )}
      {places.data && places.data.length === 0 && <p className="text-muted">Você ainda não cadastrou lugares.</p>}
      {places.data && places.data.length > 0 && (
        <ul className="grid gap-3">
          {places.data.map((p) => (
            <li key={p.id} className="fe-card grid gap-2 p-4">
              <PlaceRow place={p} />
            </li>
          ))}
        </ul>
      )}
    </section>
  );
}

function PlaceRow({ place }: { place: EstablishmentView }) {
  const published = place.published === true;
  return (
    <>
      <div className="flex flex-wrap items-center justify-between gap-2">
        <h3 className="font-bold">
          <Link href={`/lugares/${place.id}`} className="underline">
            {place.name}
          </Link>
        </h3>
        <span className="fe-tag">
          {published ? <IconSuccess size={16} className="text-success" /> : <IconInfo size={16} />}
          {published ? 'No ar' : 'Fora do ar'}
        </span>
      </div>
      <p className="text-sm text-muted">
        {place.primaryType.label}
        {place.status === 'CLOSED' ? ' · Fechado' : ''}
      </p>
      {!published && place.publishBlockers && place.publishBlockers.length > 0 && (
        <div className="text-sm">
          <p className="font-semibold">Por que está fora do ar:</p>
          <ul className="list-disc pl-5">
            {place.publishBlockers.map((b) => (
              <li key={b}>{b}</li>
            ))}
          </ul>
        </div>
      )}
    </>
  );
}
