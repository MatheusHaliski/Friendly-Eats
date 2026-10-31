'use client';

import { isApiError, type DataExportView } from '@friendly-eats/api';
import { useMutation, useQueryClient } from '@tanstack/react-query';
import Link from 'next/link';
import { useId, useRef, useState, type FormEvent } from 'react';
import { Dialog } from '@/components/Dialog';
import { ErrorSummary, Notice, useDeferredFocus } from '@/components/Form';
import { IconDownload, IconExport, IconSuccess } from '@/components/icons';
import { api } from '@/lib/api';
import { mapSudoError } from '@/lib/field-errors';
import { formatDate } from '@/lib/format';
import { formatDateTime } from '@/lib/post-format';
import { EXPORTS_KEY, useDataExports } from '@/lib/queries';
import { formatBytes, nextExportAllowedAt } from '@/lib/titular';
import { checkSudo, SudoFields, sudoBody, type SudoValues } from './SudoFields';

/**
 * Configurações → Conta → Seus dados (RF18; LGPD, art. 18, V, e art. 19): pedir uma cópia com o modo sudo, ver o
 * andamento e baixar o ZIP pela URL pré-assinada, que vale 72 horas.
 */
export function DataExportCard({ withCode }: { withCode: boolean }) {
  const uid = useId();
  const exports = useDataExports();
  const [open, setOpen] = useState(false);
  const [doneRef, focusDone] = useDeferredFocus<HTMLDivElement>();
  const [done, setDone] = useState<string | null>(null);
  const items = exports.data ?? [];
  const nextAt = nextExportAllowedAt(items);

  return (
    <section aria-labelledby={`${uid}-titulo`} className="fe-card grid gap-3 p-5" data-testid="seus-dados">
      <h2 id={`${uid}-titulo`} className="flex items-center gap-2 text-xl font-bold">
        <IconExport size={22} />
        Seus dados
      </h2>
      <p>
        Baixe uma cópia de tudo o que o Friendly Eats guarda sobre você: perfil, preferências, consentimentos, posts, comentários, avaliações, curtidas,
        salvos, verificação de idade e mais, em arquivos JSON dentro de um ZIP. Fica pronto em até 15 dias, em geral em minutos, e avisamos por e-mail.{' '}
        <span className="fe-law">Art. 18, V — portabilidade</span>
      </p>
      <div ref={doneRef} tabIndex={-1} className="outline-none">
        {done && (
          <Notice tone="success" live>
            <p>{done}</p>
          </Notice>
        )}
      </div>
      {exports.isError && (
        <Notice tone="error">
          <p>Não foi possível carregar os seus pedidos. Recarregue a página.</p>
        </Notice>
      )}
      {items.length > 0 && (
        <ul className="grid gap-2" aria-label="Pedidos de exportação">
          {items.map((e) => (
            <ExportItem key={e.id} item={e} />
          ))}
        </ul>
      )}
      <div className="flex flex-wrap items-center gap-3">
        <button type="button" className="fe-btn" disabled={!!nextAt} onClick={() => setOpen(true)}>
          <IconExport size={18} />
          Pedir uma cópia…
        </button>
        {nextAt && <p className="text-sm text-muted">Dá para pedir de novo a partir de {formatDateTime(nextAt.toISOString())}.</p>}
      </div>
      <p className="text-sm text-muted">
        Os direitos do titular e o contato do encarregado estão na página{' '}
        <Link href="/privacidade" className="font-semibold text-fg underline">
          Privacidade
        </Link>
        .
      </p>
      <ExportDialog
        open={open}
        withCode={withCode}
        onClose={() => setOpen(false)}
        onDone={(v) => {
          setOpen(false);
          setDone(`Pedido registrado. Avisaremos por e-mail quando o arquivo estiver pronto (prazo: ${formatDate(v.dueAt)}).`);
          focusDone();
        }}
      />
    </section>
  );
}

function ExportItem({ item }: { item: DataExportView }) {
  return (
    <li className="flex flex-wrap items-center justify-between gap-2 rounded-md border-2 border-line p-3" data-testid="pedido-exportacao">
      <span className="grid">
        <span className="flex items-center gap-1.5 font-semibold">
          {item.status === 'READY' && <IconSuccess size={18} className="text-success" />}
          {item.statusLabel}
        </span>
        <span className="text-sm text-muted">
          Pedido em {formatDateTime(item.requestedAt)}
          {item.status === 'READY' && item.expiresAt ? ` · link vale até ${formatDateTime(item.expiresAt)}` : ''}
          {item.status === 'REQUESTED' || item.status === 'PROCESSING' ? ` · prazo ${formatDate(item.dueAt)}` : ''}
          {item.sizeBytes ? ` · ${formatBytes(item.sizeBytes)}` : ''}
        </span>
      </span>
      {item.downloadUrl && (
        <a href={item.downloadUrl} className="fe-btn fe-btn-sm fe-btn-primary" rel="noopener noreferrer">
          <IconDownload size={16} />
          Baixar ZIP
        </a>
      )}
    </li>
  );
}

type Errors = { fields: Record<string, string[]>; general: string[] };
const NONE: Errors = { fields: {}, general: [] };

function ExportDialog({ open, withCode, onClose, onDone }: { open: boolean; withCode: boolean; onClose: () => void; onDone: (v: DataExportView) => void }) {
  const uid = useId();
  const queryClient = useQueryClient();
  const cancelRef = useRef<HTMLButtonElement>(null);
  const [summaryRef, focusSummary] = useDeferredFocus<HTMLDivElement>();
  const [sudo, setSudo] = useState<SudoValues>({ password: '', code: '' });
  const [errors, setErrors] = useState<Errors>(NONE);
  const request = useMutation({
    mutationFn: () => api.privacy.requestExport(sudoBody(sudo, withCode)),
    onSuccess: (v) => {
      void queryClient.invalidateQueries({ queryKey: EXPORTS_KEY });
      setSudo({ password: '', code: '' });
      setErrors(NONE);
      onDone(v);
    },
    onError: (e) => {
      if (isApiError(e, 403)) setErrors(mapSudoError(e.detail, withCode));
      else setErrors({ fields: {}, general: [isApiError(e) ? e.detail : 'Não foi possível pedir agora. Tente de novo.'] });
      focusSummary();
    },
  });
  function close() {
    setSudo({ password: '', code: '' });
    setErrors(NONE);
    onClose();
  }
  function submit(e: FormEvent) {
    e.preventDefault();
    const fields = checkSudo(sudo, withCode);
    if (Object.keys(fields).length) {
      setErrors({ fields, general: [] });
      focusSummary();
      return;
    }
    request.mutate();
  }
  const titleId = `${uid}-titulo`;
  const descId = `${uid}-desc`;
  const summary = [
    ...Object.entries(errors.fields).flatMap(([f, msgs]) => msgs.map((message) => ({ message, fieldId: `${uid}-${f}` }))),
    ...errors.general.map((message) => ({ message })),
  ];
  return (
    <Dialog open={open} onClose={close} labelledBy={titleId} describedBy={descId} initialFocus={cancelRef}>
      <form noValidate onSubmit={submit} className="grid gap-4">
        <h2 id={titleId} className="text-2xl font-extrabold">
          Pedir uma cópia dos seus dados
        </h2>
        <p id={descId}>
          Para proteger os seus dados, confirme que é você. Avisaremos por e-mail quando o arquivo estiver pronto; o link para baixar vale 72 horas.
        </p>
        <ErrorSummary ref={summaryRef} items={summary} />
        <SudoFields idPrefix={uid} values={sudo} onChange={setSudo} withCode={withCode} errors={errors.fields} />
        <div className="flex flex-wrap justify-end gap-2">
          <button ref={cancelRef} type="button" className="fe-btn" onClick={close}>
            Cancelar
          </button>
          <button type="submit" className="fe-btn fe-btn-primary" disabled={request.isPending}>
            <IconExport size={18} />
            {request.isPending ? 'Pedindo…' : 'Pedir a cópia'}
          </button>
        </div>
      </form>
    </Dialog>
  );
}
