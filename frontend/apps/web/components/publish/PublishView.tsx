'use client';

import {
  isApiError,
  type FrameCatalog,
  type PostCardView,
  type PostDetailView,
  type PostKindItem,
  type PostVocabulary,
  type SessionUserView,
  type TaxonomyCatalog,
} from '@friendly-eats/api';
import { useMutation, useQueryClient } from '@tanstack/react-query';
import Link from 'next/link';
import { useRouter } from 'next/navigation';
import { useId, useMemo, useState, type ReactNode } from 'react';
import { ErrorSummary, Field, FieldErrors, Notice, useFocusOnChange } from '@/components/Form';
import { POST_KIND_ICONS } from '@/components/icons';
import { KindBadge } from '@/components/post/PostBits';
import { FrameStudio } from '@/components/studio/FrameStudio';
import { accountType } from '@/lib/account-types';
import { api } from '@/lib/api';
import { mapPostErrors } from '@/lib/field-errors';
import { formatCents, parseReaisToCents } from '@/lib/format';
import { fromConfig, toConfig, validateFrame, type FrameState } from '@/lib/frame';
import { buildDraft, buildPatch, emptyValues, formSpec, publishableKinds, valuesFromPost, type FormErrors, type PostFormValues } from '@/lib/post-form';
import { postKey, useFrameCatalog, usePost, useVocabulary } from '@/lib/post-queries';
import { useRequireSession, useTaxonomy } from '@/lib/queries';
import { AllergenFields } from './AllergenFields';
import { PhotoPicker, type PhotoState } from './PhotoPicker';
import { PlacePicker } from './PlacePicker';

/** Publicar (/publicar) e editar (/posts/[id]/editar) um post de comida (RF9) com o estúdio de moldura (RF16). */
export function PublishView({ editId }: { editId?: string }) {
  const session = useRequireSession(editId ? `/posts/${editId}/editar` : '/publicar');
  const vocabulary = useVocabulary();
  const catalog = useFrameCatalog();
  const taxonomy = useTaxonomy();
  const post = usePost(editId ?? '', !!editId);
  const user = session.data;

  if (session.isPending || !user || vocabulary.isPending || catalog.isPending || (editId && post.isPending)) {
    return (
      <p role="status" className="text-muted">
        Carregando…
      </p>
    );
  }
  if (vocabulary.isError || catalog.isError) {
    return (
      <Notice tone="error" live>
        <p>Não foi possível abrir o formulário agora. Tente de novo em instantes.</p>
      </Notice>
    );
  }
  if (editId) {
    if (post.isError || !post.data) {
      return (
        <div className="grid max-w-xl gap-4">
          <h1 className="text-3xl font-extrabold">Post não encontrado</h1>
          <p>
            <Link href="/feed" className="underline">
              Voltar para o feed
            </Link>
          </p>
        </div>
      );
    }
    if (!post.data.viewer.isAuthor) {
      return (
        <div className="grid max-w-xl gap-4">
          <h1 className="text-3xl font-extrabold">Só quem publicou edita o post</h1>
          <p>
            <Link href={`/posts/${editId}`} className="underline">
              Ver o post
            </Link>
          </p>
        </div>
      );
    }
  }
  return (
    <PostEditor
      key={editId ?? 'novo'}
      user={user}
      vocabulary={vocabulary.data}
      catalog={catalog.data}
      taxonomy={taxonomy.data}
      post={editId ? post.data : undefined}
    />
  );
}

interface EditorProps {
  user: SessionUserView;
  vocabulary: PostVocabulary;
  catalog: FrameCatalog;
  taxonomy: TaxonomyCatalog | undefined;
  post?: PostDetailView;
}

function PostEditor({ user, vocabulary, catalog, taxonomy, post }: EditorProps) {
  const uid = useId();
  const router = useRouter();
  const qc = useQueryClient();
  const author = user.category.code;
  const kinds = publishableKinds(vocabulary.kinds, vocabulary.publishableKinds);
  const [kindCode, setKindCode] = useState<string | null>(post?.kind.code ?? (kinds.length === 1 ? kinds[0]!.code : null));
  const kind: PostKindItem | undefined = vocabulary.kinds.find((k) => k.code === kindCode);
  const [values, setValues] = useState<PostFormValues>(() => (post ? valuesFromPost(post) : emptyValues(kind ?? null, author)));
  const [photo, setPhoto] = useState<PhotoState>({ key: null, currentUrl: post?.mediaUrl ?? null, removed: false });
  const [photoBusy, setPhotoBusy] = useState(false);
  const initialFrame = useMemo(() => fromConfig(post?.background?.config ?? null), [post]);
  const [frame, setFrame] = useState<FrameState>(initialFrame);
  const [errors, setErrors] = useState<FormErrors>({});
  const [general, setGeneral] = useState<string[]>([]);
  const [attempt, setAttempt] = useState(0);
  const summaryRef = useFocusOnChange<HTMLDivElement>(attempt);
  const spec = kind ? formSpec(kind, author) : null;
  const set = <K extends keyof PostFormValues>(k: K, v: PostFormValues[K]) => setValues((old) => ({ ...old, [k]: v }));

  const alcoholic = !!taxonomy?.formats.find((f) => f.code === values.foodFormat)?.alcoholic;

  const save = useMutation({
    mutationFn: async () => {
      if (!spec) throw new Error('sem tipo');
      const frameConfig = spec.fields.has('background') ? toConfig(frame) : null;
      if (post) {
        const changed = JSON.stringify(toConfig(frame)) !== JSON.stringify(toConfig(initialFrame));
        const { patch } = buildPatch(spec, values, { key: photo.key, keepCurrent: !!photo.currentUrl && !photo.removed, removed: photo.removed }, frameConfig, changed);
        return api.posts.update(post.id, patch);
      }
      const { draft } = buildDraft(spec, values, { key: photo.key, keepCurrent: false }, frameConfig);
      return api.posts.create(draft);
    },
    onSuccess: (view) => {
      qc.setQueryData(postKey(view.id), view);
      void qc.invalidateQueries({ queryKey: ['posts'] });
      void qc.invalidateQueries({ queryKey: ['perfil'] });
      router.push(`/posts/${view.id}`);
    },
    onError: (e) => {
      if (isApiError(e, 422)) {
        const visible = new Set(Object.keys(fieldIds()));
        const mapped = mapPostErrors(e.errors.length ? e.errors : [e.detail], visible);
        setErrors(mapped.fields);
        setGeneral(mapped.general);
      } else if (isApiError(e, 401)) {
        router.push(`/entrar?voltar=${encodeURIComponent(post ? `/posts/${post.id}/editar` : '/publicar')}`);
        return;
      } else {
        setErrors({});
        setGeneral([isApiError(e) ? e.detail : 'Não foi possível publicar agora. Tente de novo.']);
      }
      setAttempt((n) => n + 1);
    },
  });

  /** Campos da tela que existem para este tipo (para os erros do 422 e os links do resumo). */
  function fieldIds(): Record<string, string> {
    if (!spec) return {};
    const out: Record<string, string> = { title: 'post-title' };
    const add = (k: string) => (out[k] = `post-${k}`);
    const f = spec.fields;
    if (f.has('body')) add('body');
    if (f.has('photo')) {
      add('photo');
      add('altText');
    }
    if (f.has('place')) add('place');
    if (f.has('cuisine')) add('cuisine');
    if (f.has('foodFormat')) add('foodFormat');
    if (f.has('dietaryStyles')) add('dietaryStyles');
    if (f.has('allergens')) add('allergens');
    if (f.has('warnings')) add('warnings');
    if (f.has('ageRating')) add('ageRating');
    if (f.has('advertising')) add('advertising');
    if (f.has('city')) add('city');
    if (f.has('period')) {
      add('startsAt');
      add('endsAt');
    }
    if (f.has('priceCents') || f.has('free')) add('price');
    if (f.has('promoPrice')) {
      add('priceFrom');
      add('priceTo');
      add('discount');
    }
    if (f.has('recipe')) {
      add('ingredients');
      add('steps');
    }
    if (f.has('servings')) add('servings');
    if (f.has('prepMinutes')) add('prepMinutes');
    if (f.has('alert')) for (const k of ['alertProduct', 'alertBrand', 'alertLots', 'alertReason', 'alertSourceUrl']) add(k);
    if (f.has('background')) out.background = 'post-background';
    return out;
  }

  function submit() {
    if (!spec || !kind) return;
    if (photoBusy) {
      setGeneral(['Espere a foto terminar de enviar.']);
      setAttempt((n) => n + 1);
      return;
    }
    const keep = !!photo.currentUrl && !photo.removed;
    const { errors: local } = buildDraft(spec, values, { key: photo.key, keepCurrent: keep }, null);
    const frameErrors = spec.fields.has('background') ? validateFrame(frame, catalog, kind.feed) : [];
    const all: FormErrors = { ...local };
    if (frameErrors.length) all.background = frameErrors;
    if (Object.keys(all).length) {
      setErrors(all);
      setGeneral([]);
      setAttempt((n) => n + 1);
      return;
    }
    setErrors({});
    setGeneral([]);
    save.mutate();
  }

  const ids = fieldIds();
  const summary = [
    ...general.map((message) => ({ message })),
    ...Object.entries(errors).flatMap(([k, msgs]) => msgs.map((message) => ({ message, fieldId: ids[k] }))),
  ];

  const info = accountType(author);
  const preview: PostCardView | null = kind
    ? {
        id: 'previa',
        kind: { code: kind.code, label: kind.label, lucideIcon: kind.lucideIcon, border: kind.border },
        feed: kind.feed,
        mediaType: photo.key || (photo.currentUrl && !photo.removed) ? 'IMAGE' : 'TEXT',
        title: values.title.trim() || (kind.code === 'PERGUNTA' ? 'Sua pergunta aparece aqui' : 'O título aparece aqui'),
        excerpt: values.body.trim() ? values.body.trim().slice(0, 160) : null,
        mediaUrl: photo.localUrl ?? (photo.removed ? null : photo.currentUrl),
        altText: values.altText || null,
        establishment: null,
        cuisine: null,
        foodFormat: null,
        dietaryStyles: [],
        allergens: [],
        warnings: [],
        ageRating: alcoholic ? 'AGE_18' : values.ageRating,
        ageRatingLabel: alcoholic || values.ageRating === 'AGE_18' ? '18+' : 'Livre',
        advertising: values.advertising,
        advertisingLabel: values.advertising ? 'Publicidade' : null,
        startsAt: null,
        endsAt: null,
        price: previewPrice(spec, values),
        alert: null,
        expiresAt: null,
        expired: false,
        status: 'PUBLISHED',
        publishedAt: post?.publishedAt ?? new Date().toISOString(),
        author: {
          id: user.id,
          name: user.name,
          handle: user.handle,
          category: { code: author, label: user.category.label, color: user.category.color, lightColor: user.category.color, glyph: info.glyph, glyphName: info.glyphName, lucideIcon: info.icon },
          verified: user.categoryVerification === 'VERIFIED',
          avatarUrl: null,
          profilePath: null,
          available: true,
        },
        counts: { likes: 0, comments: 0, shares: 0 },
        viewer: { liked: false, saved: false, isAuthor: true },
        detailPath: '',
        background: null,
      }
    : null;

  const title = post ? 'Editar post' : 'Publicar';

  if (!post && kinds.length === 0) {
    return (
      <div className="grid max-w-2xl gap-4">
        <h1 className="text-3xl font-extrabold">{title}</h1>
        <Notice>
          {user.accountStatus !== 'ACTIVE' ? (
            <p>
              Sua conta precisa estar ativa para publicar.{' '}
              <Link href="/confirmar-email" className="font-semibold underline">
                Confirmar e-mail
              </Link>
            </p>
          ) : user.categoryVerification === 'PENDING' ? (
            <p>Sua conta de {user.category.label} ainda está em verificação: você já pode ler e interagir, e publica depois do selo.</p>
          ) : (
            <p>Sua conta ainda não pode publicar nenhum tipo de post.</p>
          )}
        </Notice>
      </div>
    );
  }

  return (
    <form
      aria-labelledby={`${uid}-titulo`}
      className="grid gap-8"
      noValidate
      onSubmit={(e) => {
        e.preventDefault();
        submit();
      }}
    >
      <div className="grid gap-2">
        <h1 id={`${uid}-titulo`} className="text-3xl font-extrabold">
          {title}
        </h1>
        <p className="text-muted">Campos com “(obrigatório)” dependem do tipo de post escolhido.</p>
      </div>
      <ErrorSummary ref={summaryRef} items={summary} />

      {post ? (
        <p className="flex flex-wrap items-center gap-2">
          <span className="font-semibold">Tipo:</span> <KindBadge kind={post.kind} />
          <span className="text-sm text-muted">O tipo não muda: para outro tipo, apague e publique de novo.</span>
        </p>
      ) : (
        <fieldset className="grid gap-3">
          <legend className="mb-2 text-xl font-bold">Tipo de post</legend>
          <div className="grid gap-2 sm:grid-cols-2 lg:grid-cols-3" role="radiogroup" aria-label="Tipo de post">
            {kinds.map((k) => {
              const Icon = POST_KIND_ICONS[k.code];
              return (
                <label key={k.code} className="fe-card flex cursor-pointer items-start gap-3 p-3 has-[:checked]:border-2 has-[:checked]:border-fg">
                  <input
                    type="radio"
                    name="tipo-post"
                    value={k.code}
                    checked={kindCode === k.code}
                    onChange={() => {
                      setKindCode(k.code);
                      setValues((old) => ({ ...emptyValues(k, author), title: old.title, body: old.body }));
                      setErrors({});
                      setGeneral([]);
                    }}
                    className="mt-1 size-5 flex-none"
                  />
                  <span className="grid gap-0.5">
                    <span className="flex items-center gap-1.5 font-bold">
                      {Icon && <Icon size={18} />}
                      {k.label}
                    </span>
                    <span className="text-sm text-muted">{k.whoCanPublish}</span>
                  </span>
                </label>
              );
            })}
          </div>
        </fieldset>
      )}

      {spec && kind && (
        <>
          <Section title="Conteúdo">
            <Field id="post-title" label={kind.code === 'PERGUNTA' ? 'Pergunta' : 'Título'} required errors={errors.title}>
              {(aria) => <input {...aria} className="fe-input" maxLength={140} value={values.title} onChange={(e) => set('title', e.target.value)} />}
            </Field>
            {spec.fields.has('body') && (
              <Field id="post-body" label={kind.code === 'PERGUNTA' ? 'Detalhes' : 'Texto'} hint="Até 5.000 caracteres." errors={errors.body}>
                {(aria) => <textarea {...aria} className="fe-input min-h-32" maxLength={5000} value={values.body} onChange={(e) => set('body', e.target.value)} />}
              </Field>
            )}
            {spec.fields.has('photo') && (
              <PhotoPicker
                value={photo}
                onChange={setPhoto}
                altText={values.altText}
                onAltText={(v) => set('altText', v)}
                required={spec.required.has('photo')}
                errors={errors.photo}
                altErrors={errors.altText}
                onBusy={setPhotoBusy}
              />
            )}
          </Section>

          <KindDetails kind={kind} spec={spec} values={values} set={set} errors={errors} />

          {spec.fields.has('place') && (
            <Section title="Lugar">
              <PlacePicker author={author} value={values.establishmentId} onChange={(id) => set('establishmentId', id)} required={spec.required.has('place')} current={post?.establishment ?? null} errors={errors.place} />
            </Section>
          )}

          {(spec.fields.has('cuisine') || spec.fields.has('dietaryStyles') || spec.fields.has('city')) && (
            <Section title="Sobre a comida">
              <div className="grid gap-4 sm:grid-cols-2">
                {spec.fields.has('cuisine') && (
                  <Field id="post-cuisine" label="Culinária" errors={errors.cuisine}>
                    {(aria) => (
                      <select {...aria} className="fe-input" value={values.cuisine} onChange={(e) => set('cuisine', e.target.value)}>
                        <option value="">Não informar</option>
                        {[...(taxonomy?.cuisines ?? [])].sort((a, b) => a.label.localeCompare(b.label, 'pt-BR')).map((c) => (
                          <option key={c.code} value={c.code}>
                            {c.label}
                          </option>
                        ))}
                      </select>
                    )}
                  </Field>
                )}
                {spec.fields.has('foodFormat') && (
                  <Field id="post-foodFormat" label="Tipo de comida" errors={errors.foodFormat}>
                    {(aria) => (
                      <select {...aria} className="fe-input" value={values.foodFormat} onChange={(e) => set('foodFormat', e.target.value)}>
                        <option value="">Não informar</option>
                        {[...(taxonomy?.formats ?? [])].sort((a, b) => a.label.localeCompare(b.label, 'pt-BR')).map((f) => (
                          <option key={f.code} value={f.code}>
                            {f.label}
                            {f.alcoholic ? ' (18+)' : ''}
                          </option>
                        ))}
                      </select>
                    )}
                  </Field>
                )}
                {spec.fields.has('city') && (
                  <Field id="post-city" label="Cidade" hint="Opcional, quando o post não cita um lugar." errors={errors.city}>
                    {(aria) => <input {...aria} className="fe-input" maxLength={120} value={values.city} onChange={(e) => set('city', e.target.value)} autoComplete="address-level2" />}
                  </Field>
                )}
              </div>
              {spec.fields.has('dietaryStyles') && (
                <fieldset className="grid gap-1" id="post-dietaryStyles">
                  <legend className="font-semibold">Estilos alimentares do prato</legend>
                  <p className="text-sm text-muted">Descrevem o prato, nunca quem publica. Até 8.</p>
                  <div className="flex flex-wrap gap-x-4">
                    {(taxonomy?.dietaryStyles ?? []).map((d) => (
                      <label key={d.code} className="flex min-h-11 items-center gap-2">
                        <input
                          type="checkbox"
                          className="size-5"
                          checked={values.dietaryStyles.includes(d.code)}
                          onChange={(e) => set('dietaryStyles', e.target.checked ? [...values.dietaryStyles, d.code] : values.dietaryStyles.filter((x) => x !== d.code))}
                        />
                        {d.label}
                      </label>
                    ))}
                  </div>
                  <FieldErrors id="post-dietaryStyles-erro" errors={errors.dietaryStyles} />
                </fieldset>
              )}
            </Section>
          )}

          {spec.fields.has('allergens') && (
            <Section title="Alergênicos">
              <AllergenFields
                vocabulary={vocabulary}
                allergens={values.allergens}
                onAllergens={(v) => set('allergens', v)}
                warnings={values.warnings}
                onWarnings={(v) => set('warnings', v)}
                showWarnings={spec.fields.has('warnings')}
                errors={errors.allergens}
                warningErrors={errors.warnings}
              />
            </Section>
          )}

          {(spec.fields.has('ageRating') || spec.fields.has('advertising')) && (
            <Section title="Classificação">
              {spec.fields.has('ageRating') && (
                <fieldset className="grid gap-1" id="post-ageRating">
                  <legend className="font-semibold">Para quem</legend>
                  <div className="flex flex-wrap gap-x-4">
                    {vocabulary.ageRatings.map((r) => (
                      <label key={r.code} className="flex min-h-11 items-center gap-2">
                        <input
                          type="radio"
                          name="post-classificacao"
                          className="size-5"
                          checked={(alcoholic ? 'AGE_18' : values.ageRating) === r.code}
                          disabled={alcoholic && r.code === 'LIVRE'}
                          onChange={() => set('ageRating', r.code as PostFormValues['ageRating'])}
                        />
                        {r.code === 'AGE_18' ? '18+ (bebida alcoólica como tema ou atração)' : r.label}
                      </label>
                    ))}
                  </div>
                  {alcoholic && <p className="text-sm text-muted">Tipo de comida alcoólico: o post fica 18+ sozinho e não aparece para visitantes nem menores.</p>}
                  <FieldErrors id="post-ageRating-erro" errors={errors.ageRating} />
                </fieldset>
              )}
              {spec.fields.has('advertising') && (
                <div className="grid gap-1" id="post-advertising">
                  <label className="flex min-h-11 items-center gap-2 font-semibold">
                    <input type="checkbox" className="size-5" checked={values.advertising} onChange={(e) => set('advertising', e.target.checked)} />
                    Publicidade (publi, permuta ou convite)
                    {spec.required.has('advertising') ? <span className="font-normal text-muted"> (obrigatório)</span> : null}
                  </label>
                  <p className="text-sm text-muted">Mostra o chip “Publicidade” no post, como pedem o CDC (art. 36) e o CONAR.</p>
                  <FieldErrors id="post-advertising-erro" errors={errors.advertising} />
                </div>
              )}
            </Section>
          )}

          <Section title="Moldura" id="post-background">
            {preview && (
              <FrameStudio
                catalog={catalog}
                state={frame}
                onChange={setFrame}
                disabled={!spec.fields.has('background')}
                category={author}
                media={preview.mediaType === 'IMAGE' ? 'IMAGE' : 'TEXT'}
                preview={preview}
                errors={errors.background}
              />
            )}
          </Section>

          <div className="flex flex-wrap gap-3">
            <button type="submit" className="fe-btn fe-btn-primary" disabled={save.isPending}>
              {save.isPending ? 'Salvando…' : post ? 'Salvar alterações' : 'Publicar'}
            </button>
            <Link href={post ? `/posts/${post.id}` : '/feed'} className="fe-btn">
              Cancelar
            </Link>
          </div>
        </>
      )}
    </form>
  );
}

/** Preço simples ou "Gratuito" na prévia do card (de/por e desconto ficam para a página do post). */
function previewPrice(spec: ReturnType<typeof formSpec> | null, v: PostFormValues): PostCardView['price'] {
  if (!spec) return null;
  const base = { priceCents: null, priceFromCents: null, priceToCents: null, discountPercent: null, free: false };
  if (spec.fields.has('free') && v.eventMode === 'FREE') return { ...base, free: true, label: 'Gratuito' };
  if (!spec.fields.has('priceCents') && !spec.fields.has('free')) return null;
  const cents = parseReaisToCents(v.price);
  return cents === null || Number.isNaN(cents) ? null : { ...base, priceCents: cents, label: formatCents(cents) };
}

function Section({ title, children, id }: { title: string; children: ReactNode; id?: string }) {
  const hid = useId();
  return (
    <section aria-labelledby={hid} id={id} className="grid gap-4 border-t border-line pt-6">
      <h2 id={hid} className="text-xl font-bold">
        {title}
      </h2>
      {children}
    </section>
  );
}

interface DetailsProps {
  kind: PostKindItem;
  spec: ReturnType<typeof formSpec>;
  values: PostFormValues;
  set: <K extends keyof PostFormValues>(k: K, v: PostFormValues[K]) => void;
  errors: FormErrors;
}

/** Campos que dependem do tipo: preço, vigência, receita e alerta. */
function KindDetails({ kind, spec, values, set, errors }: DetailsProps) {
  const f = spec.fields;
  const hasPrice = f.has('priceCents') || f.has('free') || f.has('promoPrice');
  if (!hasPrice && !f.has('period') && !f.has('recipe') && !f.has('alert')) return null;
  const reais = 'Em reais, ex.: 32,90.';
  return (
    <Section title={f.has('alert') ? 'Alerta sanitário' : f.has('recipe') ? 'Receita' : 'Preço e data'}>
      {f.has('period') && (
        <div className="grid gap-4 sm:grid-cols-2">
          <Field id="post-startsAt" label={kind.code === 'EVENTO' ? 'Começa' : 'Início da promoção'} required errors={errors.startsAt}>
            {(aria) => <input {...aria} type="datetime-local" className="fe-input" value={values.startsAt} onChange={(e) => set('startsAt', e.target.value)} />}
          </Field>
          <Field id="post-endsAt" label={kind.code === 'EVENTO' ? 'Termina' : 'Fim da promoção'} required hint={kind.expires ? 'O post sai do feed no fim.' : undefined} errors={errors.endsAt}>
            {(aria) => <input {...aria} type="datetime-local" className="fe-input" value={values.endsAt} onChange={(e) => set('endsAt', e.target.value)} />}
          </Field>
        </div>
      )}
      {kind.code === 'PRATO_DO_DIA' && kind.expires && <p className="text-sm text-muted">{kind.expires}</p>}

      {f.has('free') ? (
        <fieldset className="grid gap-2" id="post-price">
          <legend className="font-semibold">
            Entrada <span className="font-normal text-muted">(obrigatório)</span>
          </legend>
          <div className="flex flex-wrap gap-x-4">
            <label className="flex min-h-11 items-center gap-2">
              <input type="radio" name="post-entrada" className="size-5" checked={values.eventMode === 'PAID'} onChange={() => set('eventMode', 'PAID')} />
              Paga
            </label>
            <label className="flex min-h-11 items-center gap-2">
              <input type="radio" name="post-entrada" className="size-5" checked={values.eventMode === 'FREE'} onChange={() => set('eventMode', 'FREE')} />
              Gratuita
            </label>
          </div>
          {values.eventMode === 'PAID' && (
            <Field id="post-price-valor" label="Preço da entrada" hint={reais}>
              {(aria) => <input {...aria} inputMode="decimal" className="fe-input max-w-48" value={values.price} onChange={(e) => set('price', e.target.value)} />}
            </Field>
          )}
          <FieldErrors id="post-price-erro" errors={errors.price} />
        </fieldset>
      ) : (
        f.has('priceCents') && (
          <Field id="post-price" label="Preço" required={spec.required.has('priceCents')} hint={`${reais} O preço exibido é o cobrado.`} errors={errors.price}>
            {(aria) => <input {...aria} inputMode="decimal" className="fe-input max-w-48" value={values.price} onChange={(e) => set('price', e.target.value)} />}
          </Field>
        )
      )}

      {f.has('promoPrice') && (
        <fieldset className="grid gap-2">
          <legend className="font-semibold">
            Como é a promoção <span className="font-normal text-muted">(obrigatório)</span>
          </legend>
          <div className="flex flex-wrap gap-x-4">
            <label className="flex min-h-11 items-center gap-2">
              <input type="radio" name="post-promo" className="size-5" checked={values.promoMode === 'FROM_TO'} onChange={() => set('promoMode', 'FROM_TO')} />
              De/por
            </label>
            <label className="flex min-h-11 items-center gap-2">
              <input type="radio" name="post-promo" className="size-5" checked={values.promoMode === 'DISCOUNT'} onChange={() => set('promoMode', 'DISCOUNT')} />
              Desconto em %
            </label>
          </div>
          {values.promoMode === 'FROM_TO' ? (
            <div className="grid gap-4 sm:grid-cols-2">
              <Field id="post-priceFrom" label="De (preço normal)" hint={reais} errors={errors.priceFrom}>
                {(aria) => <input {...aria} inputMode="decimal" className="fe-input" value={values.priceFrom} onChange={(e) => set('priceFrom', e.target.value)} />}
              </Field>
              <Field id="post-priceTo" label="Por (preço da promoção)" hint={reais} errors={errors.priceTo}>
                {(aria) => <input {...aria} inputMode="decimal" className="fe-input" value={values.priceTo} onChange={(e) => set('priceTo', e.target.value)} />}
              </Field>
            </div>
          ) : (
            <Field id="post-discount" label="Desconto (%)" hint="De 1 a 90." errors={errors.discount}>
              {(aria) => <input {...aria} inputMode="numeric" className="fe-input max-w-32" value={values.discount} onChange={(e) => set('discount', e.target.value)} />}
            </Field>
          )}
        </fieldset>
      )}

      {f.has('recipe') && (
        <>
          <Field id="post-ingredients" label="Ingredientes" required hint="Um por linha, com a quantidade (ex.: 2 xícaras de farinha)." errors={errors.ingredients}>
            {(aria) => <textarea {...aria} className="fe-input min-h-32" value={values.ingredients} onChange={(e) => set('ingredients', e.target.value)} />}
          </Field>
          <Field id="post-steps" label="Modo de preparo" required hint="Um passo por linha." errors={errors.steps}>
            {(aria) => <textarea {...aria} className="fe-input min-h-32" value={values.steps} onChange={(e) => set('steps', e.target.value)} />}
          </Field>
          <div className="grid gap-4 sm:grid-cols-2">
            {f.has('servings') && (
              <Field id="post-servings" label="Rendimento (porções)" errors={errors.servings}>
                {(aria) => <input {...aria} inputMode="numeric" className="fe-input max-w-32" value={values.servings} onChange={(e) => set('servings', e.target.value)} />}
              </Field>
            )}
            {f.has('prepMinutes') && (
              <Field id="post-prepMinutes" label="Tempo de preparo (minutos)" errors={errors.prepMinutes}>
                {(aria) => <input {...aria} inputMode="numeric" className="fe-input max-w-32" value={values.prepMinutes} onChange={(e) => set('prepMinutes', e.target.value)} />}
              </Field>
            )}
          </div>
        </>
      )}

      {f.has('alert') && (
        <div className="grid gap-4">
          <div className="grid gap-4 sm:grid-cols-2">
            <Field id="post-alertProduct" label="Produto" required errors={errors.alertProduct}>
              {(aria) => <input {...aria} className="fe-input" maxLength={140} value={values.alertProduct} onChange={(e) => set('alertProduct', e.target.value)} />}
            </Field>
            <Field id="post-alertBrand" label="Marca" required errors={errors.alertBrand}>
              {(aria) => <input {...aria} className="fe-input" maxLength={120} value={values.alertBrand} onChange={(e) => set('alertBrand', e.target.value)} />}
            </Field>
          </div>
          <Field id="post-alertLots" label="Lotes afetados" required hint="Um por linha (ou “todos os lotes”)." errors={errors.alertLots}>
            {(aria) => <textarea {...aria} className="fe-input min-h-20" value={values.alertLots} onChange={(e) => set('alertLots', e.target.value)} />}
          </Field>
          <Field id="post-alertReason" label="Motivo" required errors={errors.alertReason}>
            {(aria) => <textarea {...aria} className="fe-input min-h-20" maxLength={1000} value={values.alertReason} onChange={(e) => set('alertReason', e.target.value)} />}
          </Field>
          <Field id="post-alertSourceUrl" label="Fonte oficial (https)" required hint="Ex.: https://www.gov.br/anvisa/…" errors={errors.alertSourceUrl}>
            {(aria) => <input {...aria} type="url" className="fe-input" value={values.alertSourceUrl} onChange={(e) => set('alertSourceUrl', e.target.value)} />}
          </Field>
        </div>
      )}
    </Section>
  );
}
