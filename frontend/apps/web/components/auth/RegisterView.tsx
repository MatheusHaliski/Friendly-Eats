'use client';

import { isApiError, type AccountTypeCode } from '@friendly-eats/api';
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import Link from 'next/link';
import { useRouter } from 'next/navigation';
import { useId, useRef, useState, type FormEvent } from 'react';
import { AccountDot } from '@/components/AccountBadge';
import { describedBy, ErrorSummary, Field, FieldErrors, Notice } from '@/components/Form';
import { ACCOUNT_ICONS } from '@/components/icons';
import { ACCOUNT_TYPES } from '@/lib/account-types';
import { api } from '@/lib/api';
import { mapRegisterErrors, type MappedErrors } from '@/lib/field-errors';
import { ageOn } from '@/lib/format';
import { SESSION_KEY } from '@/lib/queries';
import { buildTypeDetails, fieldsFor, type DetailField } from '@/lib/register-fields';
import { UFS } from '@/lib/uf';

const HANDLE = /^[a-z0-9._]{3,30}$/;
const EMPTY: MappedErrors = { fields: {}, general: [] };

/** Cadastro (RF1): dados mínimos, tipo de conta com os campos do tipo e aceite dos Termos (caixa desmarcada). */
export function RegisterView() {
  const uid = useId();
  const router = useRouter();
  const queryClient = useQueryClient();
  const terms = useQuery({ queryKey: ['termos'], queryFn: () => api.legal.terms(), staleTime: 60 * 60_000 });
  const [values, setValues] = useState({ name: '', handle: '', email: '', password: '', dateOfBirth: '' });
  const [userType, setUserType] = useState<AccountTypeCode | null>(null);
  const [informal, setInformal] = useState(false);
  const [details, setDetails] = useState<Record<string, string>>({});
  const [acceptTerms, setAcceptTerms] = useState(false);
  const [errors, setErrors] = useState<MappedErrors>(EMPTY);
  const summaryRef = useRef<HTMLDivElement>(null);

  const age = ageOn(values.dateOfBirth);
  const under16 = age !== null && age < 16;
  const detailFields = userType ? fieldsFor(userType, informal, under16) : [];
  const detailLabels = Object.fromEntries(detailFields.map((f) => [f.key, f.label]));
  const id = (field: string) => `${uid}-${field}`;

  const register = useMutation({
    mutationFn: () =>
      api.auth.register({
        name: values.name.trim(),
        handle: values.handle.trim(),
        email: values.email.trim(),
        password: values.password,
        userType: userType!,
        dateOfBirth: values.dateOfBirth,
        acceptTerms,
        termsVersion: terms.data!.version,
        typeDetails: buildTypeDetails(detailFields, details),
      }),
    onSuccess: (r) => {
      queryClient.setQueryData(SESSION_KEY, r.user);
      router.push('/confirmar-email');
    },
    onError: (e) => {
      if (isApiError(e, 422)) {
        setErrors(mapRegisterErrors(e.errors.length ? e.errors : [e.detail], detailLabels));
      } else if (isApiError(e, 409)) {
        // e-mail ou @usuário em uso
        setErrors({ fields: /@usuário/.test(e.detail) ? { handle: [e.detail] } : { email: [e.detail] }, general: [] });
      } else {
        setErrors({ fields: {}, general: [isApiError(e) ? e.detail : 'Não foi possível criar a conta agora. Tente de novo.'] });
      }
      requestAnimationFrame(() => summaryRef.current?.focus());
    },
  });

  function validate(): MappedErrors {
    const out: MappedErrors = { fields: {}, general: [] };
    const add = (f: string, m: string) => (out.fields[f] ??= []).push(m);
    const name = values.name.trim();
    if (name.length < 2 || name.length > 120) add('name', 'Informe um nome de exibição com 2 a 120 caracteres.');
    if (!HANDLE.test(values.handle.trim())) add('handle', 'O @usuário deve ter 3 a 30 caracteres: letras minúsculas, números, ponto ou sublinhado.');
    if (!/^[^@\s]+@[^@\s]+\.[^@\s]+$/.test(values.email.trim())) add('email', 'Informe um e-mail válido.');
    if (values.password.length < 12) add('password', 'A senha precisa ter pelo menos 12 caracteres. Uma frase com várias palavras funciona bem.');
    if (!values.dateOfBirth) add('dateOfBirth', 'Informe sua data de nascimento.');
    if (!userType) add('userType', 'Escolha o tipo de conta.');
    for (const f of detailFields) {
      if (f.required && !details[f.key]?.trim()) add(f.key, `Preencha ${f.label.toLowerCase()}.`);
    }
    if (!acceptTerms) add('acceptTerms', 'Para criar a conta, leia e aceite os Termos de Uso.');
    if (!terms.data) out.general.push('Não foi possível carregar a versão dos Termos de Uso. Recarregue a página.');
    return out;
  }

  function submit(e: FormEvent) {
    e.preventDefault();
    const local = validate();
    if (Object.keys(local.fields).length || local.general.length) {
      setErrors(local);
      requestAnimationFrame(() => summaryRef.current?.focus());
      return;
    }
    setErrors(EMPTY);
    register.mutate();
  }

  const err = (f: string) => errors.fields[f];
  const summary = [
    ...Object.entries(errors.fields).flatMap(([f, msgs]) =>
      msgs.map((message) => ({ message, fieldId: f === 'userType' ? `${id('tipo')}-COMENSAL` : f === 'acceptTerms' ? id('termos') : id(f) })),
    ),
    ...errors.general.map((message) => ({ message })),
  ];
  const set = (k: keyof typeof values) => (e: { target: { value: string } }) => setValues((v) => ({ ...v, [k]: e.target.value }));

  return (
    <div className="mx-auto grid w-full max-w-3xl gap-6">
      <div className="grid gap-2">
        <h1 className="text-3xl font-extrabold">Criar conta</h1>
        <p className="text-muted">
          Já tem conta?{' '}
          <Link href="/entrar" className="font-semibold text-fg underline">
            Entrar
          </Link>
        </p>
      </div>

      <form noValidate onSubmit={submit} className="grid gap-6">
        <ErrorSummary ref={summaryRef} items={summary} />

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
          <Field id={id('email')} label="E-mail" required errors={err('email')}>
            {(a) => <input {...a} type="email" className="fe-input" autoComplete="email" value={values.email} onChange={set('email')} />}
          </Field>
          <Field
            id={id('password')}
            label="Senha"
            required
            hint="Pelo menos 12 caracteres. Uma frase com várias palavras funciona bem."
            errors={err('password')}
          >
            {(a) => (
              <input {...a} type="password" className="fe-input" autoComplete="new-password" minLength={12} value={values.password} onChange={set('password')} />
            )}
          </Field>
          <Field id={id('dateOfBirth')} label="Data de nascimento" required hint="O Friendly Eats é para quem tem 13 anos ou mais." errors={err('dateOfBirth')}>
            {(a) => <input {...a} type="date" className="fe-input" autoComplete="bday" value={values.dateOfBirth} onChange={set('dateOfBirth')} />}
          </Field>
        </div>

        <fieldset className="grid gap-3" aria-describedby={err('userType') ? `${id('tipo')}-erro` : undefined}>
          <legend className="mb-1 text-lg font-bold">
            Tipo de conta <span className="text-base font-normal text-muted">(obrigatório)</span>
          </legend>
          <div className="grid gap-2 sm:grid-cols-2">
            {ACCOUNT_TYPES.map((t) => {
              const inputId = `${id('tipo')}-${t.code}`;
              const Icon = ACCOUNT_ICONS[t.icon];
              return (
                <div key={t.code} className="relative">
                  <input
                    type="radio"
                    className="fe-visually-hidden-input peer"
                    id={inputId}
                    name="tipo-de-conta"
                    value={t.code}
                    checked={userType === t.code}
                    onChange={() => {
                      setUserType(t.code);
                      setInformal(false);
                    }}
                    aria-describedby={`${inputId}-desc`}
                  />
                  <label
                    htmlFor={inputId}
                    className="flex h-full cursor-pointer gap-3 rounded-md border border-line-strong p-3 peer-checked:border-[3px] peer-checked:border-fg peer-checked:bg-sunken"
                  >
                    <span className="pt-1">
                      <AccountDot code={t.code} />
                    </span>
                    <span className="grid gap-0.5">
                      <span className="flex items-center gap-1.5 font-bold">
                        {Icon && <Icon size={18} />}
                        {t.label}
                      </span>
                      <span id={`${inputId}-desc`} className="text-sm text-muted">
                        {t.description}
                        {t.adultOnly ? ' · 18 anos ou mais' : ''}
                        {t.needsSeal ? ' · selo conferido pela moderação' : ''}
                      </span>
                    </span>
                  </label>
                </div>
              );
            })}
          </div>
          <FieldErrors id={`${id('tipo')}-erro`} errors={err('userType')} />
        </fieldset>

        {userType && (
          <fieldset className="grid gap-4 rounded-md border border-line p-4">
            <legend className="px-1 font-bold">Dados de {ACCOUNT_TYPES.find((t) => t.code === userType)?.label}</legend>
            {userType === 'ESTABELECIMENTO' && (
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
            {informal && details.informalFormat === 'COZINHA_CASEIRA' && (
              <Notice>
                <p>Na cozinha caseira, o público vê só o bairro e a cidade, nunca o seu endereço.</p>
              </Notice>
            )}
            {userType !== 'COMENSAL' && age !== null && age < 18 && (
              <Notice tone="error">
                <p>Contas de {ACCOUNT_TYPES.find((t) => t.code === userType)?.label} precisam de um responsável maior de 18 anos.</p>
              </Notice>
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
          </fieldset>
        )}

        <div className="grid gap-2">
          <div className="flex items-start gap-2">
            <input
              id={id('termos')}
              type="checkbox"
              className="mt-0.5 size-6 flex-none"
              checked={acceptTerms}
              onChange={(e) => setAcceptTerms(e.target.checked)}
              aria-invalid={err('acceptTerms') ? true : undefined}
              aria-describedby={describedBy(`${id('termos')}-versao`, err('acceptTerms') && `${id('termos')}-erro`)}
            />
            <label htmlFor={id('termos')} className="font-semibold">
              Li e aceito os Termos de Uso do Friendly Eats
            </label>
          </div>
          <p id={`${id('termos')}-versao`} className="pl-8 text-sm text-muted">
            {terms.data ? `Versão ${terms.data.version}. ` : terms.isError ? 'Não foi possível carregar a versão dos Termos. ' : 'Carregando a versão… '}
            <Link href="/termos" target="_blank" rel="noopener" className="font-semibold text-fg underline">
              Ler os Termos de Uso<span className="sr-only"> (abre em nova aba)</span>
            </Link>
          </p>
          <div className="pl-8">
            <FieldErrors id={`${id('termos')}-erro`} errors={err('acceptTerms')} />
          </div>
          <p className="pl-8 text-sm text-muted">
            Os usos opcionais dos seus dados começam desligados; você escolhe em Configurações → Privacidade.
          </p>
        </div>

        <div>
          <button type="submit" className="fe-btn fe-btn-primary" disabled={register.isPending}>
            {register.isPending ? 'Criando a conta…' : 'Criar conta'}
          </button>
        </div>
      </form>
    </div>
  );
}

export function DetailInput({
  field,
  id,
  value,
  errors,
  onChange,
}: {
  field: DetailField;
  id: string;
  value: string;
  errors?: string[];
  onChange: (v: string) => void;
}) {
  const label = (
    <>
      {field.label}
      {field.private ? <span className="font-normal text-muted"> · privado</span> : null}
    </>
  );
  return (
    <Field id={id} label={label} required={field.required} hint={field.hint} errors={errors}>
      {(a) => {
        if (field.kind === 'select' || field.kind === 'uf') {
          const options = field.kind === 'uf' ? UFS.map((u) => ({ value: u.sigla, label: `${u.sigla} – ${u.nome}` })) : (field.options ?? []);
          return (
            <select {...a} className="fe-input" value={value} onChange={(e) => onChange(e.target.value)}>
              <option value="">Escolha…</option>
              {options.map((o) => (
                <option key={o.value} value={o.value}>
                  {o.label}
                </option>
              ))}
            </select>
          );
        }
        return (
          <input
            {...a}
            className="fe-input"
            type={field.kind === 'email' ? 'email' : field.kind === 'url' ? 'url' : 'text'}
            inputMode={field.kind === 'document' ? 'text' : undefined}
            autoComplete={field.autoComplete ?? 'off'}
            placeholder={field.kind === 'url' ? 'https://' : undefined}
            value={value}
            onChange={(e) => onChange(e.target.value)}
          />
        );
      }}
    </Field>
  );
}
