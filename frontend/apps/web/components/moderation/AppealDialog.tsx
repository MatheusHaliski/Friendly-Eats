'use client';

import { isApiError } from '@friendly-eats/api';
import { useMutation, useQueryClient } from '@tanstack/react-query';
import { useId, useState, type FormEvent } from 'react';
import { Dialog } from '@/components/Dialog';
import { Notice, useDeferredFocus } from '@/components/Form';
import { IconAppeal } from '@/components/icons';
import { api } from '@/lib/api';
import { MY_DECISIONS_KEY, MY_REPORTS_KEY } from '@/lib/moderation';

const MIN = 20;
const MAX = 2000;

/** "Contestar decisão" (ECA Digital, art. 30): outra pessoa da moderação revê. */
export function AppealDialog({ caseId, protocol, open, onClose, onDone }: { caseId: number; protocol: string; open: boolean; onClose: () => void; onDone: (msg: string) => void }) {
  const uid = useId();
  const qc = useQueryClient();
  const [reason, setReason] = useState('');
  const [error, setError] = useState<string | null>(null);
  const [errRef, focusErr] = useDeferredFocus<HTMLDivElement>();
  const send = useMutation({
    mutationFn: () => api.reports.appeal(caseId, reason.trim()),
    onSuccess: () => {
      void qc.invalidateQueries({ queryKey: MY_DECISIONS_KEY });
      void qc.invalidateQueries({ queryKey: MY_REPORTS_KEY });
      setReason('');
      onDone(`Recurso registrado no caso ${protocol}. Outra pessoa da moderação vai rever a decisão.`);
    },
    onError: (e) => {
      setError(isApiError(e) ? (e.errors[0] ?? e.detail) : 'Não foi possível enviar agora.');
      focusErr();
    },
  });
  function submit(e: FormEvent) {
    e.preventDefault();
    if (reason.trim().length < MIN) {
      setError(`Explique o recurso em pelo menos ${MIN} caracteres.`);
      focusErr();
      return;
    }
    setError(null);
    send.mutate();
  }
  return (
    <Dialog open={open} onClose={onClose} labelledBy={`${uid}-titulo`} describedBy={`${uid}-desc`}>
      <form noValidate onSubmit={submit} className="grid gap-4" aria-labelledby={`${uid}-titulo`}>
        <h2 id={`${uid}-titulo`} className="flex items-center gap-2 text-xl font-bold">
          <IconAppeal size={20} />
          Contestar decisão · {protocol}
        </h2>
        <p id={`${uid}-desc`} className="text-muted">
          Conte por que a decisão deve mudar. Uma pessoa da moderação diferente de quem decidiu revê e responde com o motivo.
        </p>
        <div ref={errRef} tabIndex={-1} className="outline-none">
          {error && (
            <Notice tone="error" live>
              <p>{error}</p>
            </Notice>
          )}
        </div>
        <div className="grid gap-1">
          <label htmlFor={`${uid}-texto`} className="font-semibold">
            Seu recurso <span className="font-normal text-muted">(de {MIN} a {MAX} caracteres)</span>
          </label>
          <textarea id={`${uid}-texto`} className="fe-input min-h-28" value={reason} maxLength={MAX} onChange={(e) => setReason(e.target.value)} />
        </div>
        <div className="flex flex-wrap gap-2">
          <button type="submit" className="fe-btn fe-btn-primary" disabled={send.isPending}>
            {send.isPending ? 'Enviando…' : 'Enviar recurso'}
          </button>
          <button type="button" className="fe-btn" onClick={onClose}>
            Cancelar
          </button>
        </div>
      </form>
    </Dialog>
  );
}
