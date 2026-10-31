'use client';

import {
  isApiError,
  type Coded,
  type DimensionItem,
  type ReviewDimensionCode,
  type ReviewDisclosure,
  type ReviewDraft,
  type ReviewView,
  type VisitModeCode,
} from '@friendly-eats/api';
import { useMutation } from '@tanstack/react-query';
import { useRouter } from 'next/navigation';
import { useId, useState, type FormEvent } from 'react';
import { describedBy, ErrorSummary, FieldErrors, Notice, useDeferredFocus } from '@/components/Form';
import { RadioPills } from '@/components/settings/PreferenceControls';
import { StarRatingInput } from '@/components/StarRatingInput';
import { api } from '@/lib/api';
import { mapReviewErrors, type MappedErrors } from '@/lib/field-errors';
import { centsToInput, parseReaisToCents } from '@/lib/format';
import { dimensionsFor, keepDimensions, MAX_BODY, MAX_SPENT_CENTS, VISIT_MODES } from '@/lib/review';
import { loginHref } from '@/lib/safe-redirect';

interface Props {
  placeId: string;
  initial: ReviewView | null;
  dimensions: DimensionItem[];
  visitModes?: Coded[];
  onSaved: (review: ReviewView, created: boolean) => void;
  onCancel?: () => void;
}

const EMPTY: MappedErrors = { fields: {}, general: [] };

/** Formulário "Avaliar" (RF8): nota, modo de visita, dimensões do modo, gasto por pessoa e texto. */
export function ReviewForm({ placeId, initial, dimensions, visitModes, onSaved, onCancel }: Props) {
  const uid = useId();
  const router = useRouter();
  const [rating, setRating] = useState<number | null>(initial?.rating ?? null);
  const [mode, setMode] = useState<VisitModeCode | null>((initial?.visitMode.code as VisitModeCode | undefined) ?? null);
  const [dims, setDims] = useState<Partial<Record<ReviewDimensionCode, number>>>(initial?.dimensions ?? {});
  const [spent, setSpent] = useState(centsToInput(initial?.spentPerPersonCents));
  const [body, setBody] = useState(initial?.body ?? '');
  const [disclosure, setDisclosure] = useState<ReviewDisclosure>(
    initial?.trust === 'REVIEW_INCENTIVIZED' ? 'COURTESY' : initial?.trust === 'REVIEW_CONFLICT' ? 'RELATIONSHIP' : 'NONE',
  );
  const [errors, setErrors] = useState<MappedErrors>(EMPTY);
  const [serverMessage, setServerMessage] = useState<string | null>(null);
  const [summaryRef, focusSummary] = useDeferredFocus<HTMLDivElement>();
  const modes = (visitModes?.length ? visitModes : VISIT_MODES).map((m) => ({ code: m.code as VisitModeCode, label: m.label }));
  const shownDims = dimensionsFor(mode, dimensions);
  const dimLabels = Object.fromEntries(dimensions.map((d) => [d.code, d.label]));

  const save = useMutation({
    mutationFn: (draft: ReviewDraft) => api.establishments.saveMyReview(placeId, draft),
    onSuccess: ({ review, created }) => onSaved(review, created),
    onError: (e) => {
      if (isApiError(e, 401)) {
        router.push(loginHref(`/lugares/${placeId}`));
        return;
      }
      if (isApiError(e, 422)) {
        setErrors(mapReviewErrors(e.errors.length ? e.errors : [e.detail], dimLabels));
      } else {
        // 403 (organização, dono, conta não ativa), 404 (lugar fora do ar) e 409 (removida pela moderação)
        setServerMessage(isApiError(e) ? e.detail : 'Não foi possível salvar agora. Tente de novo.');
      }
      focusSummary();
    },
  });

  const fieldIds: Record<string, string> = {
    rating: `${uid}-nota`,
    visitMode: `${uid}-modo`,
    spent: `${uid}-gasto`,
    body: `${uid}-texto`,
    ...Object.fromEntries(dimensions.map((d) => [`dim-${d.code}`, `${uid}-dim-${d.code}`])),
  };

  function submit(e: FormEvent) {
    e.preventDefault();
    setServerMessage(null);
    const local: MappedErrors = { fields: {}, general: [] };
    if (!rating) local.fields.rating = ['Dê uma nota de 1 a 5.'];
    if (!mode) local.fields.visitMode = ['Escolha como foi a visita.'];
    const cents = parseReaisToCents(spent);
    if (cents !== null && Number.isNaN(cents)) local.fields.spent = ['Informe o valor em reais, por exemplo 45,90.'];
    else if (cents !== null && (cents < 0 || cents > MAX_SPENT_CENTS)) local.fields.spent = ['Gasto por pessoa entre R$ 0 e R$ 5.000.'];
    if (body.length > MAX_BODY) local.fields.body = [`O texto pode ter até ${MAX_BODY} caracteres.`];
    if (Object.keys(local.fields).length) {
      setErrors(local);
      focusSummary();
      return;
    }
    setErrors(EMPTY);
    save.mutate({
      rating,
      visitMode: mode,
      dimensions: keepDimensions(dims, mode, dimensions),
      spentPerPersonCents: cents,
      body: body.trim() ? body.trim() : null,
      disclosure,
    });
  }

  const summary = [
    ...Object.entries(errors.fields).flatMap(([field, msgs]) => msgs.map((message) => ({ message, fieldId: fieldIds[field] }))),
    ...errors.general.map((message) => ({ message })),
  ];
  const err = (field: string) => errors.fields[field];
  const errId = (field: string) => (err(field)?.length ? `${fieldIds[field]}-erro` : undefined);

  return (
    <form noValidate onSubmit={submit} className="grid gap-5" aria-label="Avaliar este lugar">
      {serverMessage ? (
        <div ref={summaryRef} tabIndex={-1} className="outline-none">
          <Notice tone="error" live>
            <p>{serverMessage}</p>
          </Notice>
        </div>
      ) : (
        <ErrorSummary ref={summaryRef} items={summary} />
      )}

      <div>
        <StarRatingInput
          id={fieldIds.rating}
          name="nota"
          legend="Sua nota"
          value={rating}
          onChange={setRating}
          required
          invalid={!!err('rating')}
          describedBy={errId('rating')}
        />
        <FieldErrors id={`${fieldIds.rating}-erro`} errors={err('rating')} />
      </div>

      <fieldset
        id={fieldIds.visitMode}
        tabIndex={-1}
        className="grid gap-2 outline-none"
        aria-describedby={errId('visitMode')}
      >
        <legend className="mb-1 font-semibold">
          Como foi a visita? <span className="font-normal text-muted">(obrigatório)</span>
        </legend>
        <div className="flex flex-wrap gap-2">
          {modes.map((m) => {
            const id = `${uid}-modo-${m.code}`;
            return (
              <span key={m.code} className="relative inline-flex">
                <input
                  type="radio"
                  className="fe-visually-hidden-input"
                  id={id}
                  name="modo"
                  value={m.code}
                  checked={mode === m.code}
                  onChange={() => {
                    setMode(m.code);
                    setDims((d) => keepDimensions(d, m.code, dimensions));
                  }}
                />
                <label htmlFor={id} className="fe-pill">
                  {m.label}
                </label>
              </span>
            );
          })}
        </div>
        <FieldErrors id={`${fieldIds.visitMode}-erro`} errors={err('visitMode')} />
      </fieldset>

      {shownDims.length > 0 && (
        <fieldset className="grid gap-3 rounded-md border border-line p-4">
          <legend className="px-1 font-semibold">
            Notas por aspecto <span className="font-normal text-muted">(opcional)</span>
          </legend>
          <p className="text-sm text-muted">Explicam a sua nota, mas não a compõem.</p>
          {shownDims.map((d) => {
            const key = `dim-${d.code}`;
            return (
              <div key={d.code} className="grid gap-1">
                <div className="flex flex-wrap items-end gap-3">
                  <StarRatingInput
                    id={fieldIds[key]}
                    name={`dimensao-${d.code}`}
                    legend={d.label}
                    size="sm"
                    value={dims[d.code] ?? null}
                    onChange={(v) => setDims((prev) => ({ ...prev, [d.code]: v }))}
                    invalid={!!err(key)}
                    describedBy={errId(key)}
                  />
                  {dims[d.code] !== undefined && (
                    <button
                      type="button"
                      className="fe-btn fe-btn-sm"
                      onClick={() =>
                        setDims((prev) => {
                          const next = { ...prev };
                          delete next[d.code];
                          return next;
                        })
                      }
                    >
                      Limpar<span className="sr-only"> a nota de {d.label}</span>
                    </button>
                  )}
                </div>
                <FieldErrors id={`${fieldIds[key]}-erro`} errors={err(key)} />
              </div>
            );
          })}
        </fieldset>
      )}

      <div className="grid gap-1">
        <label htmlFor={fieldIds.spent} className="font-semibold">
          Quanto você gastou por pessoa? <span className="font-normal text-muted">(opcional)</span>
        </label>
        <p id={`${fieldIds.spent}-dica`} className="text-sm text-muted">
          Prato e bebida não alcoólica, em reais. Ajuda a mostrar a faixa de preço real do lugar.
        </p>
        <div className="flex max-w-xs items-center gap-2">
          <span aria-hidden="true" className="font-semibold">
            R$
          </span>
          <input
            id={fieldIds.spent}
            className="fe-input"
            inputMode="decimal"
            autoComplete="off"
            placeholder="45,90"
            value={spent}
            onChange={(e) => setSpent(e.target.value)}
            aria-describedby={describedBy(`${fieldIds.spent}-dica`, errId('spent'))}
            aria-invalid={err('spent') ? true : undefined}
          />
        </div>
        <FieldErrors id={`${fieldIds.spent}-erro`} errors={err('spent')} />
      </div>

      <div className="grid gap-1">
        <label htmlFor={fieldIds.body} className="font-semibold">
          Conte como foi <span className="font-normal text-muted">(opcional)</span>
        </label>
        <textarea
          id={fieldIds.body}
          className="fe-input min-h-32"
          value={body}
          maxLength={MAX_BODY}
          onChange={(e) => setBody(e.target.value)}
          aria-describedby={describedBy(`${fieldIds.body}-contador`, errId('body'))}
          aria-invalid={err('body') ? true : undefined}
        />
        <p id={`${fieldIds.body}-contador`} className="text-sm text-muted">
          {body.length} de {MAX_BODY} caracteres
        </p>
        <FieldErrors id={`${fieldIds.body}-erro`} errors={err('body')} />
      </div>

      <RadioPills<ReviewDisclosure>
        name={`${uid}-vinculo`}
        legend="Você tem vínculo com o lugar?"
        hint="Declarar é obrigatório pela política de avaliações. Cortesia ou convite aparecem na avaliação e pesam menos na nota; quem trabalha, é da família ou concorrente fica fora da nota."
        value={disclosure}
        onChange={setDisclosure}
        options={[
          { value: 'NONE', label: 'Não tenho' },
          { value: 'COURTESY', label: 'Recebi cortesia' },
          { value: 'INVITATION', label: 'Fui convidado' },
          { value: 'RELATIONSHIP', label: 'Trabalho, é da família ou concorrente' },
        ]}
      />

      <div className="flex flex-wrap gap-2">
        <button type="submit" className="fe-btn fe-btn-primary" disabled={save.isPending}>
          {save.isPending ? 'Enviando…' : initial ? 'Salvar alterações' : 'Publicar avaliação'}
        </button>
        {onCancel && (
          <button type="button" className="fe-btn" onClick={onCancel}>
            Cancelar
          </button>
        )}
      </div>
    </form>
  );
}
