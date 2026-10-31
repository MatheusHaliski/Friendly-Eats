'use client';

import type { IntegrityView } from '@friendly-eats/api';
import { useId } from 'react';
import { IconConsumerAlert, IconFrozen } from '@/components/icons';
import { formatWhen, useIntegrity } from '@/lib/moderation';

/**
 * Alertas ao consumidor e congelamento de novas avaliações na página do lugar (RF13 · RC-SEG-05): texto factual com
 * ícone e título escritos (a cor nunca é o único sinal), o histórico e "Como verificamos as avaliações".
 */
export function IntegrityBanner({ placeId }: { placeId: string }) {
  const integrity = useIntegrity(placeId);
  if (!integrity.data) return null;
  return <IntegrityContent data={integrity.data} />;
}

function IntegrityContent({ data }: { data: IntegrityView }) {
  const uid = useId();
  const past = data.history.filter((a) => !a.active);
  return (
    <section aria-labelledby={`${uid}-titulo`} className="grid gap-3" data-testid="integridade">
      <h2 id={`${uid}-titulo`} className="sr-only">
        Avisos sobre as avaliações deste lugar
      </h2>
      {data.active.map((a) => (
        <div key={a.id} className="flex items-start gap-3 rounded-md border-2 border-error p-4" role="note" data-testid="alerta-consumidor">
          <IconConsumerAlert size={24} className="mt-0.5 flex-none text-error" />
          <div className="grid gap-1">
            <p className="font-bold">Alerta ao consumidor: {a.kind.label}</p>
            <p>{a.message}</p>
            <p className="text-sm text-muted">Desde {formatWhen(a.startsAt)}</p>
          </div>
        </div>
      ))}
      {data.frozen && data.frozenUntil && (
        <div className="flex items-start gap-3 rounded-md border-2 border-line-strong p-4" role="note" data-testid="aviso-congelamento">
          <IconFrozen size={24} className="mt-0.5 flex-none" />
          <div className="grid gap-1">
            <p className="font-bold">Novas avaliações pausadas até {formatWhen(data.frozenUntil)}</p>
            <p>Quem já avaliou pode editar a própria avaliação. A nota mostrada não conta as avaliações em análise.</p>
          </div>
        </div>
      )}
      <details className="fe-card p-4">
        <summary className="cursor-pointer font-semibold">Como verificamos as avaliações</summary>
        <p className="mt-2">{data.howWeVerify}</p>
        {past.length > 0 && (
          <>
            <h3 className="mt-3 font-semibold">Histórico de alertas</h3>
            <ul className="mt-1 grid list-disc gap-1 pl-5 text-sm">
              {past.map((a) => (
                <li key={a.id}>
                  {a.kind.label}: de {formatWhen(a.startsAt)} a {formatWhen(a.liftedAt ?? a.endsAt)}
                </li>
              ))}
            </ul>
          </>
        )}
      </details>
    </section>
  );
}
