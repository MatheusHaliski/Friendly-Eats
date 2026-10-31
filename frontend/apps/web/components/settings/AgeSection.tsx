'use client';

import {
  isApiError,
  type AgeStartView,
  type AgeVerificationView,
  type GuardianActionView,
  type GuardianRequestView,
  type MinorLinkView,
  type ProtectionCode,
} from '@friendly-eats/api';
import { useMutation, useQueryClient } from '@tanstack/react-query';
import Link from 'next/link';
import { useRouter, useSearchParams } from 'next/navigation';
import { useEffect, useId, useRef, useState, type FormEvent, type ReactNode } from 'react';
import { Field, Notice, useDeferredFocus } from '@/components/Form';
import { IconAgeVerify, IconGuardian, IconInfo, IconLocked, IconNext, IconSuccess } from '@/components/icons';
import { api } from '@/lib/api';
import { formatDate } from '@/lib/format';
import { formatDateTime } from '@/lib/post-format';
import { AGE_KEY, GUARDIAN_KEY, SUPERVISED_KEY, useAgeVerification, useGuardianLink, useSession, useSupervisedMinors } from '@/lib/queries';
import { ResultNotice, type Result } from './PreferenceControls';
import { SectionHeading } from './SectionHeading';

/**
 * Configurações → Idade e responsável (RF17): verificar a idade (sem autodeclaração, ECA Digital art. 9º), contestar o
 * resultado, o vínculo do menor com o responsável e, para quem é responsável, a lista de quem supervisiona.
 */
export function AgeSection() {
  const session = useSession();
  const sp = useSearchParams();
  const user = session.data;
  // o convite fica na tela depois de aceito, mesmo com a URL já limpa
  const [invite] = useState(() => sp.get('convite'));
  if (!user) return null;
  const returning = sp.get('verificacao');
  return (
    <>
      <SectionHeading description="Conteúdo de bebida alcoólica aparece só com a idade verificada. Contas de menores de 16 ficam vinculadas a um responsável.">
        Idade e responsável
      </SectionHeading>
      {invite && <InviteCard token={invite} />}
      <AgeVerificationCard returning={returning} />
      {!user.minor && <SupervisedCard />}
      <MinorGuardianCard />
      <p className="text-sm">
        Como tratamos esses dados está nos{' '}
        <Link href="/termos" className="font-semibold underline">
          Termos de Uso
        </Link>{' '}
        e na página{' '}
        <Link href="/privacidade" className="font-semibold underline">
          Privacidade
        </Link>
        .
      </p>
    </>
  );
}

// ------------------------------------------------------------ partes comuns

/** Aviso de resultado de um cartão, focado depois de montado (o leitor de tela anuncia). */
function useResult() {
  const [result, setResult] = useState<Result>(null);
  const [ref, focus] = useDeferredFocus<HTMLDivElement>();
  return { result, setResult, ref, focus };
}

type ResultChannel = ReturnType<typeof useResult>;

/** Mutação que escreve o sucesso ou o erro no aviso do cartão. */
function useAction<T, A = void>(channel: ResultChannel, fn: (a: A) => Promise<T>, success: (r: T) => string | null, after?: (r: T) => void) {
  return useMutation({
    mutationFn: fn,
    onMutate: () => channel.setResult(null),
    onSuccess: (r) => {
      after?.(r);
      const text = success(r);
      if (text) {
        channel.setResult({ tone: 'success', text });
        channel.focus();
      }
    },
    onError: (e) => {
      channel.setResult({ tone: 'error', text: isApiError(e) ? (e.errors.length ? e.errors.join(' ') : e.detail) : 'Não foi possível concluir agora. Tente de novo.' });
      channel.focus();
    },
  });
}

function Card({ title, icon, children, testId }: { title: string; icon: ReactNode; children: ReactNode; testId?: string }) {
  const uid = useId();
  return (
    <section aria-labelledby={`${uid}-titulo`} className="fe-card grid gap-4 p-5" data-testid={testId}>
      <h2 id={`${uid}-titulo`} className="flex items-center gap-2 text-xl font-bold">
        {icon}
        {title}
      </h2>
      {children}
    </section>
  );
}

function History({ items, empty }: { items: Array<{ key: string; label: string; detail?: string | null; at: string }>; empty: string }) {
  if (!items.length) return <p className="text-sm text-muted">{empty}</p>;
  return (
    <ol className="grid gap-1 text-sm">
      {items.map((h) => (
        <li key={h.key} className="flex flex-wrap gap-x-2">
          <time dateTime={h.at} className="text-muted">
            {formatDateTime(h.at)}
          </time>
          <span>
            {h.label}
            {h.detail ? `: ${h.detail}` : ''}
          </span>
        </li>
      ))}
    </ol>
  );
}

// ------------------------------------------------------------ verificação de idade

const STATUS_ICON: Record<AgeVerificationView['status'], ReactNode> = {
  VERIFIED_ADULT: <IconSuccess size={20} className="text-success" />,
  VERIFIED_MINOR: <IconLocked size={20} />,
  PENDING: <IconInfo size={20} />,
  FAILED: <IconInfo size={20} />,
  UNVERIFIED: <IconInfo size={20} />,
};

function AgeVerificationCard({ returning }: { returning: string | null }) {
  const age = useAgeVerification();
  const queryClient = useQueryClient();
  const router = useRouter();
  const [session, setSession] = useState<AgeStartView | null>(null);
  const channel = useResult();
  const done = (v: AgeVerificationView) => {
    // uma leitura que ainda estava a caminho não pode sobrescrever o resultado
    void queryClient.cancelQueries({ queryKey: AGE_KEY }).then(() => queryClient.setQueryData(AGE_KEY, v));
    // feed, busca e páginas passam a mostrar (ou esconder) o conteúdo de bebida
    void queryClient.invalidateQueries({ predicate: (q) => q.queryKey[0] !== 'eu' && q.queryKey[0] !== 'sessao' });
  };
  const start = useAction(
    channel,
    () => api.age.start(),
    (s) => (s.simulated ? 'Verificação aberta. Escolha o resultado simulado abaixo.' : null),
    (s) => {
      void queryClient.invalidateQueries({ queryKey: AGE_KEY });
      if (s.simulated) setSession(s);
      else window.location.assign(s.redirectUrl);
    },
  );
  const complete = useAction(
    channel,
    ({ reference, params }: { reference: string; params: Record<string, string> }) => api.age.complete(reference, params),
    (v) =>
      v.status === 'VERIFIED_ADULT'
        ? 'Idade verificada: o conteúdo de bebida alcoólica já aparece para você.'
        : v.status === 'PENDING'
          ? 'O fornecedor ainda não concluiu a verificação. Tente de novo em instantes.'
          : `Resultado: ${v.statusLabel}.`,
    (v) => {
      done(v);
      setSession(null);
    },
  );

  // volta do fornecedor: /configuracoes/idade?verificacao=ref&… → o servidor pergunta o resultado
  const asked = useRef(false);
  const sp = useSearchParams();
  useEffect(() => {
    if (!returning || asked.current) return;
    asked.current = true;
    const params: Record<string, string> = {};
    sp.forEach((v, k) => {
      if (k !== 'verificacao' && k !== 'convite') params[k] = v;
    });
    complete.mutate({ reference: returning, params });
    router.replace('/configuracoes/idade', { scroll: false });
  }, [returning, sp, complete, router]);

  const v = age.data;
  return (
    <Card title="Verificação de idade" icon={<IconAgeVerify size={22} />} testId="cartao-idade">
      <p className="text-muted">
        Pela lei (ECA Digital, art. 9º, e Decreto 12.880/2026), a data de nascimento informada não basta para ver conteúdo de bebida alcoólica.
        A verificação é feita por um fornecedor especializado. <strong className="text-fg">Guardamos só o resultado, o método e a data, nunca a imagem do documento.</strong>
      </p>
      {age.isPending && <p className="text-muted">Carregando…</p>}
      {age.isError && (
        <Notice tone="error">
          <p>Não foi possível carregar a verificação de idade. Recarregue a página.</p>
        </Notice>
      )}
      {v && (
        <>
          <dl className="grid gap-2 sm:grid-cols-[12rem_1fr]">
            <dt className="font-semibold">Situação</dt>
            <dd className="flex items-center gap-2" data-testid="status-idade">
              {STATUS_ICON[v.status]}
              {v.statusLabel}
            </dd>
            {v.verifiedAt && (
              <>
                <dt className="font-semibold">Verificada em</dt>
                <dd>
                  {formatDate(v.verifiedAt)}
                  {v.method ? ` · método: ${v.method}` : ''}
                </dd>
              </>
            )}
            {v.validUntil && (
              <>
                <dt className="font-semibold">Vale até</dt>
                <dd>{formatDate(v.validUntil)}</dd>
              </>
            )}
            <dt className="font-semibold">Conteúdo de bebida</dt>
            <dd>{v.alcoholContentVisible ? 'Aparece para você' : 'Oculto'}</dd>
          </dl>
          {!v.declaredAdult && (
            <Notice>
              <p>Pela data de nascimento informada, você tem menos de 18 anos: o conteúdo de bebida alcoólica fica oculto e não há o que verificar.</p>
            </Notice>
          )}
          {v.declaredAdult && !v.providerAvailable && (
            <Notice>
              <p>A verificação de idade ainda não está disponível. Enquanto isso, o conteúdo de bebida alcoólica fica oculto.</p>
            </Notice>
          )}
          <ResultNotice result={channel.result} innerRef={channel.ref} />
          {v.canStart && !session && (
            <div>
              <button type="button" className="fe-btn fe-btn-primary" disabled={start.isPending} onClick={() => start.mutate()}>
                <IconAgeVerify size={18} />
                {start.isPending ? 'Abrindo…' : v.status === 'PENDING' ? 'Recomeçar a verificação' : 'Verificar minha idade'}
              </button>
            </div>
          )}
          {session && <SimulatedProvider session={session} busy={complete.isPending} onPick={(params) => complete.mutate({ reference: session.reference, params })} />}
          {v.appeal && <AppealStatus appeal={v.appeal} />}
          {v.canAppeal && <AppealForm />}
          <details className="grid gap-2">
            <summary className="cursor-pointer font-semibold">Histórico da verificação</summary>
            <History
              empty="Nenhuma verificação ainda."
              items={v.history.map((h, i) => ({ key: `${i}-${h.occurredAt}`, label: h.statusLabel, detail: h.note, at: h.occurredAt }))}
            />
          </details>
        </>
      )}
    </Card>
  );
}

/** Só em desenvolvimento: o fornecedor simulado deixa escolher o resultado. */
function SimulatedProvider({ session, busy, onPick }: { session: AgeStartView; busy: boolean; onPick: (p: Record<string, string>) => void }) {
  return (
    <div role="group" aria-label="Fornecedor simulado" className="grid gap-2 rounded-md border-2 border-dashed border-line-strong p-3" data-testid="fornecedor-simulado">
      <p className="font-semibold">Fornecedor simulado (só em desenvolvimento)</p>
      <p className="text-sm text-muted">{session.instructions}</p>
      <div className="flex flex-wrap gap-2">
        <button type="button" className="fe-btn fe-btn-sm" disabled={busy} onClick={() => onPick({ simulate: 'ADULT' })}>
          Resultado: maior de 18
        </button>
        <button type="button" className="fe-btn fe-btn-sm" disabled={busy} onClick={() => onPick({ simulate: 'MINOR' })}>
          Resultado: menor de 18
        </button>
        <button type="button" className="fe-btn fe-btn-sm" disabled={busy} onClick={() => onPick({ simulate: 'FAILED' })}>
          Resultado: falhou
        </button>
      </div>
    </div>
  );
}

const APPEAL_STATUS: Record<string, string> = { OPEN: 'Em análise por uma pessoa', ACCEPTED: 'Aceita: refaça a verificação', REJECTED: 'Recusada' };

function AppealStatus({ appeal }: { appeal: NonNullable<AgeVerificationView['appeal']> }) {
  return (
    <div className="grid gap-1 rounded-md border-2 border-line-strong p-3" data-testid="contestacao">
      <p className="font-semibold">Contestação: {APPEAL_STATUS[appeal.status] ?? appeal.status}</p>
      <p className="text-sm text-muted">Enviada em {formatDate(appeal.createdAt)}.</p>
      {appeal.decisionNote && <p className="text-sm">Resposta: {appeal.decisionNote}</p>}
    </div>
  );
}

function AppealForm() {
  const uid = useId();
  const queryClient = useQueryClient();
  const [reason, setReason] = useState('');
  const [error, setError] = useState<string[] | undefined>();
  const channel = useResult();
  const action = useAction(
    channel,
    (text: string) => api.age.appeal(text),
    () => 'Contestação enviada. Uma pessoa da equipe vai revisar e responder por e-mail.',
    () => {
      setReason('');
      void queryClient.invalidateQueries({ queryKey: AGE_KEY });
    },
  );
  function submit(e: FormEvent) {
    e.preventDefault();
    const text = reason.trim();
    if (text.length < 10) {
      setError(['Explique o motivo em pelo menos 10 caracteres.']);
      return;
    }
    setError(undefined);
    action.mutate(text);
  }
  return (
    <form noValidate onSubmit={submit} className="grid gap-3" aria-labelledby={`${uid}-titulo`}>
      <h3 id={`${uid}-titulo`} className="font-bold">
        Contestar o resultado
      </h3>
      <p className="text-sm text-muted">Se a idade aferida estiver errada, conte o que aconteceu. Uma pessoa revisa o pedido (Decreto 12.880, art. 27).</p>
      <Field id={`${uid}-motivo`} label="Motivo" required hint="De 10 a 1000 caracteres." errors={error}>
        {(a) => <textarea {...a} className="fe-input min-h-24" maxLength={1000} value={reason} onChange={(e) => setReason(e.target.value)} />}
      </Field>
      <ResultNotice result={channel.result} innerRef={channel.ref} />
      <div>
        <button type="submit" className="fe-btn" disabled={action.isPending}>
          {action.isPending ? 'Enviando…' : 'Enviar contestação'}
        </button>
      </div>
    </form>
  );
}

// ------------------------------------------------------------ convite recebido (responsável)

function InviteCard({ token }: { token: string }) {
  const queryClient = useQueryClient();
  const router = useRouter();
  const [needsAge, setNeedsAge] = useState(false);
  const [ref, focus] = useDeferredFocus<HTMLDivElement>();
  const [result, setResult] = useState<Result>(null);
  const accept = useMutation({
    mutationFn: () => api.guardian.accept(token),
    onSuccess: (minor) => {
      void queryClient.invalidateQueries({ queryKey: SUPERVISED_KEY });
      setResult({ tone: 'success', text: `Vínculo aceito. Agora você supervisiona a conta de ${minor.name} (@${minor.handle}).` });
      focus();
      router.replace('/configuracoes/idade', { scroll: false });
    },
    onError: (e) => {
      setNeedsAge(isApiError(e, 403) && !!e.problem.ageVerificationRequired);
      setResult({ tone: 'error', text: isApiError(e) ? e.detail : 'Não foi possível aceitar agora. Tente de novo.' });
      focus();
    },
  });
  return (
    <Card title="Convite para ser responsável" icon={<IconGuardian size={22} />} testId="cartao-convite">
      <p>
        Você recebeu um convite para supervisionar a conta de um menor no Friendly Eats (ECA Digital, arts. 17, 18 e 24). Para aceitar, a sua idade
        precisa estar verificada como maior de 18.
      </p>
      <ResultNotice result={result} innerRef={ref} />
      {needsAge && <p className="text-sm">Use o botão Verificar minha idade abaixo e depois aceite de novo.</p>}
      {result?.tone !== 'success' && (
        <div>
          <button type="button" className="fe-btn fe-btn-primary" disabled={accept.isPending} onClick={() => accept.mutate()}>
            <IconGuardian size={18} />
            {accept.isPending ? 'Aceitando…' : 'Aceitar o vínculo'}
          </button>
        </div>
      )}
    </Card>
  );
}

// ------------------------------------------------------------ quem eu supervisiono

function SupervisedCard() {
  const minors = useSupervisedMinors();
  if (!minors.data?.length) return null;
  return (
    <Card title="Quem eu supervisiono" icon={<IconGuardian size={22} />} testId="cartao-supervisao">
      <ul className="grid gap-2">
        {minors.data.map((m) => (
          <li key={m.minorId}>
            <Link href={`/configuracoes/idade/menor/${m.minorId}`} className="flex min-h-11 items-center justify-between gap-3 rounded-md border-2 border-line p-3 no-underline hover:bg-sunken">
              <span className="grid">
                <span className="font-semibold">
                  {m.name} <span className="text-muted">@{m.handle}</span>
                </span>
                <span className="text-sm text-muted">
                  {m.age} anos · vinculado em {formatDate(m.linkedAt)}
                  {m.pendingRequests ? ` · ${m.pendingRequests} ${m.pendingRequests === 1 ? 'pedido pendente' : 'pedidos pendentes'}` : ''}
                </span>
              </span>
              <span className="flex items-center gap-1 font-semibold">
                Abrir painel
                <IconNext size={18} />
              </span>
            </Link>
          </li>
        ))}
      </ul>
    </Card>
  );
}

// ------------------------------------------------------------ vínculo do menor

const LINK_LABEL: Record<MinorLinkView['status'], string> = { NONE: 'Sem responsável vinculado', PENDING: 'Convite enviado, aguardando o responsável', ACTIVE: 'Supervisão ativa' };
export const REQUEST_STATUS: Record<GuardianRequestView['status'], string> = {
  PENDING: 'Aguardando o responsável',
  APPROVED: 'Aprovado',
  DENIED: 'Negado',
  CANCELLED: 'Cancelado',
};

function MinorGuardianCard() {
  const link = useGuardianLink();
  const queryClient = useQueryClient();
  const uid = useId();
  const [email, setEmail] = useState('');
  const update = (v: MinorLinkView) => queryClient.setQueryData(GUARDIAN_KEY, v);
  const channel = useResult();
  const invite = useAction(channel, (to: string | undefined) => api.guardian.invite(to), () => 'Convite enviado ao responsável.', (v) => {
    update(v);
    setEmail('');
  });
  const request = useAction(
    channel,
    (p: ProtectionCode) => api.guardian.request(p),
    (r) => `Pedido enviado ao responsável: ${r.label}.`,
    () => void queryClient.invalidateQueries({ queryKey: GUARDIAN_KEY }),
  );
  const unlink = useAction(channel, () => api.guardian.unlink(), () => 'Vínculo desfeito.', update);
  const v = link.data;
  if (!v?.eligible) return null;
  const active = v.status === 'ACTIVE';
  const pendingFor = new Set(v.requests.filter((r) => r.status === 'PENDING').map((r) => r.protection));

  return (
    <Card title="Seu responsável" icon={<IconGuardian size={22} />} testId="cartao-responsavel">
      <p className="text-muted">
        {v.required
          ? 'Contas de menores de 16 anos ficam vinculadas a um responsável (ECA Digital, art. 24). Enquanto o vínculo não é aceito, todas as proteções ficam ligadas.'
          : 'A partir dos 16 anos o vínculo é opcional. Com ele, o responsável pode consentir os estilos alimentares no seu perfil (LGPD, art. 14).'}
      </p>
      <p className="flex items-center gap-2 font-semibold" data-testid="status-vinculo">
        {active ? <IconSuccess size={20} className="text-success" /> : <IconInfo size={20} />}
        {LINK_LABEL[v.status]}
        {active && v.guardian ? `: ${v.guardian.name} (@${v.guardian.handle})` : ''}
      </p>
      {active && <p className="text-sm">O seu responsável vê as suas configurações de proteção e decide os pedidos abaixo. Tudo o que ele faz aparece no histórico.</p>}
      {v.status === 'PENDING' && (
        <p className="text-sm">
          Enviado para <strong>{v.guardianEmail}</strong>
          {v.invitedAt ? ` em ${formatDate(v.invitedAt)}` : ''}
          {v.inviteExpiresAt ? `; o link vale até ${formatDate(v.inviteExpiresAt)}` : ''}.
        </p>
      )}
      {v.overdue && (
        <Notice tone="error">
          <p data-testid="aviso-pendente">O convite está sem resposta há {v.pendingDays} dias. Lembre o seu responsável ou reenvie o convite.</p>
        </Notice>
      )}
      <ResultNotice result={channel.result} innerRef={channel.ref} />
      {v.status === 'PENDING' && (
        <div>
          <button type="button" className="fe-btn" disabled={invite.isPending} onClick={() => invite.mutate(undefined)}>
            Reenviar convite
          </button>
        </div>
      )}
      {!active && (
        <form
          noValidate
          className="grid gap-2 sm:grid-cols-[minmax(0,1fr)_auto] sm:items-end"
          onSubmit={(e) => {
            e.preventDefault();
            invite.mutate(email.trim());
          }}
        >
          <Field id={`${uid}-email`} label={v.status === 'PENDING' ? 'Convidar outro e-mail' : 'E-mail do responsável'}>
            {(a) => <input {...a} type="email" autoComplete="off" className="fe-input" value={email} onChange={(e) => setEmail(e.target.value)} />}
          </Field>
          <button type="submit" className="fe-btn" disabled={invite.isPending || !email.trim()}>
            Enviar convite
          </button>
        </form>
      )}
      {v.required && (
        <div className="grid gap-2">
          <h3 className="font-bold">Proteções da sua conta</h3>
          <ul className="grid gap-2">
            {v.protections.map((p) => (
              <li key={p.code} className="flex flex-wrap items-center justify-between gap-2 rounded-md border-2 border-line p-3">
                <span className="grid">
                  <span className="flex items-center gap-1.5 font-semibold">
                    {!p.allowed && <IconLocked size={16} />}
                    {p.label}
                  </span>
                  <span className="text-sm text-muted">{p.allowed ? 'Liberado pelo responsável' : 'Proteção ligada'}</span>
                </span>
                {!p.allowed &&
                  (pendingFor.has(p.code) ? (
                    <span className="text-sm font-semibold">Pedido enviado</span>
                  ) : (
                    <button type="button" className="fe-btn fe-btn-sm" disabled={!active || request.isPending} onClick={() => request.mutate(p.code)}>
                      Pedir ao responsável<span className="sr-only">: {p.label}</span>
                    </button>
                  ))}
              </li>
            ))}
          </ul>
          {!active && <p className="text-sm text-muted">Os pedidos ficam disponíveis depois que o responsável aceitar o vínculo.</p>}
        </div>
      )}
      {v.canUnlink && (
        <div>
          <button type="button" className="fe-btn" disabled={unlink.isPending} onClick={() => unlink.mutate()}>
            Desfazer o vínculo
          </button>
        </div>
      )}
      <details className="grid gap-2">
        <summary className="cursor-pointer font-semibold">Histórico do vínculo</summary>
        <History empty="Nada por enquanto." items={guardianHistory(v.history)} />
      </details>
    </Card>
  );
}

export function guardianHistory(items: GuardianActionView[]) {
  return items.map((h, i) => ({ key: `${i}-${h.occurredAt}`, label: h.label, detail: h.detail, at: h.occurredAt }));
}

export { History as GuardianHistory };
