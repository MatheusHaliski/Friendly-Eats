'use client';

import { isApiError, type ReceiptView, type ReportReasonCode, type ReportTargetType } from '@friendly-eats/api';
import { useMutation, useQueryClient } from '@tanstack/react-query';
import Link from 'next/link';
import { usePathname } from 'next/navigation';
import { useId, useRef, useState, type FormEvent } from 'react';
import { Dialog } from '@/components/Dialog';
import { FieldErrors, Notice, useDeferredFocus } from '@/components/Form';
import { IconLogin, IconReport } from '@/components/icons';
import { api } from '@/lib/api';
import { formatWhen, MY_REPORTS_KEY, publicReportHref, reasonsFor, TARGET_TEXT, useReasons } from '@/lib/moderation';
import { useSession } from '@/lib/queries';
import { loginHref } from '@/lib/safe-redirect';
import { ReasonFields } from './ReasonFields';

const MAX = 1000;

interface Props {
  open: boolean;
  onClose: () => void;
  targetType: ReportTargetType;
  targetId: string | number;
  /** Ex.: "o comentário de Ana" — entra no título para quem usa leitor de tela. */
  context: string;
}

/**
 * Denunciar (RF13): motivo fechado + texto opcional, recibo com protocolo. Sem conta, o diálogo leva ao login ou ao
 * formulário público (/denunciar). "Criança em risco" pede a marcação de indício de abuso sexual e mostra os canais de
 * emergência.
 */
export function ReportDialog({ open, onClose, targetType, targetId, context }: Props) {
  const uid = useId();
  const session = useSession();
  const pathname = usePathname();
  const reasons = useReasons();
  const qc = useQueryClient();
  const [reason, setReason] = useState<ReportReasonCode | null>(null);
  const [details, setDetails] = useState('');
  const [csa, setCsa] = useState(false);
  const [errors, setErrors] = useState<string[]>([]);
  const [receipt, setReceipt] = useState<ReceiptView | null>(null);
  const [resultRef, focusResult] = useDeferredFocus<HTMLDivElement>();
  const closeRef = useRef<HTMLButtonElement>(null);

  const send = useMutation({
    mutationFn: () =>
      api.reports.create({
        targetType,
        targetId: String(targetId),
        reason: reason!,
        details: details.trim() || null,
        childSexualAbuse: reason === 'CRIANCA_EM_RISCO' ? csa : undefined,
      }),
    onSuccess: (r) => {
      setReceipt(r);
      setErrors([]);
      void qc.invalidateQueries({ queryKey: MY_REPORTS_KEY });
      focusResult();
    },
    onError: (e) => {
      setErrors(isApiError(e, 422) && e.errors.length ? e.errors : [isApiError(e) ? e.detail : 'Não foi possível enviar agora. Tente de novo.']);
      focusResult();
    },
  });

  function close() {
    setReason(null);
    setDetails('');
    setCsa(false);
    setErrors([]);
    setReceipt(null);
    onClose();
  }

  function submit(e: FormEvent) {
    e.preventDefault();
    if (!reason) {
      setErrors(['Escolha um motivo.']);
      focusResult();
      return;
    }
    if (reason === 'OUTRO' && details.trim().length < 10) {
      setErrors(['Em "Outro motivo", conte em poucas palavras o que aconteceu.']);
      focusResult();
      return;
    }
    send.mutate();
  }

  const title = `Denunciar ${context}`;
  return (
    <Dialog open={open} onClose={close} labelledBy={`${uid}-titulo`} describedBy={`${uid}-desc`}>
      <div className="grid gap-4">
        <h2 id={`${uid}-titulo`} className="flex items-center gap-2 text-xl font-bold">
          <IconReport size={20} />
          {title}
        </h2>
        {!session.data ? (
          <>
            <p id={`${uid}-desc`}>
              Para denunciar com acompanhamento em &quot;Minhas denúncias&quot;, entre na sua conta. Sem conta, use o formulário público: ele pede
              nome e e-mail, porque a denúncia não é anônima.
            </p>
            <div className="flex flex-wrap gap-2">
              <Link href={loginHref(pathname)} className="fe-btn fe-btn-primary">
                <IconLogin size={18} />
                Entrar
              </Link>
              <Link href={publicReportHref(targetType, targetId)} className="fe-btn">
                Denunciar sem conta
              </Link>
              <button type="button" className="fe-btn" onClick={close}>
                Cancelar
              </button>
            </div>
          </>
        ) : receipt ? (
          <>
            <div ref={resultRef} tabIndex={-1} className="outline-none" id={`${uid}-desc`}>
              <Notice tone="success" live>
                <p className="font-semibold">Protocolo {receipt.protocol}</p>
                <p>{receipt.message}</p>
                <p className="text-sm">Prazo de análise: {formatWhen(receipt.reviewBy)}.</p>
              </Notice>
            </div>
            <p>
              Acompanhe em{' '}
              <Link href="/configuracoes/denuncias" className="font-semibold underline">
                Minhas denúncias
              </Link>
              . Quem publicou não fica sabendo quem denunciou.
            </p>
            <p>
              <button ref={closeRef} type="button" className="fe-btn fe-btn-primary" onClick={close}>
                Fechar
              </button>
            </p>
          </>
        ) : (
          <form noValidate onSubmit={submit} className="grid gap-4" aria-labelledby={`${uid}-titulo`}>
            <p id={`${uid}-desc`} className="text-muted">
              Uma pessoa da moderação analisa. Quem publicou não fica sabendo quem denunciou.
            </p>
            <div ref={resultRef} tabIndex={-1} className="outline-none">
              {errors.length > 0 && (
                <Notice tone="error" live>
                  {errors.map((m) => (
                    <p key={m}>{m}</p>
                  ))}
                </Notice>
              )}
            </div>
            {reasons.isPending && <p className="text-muted">Carregando os motivos…</p>}
            {reasons.isError && (
              <Notice tone="error">
                <p>Não foi possível carregar os motivos. Feche e tente de novo.</p>
              </Notice>
            )}
            {reasons.data && (
              <ReasonFields name={`${uid}-motivo`} legend={`Por que ${TARGET_TEXT[targetType]} fere as regras?`} reasons={reasonsFor(reasons.data, targetType)} value={reason} onChange={setReason} />
            )}
            {reason === 'CRIANCA_EM_RISCO' && (
              <Notice>
                <p className="font-semibold">O conteúdo sai do ar na hora enquanto a moderação analisa.</p>
                <p>Se uma criança corre risco agora, ligue 190 (Polícia) ou disque 100 (Direitos Humanos).</p>
                <label className="flex min-h-6 items-start gap-2">
                  <input type="checkbox" className="mt-1 h-5 w-5 flex-none" checked={csa} onChange={(e) => setCsa(e.target.checked)} />
                  <span>Envolve exploração ou abuso sexual (o caso é registrado para comunicação às autoridades).</span>
                </label>
              </Notice>
            )}
            <div className="grid gap-1">
              <label htmlFor={`${uid}-texto`} className="font-semibold">
                Conte mais <span className="font-normal text-muted">{reason === 'OUTRO' ? '(obrigatório)' : '(opcional)'}</span>
              </label>
              <textarea
                id={`${uid}-texto`}
                className="fe-input min-h-24"
                value={details}
                maxLength={MAX}
                onChange={(e) => setDetails(e.target.value)}
                aria-describedby={`${uid}-dica`}
              />
              <p id={`${uid}-dica`} className="text-sm text-muted">
                Até {MAX.toLocaleString('pt-BR')} caracteres. Não escreva dados pessoais de ninguém.
              </p>
              <FieldErrors id={`${uid}-erro`} errors={[]} />
            </div>
            <div className="flex flex-wrap gap-2">
              <button type="submit" className="fe-btn fe-btn-primary" disabled={send.isPending || !reasons.data}>
                <IconReport size={18} />
                {send.isPending ? 'Enviando…' : 'Enviar denúncia'}
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
