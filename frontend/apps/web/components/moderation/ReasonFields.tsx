'use client';

import type { ReasonView, ReportReasonCode } from '@friendly-eats/api';

/** Lista de motivos em botões de opção nativos: rótulo + descrição, a prioridade nunca só por cor. */
export function ReasonFields({
  name,
  legend,
  reasons,
  value,
  onChange,
}: {
  name: string;
  legend: string;
  reasons: ReasonView[];
  value: ReportReasonCode | null;
  onChange: (code: ReportReasonCode) => void;
}) {
  return (
    <fieldset className="grid gap-2">
      <legend className="mb-1 font-semibold">
        {legend} <span className="font-normal text-muted">(obrigatório)</span>
      </legend>
      <ul className="grid gap-1">
        {reasons.map((r) => {
          const id = `${name}-${r.code}`;
          return (
            <li key={r.code}>
              <label htmlFor={id} className="flex min-h-11 cursor-pointer items-start gap-3 rounded-md border border-line p-2 has-[:checked]:border-line-strong">
                <input
                  id={id}
                  type="radio"
                  name={name}
                  value={r.code}
                  checked={value === r.code}
                  onChange={() => onChange(r.code)}
                  className="mt-1 h-5 w-5 flex-none"
                  aria-describedby={`${id}-desc`}
                />
                <span className="grid">
                  <span className="font-semibold">{r.label}</span>
                  <span id={`${id}-desc`} className="text-sm text-muted">
                    {r.description}
                  </span>
                </span>
              </label>
            </li>
          );
        })}
      </ul>
    </fieldset>
  );
}
