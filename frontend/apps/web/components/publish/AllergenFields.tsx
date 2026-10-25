'use client';

import type { AllergenLevelCode, PostVocabulary } from '@friendly-eats/api';
import { FieldErrors } from '@/components/Form';

interface Props {
  vocabulary: PostVocabulary;
  allergens: Record<string, AllergenLevelCode>;
  onAllergens: (v: Record<string, AllergenLevelCode>) => void;
  warnings: string[];
  onWarnings: (v: string[]) => void;
  showWarnings: boolean;
  errors?: string[];
  warningErrors?: string[];
}

/**
 * Alergênicos da Anvisa (RDC 26/2015) com o nível de cada um, e as advertências (lactose, glúten), sempre com o
 * aviso de informação autodeclarada. Os propostos em 2025 ficam de fora: o servidor recusa.
 */
export function AllergenFields({ vocabulary, allergens, onAllergens, warnings, onWarnings, showWarnings, errors, warningErrors }: Props) {
  const list = vocabulary.allergens.filter((a) => a.status === 'VIGENTE');
  const declared = Object.keys(allergens).length;
  return (
    <fieldset className="grid gap-3" id="post-allergens" aria-describedby="post-allergens-aviso">
      <legend className="font-semibold">
        Alergênicos <span className="font-normal text-muted">({declared === 0 ? 'nenhum declarado' : `${declared} declarado${declared === 1 ? '' : 's'}`})</span>
      </legend>
      <p id="post-allergens-aviso" className="text-sm text-muted">
        {vocabulary.allergenNotice}
      </p>
      <details className="fe-card p-3" open={declared > 0}>
        <summary className="min-h-6 cursor-pointer font-semibold">Declarar alergênicos do prato</summary>
        <div className="mt-3 grid gap-2 sm:grid-cols-2 lg:grid-cols-3">
          {list.map((a) => {
            const id = `post-alergeno-${a.code}`;
            return (
              <div key={a.code} className="grid gap-1">
                <label htmlFor={id} className="text-sm font-semibold">
                  {a.label}
                </label>
                <select
                  id={id}
                  className="fe-input"
                  value={allergens[a.code] ?? ''}
                  onChange={(e) => {
                    const next = { ...allergens };
                    if (e.target.value) next[a.code] = e.target.value as AllergenLevelCode;
                    else delete next[a.code];
                    onAllergens(next);
                  }}
                >
                  <option value="">Não contém / não informado</option>
                  {vocabulary.allergenLevels.map((l) => (
                    <option key={l.code} value={l.code}>
                      {l.label}
                    </option>
                  ))}
                </select>
              </div>
            );
          })}
        </div>
      </details>
      <FieldErrors id="post-allergens-erro" errors={errors} />
      {showWarnings && (
        <div className="grid gap-1" id="post-warnings">
          <p className="text-sm font-semibold">Advertências (não são alergênicos)</p>
          <div className="flex flex-wrap gap-x-4">
            {vocabulary.warnings.map((w) => (
              <label key={w.code} className="flex min-h-11 items-center gap-2">
                <input
                  type="checkbox"
                  className="size-5"
                  checked={warnings.includes(w.code)}
                  onChange={(e) => onWarnings(e.target.checked ? [...warnings, w.code] : warnings.filter((x) => x !== w.code))}
                />
                {w.label}
              </label>
            ))}
          </div>
          <FieldErrors id="post-warnings-erro" errors={warningErrors} />
        </div>
      )}
    </fieldset>
  );
}
