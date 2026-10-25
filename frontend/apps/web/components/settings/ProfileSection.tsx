'use client';

import { isApiError, type ProfilePatch, type ProfileView, type SessionUserView, type TaxonomyCatalog } from '@friendly-eats/api';
import { useMutation, useQueryClient } from '@tanstack/react-query';
import { useId, useState, type FormEvent } from 'react';
import { AccountBadge, verificationText } from '@/components/AccountBadge';
import { DetailInput } from '@/components/auth/RegisterView';
import { ErrorSummary, Field, Notice, useDeferredFocus } from '@/components/Form';
import { api } from '@/lib/api';
import { mapProfileErrors, type MappedErrors } from '@/lib/field-errors';
import { formatDate } from '@/lib/format';
import { PROFILE_KEY, SESSION_KEY, useConsents, useProfile, useSession, useTaxonomy } from '@/lib/queries';
import { buildTypeDetails, fieldsFor } from '@/lib/register-fields';
import { UFS } from '@/lib/uf';
import { PhotoField } from './PhotoField';
import { SectionHeading } from './SectionHeading';
import { MAX_FAVORITE_CUISINES, TasteFields } from './TasteFields';

const HANDLE = /^[a-z0-9._]{3,30}$/;
const PHONE = /^\+[1-9]\d{7,14}$/;
const EMPTY: MappedErrors = { fields: {}, general: [] };

/** Configurações → Perfil (RF2). */
export function ProfileSection() {
  const profile = useProfile();
  const taxonomy = useTaxonomy();
  const consents = useConsents();
  const session = useSession();

  const sensitive = consents.data?.find((c) => c.purpose === 'SENSITIVE_PROFILE_DATA');
  return (
    <>
      <SectionHeading description="O que aparece no seu perfil. E-mail, senha, data de nascimento e tipo de conta não mudam por aqui.">
        Perfil
      </SectionHeading>
      {(profile.isPending || taxonomy.isPending) && <p className="text-muted">Carregando…</p>}
      {(profile.isError || taxonomy.isError) && (
        <Notice tone="error">
          <p>Não foi possível carregar o seu perfil. Recarregue a página.</p>
        </Notice>
      )}
      {profile.data && taxonomy.data && session.data && (
        <>
          <PhotoField profile={profile.data} />
          <ProfileForm
            profile={profile.data}
            taxonomy={taxonomy.data}
            user={session.data}
            sensitiveGranted={consents.isPending ? false : consents.isError ? null : (sensitive?.granted ?? false)}
            sensitiveLocked={sensitive?.lockedForMinor === true && !sensitive.granted}
          />
        </>
      )}
    </>
  );
}

function asText(v: unknown): string {
  return v === null || v === undefined ? '' : String(v);
}

function initialDetails(profile: ProfileView): Record<string, string> {
  return Object.fromEntries(Object.entries(profile.typeDetails ?? {}).map(([k, v]) => [k, asText(v)]));
}

function splitList(text: string, sep: RegExp): string[] {
  return text
    .split(sep)
    .map((s) => s.trim())
    .filter(Boolean);
}

const sameList = (a: readonly string[], b: readonly string[]) => a.length === b.length && a.every((x, i) => x === b[i]);
const sameSet = (a: readonly string[], b: readonly string[]) => a.length === b.length && a.every((x) => b.includes(x));

function ProfileForm({
  profile,
  taxonomy,
  user,
  sensitiveGranted,
  sensitiveLocked,
}: {
  profile: ProfileView;
  taxonomy: TaxonomyCatalog;
  user: SessionUserView;
  sensitiveGranted: boolean | null;
  sensitiveLocked: boolean;
}) {
  const uid = useId();
  const id = (f: string) => `${uid}-${f}`;
  const queryClient = useQueryClient();
  const [summaryRef, focusSummary] = useDeferredFocus<HTMLDivElement>();
  const [savedRef, focusSaved] = useDeferredFocus<HTMLDivElement>();
  const [values, setValues] = useState({
    name: profile.name,
    handle: profile.handle,
    bio: profile.bio ?? '',
    pronouns: profile.pronouns.join(', '),
    links: profile.links.join('\n'),
    countryOfResidence: profile.countryOfResidence ?? 'BR',
    stateOfResidence: profile.stateOfResidence ?? '',
    cityOfResidence: profile.cityOfResidence ?? '',
    recoveryEmail: profile.recoveryEmail ?? '',
    cellphoneNumber: profile.cellphoneNumber ?? '',
  });
  const [details, setDetails] = useState<Record<string, string>>(() => initialDetails(profile));
  const [informal, setInformal] = useState(() => 'informalFormat' in (profile.typeDetails ?? {}));
  const [cuisines, setCuisines] = useState<string[]>(profile.favoriteCuisines ?? []);
  const [styles, setStyles] = useState<string[]>(profile.dietaryStyles ?? []);
  const [errors, setErrors] = useState<MappedErrors>(EMPTY);
  const [saved, setSaved] = useState<string | null>(null);

  const type = profile.category.code;
  const detailFields = fieldsFor(type, informal, user.minor || 'guardianEmail' in (profile.typeDetails ?? {}));
  const detailLabels = Object.fromEntries(detailFields.map((f) => [f.key, f.label]));
  const set = (k: keyof typeof values) => (e: { target: { value: string } }) => setValues((v) => ({ ...v, [k]: e.target.value }));
  const err = (f: string) => errors.fields[f];

  /** Só o que mudou vai no PATCH: mandar typeDetails igual poderia mandar a conta com selo de volta à conferência. */
  function buildPatch(): ProfilePatch {
    const p: ProfilePatch = {};
    const name = values.name.trim();
    const handle = values.handle.trim();
    if (name !== profile.name) p.name = name;
    if (handle !== profile.handle) p.handle = handle;
    if (values.bio.trim() !== (profile.bio ?? '')) p.bio = values.bio.trim();
    const pronouns = splitList(values.pronouns, /,/);
    if (!sameList(pronouns, profile.pronouns)) p.pronouns = pronouns;
    const links = splitList(values.links, /\n/);
    if (!sameList(links, profile.links)) p.links = links;
    const country = values.countryOfResidence.trim().toUpperCase();
    if (country !== (profile.countryOfResidence ?? '') && !(country === 'BR' && !profile.countryOfResidence && !values.stateOfResidence && !values.cityOfResidence))
      p.countryOfResidence = country;
    if (values.stateOfResidence.trim() !== (profile.stateOfResidence ?? '')) p.stateOfResidence = values.stateOfResidence.trim();
    if (values.cityOfResidence.trim() !== (profile.cityOfResidence ?? '')) p.cityOfResidence = values.cityOfResidence.trim();
    if (values.recoveryEmail.trim() !== (profile.recoveryEmail ?? '')) p.recoveryEmail = values.recoveryEmail.trim();
    const phone = values.cellphoneNumber.replace(/[\s()-]/g, '');
    if (phone !== (profile.cellphoneNumber ?? '')) p.cellphoneNumber = phone;
    const typeDetails = buildTypeDetails(detailFields, details);
    const before = initialDetails(profile);
    const changed =
      Object.keys(typeDetails).length !== Object.keys(before).length || Object.entries(typeDetails).some(([k, v]) => before[k] !== v);
    if (changed) p.typeDetails = typeDetails;
    if (!sameSet(cuisines, profile.favoriteCuisines ?? [])) p.favoriteCuisines = cuisines;
    // sem o consentimento sensível a API recusa estilos (403); a lista vazia ela sempre aceita
    if ((sensitiveGranted === true || styles.length === 0) && !sameSet(styles, profile.dietaryStyles ?? [])) p.dietaryStyles = styles;
    return p;
  }

  function validate(): MappedErrors {
    const out: MappedErrors = { fields: {}, general: [] };
    const add = (f: string, m: string) => (out.fields[f] ??= []).push(m);
    const name = values.name.trim();
    if (name.length < 2 || name.length > 120) add('name', 'Informe um nome de exibição com 2 a 120 caracteres.');
    if (!HANDLE.test(values.handle.trim())) add('handle', 'O @usuário deve ter 3 a 30 caracteres: letras minúsculas, números, ponto ou sublinhado.');
    if (values.bio.length > 150) add('bio', 'A bio tem no máximo 150 caracteres.');
    const pronouns = splitList(values.pronouns, /,/);
    if (pronouns.length > 4 || pronouns.some((x) => x.length > 20)) add('pronouns', 'Até 4 pronomes, com até 20 caracteres cada.');
    const links = splitList(values.links, /\n/);
    if (links.length > 5 || links.some((l) => !/^https:\/\/\S{4,200}$/.test(l))) add('links', 'Até 5 links, cada um começando com https://');
    const country = values.countryOfResidence.trim();
    if (country && !/^[A-Za-z]{2}$/.test(country)) add('countryOfResidence', 'País no formato ISO de 2 letras, ex.: BR.');
    const recovery = values.recoveryEmail.trim();
    if (recovery && (!/^[^@\s]+@[^@\s]+\.[^@\s]+$/.test(recovery) || recovery.toLowerCase() === profile.email.toLowerCase()))
      add('recoveryEmail', 'O e-mail de recuperação precisa ser válido e diferente do e-mail de login.');
    const phone = values.cellphoneNumber.replace(/[\s()-]/g, '');
    if (phone && !PHONE.test(phone)) add('cellphoneNumber', 'Celular no formato internacional, ex.: +5541999990000.');
    for (const f of detailFields) {
      if (f.required && !details[f.key]?.trim()) add(f.key, `Preencha ${f.label.toLowerCase()}.`);
    }
    if (cuisines.length > MAX_FAVORITE_CUISINES) add('favoriteCuisines', `Escolha até ${MAX_FAVORITE_CUISINES} culinárias.`);
    return out;
  }

  const save = useMutation({
    mutationFn: (patch: ProfilePatch) => api.me.updateProfile(patch),
    onSuccess: (view) => {
      const backToReview = profile.categoryVerification === 'VERIFIED' && view.categoryVerification === 'PENDING';
      queryClient.setQueryData(PROFILE_KEY, view);
      void queryClient.invalidateQueries({ queryKey: SESSION_KEY });
      setCuisines(view.favoriteCuisines ?? []);
      setStyles(view.dietaryStyles ?? []);
      setSaved(backToReview ? 'Perfil salvo. Como a conta tem selo, os dados voltaram para a conferência.' : 'Perfil salvo.');
      focusSaved();
    },
    onError: (e) => {
      if (isApiError(e, 422)) {
        setErrors(mapProfileErrors(e.errors.length ? e.errors : [e.detail], detailLabels));
      } else if (isApiError(e, 409)) {
        setErrors({ fields: { handle: [e.detail] }, general: [] });
      } else if (isApiError(e, 403) && /estilo/i.test(e.detail)) {
        setErrors({ fields: { dietaryStyles: [e.detail] }, general: [] });
      } else {
        setErrors({ fields: {}, general: [isApiError(e) ? e.detail : 'Não foi possível salvar agora. Tente de novo.'] });
      }
      focusSummary();
    },
  });

  function submit(e: FormEvent) {
    e.preventDefault();
    setSaved(null);
    const local = validate();
    if (Object.keys(local.fields).length || local.general.length) {
      setErrors(local);
      focusSummary();
      return;
    }
    setErrors(EMPTY);
    const patch = buildPatch();
    if (!Object.keys(patch).length) {
      setSaved('Nada mudou desde a última vez que você salvou.');
      focusSaved();
      return;
    }
    save.mutate(patch);
  }

  const fieldId = (f: string) => (f === 'favoriteCuisines' ? id('gosto-favoriteCuisines') : f === 'dietaryStyles' ? `${id('gosto')}-estilo-${taxonomy.dietaryStyles[0]?.code}` : id(f));
  const summary = [
    ...Object.entries(errors.fields).flatMap(([f, msgs]) => msgs.map((message) => ({ message, fieldId: fieldId(f) }))),
    ...errors.general.map((message) => ({ message })),
  ];
  const seal = verificationText(profile.categoryVerification);
  const brazil = values.countryOfResidence.trim().toUpperCase() === 'BR';

  return (
    <form noValidate onSubmit={submit} className="grid gap-6">
      <ErrorSummary ref={summaryRef} items={summary} />

      <section aria-labelledby={id('titulo-dados')} className="fe-card grid gap-4 p-5">
        <h2 id={id('titulo-dados')} className="text-xl font-bold">
          Dados do perfil
        </h2>
        {profile.categoryVerification === 'VERIFIED' && (
          <Notice>
            <p>A conta tem selo: trocar o nome, o @usuário ou os dados do tipo de conta manda os dados de volta para a conferência.</p>
          </Notice>
        )}
        <div className="grid gap-4 sm:grid-cols-2">
          <Field id={id('name')} label="Nome de exibição" required errors={err('name')}>
            {(a) => <input {...a} className="fe-input" autoComplete="name" value={values.name} onChange={set('name')} />}
          </Field>
          <Field id={id('handle')} label="@usuário" required hint="3 a 30: letras minúsculas, números, ponto ou sublinhado." errors={err('handle')}>
            {(a) => (
              <div className="flex items-center gap-1">
                <span aria-hidden="true" className="font-semibold">
                  @
                </span>
                <input
                  {...a}
                  className="fe-input"
                  autoComplete="username"
                  autoCapitalize="none"
                  spellCheck={false}
                  value={values.handle}
                  onChange={(e) => setValues((v) => ({ ...v, handle: e.target.value.toLowerCase().replace(/^@/, '') }))}
                />
              </div>
            )}
          </Field>
          <Field
            id={id('bio')}
            label="Bio"
            hint={<span aria-live="polite">{`${values.bio.length} de 150 caracteres`}</span>}
            errors={err('bio')}
            className="grid content-start gap-1 sm:col-span-2"
          >
            {(a) => <textarea {...a} className="fe-input min-h-24" maxLength={150} value={values.bio} onChange={set('bio')} />}
          </Field>
          <Field id={id('pronouns')} label="Pronomes" hint="Até 4, separados por vírgula. Ex.: ela, dela" errors={err('pronouns')}>
            {(a) => <input {...a} className="fe-input" value={values.pronouns} onChange={set('pronouns')} />}
          </Field>
          <Field id={id('links')} label="Links" hint="Até 5, um por linha, começando com https://" errors={err('links')}>
            {(a) => <textarea {...a} className="fe-input min-h-24" inputMode="url" spellCheck={false} value={values.links} onChange={set('links')} />}
          </Field>
        </div>
      </section>

      <section aria-labelledby={id('titulo-onde')} className="fe-card grid gap-4 p-5">
        <h2 id={id('titulo-onde')} className="text-xl font-bold">
          Onde você mora e contato
        </h2>
        <div className="grid gap-4 sm:grid-cols-3">
          <Field id={id('countryOfResidence')} label="País" hint="2 letras, ex.: BR" errors={err('countryOfResidence')}>
            {(a) => (
              <input
                {...a}
                className="fe-input uppercase"
                maxLength={2}
                autoComplete="country"
                value={values.countryOfResidence}
                onChange={(e) => setValues((v) => ({ ...v, countryOfResidence: e.target.value.toUpperCase() }))}
              />
            )}
          </Field>
          <Field id={id('stateOfResidence')} label={brazil ? 'UF' : 'Estado ou província'} errors={err('stateOfResidence')}>
            {(a) =>
              brazil ? (
                <select {...a} className="fe-input" value={values.stateOfResidence} onChange={set('stateOfResidence')}>
                  <option value="">Escolha…</option>
                  {UFS.map((u) => (
                    <option key={u.sigla} value={u.sigla}>
                      {u.sigla} – {u.nome}
                    </option>
                  ))}
                </select>
              ) : (
                <input {...a} className="fe-input" autoComplete="address-level1" value={values.stateOfResidence} onChange={set('stateOfResidence')} />
              )
            }
          </Field>
          <Field id={id('cityOfResidence')} label="Cidade" errors={err('cityOfResidence')}>
            {(a) => <input {...a} className="fe-input" autoComplete="address-level2" value={values.cityOfResidence} onChange={set('cityOfResidence')} />}
          </Field>
        </div>
        <div className="grid gap-4 sm:grid-cols-2">
          <Field id={id('cellphoneNumber')} label="Celular" hint="Formato internacional, ex.: +5541999990000. Privado." errors={err('cellphoneNumber')}>
            {(a) => <input {...a} type="tel" className="fe-input" autoComplete="tel" value={values.cellphoneNumber} onChange={set('cellphoneNumber')} />}
          </Field>
          <Field id={id('recoveryEmail')} label="E-mail de recuperação" hint="Diferente do e-mail de login. Privado." errors={err('recoveryEmail')}>
            {(a) => <input {...a} type="email" className="fe-input" autoComplete="email" value={values.recoveryEmail} onChange={set('recoveryEmail')} />}
          </Field>
        </div>
        <dl className="grid gap-3 text-sm sm:grid-cols-2">
          <div>
            <dt className="text-muted">E-mail de login</dt>
            <dd className="font-semibold">{profile.email}</dd>
          </div>
          <div>
            <dt className="text-muted">Data de nascimento</dt>
            <dd className="font-semibold">
              {profile.dateOfBirth ? formatDate(`${profile.dateOfBirth}T12:00:00Z`) : '—'}{' '}
              <span className="font-normal text-muted">(só muda pelo suporte)</span>
            </dd>
          </div>
        </dl>
      </section>

      <section aria-labelledby={id('titulo-tipo')} className="fe-card grid gap-4 p-5">
        <h2 id={id('titulo-tipo')} className="flex flex-wrap items-center gap-2 text-xl font-bold">
          Dados de <AccountBadge code={profile.category.code} label={profile.category.label} />
        </h2>
        {seal && <p className="text-sm text-muted">{seal}</p>}
        {type === 'ESTABELECIMENTO' && (
          <div className="flex min-h-11 items-start gap-2">
            <input
              id={id('informal')}
              type="checkbox"
              className="mt-0.5 size-6 flex-none"
              checked={informal}
              onChange={(e) => setInformal(e.target.checked)}
              aria-describedby={`${id('informal')}-dica`}
            />
            <div className="grid gap-0.5">
              <label htmlFor={id('informal')} className="font-semibold">
                Não tenho CNPJ (ambulante, food truck, cozinha caseira)
              </label>
              <p id={`${id('informal')}-dica`} className="text-sm text-muted">
                O cadastro usa o CPF do titular no lugar de CNPJ, razão social, CNAE e representante.
              </p>
            </div>
          </div>
        )}
        <div className="grid gap-4 sm:grid-cols-2">
          {detailFields.map((f) => (
            <DetailInput
              key={`${informal ? 'cpf' : 'cnpj'}-${f.key}`}
              field={f}
              id={id(f.key)}
              value={details[f.key] ?? ''}
              errors={err(f.key)}
              onChange={(v) => setDetails((d) => ({ ...d, [f.key]: v }))}
            />
          ))}
        </div>
      </section>

      <TasteFields
        idPrefix={id('gosto')}
        taxonomy={taxonomy}
        cuisines={cuisines}
        onCuisines={setCuisines}
        styles={styles}
        onStyles={setStyles}
        sensitiveGranted={sensitiveGranted}
        sensitiveLocked={sensitiveLocked}
        errors={{ favoriteCuisines: err('favoriteCuisines'), dietaryStyles: err('dietaryStyles') }}
      />

      <div className="grid gap-3">
        <div ref={savedRef} tabIndex={-1} className="outline-none">
          {saved && (
            <Notice tone="success" live>
              <p>{saved}</p>
            </Notice>
          )}
        </div>
        <div>
          <button type="submit" className="fe-btn fe-btn-primary" disabled={save.isPending}>
            {save.isPending ? 'Salvando…' : 'Salvar perfil'}
          </button>
        </div>
      </div>
    </form>
  );
}
