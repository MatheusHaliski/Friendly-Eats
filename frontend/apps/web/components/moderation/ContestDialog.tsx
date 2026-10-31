'use client';

import { isApiError, type ReceiptView, type ReportReasonCode } from '@friendly-eats/api';
import { useMutation } from '@tanstack/react-query';
import { useId, useState, type FormEvent } from 'react';
import { Dialog } from '@/components/Dialog';
import { Notice, useDeferredFocus } from '@/components/Form';
import { IconAppeal } from '@/components/icons';
import { api } from '@/lib/api';
import { formatWhen, reasonsFor, useReasons } from '@/lib/moderation';
import { ReasonFields } from './ReasonFields';

const MIN = 20;
const MAX = 1000;

/**
 * Contestação de avaliação pelo dono do lugar (RC-EST-14 · RC-SEG-08): só pelos motivos da política, com explicação.
 * A avaliação fica no ar até a decisão e o dono não recebe dados de quem avaliou.
 */
export function ContestDialog({
  open,
  onClose,
  placeId,
  reviewId,
  author,
}: {
  open: boolean;
  onClose: () => void;
  placeId: string;
  reviewId: number;
  author: string;
}) {
  const uid = useId();
  const reasons = useReasons();
  const [reason, setReason] = useState<ReportReasonCode | null>(null);
  const [details, setDetails] = useState('');
  const [errors, setErrors] = useState<string[]>([]);
  const [receipt, setReceipt] = useState<ReceiptView | null>(null);
  const [resultRef, focusResult] = useDeferredFocus<HTMLDivElement>();

  const send = useMutation({
    mutationFn: () => api.reports.contestReview(placeId, reviewId, { reason: reason!, details: details.trim() }),
    onSuccess: (r) => {
      setReceipt(r);
      setErrors([]);
      focusResult();
    },
    onError: (e) => {
      setErrors(isApiError(e, 422) && e.errors.length ? e.errors : [isApiError(e) ? e.detail : 'Não foi possível enviar agora.']);
      focusResult();
    },
  });

  function close() {
    setReason(null);
    setDetails('');
    setErrors([]);
    setReceipt(null);
    onClose();
  }

  function submit(e: FormEvent) {
    e.preventDefault();
    const problems: string[] = [];
    if (!reason) problems.push('Escolha um motivo da política.');
    if (details.trim().length < MIN) problems.push(`Explique a contestação em pelo menos ${MIN} caracteres.`);
    if (problems.length) {
      setErrors(problems);
      focusResult();
      return;
    }
    send.mutate();
  }

  return (
    <Dialog open={open} onClose={close} labelledBy={`${uid}-titulo`} describedBy={`${uid}-desc`}>
      <div className="grid gap-4">
        <h2 id={`${uid}-titulo`} className="flex items-center gap-2 text-xl font-bold">
          <IconAppeal size={20} />
          Contestar a avaliação de {author}
        </h2>
        {receipt ? (
          <>
            <div ref={resultRef} tabIndex={-1} className="outline-none" id={`${uid}-desc`}>
              <Notice tone="success" live>
                <p className="font-semibold">Protocolo {receipt.protocol}</p>
                <p>A avaliação continua no ar até a decisão, prevista até {formatWhen(receipt.reviewBy)}.</p>
              </Notice>
            </div>
            <p>
              <button type="button" className="fe-btn fe-btn-primary" onClick={close}>
                Fechar
              </button>
            </p>
          </>
        ) : (
          <form noValidate onSubmit={submit} className="grid gap-4" aria-labelledby={`${uid}-titulo`}>
            <div id={`${uid}-desc`} className="grid gap-1 text-muted">
              <p>Discordar da nota não é motivo de contestação. Quando a divergência é só sobre o que aconteceu, a avaliação fica.</p>
              <p>A avaliação continua no ar até a decisão. Você não recebe dados de quem avaliou, e ameaça ou exposição de quem avaliou é removida.</p>
            </div>
            <div ref={resultRef} tabIndex={-1} className="outline-none">
              {errors.length > 0 && (
                <Notice tone="error" live>
                  {errors.map((m) => (
                    <p key={m}>{m}</p>
                  ))}
                </Notice>
              )}
            </div>
            {reasons.data && (
              <ReasonFields name={`${uid}-motivo`} legend="Motivo da política" reasons={reasonsFor(reasons.data, 'REVIEW', true)} value={reason} onChange={setReason} />
            )}
            <div className="grid gap-1">
              <label htmlFor={`${uid}-texto`} className="font-semibold">
                Explique <span className="font-normal text-muted">(obrigatório, de {MIN} a {MAX} caracteres)</span>
              </label>
              <textarea id={`${uid}-texto`} className="fe-input min-h-24" value={details} maxLength={MAX} onChange={(e) => setDetails(e.target.value)} />
            </div>
            <div className="flex flex-wrap gap-2">
              <button type="submit" className="fe-btn fe-btn-primary" disabled={send.isPending || !reasons.data}>
                <IconAppeal size={18} />
                {send.isPending ? 'Enviando…' : 'Enviar contestação'}
              </button>
              <button type="button" className="fe-btn" onClick={close}>
                Cancelar
              </button>
            </div>
          </form>
        )}
      </div>
    </Dialog>
  );
}
