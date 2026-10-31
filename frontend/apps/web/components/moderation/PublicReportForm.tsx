'use client';

import { isApiError, type ReceiptView, type ReportReasonCode, type ReportTargetType } from '@friendly-eats/api';
import { useMutation } from '@tanstack/react-query';
import { useSearchParams } from 'next/navigation';
import { useId, useState, type FormEvent } from 'react';
import { ErrorSummary, Field, Notice, useDeferredFocus } from '@/components/Form';
import { IconReport } from '@/components/icons';
import { api } from '@/lib/api';
import { formatWhen, parseTargetLink, reasonsFor, useReasons } from '@/lib/moderation';
import { ReasonFields } from './ReasonFields';

const TYPES: ReportTargetType[] = ['POST', 'COMMENT', 'REVIEW', 'PROFILE', 'ESTABLISHMENT'];

/**
 * Denúncia sem conta (RF13): canal para não usuários exigido pelo STF (Temas 987 e 533, item 9). Nome e e-mail são
 * obrigatórios (a denúncia não é anônima, ECA Digital, art. 29, §2º); o alvo vem do botão "Denunciar" (tipo e id na
 * URL) ou do link colado.
 */
export function PublicReportForm() {
  const uid = useId();
  const params = useSearchParams();
  const presetType = TYPES.find((t) => t === params.get('tipo')) ?? null;
  const presetId = params.get('id');
  const reasons = useReasons();
  const [name, setName] = useState('');
  const [email, setEmail] = useState('');
  const [link, setLink] = useState('');
  const [reason, setReason] = useState<ReportReasonCode | null>(null);
  const [details, setDetails] = useState('');
  const [csa, setCsa] = useState(false);
  const [errors, setErrors] = useState<Array<{ message: string; fieldId?: string }>>([]);
  const [receipt, setReceipt] = useState<ReceiptView | null>(null);
  const [summaryRef, focusSummary] = useDeferredFocus<HTMLDivElement>();
  const [okRef, focusOk] = useDeferredFocus<HTMLDivElement>();

  const target = presetType && presetId ? { type: presetType, id: presetId } : parseTargetLink(link);
  const send = useMutation({
    mutationFn: () =>
      api.reports.createPublic({
        name: name.trim(),
        email: email.trim(),
        targetType: target!.type,
        targetId: target!.id,
        reason: reason!,
        details: details.trim() || null,
        childSexualAbuse: reason === 'CRIANCA_EM_RISCO' ? csa : undefined,
      }),
    onSuccess: (r) => {
      setReceipt(r);
      focusOk();
    },
    onError: (e) => {
      const msgs = isApiError(e, 422) && e.errors.length ? e.errors : [isApiError(e) ? e.detail : 'Não foi possível enviar agora. Tente de novo.'];
      setErrors(msgs.map((message) => ({ message })));
      focusSummary();
    },
  });

  function submit(e: FormEvent) {
    e.preventDefault();
    const local: Array<{ message: string; fieldId?: string }> = [];
    if (name.trim().length < 2) local.push({ message: 'Informe seu nome: a denúncia não pode ser anônima.', fieldId: `${uid}-nome` });
    if (!/^[^\s@]+@[^\s@]+\.[^\s@]{2,}$/.test(email.trim())) local.push({ message: 'Informe um e-mail válido para receber o resultado.', fieldId: `${uid}-email` });
    if (!target) local.push({ message: 'Cole o link do post, perfil ou lugar (ex.: https://friendlyeats.app/p/…).', fieldId: `${uid}-link` });
    if (!reason) local.push({ message: 'Escolha um motivo.' });
    setErrors(local);
    if (local.length) {
      focusSummary();
      return;
    }
    send.mutate();
  }

  if (receipt) {
    return (
      <div ref={okRef} tabIndex={-1} className="outline-none">
        <Notice tone="success" live>
          <p className="font-semibold">Protocolo {receipt.protocol}</p>
          <p>{receipt.message}</p>
          <p>Prazo de análise: {formatWhen(receipt.reviewBy)}. Enviaremos o resultado para {email.trim()}.</p>
        </Notice>
      </div>
    );
  }
  const type = target?.type ?? 'POST';
  return (
    <form noValidate onSubmit={submit} className="grid max-w-2xl gap-5" aria-label="Denunciar sem conta">
      <ErrorSummary ref={summaryRef} items={errors} />
      <Field id={`${uid}-nome`} label="Seu nome" required>
        {(a) => <input {...a} className="fe-input" autoComplete="name" value={name} onChange={(e) => setName(e.target.value)} />}
      </Field>
      <Field id={`${uid}-email`} label="Seu e-mail" hint="Só para o protocolo e o resultado; quem publicou não fica sabendo quem denunciou." required>
        {(a) => <input {...a} type="email" className="fe-input" autoComplete="email" value={email} onChange={(e) => setEmail(e.target.value)} />}
      </Field>
      {presetType && presetId ? (
        <p>
          Conteúdo denunciado: <strong>{presetType === 'COMMENT' ? 'comentário' : presetType === 'REVIEW' ? 'avaliação' : presetType === 'PROFILE' ? 'perfil' : presetType === 'ESTABLISHMENT' ? 'página de lugar' : 'post'}</strong> nº {presetId}
        </p>
      ) : (
        <Field id={`${uid}-link`} label="Link do conteúdo" hint="Post, perfil ou página de lugar do Friendly Eats." required>
          {(a) => <input {...a} type="url" className="fe-input" value={link} onChange={(e) => setLink(e.target.value)} />}
        </Field>
      )}
      {reasons.data && (
        <ReasonFields name={`${uid}-motivo`} legend="Motivo" reasons={reasonsFor(reasons.data, type)} value={reason} onChange={setReason} />
      )}
      {reason === 'CRIANCA_EM_RISCO' && (
        <Notice>
          <p>Se uma criança corre risco agora, ligue 190 (Polícia) ou disque 100 (Direitos Humanos).</p>
          <label className="flex min-h-6 items-start gap-2">
            <input type="checkbox" className="mt-1 h-5 w-5 flex-none" checked={csa} onChange={(e) => setCsa(e.target.checked)} />
            <span>Envolve exploração ou abuso sexual.</span>
          </label>
        </Notice>
      )}
      <Field id={`${uid}-texto`} label="Conte mais" hint="Opcional, até 1.000 caracteres.">
        {(a) => <textarea {...a} className="fe-input min-h-24" maxLength={1000} value={details} onChange={(e) => setDetails(e.target.value)} />}
      </Field>
      <div>
        <button type="submit" className="fe-btn fe-btn-primary" disabled={send.isPending}>
          <IconReport size={18} />
          {send.isPending ? 'Enviando…' : 'Enviar denúncia'}
        </button>
      </div>
    </form>
  );
}
