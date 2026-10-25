'use client';

import type { TaxonomyCatalog } from '@friendly-eats/api';
import Link from 'next/link';
import { useState } from 'react';
import { describedBy, FieldErrors } from '@/components/Form';
import { IconClose, IconLocked } from '@/components/icons';

export const MAX_FAVORITE_CUISINES = 5;

interface TasteFieldsProps {
  idPrefix: string;
  taxonomy: TaxonomyCatalog;
  cuisines: string[];
  onCuisines: (codes: string[]) => void;
  styles: string[];
  onStyles: (codes: string[]) => void;
  /** Consentimento SENSITIVE_PROFILE_DATA; null enquanto carrega ou se não deu para saber. */
  sensitiveGranted: boolean | null;
  /** lockedForMinor do consentimento sensível (menor de 18, LGPD Art. 14): nem dá para ligar. */
  sensitiveLocked?: boolean;
  errors: Record<string, string[] | undefined>;
}

/**
 * Gosto (RF2 · RF3): até 5 culinárias favoritas da taxonomia e os estilos alimentares, que ficam bloqueados até o
 * consentimento de dado sensível (LGPD Art. 11).
 */
export function TasteFields({ idPrefix, taxonomy, cuisines, onCuisines, styles, onStyles, sensitiveGranted, sensitiveLocked, errors }: TasteFieldsProps) {
  const id = (s: string) => `${idPrefix}-${s}`;
  const [toAdd, setToAdd] = useState('');
  const label = (code: string) => taxonomy.cuisines.find((c) => c.code === code)?.label ?? code;
  const full = cuisines.length >= MAX_FAVORITE_CUISINES;
  const locked = sensitiveGranted !== true;

  function add(code: string) {
    setToAdd('');
    if (!code || cuisines.includes(code) || full) return;
    onCuisines([...cuisines, code]);
  }

  return (
    <fieldset className="fe-card grid gap-5 p-5">
      <legend className="float-left mb-1 w-full text-xl font-bold">Seu gosto</legend>

      <div className="clear-both grid gap-2">
        <p id={id('culinarias-titulo')} className="font-semibold">
          Culinárias favoritas
        </p>
        <p id={id('culinarias-dica')} className="text-sm text-muted">
          Até {MAX_FAVORITE_CUISINES}. Elas ajudam a sugerir lugares; se o feed personalizado estiver desligado, ficam só no seu perfil.
        </p>
        {cuisines.length > 0 ? (
          <ul className="flex flex-wrap gap-2" aria-labelledby={id('culinarias-titulo')}>
            {cuisines.map((code) => (
              <li key={code} className="fe-tag gap-1 pr-1">
                {label(code)}
                <button
                  type="button"
                  className="inline-grid size-6 place-items-center rounded-full hover:bg-sunken"
                  onClick={() => onCuisines(cuisines.filter((c) => c !== code))}
                  aria-label={`Remover ${label(code)}`}
                >
                  <IconClose size={14} />
                </button>
              </li>
            ))}
          </ul>
        ) : (
          <p className="text-sm">Nenhuma culinária escolhida.</p>
        )}
        <p className="text-sm" aria-live="polite" data-testid="contagem-culinarias">
          {cuisines.length} de {MAX_FAVORITE_CUISINES} escolhidas{full ? ' (limite atingido)' : ''}
        </p>
        <div className="grid gap-1 sm:max-w-sm">
          <label htmlFor={id('favoriteCuisines')} className="font-semibold">
            Adicionar culinária
          </label>
          <select
            id={id('favoriteCuisines')}
            className="fe-input"
            value={toAdd}
            disabled={full}
            onChange={(e) => add(e.target.value)}
            aria-describedby={describedBy(id('culinarias-dica'), errors.favoriteCuisines && id('favoriteCuisines-erro'))}
            aria-invalid={errors.favoriteCuisines ? true : undefined}
          >
            <option value="">{full ? 'Remova uma para trocar' : 'Escolha…'}</option>
            {taxonomy.cuisineMacros.map((m) => (
              <optgroup key={m.code} label={m.label}>
                {taxonomy.cuisines
                  .filter((c) => c.macro === m.code && !cuisines.includes(c.code))
                  .map((c) => (
                    <option key={c.code} value={c.code}>
                      {c.label}
                    </option>
                  ))}
              </optgroup>
            ))}
          </select>
          <FieldErrors id={id('favoriteCuisines-erro')} errors={errors.favoriteCuisines} />
        </div>
      </div>

      <fieldset
        className="grid gap-3"
        aria-describedby={describedBy(id('estilos-aviso'), errors.dietaryStyles && id('dietaryStyles-erro'))}
        disabled={locked}
        data-testid="estilos-alimentares"
      >
        <legend className="mb-1 flex items-center gap-1.5 font-semibold">
          {locked && <IconLocked size={16} />}
          Estilos alimentares
          {locked && <span className="font-normal text-muted"> (bloqueado)</span>}
        </legend>
        <div id={id('estilos-aviso')} className="rounded-md border border-line-strong p-3 text-sm">
          <p>Estilos alimentares podem revelar saúde ou religião (LGPD Art. 11).</p>
          {sensitiveGranted === true ? (
            <p>
              Você autorizou guardá-los. Para apagar todos, desligue “dados sensíveis do perfil” em{' '}
              <Link href="/configuracoes/privacidade#consentimentos" className="font-semibold underline">
                Privacidade
              </Link>
              .
            </p>
          ) : sensitiveLocked ? (
            <p>Disponível a partir dos 18 anos: até lá, só com o consentimento do responsável.</p>
          ) : (
            <p>
              {sensitiveGranted === null ? 'Não deu para conferir o seu consentimento. ' : ''}
              Para escolher, ligue antes o consentimento de dados sensíveis do perfil em{' '}
              <Link href="/configuracoes/privacidade#consentimentos" className="font-semibold underline">
                Privacidade
              </Link>
              .
            </p>
          )}
        </div>
        <div className="flex flex-wrap gap-2">
          {taxonomy.dietaryStyles.map((s) => {
            const inputId = id(`estilo-${s.code}`);
            return (
              <div key={s.code} className="relative">
                <input
                  id={inputId}
                  type="checkbox"
                  className="fe-visually-hidden-input peer"
                  checked={styles.includes(s.code)}
                  onChange={(e) => onStyles(e.target.checked ? [...styles, s.code] : styles.filter((c) => c !== s.code))}
                />
                <label htmlFor={inputId} className="fe-pill peer-disabled:cursor-not-allowed peer-disabled:opacity-70">
                  {s.label}
                </label>
              </div>
            );
          })}
        </div>
        <FieldErrors id={id('dietaryStyles-erro')} errors={errors.dietaryStyles} />
      </fieldset>
    </fieldset>
  );
}
