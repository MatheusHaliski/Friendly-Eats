'use client';

import type { MyDecisionView, MyReportView } from '@friendly-eats/api';
import { useQuery } from '@tanstack/react-query';
import Link from 'next/link';
import { useId, useState } from 'react';
import { Notice } from '@/components/Form';
import { IconAppeal, IconMyReports } from '@/components/icons';
import { SectionHeading } from '@/components/settings/SectionHeading';
import { api } from '@/lib/api';
import { formatWhen, MY_DECISIONS_KEY, MY_REPORTS_KEY } from '@/lib/moderation';
import { AppealDialog } from './AppealDialog';

/** Configurações → Denúncias e decisões (RF13): "Minhas denúncias" e "Decisões sobre meu conteúdo", com recurso. */
export function MyModerationView() {
  const [status, setStatus] = useState('');
  return (
    <>
      <SectionHeading description="Acompanhe as denúncias que você fez e as decisões da moderação sobre o que você publicou. Você pode recorrer de cada decisão no prazo.">
        Denúncias e decisões
      </SectionHeading>
      <p role="status" className="font-semibold" data-testid="status-recurso">
        {status}
      </p>
      <Decisions onDone={setStatus} />
      <Reports onDone={setStatus} />
      <p className="text-sm">
        Regras, canais de apoio e denúncia sem conta em{' '}
        <Link href="/seguranca" className="font-semibold underline">
          Segurança e denúncias
        </Link>
        .
      </p>
    </>
  );
}

function Decisions({ onDone }: { onDone: (m: string) => void }) {
  const uid = useId();
  const q = useQuery({ queryKey: MY_DECISIONS_KEY, queryFn: () => api.reports.myDecisions() });
  return (
    <section aria-labelledby={`${uid}-titulo`} className="grid gap-3">
      <h2 id={`${uid}-titulo`} className="flex items-center gap-2 text-xl font-bold">
        <IconAppeal size={20} />
        Decisões sobre meu conteúdo
      </h2>
      {q.isPending && <p className="text-muted">Carregando…</p>}
      {q.isError && (
        <Notice tone="error">
          <p>Não foi possível carregar as decisões.</p>
        </Notice>
      )}
      {q.data && q.data.length === 0 && <p className="text-muted">Nenhuma decisão da moderação sobre o que você publicou.</p>}
      {q.data && q.data.length > 0 && (
        <ul className="grid gap-3">
          {q.data.map((d) => (
            <DecisionCard key={d.caseId} d={d} onDone={onDone} />
          ))}
        </ul>
      )}
    </section>
  );
}

function DecisionCard({ d, onDone }: { d: MyDecisionView; onDone: (m: string) => void }) {
  const [open, setOpen] = useState(false);
  return (
    <li className="fe-card grid gap-2 p-4" data-testid="decisao">
      <p className="text-sm text-muted">
        {d.protocol} · {d.target.typeLabel} · {formatWhen(d.decision.createdAt)}
      </p>
      <p className="font-bold">
        {d.decision.stage === 'PREVENTIVE' ? 'Retirado preventivamente' : d.decision.action.label}
        {d.decision.policy ? ` · ${d.decision.policy.label}` : ''}
      </p>
      {d.target.excerpt && <p className="text-sm">&ldquo;{d.target.excerpt}&rdquo;</p>}
      <p>{d.decision.statement}</p>
      <p className="text-sm text-muted">{d.decision.origin}.</p>
      <p className="text-sm">{d.howToAppeal}</p>
      {d.appealDeadline && d.canAppeal && <p className="text-sm">Prazo para recorrer: até {formatWhen(d.appealDeadline)}.</p>}
      {d.appeal && (
        <p className="text-sm font-semibold">
          Recurso: {d.appeal.status === 'OPEN' ? 'em análise' : d.appeal.status === 'REVERSED' ? 'aceito, a decisão foi revertida' : 'negado, a decisão foi mantida'}
        </p>
      )}
      {d.appealResult && <p className="text-sm">Resposta: {d.appealResult.statement}</p>}
      {d.canAppeal && (
        <p>
          <button type="button" className="fe-btn fe-btn-sm" onClick={() => setOpen(true)}>
            <IconAppeal size={16} />
            Contestar decisão
          </button>
        </p>
      )}
      <AppealDialog
        caseId={d.caseId}
        protocol={d.protocol}
        open={open}
        onClose={() => setOpen(false)}
        onDone={(m) => {
          setOpen(false);
          onDone(m);
        }}
      />
    </li>
  );
}

function Reports({ onDone }: { onDone: (m: string) => void }) {
  const uid = useId();
  const q = useQuery({ queryKey: MY_REPORTS_KEY, queryFn: () => api.reports.mine() });
  return (
    <section aria-labelledby={`${uid}-titulo`} className="grid gap-3">
      <h2 id={`${uid}-titulo`} className="flex items-center gap-2 text-xl font-bold">
        <IconMyReports size={20} />
        Minhas denúncias
      </h2>
      {q.isPending && <p className="text-muted">Carregando…</p>}
      {q.isError && (
        <Notice tone="error">
          <p>Não foi possível carregar as denúncias.</p>
        </Notice>
      )}
      {q.data && q.data.length === 0 && <p className="text-muted">Você ainda não fez denúncias.</p>}
      {q.data && q.data.length > 0 && (
        <ul className="grid gap-3">
          {q.data.map((r) => (
            <ReportCard key={r.protocol} r={r} onDone={onDone} />
          ))}
        </ul>
      )}
    </section>
  );
}

function ReportCard({ r, onDone }: { r: MyReportView; onDone: (m: string) => void }) {
  const [open, setOpen] = useState(false);
  return (
    <li className="fe-card grid gap-1 p-4" data-testid="minha-denuncia">
      <p className="font-bold">
        {r.protocol} · {r.statusLabel}
      </p>
      <p className="text-sm">
        {r.target.typeLabel}
        {r.target.excerpt ? `: “${r.target.excerpt}”` : ''} · {r.reason.label}
      </p>
      <p className="text-sm text-muted">Enviada em {formatWhen(r.createdAt)}</p>
      {r.outcomeLabel && <p>{r.outcomeLabel}</p>}
      {r.appealStatus && <p className="text-sm font-semibold">Recurso: {r.appealStatus === 'OPEN' ? 'em análise' : r.appealStatus === 'REVERSED' ? 'aceito' : 'negado'}</p>}
      {r.canAppeal && (
        <>
          <p className="text-sm">Discorda do arquivamento? Recorra até {formatWhen(r.appealDeadline)}.</p>
          <p>
            <button type="button" className="fe-btn fe-btn-sm" onClick={() => setOpen(true)}>
              <IconAppeal size={16} />
              Recorrer do arquivamento
            </button>
          </p>
          <AppealDialog
            caseId={r.caseId}
            protocol={r.protocol}
            open={open}
            onClose={() => setOpen(false)}
            onDone={(m) => {
              setOpen(false);
              onDone(m);
            }}
          />
        </>
      )}
    </li>
  );
}
