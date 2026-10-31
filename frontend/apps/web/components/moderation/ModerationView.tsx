'use client';

import {
  isApiError,
  type AlertRequest,
  type CaseDetailView,
  type CaseStatusCode,
  type CaseSummaryView,
  type ModerationActionCode,
  type ReportReasonCode,
} from '@friendly-eats/api';
import { keepPreviousData, useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import Link from 'next/link';
import { useId, useState, type FormEvent, type ReactNode } from 'react';
import { TabPanel, Tabs } from '@/components/feed/Tabs';
import { Notice, useDeferredFocus } from '@/components/Form';
import { IconConsumerAlert, IconDeadline, IconModeration } from '@/components/icons';
import { Pagination } from '@/components/Pagination';
import { RadioPills } from '@/components/settings/PreferenceControls';
import { api } from '@/lib/api';
import { caseKey, casesKey, formatWhen, useReasons, useRoles } from '@/lib/moderation';
import { useRequireSession } from '@/lib/queries';

type TabKey = CaseStatusCode | 'STATS';
const TABS: Array<{ key: TabKey; label: string }> = [
  { key: 'OPEN', label: 'Na fila' },
  { key: 'IN_APPEAL', label: 'Em recurso' },
  { key: 'DECIDED', label: 'Decididos' },
  { key: 'FINAL', label: 'Encerrados' },
  { key: 'STATS', label: 'Relatório' },
];
const PAGE = 20;

/**
 * Área da moderação (RF13): fila priorizada com prazo, detalhe com a cópia guardada, denúncias, sinais e histórico,
 * decisão motivada, recurso por outra pessoa, comunicação às autoridades, alertas do lugar e o relatório agregado.
 */
export function ModerationView() {
  const session = useRequireSession('/moderacao');
  const roles = useRoles();
  const [tab, setTab] = useState<TabKey>('OPEN');
  const [selected, setSelected] = useState<number | null>(null);

  if (session.isPending || !session.data || roles.isPending) return <p className="text-muted">Carregando…</p>;
  if (!roles.data?.moderator) {
    return (
      <div className="grid max-w-xl gap-4">
        <h1 className="text-3xl font-extrabold">Moderação</h1>
        <Notice>
          <p>Só a equipe de moderação acessa esta área. Para denunciar um conteúdo, use o botão &quot;Denunciar&quot; no menu &quot;…&quot; dele.</p>
        </Notice>
      </div>
    );
  }
  return (
    <div className="grid gap-6">
      <header className="grid gap-1">
        <h1 className="flex items-center gap-2 text-3xl font-extrabold">
          <IconModeration size={28} />
          Moderação
        </h1>
        <p className="text-muted">Criança em risco e desinformação sanitária vêm primeiro. Toda decisão vai para o autor com o motivo, e o recurso é revisto por outra pessoa.</p>
      </header>
      <Tabs
        label="Situação dos casos"
        idPrefix="moderacao"
        items={TABS}
        selected={tab}
        onSelect={(k) => {
          setTab(k);
          setSelected(null);
        }}
      />
      <TabPanel idPrefix="moderacao" tab={tab}>
        {tab === 'STATS' ? (
          <Stats />
        ) : (
          <div className="grid gap-6 lg:grid-cols-[22rem_minmax(0,1fr)] lg:items-start">
            <Queue key={tab} status={tab} selected={selected} onSelect={setSelected} />
            {selected != null ? <CaseDetail id={selected} /> : <p className="text-muted">Escolha um caso da lista para ver o detalhe.</p>}
          </div>
        )}
      </TabPanel>
    </div>
  );
}

function Queue({ status, selected, onSelect }: { status: CaseStatusCode; selected: number | null; onSelect: (id: number) => void }) {
  const [page, setPage] = useState(0);
  const q = useQuery({ queryKey: casesKey(status, page), queryFn: () => api.moderation.queue(status, page, PAGE), placeholderData: keepPreviousData });
  if (q.isPending) return <p className="text-muted">Carregando a fila…</p>;
  if (q.isError) {
    return (
      <Notice tone="error">
        <p>Não foi possível carregar a fila. {isApiError(q.error) ? q.error.detail : ''}</p>
      </Notice>
    );
  }
  return (
    <div className="grid gap-3">
      {q.data.items.length === 0 ? (
        <p className="text-muted">Nenhum caso aqui.</p>
      ) : (
        <ol className="grid gap-2" aria-label="Casos" data-testid="fila">
          {q.data.items.map((c) => (
            <li key={c.id}>
              <button
                type="button"
                className="fe-card grid w-full gap-1 p-3 text-left"
                aria-pressed={selected === c.id}
                onClick={() => onSelect(c.id)}
              >
                <CaseLine c={c} />
              </button>
            </li>
          ))}
        </ol>
      )}
      <Pagination page={q.data.page} size={q.data.size} total={q.data.total} onPage={setPage} label="Páginas da fila" />
    </div>
  );
}

function CaseLine({ c }: { c: CaseSummaryView }) {
  return (
    <>
      <span className="flex flex-wrap items-center gap-2">
        <span className="fe-tag">Prioridade {c.priority.label}</span>
        {c.preventive && <span className="fe-tag">Retirado preventivamente</span>}
        {c.authorityReferral === 'PENDING' && <span className="fe-tag">Comunicar autoridades</span>}
      </span>
      <span className="font-bold">{c.topReason.label}</span>
      <span className="text-sm">
        {c.target.typeLabel}
        {c.target.excerpt ? `: “${c.target.excerpt}”` : ''}
      </span>
      <span className="flex items-center gap-1 text-sm text-muted">
        <IconDeadline size={14} />
        {c.protocol} · {c.reportCount} {c.reportCount === 1 ? 'denúncia' : 'denúncias'} · prazo {formatWhen(c.dueAt)}
        {c.overdue ? ' · atrasado' : ''}
      </span>
    </>
  );
}

function CaseDetail({ id }: { id: number }) {
  const uid = useId();
  const q = useQuery({ queryKey: caseKey(id), queryFn: () => api.moderation.get(id) });
  if (q.isPending) return <p className="text-muted">Carregando o caso…</p>;
  if (q.isError) {
    return (
      <Notice tone="error">
        <p>Não foi possível carregar o caso.</p>
      </Notice>
    );
  }
  const d = q.data;
  const s = d.summary;
  return (
    <article aria-labelledby={`${uid}-titulo`} className="fe-card grid gap-5 p-5" data-testid="caso">
      <header className="grid gap-1">
        <h2 id={`${uid}-titulo`} className="text-2xl font-bold">
          {s.protocol} · {s.topReason.label}
        </h2>
        <p className="text-sm text-muted">
          {s.status.label} · prioridade {s.priority.label} · prazo {formatWhen(s.dueAt)}
        </p>
        <p>
          {s.target.typeLabel} · situação: <strong>{d.currentState}</strong>
          {s.target.path && (
            <>
              {' · '}
              <Link href={s.target.path} className="underline">
                Abrir no app
              </Link>
            </>
          )}
        </p>
      </header>

      <Block title="Conteúdo guardado na denúncia">
        <dl className="grid gap-1">
          {Object.entries(d.snapshot).map(([k, v]) => (
            <div key={k} className="grid gap-0.5">
              <dt className="text-sm text-muted">{k}</dt>
              <dd className="whitespace-pre-line break-words">{String(v)}</dd>
            </div>
          ))}
        </dl>
      </Block>

      <Block title={`Denúncias (${d.reports.length})`}>
        <ul className="grid gap-2">
          {d.reports.map((r) => (
            <li key={r.id} className="grid gap-0.5 border-l-4 border-line-strong pl-3">
              <span className="font-semibold">
                {r.reason.label} · {r.source.label}
              </span>
              <span className="text-sm">
                {r.reporter} · {formatWhen(r.createdAt)} · {r.protocol}
                {r.lowTrust ? ' · denunciante com muitas improcedentes (prioridade reduzida)' : ''}
                {r.childSexualAbuse ? ' · indício de abuso sexual' : ''}
              </span>
              {r.details && <span className="whitespace-pre-line">{r.details}</span>}
            </li>
          ))}
        </ul>
      </Block>

      {d.signals.length > 0 && (
        <Block title="Sinais automáticos">
          <ul className="grid list-disc gap-1 pl-5">
            {d.signals.map((g) => (
              <li key={g.code}>{g.label}</li>
            ))}
          </ul>
        </Block>
      )}

      <Block title="Histórico de decisões">
        {d.decisions.length === 0 ? (
          <p className="text-muted">Nenhuma decisão ainda.</p>
        ) : (
          <ol className="grid gap-2" data-testid="historico">
            {d.decisions.map((x) => (
              <li key={x.id} className="grid gap-0.5">
                <span className="font-semibold">
                  {x.stage === 'PREVENTIVE' ? 'Preventiva' : x.stage === 'APPEAL' ? 'Recurso' : 'Decisão'} · {x.action.label}
                  {x.policy ? ` · ${x.policy.label}` : ''}
                </span>
                <span className="text-sm text-muted">
                  {formatWhen(x.createdAt)} · {x.automated ? 'automática' : `moderação #${x.moderatorId}`}
                </span>
                <span>{x.statement}</span>
              </li>
            ))}
          </ol>
        )}
        {d.appeals.length > 0 && (
          <ul className="mt-2 grid gap-1">
            {d.appeals.map((a) => (
              <li key={a.id}>
                <span className="font-semibold">Recurso {a.role === 'AUTHOR' ? 'do autor' : 'de quem denunciou'}</span> ({a.status === 'OPEN' ? 'aberto' : a.status === 'REVERSED' ? 'revertido' : 'mantido'}): {a.reason}
              </li>
            ))}
          </ul>
        )}
      </Block>

      {!d.viewerCanDecide && d.viewerBlockedReason && (
        <Notice>
          <p>{d.viewerBlockedReason}</p>
        </Notice>
      )}
      {d.viewerCanDecide && s.status.code === 'OPEN' && <DecisionForm detail={d} />}
      {d.viewerCanDecide && s.status.code === 'IN_APPEAL' && <DecisionForm detail={d} appeal />}
      {s.authorityReferral !== 'NONE' && <AuthorityForm detail={d} />}
      {d.establishmentId != null && <AlertForm placeId={d.establishmentId} />}
    </article>
  );
}

function Block({ title, children }: { title: string; children: ReactNode }) {
  const uid = useId();
  return (
    <section aria-labelledby={uid} className="grid gap-2">
      <h3 id={uid} className="font-bold">
        {title}
      </h3>
      {children}
    </section>
  );
}

function DecisionForm({ detail, appeal = false }: { detail: CaseDetailView; appeal?: boolean }) {
  const uid = useId();
  const qc = useQueryClient();
  const reasons = useReasons();
  const [outcome, setOutcome] = useState<'MAINTAINED' | 'REVERSED'>('MAINTAINED');
  const [action, setAction] = useState<ModerationActionCode>('KEEP');
  const [policy, setPolicy] = useState<string>(detail.summary.topReason.code);
  const [statement, setStatement] = useState('');
  const [message, setMessage] = useState<{ tone: 'success' | 'error'; text: string } | null>(null);
  const [msgRef, focusMsg] = useDeferredFocus<HTMLDivElement>();
  const id = detail.summary.id;
  const needsPolicy = appeal ? outcome === 'REVERSED' && action !== 'KEEP' : action !== 'KEEP';

  const send = useMutation({
    mutationFn: () =>
      appeal
        ? api.moderation.decideAppeal(id, {
            outcome,
            action: outcome === 'REVERSED' ? action : null,
            policyReason: needsPolicy ? (policy as ReportReasonCode) : null,
            statement: statement.trim(),
          })
        : api.moderation.decide(id, { action, policyReason: needsPolicy ? (policy as ReportReasonCode) : null, statement: statement.trim() }),
    onSuccess: (view) => {
      qc.setQueryData(caseKey(id), view);
      void qc.invalidateQueries({ queryKey: ['moderacao', 'casos'] });
      setMessage({ tone: 'success', text: appeal ? 'Recurso decidido. Quem recorreu foi avisado.' : 'Decisão registrada. O autor e quem denunciou foram avisados.' });
      focusMsg();
    },
    onError: (e) => {
      setMessage({ tone: 'error', text: isApiError(e) ? (e.errors.join(' ') || e.detail) : 'Não foi possível registrar agora.' });
      focusMsg();
    },
  });

  function submit(e: FormEvent) {
    e.preventDefault();
    if (statement.trim().length < 20) {
      setMessage({ tone: 'error', text: 'Escreva a fundamentação em pelo menos 20 caracteres: ela vai para o autor.' });
      focusMsg();
      return;
    }
    send.mutate();
  }

  const actions = detail.allowedActions.map((a) => ({ value: a.code as ModerationActionCode, label: a.label }));
  return (
    <form noValidate onSubmit={submit} className="grid gap-4 rounded-md border-2 border-line-strong p-4" aria-labelledby={`${uid}-titulo`}>
      <h3 id={`${uid}-titulo`} className="text-lg font-bold">
        {appeal ? 'Decidir o recurso' : 'Decidir'}
      </h3>
      {appeal && (
        <RadioPills
          name={`${uid}-resultado`}
          legend="Resultado"
          value={outcome}
          onChange={setOutcome}
          options={[
            { value: 'MAINTAINED', label: 'Manter a decisão' },
            { value: 'REVERSED', label: 'Reverter' },
          ]}
        />
      )}
      {(!appeal || outcome === 'REVERSED') && (
        <RadioPills name={`${uid}-acao`} legend={appeal ? 'Nova medida' : 'Medida'} value={action} onChange={setAction} options={actions} />
      )}
      {needsPolicy && reasons.data && (
        <div className="grid gap-1">
          <label htmlFor={`${uid}-regra`} className="font-semibold">
            Regra da política violada
          </label>
          <select id={`${uid}-regra`} className="fe-input" value={policy} onChange={(e) => setPolicy(e.target.value)}>
            {reasons.data.map((r) => (
              <option key={r.code} value={r.code}>
                {r.label}
              </option>
            ))}
          </select>
        </div>
      )}
      <div className="grid gap-1">
        <label htmlFor={`${uid}-motivo`} className="font-semibold">
          Fundamentação <span className="font-normal text-muted">(vai para o autor; de 20 a 2.000 caracteres)</span>
        </label>
        <textarea id={`${uid}-motivo`} className="fe-input min-h-24" value={statement} maxLength={2000} onChange={(e) => setStatement(e.target.value)} />
      </div>
      <div ref={msgRef} tabIndex={-1} className="outline-none">
        {message && (
          <Notice tone={message.tone} live>
            <p>{message.text}</p>
          </Notice>
        )}
      </div>
      <div>
        <button type="submit" className="fe-btn fe-btn-primary" disabled={send.isPending}>
          {send.isPending ? 'Registrando…' : appeal ? 'Registrar resultado do recurso' : 'Registrar decisão'}
        </button>
      </div>
    </form>
  );
}

function AuthorityForm({ detail }: { detail: CaseDetailView }) {
  const uid = useId();
  const qc = useQueryClient();
  const [reference, setReference] = useState(detail.authorityReference ?? '');
  const [message, setMessage] = useState<string | null>(null);
  const send = useMutation({
    mutationFn: () => api.moderation.authorityReferral(detail.summary.id, reference.trim()),
    onSuccess: (view) => {
      qc.setQueryData(caseKey(detail.summary.id), view);
      setMessage('Comunicação registrada.');
    },
    onError: (e) => setMessage(isApiError(e) ? e.detail : 'Não foi possível registrar agora.'),
  });
  if (detail.summary.authorityReferral === 'SENT') {
    return (
      <Notice tone="success">
        <p>
          Comunicado às autoridades em {formatWhen(detail.authoritySentAt)} · referência {detail.authorityReference}.
        </p>
      </Notice>
    );
  }
  return (
    <form
      noValidate
      onSubmit={(e) => {
        e.preventDefault();
        send.mutate();
      }}
      className="grid gap-2 rounded-md border-2 border-error p-4"
      aria-labelledby={`${uid}-titulo`}
    >
      <h3 id={`${uid}-titulo`} className="font-bold">
        Comunicação às autoridades pendente
      </h3>
      <p className="text-sm">
        Indício de abuso sexual infantil: comunique pelo canal oficial (Polícia Federal, ⚖️ fluxo a confirmar) e registre aqui o protocolo. Nada é enviado
        automaticamente; o conteúdo e os metadados ficam preservados.
      </p>
      <label htmlFor={`${uid}-ref`} className="font-semibold">
        Protocolo ou referência
      </label>
      <input id={`${uid}-ref`} className="fe-input" value={reference} onChange={(e) => setReference(e.target.value)} />
      {message && <p role="status">{message}</p>}
      <div>
        <button type="submit" className="fe-btn fe-btn-sm" disabled={send.isPending}>
          Registrar comunicação
        </button>
      </div>
    </form>
  );
}

function AlertForm({ placeId }: { placeId: number }) {
  const uid = useId();
  const qc = useQueryClient();
  const integrity = useQuery({ queryKey: ['lugar', String(placeId), 'integridade'], queryFn: () => api.reports.integrity(placeId), retry: false });
  const [kind, setKind] = useState<AlertRequest['kind']>('ATIVIDADE_SUSPEITA');
  const [days, setDays] = useState('7');
  const [message, setMessage] = useState<string | null>(null);
  const refresh = () => void qc.invalidateQueries({ queryKey: ['lugar', String(placeId), 'integridade'] });
  const create = useMutation({
    mutationFn: () => api.moderation.createAlert(placeId, { kind, freezeDays: Number(days) || 0 }),
    onSuccess: () => {
      refresh();
      setMessage('Alerta publicado na página do lugar.');
    },
    onError: (e) => setMessage(isApiError(e) ? (e.errors.join(' ') || e.detail) : 'Não foi possível publicar agora.'),
  });
  const lift = useMutation({
    mutationFn: (alertId: number) => api.moderation.liftAlert(placeId, alertId),
    onSuccess: () => {
      refresh();
      setMessage('Alerta encerrado.');
    },
    onError: (e) => setMessage(isApiError(e) ? e.detail : 'Não foi possível encerrar agora.'),
  });
  return (
    <section aria-labelledby={`${uid}-titulo`} className="grid gap-3 rounded-md border border-line p-4">
      <h3 id={`${uid}-titulo`} className="flex items-center gap-2 font-bold">
        <IconConsumerAlert size={18} />
        Alertas e congelamento do lugar
      </h3>
      {integrity.data?.active.length ? (
        <ul className="grid gap-2">
          {integrity.data.active.map((a) => (
            <li key={a.id} className="flex flex-wrap items-center gap-2">
              <span>
                {a.kind.label}
                {a.freezeReviews && a.endsAt ? ` · congelado até ${formatWhen(a.endsAt)}` : ''}
              </span>
              <button type="button" className="fe-btn fe-btn-sm" onClick={() => lift.mutate(a.id)} disabled={lift.isPending}>
                Encerrar<span className="sr-only"> o alerta {a.kind.label}</span>
              </button>
            </li>
          ))}
        </ul>
      ) : (
        <p className="text-muted">Nenhum alerta ativo.</p>
      )}
      <form
        noValidate
        className="grid gap-3"
        onSubmit={(e) => {
          e.preventDefault();
          create.mutate();
        }}
      >
        <RadioPills
          name={`${uid}-tipo`}
          legend="Novo alerta"
          hint="Texto factual, baseado em prova guardada (risco de dano à imagem da empresa)."
          value={kind}
          onChange={setKind}
          options={[
            { value: 'AVALIACAO_PAGA', label: 'Avaliações pagas' },
            { value: 'ATIVIDADE_SUSPEITA', label: 'Atividade fora do padrão' },
            { value: 'ATENCAO_PUBLICA', label: 'Atenção pública' },
          ]}
        />
        <div className="grid max-w-xs gap-1">
          <label htmlFor={`${uid}-dias`} className="font-semibold">
            Congelar novas avaliações por (dias, 0 a 30)
          </label>
          <input id={`${uid}-dias`} className="fe-input" inputMode="numeric" value={days} onChange={(e) => setDays(e.target.value.replace(/\D/g, ''))} />
        </div>
        {message && <p role="status">{message}</p>}
        <div>
          <button type="submit" className="fe-btn fe-btn-sm" disabled={create.isPending}>
            Publicar alerta
          </button>
        </div>
      </form>
    </section>
  );
}

function Stats() {
  const uid = useId();
  const q = useQuery({ queryKey: ['moderacao', 'relatorio'], queryFn: () => api.moderation.stats(30) });
  if (q.isPending) return <p className="text-muted">Carregando o relatório…</p>;
  if (q.isError) {
    return (
      <Notice tone="error">
        <p>Não foi possível carregar o relatório.</p>
      </Notice>
    );
  }
  const s = q.data;
  const table = (title: string, rows: Array<{ code: string; label: string; count: number }>) => (
    <section aria-labelledby={`${uid}-${title}`} className="fe-card grid gap-2 p-4">
      <h3 id={`${uid}-${title}`} className="font-bold">
        {title}
      </h3>
      {rows.length === 0 ? (
        <p className="text-muted">Nada no período.</p>
      ) : (
        <table className="w-full text-left">
          <thead>
            <tr>
              <th scope="col">Item</th>
              <th scope="col" className="text-right">
                Quantidade
              </th>
            </tr>
          </thead>
          <tbody>
            {rows.map((r) => (
              <tr key={r.code}>
                <td>{r.label}</td>
                <td className="text-right">{r.count.toLocaleString('pt-BR')}</td>
              </tr>
            ))}
          </tbody>
        </table>
      )}
    </section>
  );
  return (
    <div className="grid gap-4" data-testid="relatorio">
      <p>
        Últimos 30 dias ({formatWhen(s.from)} a {formatWhen(s.to)}). Na fila: <strong>{s.openCases}</strong> · em recurso: <strong>{s.inAppeal}</strong> ·
        atrasados: <strong>{s.overdue}</strong>.
      </p>
      <div className="grid gap-4 md:grid-cols-2">
        {table('Denúncias por motivo', s.reportsByReason)}
        {table('Denúncias por origem', s.reportsBySource)}
        {table('Decisões', s.decisionsByAction)}
        {table('Regras aplicadas', s.decisionsByPolicy)}
        {table('Recursos', s.appeals)}
        {table('Comunicações às autoridades', s.authorityReferrals)}
      </div>
    </div>
  );
}
