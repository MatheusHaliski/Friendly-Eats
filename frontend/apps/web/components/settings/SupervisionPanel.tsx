'use client';

import { isApiError, type ProtectionCode, type SupervisionView } from '@friendly-eats/api';
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import Link from 'next/link';
import { useRouter } from 'next/navigation';
import { useId, useState } from 'react';
import { describedBy, Notice, useDeferredFocus } from '@/components/Form';
import { IconGuardian, IconLocked, IconPrev } from '@/components/icons';
import { Switch } from '@/components/Switch';
import { api } from '@/lib/api';
import { formatDate } from '@/lib/format';
import { SUPERVISED_KEY, supervisionKey } from '@/lib/queries';
import { GuardianHistory, guardianHistory, REQUEST_STATUS } from './AgeSection';
import { ResultNotice, type Result } from './PreferenceControls';
import { SectionHeading } from './SectionHeading';

/**
 * Painel de supervisão do responsável (RF17; ECA Digital, art. 18): as proteções do menor, os pedidos para aprovar ou
 * negar, o consentimento dos estilos alimentares (LGPD, art. 14) e o histórico.
 */
export function SupervisionPanel({ minorId }: { minorId: string }) {
  const panel = useQuery({ queryKey: supervisionKey(minorId), queryFn: () => api.guardian.panel(minorId) });
  if (panel.isPending) return <p className="text-muted">Carregando…</p>;
  if (panel.isError) {
    return (
      <>
        <SectionHeading>Painel de supervisão</SectionHeading>
        <Notice tone="error">
          <p>{isApiError(panel.error, 404) ? 'Você não supervisiona esta conta.' : 'Não foi possível carregar o painel. Recarregue a página.'}</p>
        </Notice>
        <BackLink />
      </>
    );
  }
  return <Panel minorId={minorId} view={panel.data} />;
}

function BackLink() {
  return (
    <Link href="/configuracoes/idade" className="inline-flex min-h-11 w-fit items-center gap-1 font-semibold underline">
      <IconPrev size={18} />
      Idade e responsável
    </Link>
  );
}

function Panel({ minorId, view }: { minorId: string; view: SupervisionView }) {
  const uid = useId();
  const queryClient = useQueryClient();
  const router = useRouter();
  const [result, setResult] = useState<Result>(null);
  const [ref, focus] = useDeferredFocus<HTMLDivElement>();
  const [busy, setBusy] = useState<string | null>(null);
  const run = useMutation({
    mutationFn: async ({ key, call }: { key: string; call: () => Promise<SupervisionView | void>; text: string }) => {
      setBusy(key);
      return call();
    },
    onSuccess: (v, { text }) => {
      if (v) queryClient.setQueryData(supervisionKey(minorId), v);
      void queryClient.invalidateQueries({ queryKey: SUPERVISED_KEY });
      setResult({ tone: 'success', text });
      focus();
    },
    onError: (e) => {
      setResult({ tone: 'error', text: isApiError(e) ? e.detail : 'Não foi possível concluir agora. Tente de novo.' });
      focus();
    },
    onSettled: () => setBusy(null),
  });
  const m = view.minor;
  const pending = view.requests.filter((r) => r.status === 'PENDING');
  const decided = view.requests.filter((r) => r.status !== 'PENDING');

  const setProtection = (code: ProtectionCode, label: string, allowed: boolean) =>
    run.mutate({ key: code, call: () => api.guardian.setProtection(minorId, code, allowed), text: `${label}: ${allowed ? 'liberado' : 'proteção religada'}.` });

  return (
    <>
      <SectionHeading description={`${m.name} (@${m.handle}), ${m.age} anos · vinculado em ${formatDate(m.linkedAt)}`}>Painel de supervisão</SectionHeading>
      <BackLink />
      <ResultNotice result={result} innerRef={ref} />

      <section aria-labelledby={`${uid}-pedidos`} className="fe-card grid gap-3 p-5" data-testid="pedidos">
        <h2 id={`${uid}-pedidos`} className="text-xl font-bold">
          Pedidos para afrouxar proteções
        </h2>
        {pending.length === 0 && <p className="text-muted">Nenhum pedido pendente.</p>}
        <ul className="grid gap-2">
          {pending.map((r) => (
            <li key={r.id} className="flex flex-wrap items-center justify-between gap-2 rounded-md border-2 border-line p-3">
              <span className="grid">
                <span className="font-semibold">{r.label}</span>
                <span className="text-sm text-muted">Pedido em {formatDate(r.createdAt)}</span>
              </span>
              <span className="flex gap-2">
                <button
                  type="button"
                  className="fe-btn fe-btn-sm fe-btn-primary"
                  disabled={busy !== null}
                  onClick={() => run.mutate({ key: `r${r.id}`, call: () => api.guardian.decide(minorId, r.id, true), text: `Pedido aprovado: ${r.label}.` })}
                >
                  Aprovar<span className="sr-only">: {r.label}</span>
                </button>
                <button
                  type="button"
                  className="fe-btn fe-btn-sm"
                  disabled={busy !== null}
                  onClick={() => run.mutate({ key: `r${r.id}`, call: () => api.guardian.decide(minorId, r.id, false), text: `Pedido negado: ${r.label}.` })}
                >
                  Negar<span className="sr-only">: {r.label}</span>
                </button>
              </span>
            </li>
          ))}
        </ul>
        {decided.length > 0 && (
          <details>
            <summary className="cursor-pointer font-semibold">Pedidos anteriores</summary>
            <ul className="mt-2 grid gap-1 text-sm">
              {decided.map((r) => (
                <li key={r.id}>
                  {r.label}: {REQUEST_STATUS[r.status]}
                  {r.decidedAt ? ` em ${formatDate(r.decidedAt)}` : ''}
                </li>
              ))}
            </ul>
          </details>
        )}
      </section>

      <section aria-labelledby={`${uid}-protecoes`} className="fe-card grid gap-3 p-5">
        <h2 id={`${uid}-protecoes`} className="text-xl font-bold">
          Proteções da conta
        </h2>
        <p className="text-muted">
          {view.protectionsLocked
            ? 'Para menores de 16 anos, só você pode desligar cada proteção. Ligada = mais protegido.'
            : 'A partir dos 16 anos o próprio adolescente controla estas configurações.'}
        </p>
        <ul className="divide-y divide-line">
          {view.protections.map((p) => (
            <li key={p.code} className="py-1">
              <SwitchRow
                id={`${uid}-${p.code}`}
                title={p.label}
                description={p.description}
                checked={!p.allowed}
                busy={busy === p.code}
                disabled={!view.protectionsLocked || (busy !== null && busy !== p.code)}
                onChange={(on) => setProtection(p.code, p.label, !on)}
              />
            </li>
          ))}
        </ul>
      </section>

      <section aria-labelledby={`${uid}-sensivel`} className="fe-card grid gap-3 p-5">
        <h2 id={`${uid}-sensivel`} className="text-xl font-bold">
          Estilos alimentares no perfil
        </h2>
        <p className="text-muted">
          Estilos como sem glúten, halal ou kosher podem revelar saúde ou religião (dado sensível). Para quem tem menos de 18 anos, guardá-los exige o
          seu consentimento (LGPD, art. 14). Revogar apaga os estilos salvos.
        </p>
        {view.sensitiveConsentAvailable ? (
          <SwitchRow
            id={`${uid}-consentimento`}
            title="Consinto que guarde os estilos alimentares"
            checked={view.sensitiveConsent}
            busy={busy === 'sensivel'}
            disabled={busy !== null && busy !== 'sensivel'}
            onChange={(on) =>
              run.mutate({
                key: 'sensivel',
                call: () => api.guardian.setSensitiveConsent(minorId, on),
                text: on ? 'Consentimento registrado.' : 'Consentimento revogado; os estilos alimentares salvos foram apagados.',
              })
            }
          />
        ) : (
          <p className="flex items-center gap-1.5 text-sm">
            <IconLocked size={16} />A conta já é de maior de idade: o consentimento é do titular.
          </p>
        )}
      </section>

      <section aria-labelledby={`${uid}-historico`} className="fe-card grid gap-3 p-5">
        <h2 id={`${uid}-historico`} className="text-xl font-bold">
          Histórico
        </h2>
        <GuardianHistory empty="Nada por enquanto." items={guardianHistory(view.history)} />
      </section>

      <section aria-labelledby={`${uid}-desfazer`} className="fe-card grid gap-3 p-5">
        <h2 id={`${uid}-desfazer`} className="flex items-center gap-2 text-xl font-bold">
          <IconGuardian size={22} />
          Desfazer o vínculo
        </h2>
        <p className="text-muted">Sem o vínculo, as proteções voltam ao máximo e o consentimento dos estilos alimentares é revogado.</p>
        <div>
          <button
            type="button"
            className="fe-btn"
            disabled={busy !== null}
            onClick={() =>
              run.mutate(
                { key: 'desfazer', call: () => api.guardian.unlinkMinor(minorId), text: 'Vínculo desfeito.' },
                { onSuccess: () => router.push('/configuracoes/idade') },
              )
            }
          >
            Desfazer o vínculo
          </button>
        </div>
      </section>
    </>
  );
}

/** Linha com título, descrição e o interruptor (o nome do interruptor é o título). */
function SwitchRow({
  id,
  title,
  description,
  checked,
  busy,
  disabled,
  onChange,
}: {
  id: string;
  title: string;
  description?: string;
  checked: boolean;
  busy?: boolean;
  disabled?: boolean;
  onChange: (on: boolean) => void;
}) {
  return (
    <div className="flex flex-wrap items-start justify-between gap-x-4 gap-y-2 py-3">
      <div className="grid min-w-0 flex-1 basis-56 gap-1">
        <p id={`${id}-titulo`} className="font-bold">
          {title}
        </p>
        {description && (
          <p id={`${id}-desc`} className="text-sm text-muted">
            {description}
          </p>
        )}
      </div>
      <Switch
        id={id}
        checked={checked}
        onChange={onChange}
        labelledBy={`${id}-titulo`}
        describedBy={describedBy(description && `${id}-desc`)}
        disabled={disabled}
        busy={busy}
      />
    </div>
  );
}
