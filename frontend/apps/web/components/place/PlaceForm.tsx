'use client';

import { isApiError, type EstablishmentView, type SessionUserView, type TaxonomyCatalog, type WeekDay } from '@friendly-eats/api';
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import Link from 'next/link';
import { useRouter } from 'next/navigation';
import { useMemo, useRef, useState, type ReactNode } from 'react';
import { ErrorSummary, Field, FieldErrors, Notice, describedBy, useDeferredFocus } from '@/components/Form';
import { IconDelete } from '@/components/icons';
import { ownsPlaces } from '@/lib/account-types';
import { api } from '@/lib/api';
import {
  MAX_RANGES,
  MAX_SECONDARY_CUISINES,
  MAX_SECONDARY_FORMATS,
  MAX_SECONDARY_TYPES,
  PAYMENT_METHODS,
  WEEK_DAYS,
  buildPlaceDraft,
  emptyPlaceValues,
  mapPlaceErrors,
  valuesFromPlace,
  type PlaceErrors,
  type PlaceFormValues,
} from '@/lib/place-form';
import { placeKey, useRequireSession, useTaxonomy } from '@/lib/queries';
import { UFS } from '@/lib/uf';
import { PlaceMap } from './PlaceMap';

/** Centro do mapa enquanto o dono não marca o pino: a capital da UF escolhida não é conhecida aqui, então o Brasil. */
const BRAZIL_CENTER = { lat: -15.7801, lng: -47.9292 };

/** Cadastro (/lugares/novo) e edição (/lugares/[id]/editar) da página do lugar pelo dono (RF6, RF7, RF11). */
export function PlaceFormView({ editId }: { editId?: string }) {
  const session = useRequireSession(editId ? `/lugares/${editId}/editar` : '/lugares/novo');
  const taxonomy = useTaxonomy();
  const place = useQuery({ queryKey: placeKey(editId ?? ''), queryFn: () => api.establishments.get(editId!), enabled: !!editId });
  const user = session.data;

  if (session.isPending || !user || taxonomy.isPending || (editId && place.isPending)) {
    return (
      <p role="status" className="text-muted">
        Carregando…
      </p>
    );
  }
  if (taxonomy.isError) {
    return (
      <Notice tone="error" live>
        <p>Não foi possível abrir o formulário agora. Tente de novo em instantes.</p>
      </Notice>
    );
  }
  if (!ownsPlaces(user.category.code)) {
    return (
      <div className="grid max-w-xl gap-4">
        <h1 className="text-3xl font-extrabold">Cadastrar lugar</h1>
        <Notice>
          <p>Só contas de Estabelecimento ou de Varejo &amp; Marca cadastram lugares. Para indicar um lugar, avalie ou publique sobre ele.</p>
        </Notice>
      </div>
    );
  }
  if (editId && (place.isError || !place.data?.viewerCanEdit)) {
    return (
      <div className="grid max-w-xl gap-4">
        <h1 className="text-3xl font-extrabold">Só quem cuida da página edita o lugar</h1>
        <p>
          <Link href={`/lugares/${editId}`} className="underline">
            Ver a página do lugar
          </Link>
        </p>
      </div>
    );
  }
  return <PlaceEditor key={editId ?? 'novo'} user={user} taxonomy={taxonomy.data} place={editId ? place.data : undefined} />;
}

interface EditorProps {
  user: SessionUserView;
  taxonomy: TaxonomyCatalog;
  place?: EstablishmentView;
}

function PlaceEditor({ user, taxonomy, place }: EditorProps) {
  const router = useRouter();
  const qc = useQueryClient();
  const [values, setValues] = useState<PlaceFormValues>(() => (place ? valuesFromPlace(place) : emptyPlaceValues()));
  const [errors, setErrors] = useState<PlaceErrors>({});
  const [general, setGeneral] = useState<string[]>([]);
  const [summaryRef, focusSummary] = useDeferredFocus<HTMLDivElement>();
  const mapCenter = useRef<(() => { lat: number; lng: number }) | null>(null);
  const set = <K extends keyof PlaceFormValues>(k: K, v: PlaceFormValues[K]) => setValues((old) => ({ ...old, [k]: v }));
  const editing = !!place;

  const retail = user.category.code === 'VAREJO';
  const types = taxonomy.establishmentTypes.filter((t) => (retail ? t.group === 'VAREJO' : t.group !== 'VAREJO'));
  const group = taxonomy.establishmentTypes.find((t) => t.code === values.primaryType)?.group ?? null;

  const save = useMutation({
    mutationFn: async () => {
      const { draft } = buildPlaceDraft(values, editing, taxonomy.actionProviders);
      return place ? api.establishments.update(place.id, draft) : api.establishments.create(draft);
    },
    onSuccess: (view) => {
      qc.setQueryData(placeKey(String(view.id)), view);
      void qc.invalidateQueries({ queryKey: ['meus-lugares'] });
      router.push(`/lugares/${view.id}`);
    },
    onError: (e) => {
      if (isApiError(e, 422)) {
        const mapped = mapPlaceErrors(e.errors.length ? e.errors : [e.detail]);
        setErrors(mapped.fields);
        setGeneral(mapped.general);
      } else if (isApiError(e, 401)) {
        router.push(`/entrar?voltar=${encodeURIComponent(place ? `/lugares/${place.id}/editar` : '/lugares/novo')}`);
        return;
      } else {
        setErrors({});
        setGeneral([isApiError(e) ? e.detail : 'Não foi possível salvar agora. Tente de novo.']);
      }
      focusSummary();
    },
  });

  function submit() {
    const { errors: local } = buildPlaceDraft(values, editing, taxonomy.actionProviders);
    if (Object.keys(local).length) {
      setErrors(local);
      setGeneral([]);
      focusSummary();
      return;
    }
    setErrors({});
    setGeneral([]);
    save.mutate();
  }

  const summary = [
    ...general.map((message) => ({ message })),
    ...Object.entries(errors).flatMap(([k, msgs]) => msgs.map((message) => ({ message, fieldId: `lugar-${k}` }))),
  ];
  const err = (k: string) => errors[k];
  const lat = Number(values.latitude.replace(',', '.'));
  const lng = Number(values.longitude.replace(',', '.'));
  const hasPin = values.latitude.trim() !== '' && values.longitude.trim() !== '' && !Number.isNaN(lat) && !Number.isNaN(lng);

  return (
    <form
      aria-labelledby="titulo-form-lugar"
      className="grid max-w-3xl gap-8"
      noValidate
      onSubmit={(e) => {
        e.preventDefault();
        submit();
      }}
    >
      <div className="grid gap-2">
        <h1 id="titulo-form-lugar" className="text-3xl font-extrabold">
          {editing ? `Editar ${place.name}` : 'Cadastrar lugar'}
        </h1>
        <p className="text-muted">
          A página entra no ar quando o selo da sua conta for conferido. Tudo o que você declarar aqui aparece como
          &ldquo;declarado pelo estabelecimento&rdquo;.
        </p>
      </div>
      <ErrorSummary ref={summaryRef} items={summary} />

      <Section title="Identificação">
        <Field id="lugar-name" label="Nome do lugar" required errors={err('name')}>
          {(a) => <input {...a} className="fe-input" maxLength={120} value={values.name} onChange={(e) => set('name', e.target.value)} />}
        </Field>
        <Field id="lugar-description" label="Descrição" hint="Até 500 caracteres." errors={err('description')}>
          {(a) => <textarea {...a} className="fe-input" rows={3} maxLength={500} value={values.description} onChange={(e) => set('description', e.target.value)} />}
        </Field>
      </Section>

      <Section title="Tipo e classificação">
        <Field id="lugar-primaryType" label="Tipo principal" required errors={err('primaryType')} hint={retail ? 'Conta de Varejo & Marca: lojas do grupo Varejo.' : undefined}>
          {(a) => (
            <select {...a} className="fe-input" value={values.primaryType} onChange={(e) => set('primaryType', e.target.value)}>
              <option value="">Escolha…</option>
              {taxonomy.establishmentGroups
                .filter((g) => types.some((t) => t.group === g.code))
                .map((g) => (
                  <optgroup key={g.code} label={g.label}>
                    {types
                      .filter((t) => t.group === g.code)
                      .map((t) => (
                        <option key={t.code} value={t.code}>
                          {t.label}
                          {t.adultsOnly ? ' (18+)' : ''}
                        </option>
                      ))}
                  </optgroup>
                ))}
            </select>
          )}
        </Field>
        <ListOfSelects
          id="lugar-secondaryTypes"
          legend={`Tipos secundários (até ${MAX_SECONDARY_TYPES})`}
          max={MAX_SECONDARY_TYPES}
          values={values.secondaryTypes}
          onChange={(v) => set('secondaryTypes', v)}
          options={taxonomy.establishmentTypes.filter((t) => t.code !== values.primaryType).map((t) => ({ value: t.code, label: t.label }))}
          itemLabel="Tipo secundário"
        />
        <Field id="lugar-primaryCuisine" label="Culinária principal" errors={err('primaryCuisine')} hint="Restaurante precisa de culinária ou de tipo de comida.">
          {(a) => (
            <select {...a} className="fe-input" value={values.primaryCuisine} onChange={(e) => set('primaryCuisine', e.target.value)}>
              <option value="">Nenhuma</option>
              {taxonomy.cuisineMacros.map((m) => (
                <optgroup key={m.code} label={m.label}>
                  {taxonomy.cuisines
                    .filter((c) => c.macro === m.code)
                    .map((c) => (
                      <option key={c.code} value={c.code}>
                        {c.label}
                      </option>
                    ))}
                </optgroup>
              ))}
            </select>
          )}
        </Field>
        <ListOfSelects
          id="lugar-secondaryCuisines"
          legend={`Outras culinárias (até ${MAX_SECONDARY_CUISINES})`}
          max={MAX_SECONDARY_CUISINES}
          values={values.secondaryCuisines}
          onChange={(v) => set('secondaryCuisines', v)}
          options={taxonomy.cuisines.filter((c) => c.code !== values.primaryCuisine).map((c) => ({ value: c.code, label: c.label }))}
          itemLabel="Outra culinária"
        />
        <Field id="lugar-primaryFormat" label="Tipo de comida principal" errors={err('primaryFormat')}>
          {(a) => (
            <select {...a} className="fe-input" value={values.primaryFormat} onChange={(e) => set('primaryFormat', e.target.value)}>
              <option value="">Nenhum</option>
              {taxonomy.foodFamilies.map((f) => (
                <optgroup key={f.code} label={f.label}>
                  {taxonomy.formats
                    .filter((x) => x.family === f.code)
                    .map((x) => (
                      <option key={x.code} value={x.code}>
                        {x.label}
                      </option>
                    ))}
                </optgroup>
              ))}
            </select>
          )}
        </Field>
        <ListOfSelects
          id="lugar-secondaryFormats"
          legend={`Outros tipos de comida (até ${MAX_SECONDARY_FORMATS})`}
          max={MAX_SECONDARY_FORMATS}
          values={values.secondaryFormats}
          onChange={(v) => set('secondaryFormats', v)}
          options={taxonomy.formats.filter((f) => f.code !== values.primaryFormat).map((f) => ({ value: f.code, label: f.label }))}
          itemLabel="Outro tipo de comida"
        />
        <Field id="lugar-approach" label="Abordagem">
          {(a) => (
            <select {...a} className="fe-input" value={values.approach} onChange={(e) => set('approach', e.target.value)}>
              {taxonomy.approaches.map((x) => (
                <option key={x.code} value={x.code}>
                  {x.label}
                </option>
              ))}
            </select>
          )}
        </Field>
        <Group id="lugar-dietary" legend="Estilos alimentares que a casa atende" errors={err('dietary')} hint="Oferta da casa (não é dado de quem come).">
          <div className="grid gap-2 sm:grid-cols-2">
            {taxonomy.dietaryStyles.map((d) => (
              <label key={d.code} className="grid gap-1">
                <span>{d.label}</span>
                <select
                  className="fe-input"
                  value={values.dietary[d.code] ?? ''}
                  onChange={(e) => set('dietary', { ...values.dietary, [d.code]: e.target.value as '' | 'OFERECE' | 'EXCLUSIVO' })}
                >
                  <option value="">Não informado</option>
                  <option value="OFERECE">Oferece opções</option>
                  <option value="EXCLUSIVO">Só serve assim</option>
                </select>
              </label>
            ))}
          </div>
        </Group>
      </Section>

      <Section title="Local">
        <div className="grid gap-4 sm:grid-cols-2">
          <Field id="lugar-uf" label="UF" required errors={err('uf')}>
            {(a) => (
              <select {...a} className="fe-input" value={values.uf} onChange={(e) => set('uf', e.target.value)}>
                <option value="">Escolha…</option>
                {UFS.map((u) => (
                  <option key={u.sigla} value={u.sigla}>
                    {u.nome}
                  </option>
                ))}
              </select>
            )}
          </Field>
          <Field id="lugar-municipalityIbge" label="Código IBGE do município" required errors={err('municipalityIbge')} hint="7 dígitos, ex.: 3106200 (Belo Horizonte).">
            {(a) => (
              <input {...a} className="fe-input" inputMode="numeric" maxLength={7} value={values.municipalityIbge} onChange={(e) => set('municipalityIbge', e.target.value.replace(/\D/g, ''))} />
            )}
          </Field>
          <Field id="lugar-city" label="Cidade" required errors={err('city')}>
            {(a) => <input {...a} className="fe-input" maxLength={120} value={values.city} onChange={(e) => set('city', e.target.value)} />}
          </Field>
          <Field id="lugar-neighborhood" label="Bairro">
            {(a) => <input {...a} className="fe-input" maxLength={120} value={values.neighborhood} onChange={(e) => set('neighborhood', e.target.value)} />}
          </Field>
          <Field id="lugar-street" label="Rua">
            {(a) => <input {...a} className="fe-input" maxLength={160} value={values.street} onChange={(e) => set('street', e.target.value)} />}
          </Field>
          <Field id="lugar-streetNumber" label="Número">
            {(a) => <input {...a} className="fe-input" maxLength={20} value={values.streetNumber} onChange={(e) => set('streetNumber', e.target.value)} />}
          </Field>
          <Field id="lugar-complement" label="Complemento">
            {(a) => <input {...a} className="fe-input" maxLength={80} value={values.complement} onChange={(e) => set('complement', e.target.value)} />}
          </Field>
          <Field id="lugar-cep" label="CEP" errors={err('cep')}>
            {(a) => <input {...a} className="fe-input" inputMode="numeric" autoComplete="postal-code" maxLength={9} value={values.cep} onChange={(e) => set('cep', e.target.value)} />}
          </Field>
        </div>
        <Group
          id="lugar-addressVisibility"
          legend="O que o público vê do endereço"
          errors={err('addressVisibility')}
          hint="Cozinha caseira com CPF mostra só o bairro: o endereço é a sua casa. Você sempre vê o endereço completo."
        >
          {(
            [
              ['ADDR_PUBLIC_FULL', 'Endereço completo e o pino'],
              ['ADDR_PUBLIC_BAIRRO', 'Só o bairro e a cidade, com uma área aproximada (para quem atende em casa)'],
              ['ADDR_HIDDEN', 'Só a cidade, sem mapa'],
            ] as const
          ).map(([v, label]) => (
            <label key={v} className="flex items-start gap-2">
              <input type="radio" name="addressVisibility" className="mt-1" checked={values.addressVisibility === v} onChange={() => set('addressVisibility', v)} />
              <span>{label}</span>
            </label>
          ))}
        </Group>
        <Group id="lugar-pin" legend="Pino no mapa" errors={err('pin')} hint="Arraste o pino ou clique no mapa. Sem mouse: navegue o mapa com as setas e use o botão do centro, ou digite as coordenadas.">
          <PlaceMap
            lat={hasPin ? lat : BRAZIL_CENTER.lat}
            lng={hasPin ? lng : BRAZIL_CENTER.lng}
            label="Mapa para marcar o local do lugar"
            onMove={(la, ln) => setValues((old) => ({ ...old, latitude: String(la), longitude: String(ln) }))}
            onReady={(fn) => (mapCenter.current = fn)}
          />
          <div className="flex flex-wrap items-end gap-3">
            <label className="grid gap-1">
              <span>Latitude</span>
              <input className="fe-input w-40" inputMode="decimal" value={values.latitude} onChange={(e) => set('latitude', e.target.value)} />
            </label>
            <label className="grid gap-1">
              <span>Longitude</span>
              <input className="fe-input w-40" inputMode="decimal" value={values.longitude} onChange={(e) => set('longitude', e.target.value)} />
            </label>
            <button
              type="button"
              className="fe-btn fe-btn-sm"
              onClick={() => {
                const c = mapCenter.current?.();
                if (c) setValues((old) => ({ ...old, latitude: String(c.lat), longitude: String(c.lng) }));
              }}
            >
              Pôr o pino no centro do mapa
            </button>
            {hasPin && (
              <button type="button" className="fe-btn fe-btn-sm" onClick={() => setValues((old) => ({ ...old, latitude: '', longitude: '' }))}>
                Tirar o pino
              </button>
            )}
          </div>
        </Group>
      </Section>

      <Section title="Preço por pessoa">
        <p className="text-sm text-muted">Gasto típico de uma pessoa: prato e bebida não alcoólica, com a taxa de serviço quando cobrada.</p>
        <div className="grid gap-4 sm:grid-cols-2">
          <Field id="lugar-priceMin" label="De (R$)" errors={err('priceMin')}>
            {(a) => <input {...a} className="fe-input" inputMode="decimal" disabled={values.priceNotApplicable} value={values.priceMin} onChange={(e) => set('priceMin', e.target.value)} />}
          </Field>
          <Field id="lugar-priceMax" label="Até (R$)">
            {(a) => <input {...a} className="fe-input" inputMode="decimal" disabled={values.priceNotApplicable} value={values.priceMax} onChange={(e) => set('priceMax', e.target.value)} />}
          </Field>
        </div>
        {(group === 'VAREJO' || group === 'POLO') && (
          <label className="flex items-center gap-2">
            <input type="checkbox" checked={values.priceNotApplicable} onChange={(e) => set('priceNotApplicable', e.target.checked)} />
            Não se aplica (varejo sem refeição)
          </label>
        )}
      </Section>

      <HoursFields values={values} set={set} errors={errors} />

      <ChargesFields values={values} set={set} errors={errors} taxonomy={taxonomy} />

      <Section title="Comodidades, acessibilidade e pagamento">
        <Group id="lugar-facets" legend="O que o lugar oferece" errors={err('facets')} hint="Deixe em &ldquo;Sem informação&rdquo; o que você não sabe. O que aparece na página é &ldquo;declarado pelo estabelecimento&rdquo;.">
          {(['ATENDIMENTO', 'AMBIENTE', 'ACESSIBILIDADE', 'PAGAMENTO'] as const).map((section) => {
            const items = taxonomy.facets.filter((f) => f.section === section && (f.declarable || f.fixed) && (!group || f.groups.includes(group)));
            if (!items.length) return null;
            return (
              <fieldset key={section} className="grid gap-2 border-t border-line pt-3">
                <legend className="font-bold">{items[0]!.sectionLabel}</legend>
                <div className="grid gap-2 sm:grid-cols-2">
                  {items.map((f) =>
                    f.fixed ? (
                      <p key={f.code} className="text-sm">
                        <span className="font-semibold">{f.label}</span>: {f.values[0]?.label} · fixo, não se edita
                      </p>
                    ) : (
                      <label key={f.code} className="grid gap-1">
                        <span>{f.label}</span>
                        <select className="fe-input" value={values.facets[f.code] ?? ''} onChange={(e) => set('facets', { ...values.facets, [f.code]: e.target.value })}>
                          <option value="">Sem informação</option>
                          {f.values.map((v) => (
                            <option key={v.code} value={v.code}>
                              {v.label}
                            </option>
                          ))}
                        </select>
                      </label>
                    ),
                  )}
                </div>
              </fieldset>
            );
          })}
          <p className="text-sm text-muted">Refeições (café da manhã, almoço, jantar e madrugada) saem do horário; &ldquo;entrega por aplicativo&rdquo;, dos links de delivery.</p>
        </Group>
      </Section>

      <ActionFields values={values} set={set} errors={errors} taxonomy={taxonomy} />

      {editing && (
        <Section title="Situação">
          <Group id="lugar-status" legend="O lugar" errors={err('status')}>
            <label className="flex items-center gap-2">
              <input type="radio" name="status" checked={values.status === 'ACTIVE'} onChange={() => set('status', 'ACTIVE')} />
              Está funcionando (para uma pausa, use &ldquo;fechado temporariamente&rdquo; nos horários)
            </label>
            <label className="flex items-center gap-2">
              <input type="radio" name="status" checked={values.status === 'CLOSED'} onChange={() => set('status', 'CLOSED')} />
              Fechou de vez (a página continua no ar, com as avaliações)
            </label>
          </Group>
        </Section>
      )}

      <div className="flex flex-wrap gap-3">
        <button type="submit" className="fe-btn fe-btn-primary" disabled={save.isPending}>
          {save.isPending ? 'Salvando…' : editing ? 'Salvar alterações' : 'Cadastrar lugar'}
        </button>
        <Link href={place ? `/lugares/${place.id}` : '/conta'} className="fe-btn">
          Cancelar
        </Link>
      </div>
    </form>
  );
}

// ------------------------------------------------------------ blocos

type Setter = <K extends keyof PlaceFormValues>(k: K, v: PlaceFormValues[K]) => void;

function HoursFields({ values, set, errors }: { values: PlaceFormValues; set: Setter; errors: PlaceErrors }) {
  const setDay = (day: WeekDay, ranges: PlaceFormValues['weekly'][WeekDay]) => set('weekly', { ...values.weekly, [day]: ranges });
  return (
    <Section title="Horários">
      <label className="flex items-center gap-2">
        <input type="checkbox" checked={values.hoursInformed} onChange={(e) => set('hoursInformed', e.target.checked)} />
        Informar o horário da semana
      </label>
      {!values.hoursInformed && <p className="text-sm text-muted">Sem horário, a página mostra &ldquo;Horário não informado&rdquo; (nunca &ldquo;Fechado&rdquo;).</p>}
      {values.hoursInformed && (
        <div className="grid gap-3">
          <p className="text-sm text-muted">
            Até {MAX_RANGES} faixas por dia (ex.: almoço e jantar). Faixa que passa da meia-noite (18:00 às 02:00) fica no dia em que começa. Dia sem faixa = fechado.
          </p>
          {WEEK_DAYS.map((d) => {
            const ranges = values.weekly[d.code];
            const id = `lugar-hours-${d.code}`;
            return (
              <Group key={d.code} id={id} legend={d.label} errors={errors[`hours-${d.code}`]}>
                {ranges.length === 0 && <p className="text-sm text-muted">Fechado</p>}
                {ranges.map((r, i) => (
                  <div key={i} className="flex flex-wrap items-end gap-2">
                    <label className="grid gap-1">
                      <span className="text-sm">Abre ({i + 1}ª faixa)</span>
                      <input type="time" className="fe-input" value={r.opens} onChange={(e) => setDay(d.code, ranges.map((x, j) => (j === i ? { ...x, opens: e.target.value } : x)))} />
                    </label>
                    <label className="grid gap-1">
                      <span className="text-sm">Fecha ({i + 1}ª faixa)</span>
                      <input type="time" className="fe-input" value={r.closes} onChange={(e) => setDay(d.code, ranges.map((x, j) => (j === i ? { ...x, closes: e.target.value } : x)))} />
                    </label>
                    <button type="button" className="fe-btn fe-btn-sm" onClick={() => setDay(d.code, ranges.filter((_, j) => j !== i))}>
                      <IconDelete size={16} />
                      Remover faixa {i + 1} de {d.label.toLowerCase()}
                    </button>
                  </div>
                ))}
                {ranges.length < MAX_RANGES && (
                  <p>
                    <button type="button" className="fe-btn fe-btn-sm" onClick={() => setDay(d.code, [...ranges, { opens: '', closes: '' }])}>
                      Adicionar faixa em {d.label.toLowerCase()}
                    </button>
                  </p>
                )}
              </Group>
            );
          })}
        </div>
      )}

      <Group id="lugar-special" legend="Feriados e datas especiais" errors={errors.special} hint="Substituem o horário da semana na data. Para fechar mais de 7 dias seguidos, use &ldquo;fechado temporariamente&rdquo;.">
        {values.special.map((s, i) => {
          const update = (patch: Partial<typeof s>) => set('special', values.special.map((x, j) => (j === i ? { ...x, ...patch } : x)));
          const range = s.ranges[0] ?? { opens: '', closes: '' };
          return (
            <div key={i} className="grid gap-2 border-t border-line pt-2 sm:grid-cols-2">
              <label className="grid gap-1">
                <span>Data {i + 1}</span>
                <input type="date" className="fe-input" value={s.date} onChange={(e) => update({ date: e.target.value })} />
              </label>
              <label className="grid gap-1">
                <span>Motivo da data {i + 1} (opcional)</span>
                <input className="fe-input" maxLength={60} value={s.note} onChange={(e) => update({ note: e.target.value })} />
              </label>
              <label className="flex items-center gap-2">
                <input type="checkbox" checked={s.closed} onChange={(e) => update({ closed: e.target.checked })} />
                Fechado o dia todo
              </label>
              {!s.closed && (
                <div className="flex flex-wrap gap-2">
                  <label className="grid gap-1">
                    <span className="text-sm">Abre</span>
                    <input type="time" className="fe-input" value={range.opens} onChange={(e) => update({ ranges: [{ ...range, opens: e.target.value }] })} />
                  </label>
                  <label className="grid gap-1">
                    <span className="text-sm">Fecha</span>
                    <input type="time" className="fe-input" value={range.closes} onChange={(e) => update({ ranges: [{ ...range, closes: e.target.value }] })} />
                  </label>
                </div>
              )}
              <p>
                <button type="button" className="fe-btn fe-btn-sm" onClick={() => set('special', values.special.filter((_, j) => j !== i))}>
                  <IconDelete size={16} />
                  Remover data {i + 1}
                </button>
              </p>
            </div>
          );
        })}
        <p>
          <button type="button" className="fe-btn fe-btn-sm" onClick={() => set('special', [...values.special, { date: '', closed: true, ranges: [], note: '' }])}>
            Adicionar data especial
          </button>
        </p>
      </Group>

      <Group id="lugar-reopensOn" legend="Pausa" errors={errors.reopensOn}>
        <label className="flex items-center gap-2">
          <input type="checkbox" checked={values.tempClosed} onChange={(e) => set('tempClosed', e.target.checked)} />
          Fechado temporariamente (reforma, férias)
        </label>
        {values.tempClosed && (
          <label className="grid gap-1">
            <span>Data de volta (opcional)</span>
            <input type="date" className="fe-input w-48" value={values.reopensOn} onChange={(e) => set('reopensOn', e.target.value)} />
          </label>
        )}
        <p className="text-sm text-muted">Passada a data de volta sem você desmarcar a pausa, a página avisa &ldquo;Pode estar fechado&rdquo;.</p>
      </Group>
    </Section>
  );
}

function ChargesFields({ values, set, errors, taxonomy }: { values: PlaceFormValues; set: Setter; errors: PlaceErrors; taxonomy: TaxonomyCatalog }) {
  const methodLabel = (code: string) => taxonomy.facets.find((f) => f.code === code)?.label ?? code;
  return (
    <Section title="Cobranças declaradas">
      <Notice>
        <p>
          A página não substitui o aviso no local: a lista de preços continua afixada na entrada (Decreto 5.903/2006,
          art. 8º, § 2º). A taxa de serviço aparece sempre como <strong>opcional</strong> (Lei 13.419/2017), e consumação
          mínima não é aceita.
        </p>
      </Notice>
      <Field id="lugar-serviceFee" label="Taxa de serviço (%)" errors={errors.serviceFee} hint="De 1 a 20. Vazio ou 0 = sem taxa.">
        {(a) => <input {...a} className="fe-input w-32" inputMode="numeric" maxLength={2} value={values.serviceFee} onChange={(e) => set('serviceFee', e.target.value)} />}
      </Field>
      <Group id="lugar-couvert" legend="Couvert" errors={errors.couvert}>
        <label className="grid gap-1">
          <span>Tipo de couvert</span>
          <select className="fe-input" value={values.couvertKind} onChange={(e) => set('couvertKind', e.target.value as PlaceFormValues['couvertKind'])}>
            <option value="">Sem couvert</option>
            {taxonomy.couvertKinds.map((k) => (
              <option key={k.code} value={k.code}>
                {k.label}
              </option>
            ))}
          </select>
        </label>
        {values.couvertKind && (
          <label className="grid gap-1">
            <span>Valor por pessoa (R$)</span>
            <input className="fe-input w-40" inputMode="decimal" value={values.couvertValue} onChange={(e) => set('couvertValue', e.target.value)} />
          </label>
        )}
        {values.couvertKind === 'ARTISTICO' && (
          <fieldset className="grid gap-1">
            <legend>Dias com apresentação ao vivo</legend>
            <div className="flex flex-wrap gap-3">
              {WEEK_DAYS.map((d) => (
                <label key={d.code} className="flex items-center gap-1.5">
                  <input
                    type="checkbox"
                    checked={values.couvertDays.includes(d.code)}
                    onChange={(e) => set('couvertDays', e.target.checked ? [...values.couvertDays, d.code] : values.couvertDays.filter((x) => x !== d.code))}
                  />
                  {d.label}
                </label>
              ))}
            </div>
          </fieldset>
        )}
        {values.couvertKind === 'ENTRADA_MESA' && <p className="text-sm text-muted">Aparece como &ldquo;cobrado só se você aceitar&rdquo;.</p>}
      </Group>
      <Group id="lugar-adjustments" legend="Desconto ou acréscimo por forma de pagamento" errors={errors.adjustments} hint="Ex.: 5% de desconto no Pix (Lei 10.962/2004, art. 5º-A).">
        {values.adjustments.map((a, i) => {
          const update = (patch: Partial<typeof a>) => set('adjustments', values.adjustments.map((x, j) => (j === i ? { ...x, ...patch } : x)));
          return (
            <div key={i} className="flex flex-wrap items-end gap-2">
              <label className="grid gap-1">
                <span className="text-sm">Forma de pagamento {i + 1}</span>
                <select className="fe-input" value={a.method} onChange={(e) => update({ method: e.target.value })}>
                  <option value="">Escolha…</option>
                  {PAYMENT_METHODS.map((m) => (
                    <option key={m} value={m}>
                      {methodLabel(m)}
                    </option>
                  ))}
                </select>
              </label>
              <label className="grid gap-1">
                <span className="text-sm">Condição {i + 1}</span>
                <select className="fe-input" value={a.kind} onChange={(e) => update({ kind: e.target.value as 'DESCONTO' | 'ACRESCIMO' })}>
                  <option value="DESCONTO">Desconto</option>
                  <option value="ACRESCIMO">Acréscimo</option>
                </select>
              </label>
              <label className="grid gap-1">
                <span className="text-sm">Percentual {i + 1} (%)</span>
                <input className="fe-input w-24" inputMode="numeric" maxLength={2} value={a.percent} onChange={(e) => update({ percent: e.target.value })} />
              </label>
              <button type="button" className="fe-btn fe-btn-sm" onClick={() => set('adjustments', values.adjustments.filter((_, j) => j !== i))}>
                <IconDelete size={16} />
                Remover condição {i + 1}
              </button>
            </div>
          );
        })}
        <p>
          <button type="button" className="fe-btn fe-btn-sm" onClick={() => set('adjustments', [...values.adjustments, { method: '', kind: 'DESCONTO', percent: '' }])}>
            Adicionar condição de pagamento
          </button>
        </p>
      </Group>
    </Section>
  );
}

function ActionFields({ values, set, errors, taxonomy }: { values: PlaceFormValues; set: Setter; errors: PlaceErrors; taxonomy: TaxonomyCatalog }) {
  const reserve = taxonomy.actionProviders.filter((p) => p.kind === 'RESERVA');
  const delivery = taxonomy.actionProviders.filter((p) => p.kind === 'DELIVERY');
  const linkField = (p: (typeof reserve)[number]) => (
    <Field key={p.code} id={`lugar-link-${p.code}`} label={`Link do ${p.label}`} hint={`HTTPS, de ${p.domains.join(' ou ')}.`} errors={errors[`link-${p.code}`]}>
      {(a) => (
        <input {...a} type="url" className="fe-input" inputMode="url" placeholder="https://" value={values.links[p.code] ?? ''} onChange={(e) => set('links', { ...values.links, [p.code]: e.target.value })} />
      )}
    </Field>
  );
  return (
    <Section title="Botões de reserva, delivery e WhatsApp">
      <p className="text-sm text-muted">
        Só links: o Friendly Eats não faz reserva, pedido nem pagamento. Cada botão abre o site ou app do parceiro, e só
        aceitamos os domínios oficiais de cada um.
      </p>
      <Group id="lugar-links" legend="Links" errors={errors.links}>
        <fieldset className="grid gap-3">
          <legend className="font-bold">Reserva</legend>
          {reserve.map(linkField)}
        </fieldset>
        <fieldset className="grid gap-3 border-t border-line pt-3">
          <legend className="font-bold">Delivery</legend>
          {delivery.map(linkField)}
          <label className="grid gap-1">
            <span>App de delivery preferido (aparece primeiro)</span>
            <select className="fe-input" value={values.preferredDelivery} onChange={(e) => set('preferredDelivery', e.target.value)}>
              <option value="">Nenhum</option>
              {delivery
                .filter((p) => values.links[p.code]?.trim())
                .map((p) => (
                  <option key={p.code} value={p.code}>
                    {p.label}
                  </option>
                ))}
            </select>
          </label>
        </fieldset>
      </Group>
      <Field id="lugar-whatsapp" label="WhatsApp do estabelecimento" hint="Com DDD, ex.: (31) 99999-8888. O botão abre wa.me com esse número." errors={errors.whatsapp}>
        {(a) => <input {...a} type="tel" className="fe-input w-60" autoComplete="tel" value={values.whatsapp} onChange={(e) => set('whatsapp', e.target.value)} />}
      </Field>
    </Section>
  );
}

// ------------------------------------------------------------ apoio

function Section({ title, children }: { title: string; children: ReactNode }) {
  const id = `secao-${title.toLowerCase().normalize('NFD').replace(/[^a-z]+/g, '-')}`;
  return (
    <section aria-labelledby={id} className="fe-card grid gap-4 p-5">
      <h2 id={id} className="text-xl font-bold">
        {title}
      </h2>
      {children}
    </section>
  );
}

/** Grupo de controles com legenda, dica e erros ligados por aria-describedby. */
function Group({ id, legend, hint, errors, children }: { id: string; legend: string; hint?: ReactNode; errors?: string[]; children: ReactNode }) {
  const hintId = hint ? `${id}-dica` : null;
  const errId = errors?.length ? `${id}-erro` : null;
  return (
    <fieldset id={id} tabIndex={-1} className="grid gap-2" aria-describedby={describedBy(hintId, errId)}>
      <legend className="font-semibold">{legend}</legend>
      {hint && (
        <p id={hintId!} className="text-sm text-muted">
          {hint}
        </p>
      )}
      {children}
      <FieldErrors id={`${id}-erro`} errors={errors} />
    </fieldset>
  );
}

/** Até {@code max} selects, um a um (aparece mais um quando o anterior é preenchido). */
function ListOfSelects(props: {
  id: string;
  legend: string;
  max: number;
  values: string[];
  onChange: (v: string[]) => void;
  options: Array<{ value: string; label: string }>;
  itemLabel: string;
}) {
  const shown = useMemo(() => {
    const filled = props.values.filter(Boolean);
    return filled.length < props.max ? [...filled, ''] : filled;
  }, [props.values, props.max]);
  return (
    <fieldset id={props.id} className="grid gap-2">
      <legend className="font-semibold">{props.legend}</legend>
      <div className="grid gap-2 sm:grid-cols-2">
        {shown.map((v, i) => (
          <label key={i} className="grid gap-1">
            <span className="text-sm">
              {props.itemLabel} {i + 1}
            </span>
            <select
              className="fe-input"
              value={v}
              onChange={(e) => {
                const next = [...shown];
                next[i] = e.target.value;
                props.onChange(next.filter(Boolean));
              }}
            >
              <option value="">Nenhum</option>
              {props.options
                .filter((o) => o.value === v || !shown.includes(o.value))
                .map((o) => (
                  <option key={o.value} value={o.value}>
                    {o.label}
                  </option>
                ))}
            </select>
          </label>
        ))}
      </div>
    </fieldset>
  );
}
