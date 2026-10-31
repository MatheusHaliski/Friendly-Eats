'use client';

import Link from 'next/link';
import { useEffect, useId, useRef, useState, type FormEvent } from 'react';
import { ErrorSummary, Field, Notice, useDeferredFocus } from '@/components/Form';
import { IconEmail, IconExport, IconRights } from '@/components/icons';
import { usePrivacyContact, useSession } from '@/lib/queries';
import { privacyRequestMailto } from '@/lib/titular';

const TO_DEFINE = 'a definir';

/**
 * /privacidade (RF18; LGPD, art. 41, §1º): o encarregado em destaque, os direitos do titular com o caminho de cada um
 * e um formulário que monta o e-mail ao encarregado, para quem não tem conta também.
 */
export function PrivacyView() {
  const contact = usePrivacyContact();
  const session = useSession();
  const headingRef = useRef<HTMLHeadingElement>(null);
  useEffect(() => headingRef.current?.focus({ preventScroll: true }), []);
  const c = contact.data;
  const to = c?.privacyEmail ?? c?.dpoEmail ?? null;

  return (
    <div className="grid max-w-3xl gap-6">
      <div className="grid gap-1">
        <h1 ref={headingRef} tabIndex={-1} className="text-3xl font-extrabold outline-none">
          Privacidade e seus dados
        </h1>
        <p className="text-muted">Quem cuida dos seus dados no Friendly Eats, o que você pode pedir e como.</p>
      </div>

      {contact.isPending && <p className="text-muted">Carregando…</p>}
      {contact.isError && (
        <Notice tone="error">
          <p>Não foi possível carregar o contato do encarregado. Recarregue a página.</p>
        </Notice>
      )}

      {c && (
        <section aria-labelledby="titulo-encarregado" className="fe-card grid gap-3 p-5" data-testid="encarregado">
          <h2 id="titulo-encarregado" className="flex items-center gap-2 text-xl font-bold">
            <IconRights size={22} />
            Encarregado de dados (DPO)
          </h2>
          <dl className="grid gap-2 sm:grid-cols-[12rem_1fr]">
            <dt className="font-semibold">Controlador</dt>
            <dd>{c.controllerName ?? TO_DEFINE}</dd>
            <dt className="font-semibold">Encarregado</dt>
            <dd>{c.dpoName ?? TO_DEFINE}</dd>
            {c.dpoSubstituteName && (
              <>
                <dt className="font-semibold">Substituto</dt>
                <dd>{c.dpoSubstituteName}</dd>
              </>
            )}
            <dt className="font-semibold">E-mail</dt>
            <dd>
              {to ? (
                <a href={`mailto:${to}`} className="font-semibold underline">
                  {to}
                </a>
              ) : (
                TO_DEFINE
              )}
            </dd>
            <dt className="font-semibold">Prazo de resposta</dt>
            <dd>Confirmação na hora; resposta completa em até {c.requestDeadlineDays} dias (LGPD, art. 19).</dd>
          </dl>
          <p className="text-sm text-muted">
            <span className="fe-law">LGPD, art. 41</span> Também é possível reclamar à Autoridade Nacional de Proteção de Dados (ANPD).
          </p>
        </section>
      )}

      {c && (
        <section aria-labelledby="titulo-direitos" className="grid gap-3">
          <h2 id="titulo-direitos" className="text-xl font-bold">
            Seus direitos
          </h2>
          <ul className="fe-card divide-y divide-line">
            {c.rights.map((r) => (
              <li key={r.code} className="grid gap-1 p-4">
                <p className="flex flex-wrap items-center gap-2 font-bold">
                  {r.label}
                  <span className="fe-law">{r.article}</span>
                </p>
                <p className="text-sm">{r.how}</p>
              </li>
            ))}
          </ul>
          {session.data ? (
            <p>
              <Link href="/configuracoes/conta" className="fe-btn">
                <IconExport size={18} />
                Baixar uma cópia dos meus dados
              </Link>
            </p>
          ) : (
            <p className="text-sm">Com conta, a cópia dos dados sai pelo próprio app em Configurações → Conta → Seus dados; o link vale {c.exportLinkHours} horas.</p>
          )}
        </section>
      )}

      {c && to && <RequestForm to={to} rights={c.rights.map((r) => r.label)} />}

      <p className="text-sm">
        As regras completas estão nos{' '}
        <Link href="/termos" className="font-semibold underline">
          Termos de Uso
        </Link>
        .
      </p>
    </div>
  );
}

/** Pedido do titular por e-mail: não precisa de conta; o texto vai pronto para o app de e-mail. */
function RequestForm({ to, rights }: { to: string; rights: string[] }) {
  const uid = useId();
  const [name, setName] = useState('');
  const [email, setEmail] = useState('');
  const [right, setRight] = useState(rights[0] ?? '');
  const [message, setMessage] = useState('');
  const [errors, setErrors] = useState<Record<string, string[]>>({});
  const [href, setHref] = useState<string | null>(null);
  const [summaryRef, focusSummary] = useDeferredFocus<HTMLDivElement>();
  const [readyRef, focusReady] = useDeferredFocus<HTMLDivElement>();

  function submit(e: FormEvent) {
    e.preventDefault();
    const next: Record<string, string[]> = {};
    if (name.trim().length < 2) next.nome = ['Informe o seu nome.'];
    if (!/^[^@\s]+@[^@\s]+\.[^@\s]{2,}$/.test(email.trim())) next.email = ['Informe um e-mail para a resposta.'];
    if (message.trim().length < 10) next.mensagem = ['Descreva o pedido em pelo menos 10 caracteres.'];
    setErrors(next);
    if (Object.keys(next).length) {
      setHref(null);
      focusSummary();
      return;
    }
    setHref(privacyRequestMailto(to, { name: name.trim(), email: email.trim(), right, message: message.trim() }));
    focusReady();
  }
  const summary = Object.entries(errors).flatMap(([f, msgs]) => msgs.map((message) => ({ message, fieldId: `${uid}-${f}` })));
  return (
    <form noValidate onSubmit={submit} aria-labelledby={`${uid}-titulo`} className="fe-card grid gap-4 p-5" data-testid="pedido-titular">
      <h2 id={`${uid}-titulo`} className="flex items-center gap-2 text-xl font-bold">
        <IconEmail size={22} />
        Fazer um pedido ao encarregado
      </h2>
      <p className="text-sm text-muted">Funciona com ou sem conta. Montamos o e-mail; você confere e envia pelo seu app de e-mail.</p>
      <ErrorSummary ref={summaryRef} items={summary} />
      <div className="grid gap-4 sm:grid-cols-2">
        <Field id={`${uid}-nome`} label="Seu nome" required errors={errors.nome}>
          {(a) => <input {...a} className="fe-input" autoComplete="name" value={name} onChange={(e) => setName(e.target.value)} />}
        </Field>
        <Field id={`${uid}-email`} label="E-mail para a resposta" required errors={errors.email}>
          {(a) => <input {...a} type="email" className="fe-input" autoComplete="email" value={email} onChange={(e) => setEmail(e.target.value)} />}
        </Field>
      </div>
      <Field id={`${uid}-direito`} label="Qual direito">
        {(a) => (
          <select {...a} className="fe-input" value={right} onChange={(e) => setRight(e.target.value)}>
            {rights.map((r) => (
              <option key={r}>{r}</option>
            ))}
          </select>
        )}
      </Field>
      <Field id={`${uid}-mensagem`} label="Seu pedido" required errors={errors.mensagem}>
        {(a) => <textarea {...a} className="fe-input min-h-28" maxLength={2000} value={message} onChange={(e) => setMessage(e.target.value)} />}
      </Field>
      <div ref={readyRef} tabIndex={-1} className="outline-none">
        {href && (
          <Notice tone="success" live>
            <p>E-mail pronto para {to}.</p>
            <p>
              <a href={href} className="fe-btn fe-btn-primary" data-testid="abrir-email">
                <IconEmail size={18} />
                Abrir no meu app de e-mail
              </a>
            </p>
          </Notice>
        )}
      </div>
      <div>
        <button type="submit" className="fe-btn">
          Preparar o e-mail
        </button>
      </div>
    </form>
  );
}
