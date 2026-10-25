// Formulário de publicação (RF9) montado pelo vocabulário do servidor: os campos obrigatórios e opcionais de cada
// tipo vêm de GET /api/v1/posts/vocabulary (PostVocabulary.KindItem). Aqui eles viram grupos de campos da tela, e o
// que a pessoa digitou vira o PostDraft (ou o PATCH, na edição). O servidor confere tudo de novo (PostRules).
import type {
  AccountTypeCode,
  AgeRatingCode,
  AllergenLevelCode,
  FrameConfig,
  PostDetailView,
  PostDraft,
  PostKindCode,
  PostKindItem,
  PostPatch,
} from '@friendly-eats/api';
import { centsToInput, parseReaisToCents } from './format';

/** Grupos de campos da tela; cada um junta um ou mais campos do PostDraft. */
export type FieldKey =
  | 'title'
  | 'body'
  | 'photo'
  | 'place'
  | 'cuisine'
  | 'foodFormat'
  | 'dietaryStyles'
  | 'allergens'
  | 'warnings'
  | 'ageRating'
  | 'advertising'
  | 'city'
  | 'background'
  | 'period'
  | 'priceCents'
  | 'promoPrice'
  | 'free'
  | 'recipe'
  | 'servings'
  | 'prepMinutes'
  | 'alert';

const FIELD_OF: Record<string, FieldKey> = {
  title: 'title',
  body: 'body',
  mediaObjectKey: 'photo',
  altText: 'photo',
  establishmentId: 'place',
  cuisine: 'cuisine',
  foodFormat: 'foodFormat',
  dietaryStyles: 'dietaryStyles',
  allergens: 'allergens',
  warnings: 'warnings',
  ageRating: 'ageRating',
  advertising: 'advertising',
  city: 'city',
  background: 'background',
  startsAt: 'period',
  endsAt: 'period',
  priceCents: 'priceCents',
  priceFromCents: 'promoPrice',
  priceToCents: 'promoPrice',
  discountPercent: 'promoPrice',
  free: 'free',
  'recipe.ingredients': 'recipe',
  'recipe.steps': 'recipe',
  'recipe.servings': 'servings',
  'recipe.prepMinutes': 'prepMinutes',
  'alert.product': 'alert',
  'alert.brand': 'alert',
  'alert.lots': 'alert',
  'alert.reason': 'alert',
  'alert.sourceUrl': 'alert',
};

/** Quem é o "(dono)" e o "(criador)" das alternativas do vocabulário ("establishmentId (dono) | advertising: true (criador)"). */
function qualifierMatches(qualifier: string | null, author: AccountTypeCode): boolean {
  if (!qualifier) return true;
  if (qualifier === 'dono') return author === 'ESTABELECIMENTO' || author === 'VAREJO';
  if (qualifier === 'criador') return author === 'CRIADOR';
  return true;
}

interface Alternative {
  fields: string[];
  qualifier: string | null;
}

/** "priceFromCents+priceToCents | discountPercent" → [[priceFromCents, priceToCents], [discountPercent]]. */
export function parseRequirement(raw: string): Alternative[] {
  return raw.split('|').map((part) => {
    let p = part.trim();
    const q = /\(([^)]+)\)\s*$/.exec(p);
    const qualifier = q && (q[1] === 'dono' || q[1] === 'criador') ? q[1] : null;
    p = p.replace(/\([^)]*\)\s*$/, '').replace(/:\s*true\s*$/, '').trim();
    return { fields: p.split('+').map((f) => f.trim()).filter(Boolean), qualifier };
  });
}

export interface FormSpec {
  kind: PostKindItem;
  /** Grupos que aparecem na tela. */
  fields: Set<FieldKey>;
  /** Grupos que precisam ser preenchidos (para este tipo de conta). */
  required: Set<FieldKey>;
  /** "Um dos dois": preço ou gratuito (evento); de/por ou desconto (promoção). */
  oneOf: FieldKey[][];
}

export function formSpec(kind: PostKindItem, author: AccountTypeCode): FormSpec {
  const fields = new Set<FieldKey>();
  const required = new Set<FieldKey>();
  const oneOf: FieldKey[][] = [];
  const keyOf = (f: string) => FIELD_OF[f] ?? null;
  for (const raw of kind.requiredFields) {
    const alts = parseRequirement(raw);
    for (const alt of alts) for (const f of alt.fields) {
      const k = keyOf(f);
      if (k) fields.add(k);
    }
    const qualified = alts.some((a) => a.qualifier);
    if (alts.length === 1) {
      for (const f of alts[0]!.fields) {
        const k = keyOf(f);
        if (k) required.add(k);
      }
    } else if (qualified) {
      for (const alt of alts.filter((a) => qualifierMatches(a.qualifier, author))) {
        for (const f of alt.fields) {
          const k = keyOf(f);
          if (k) required.add(k);
        }
      }
    } else {
      const group = [...new Set(alts.flatMap((a) => a.fields.map(keyOf).filter((k): k is FieldKey => !!k)))];
      if (group.length > 1) oneOf.push(group);
      else if (group[0]) required.add(group[0]);
    }
  }
  for (const f of kind.optionalFields) {
    const k = keyOf(f);
    if (k) fields.add(k);
  }
  if (kind.photoRequired) required.add('photo');
  return { kind, fields, required, oneOf };
}

/** Tipos que a conta pode publicar, na ordem do vocabulário. */
export function publishableKinds(kinds: PostKindItem[], publishable: PostKindCode[]): PostKindItem[] {
  return kinds.filter((k) => publishable.includes(k.code));
}

// ------------------------------------------------------------ valores da tela

export interface PostFormValues {
  title: string;
  body: string;
  altText: string;
  establishmentId: number | null;
  cuisine: string;
  foodFormat: string;
  dietaryStyles: string[];
  allergens: Record<string, AllergenLevelCode>;
  warnings: string[];
  ageRating: AgeRatingCode;
  advertising: boolean;
  city: string;
  /** datetime-local (AAAA-MM-DDTHH:mm), no fuso do navegador. */
  startsAt: string;
  endsAt: string;
  price: string;
  promoMode: 'FROM_TO' | 'DISCOUNT';
  priceFrom: string;
  priceTo: string;
  discount: string;
  eventMode: 'PAID' | 'FREE';
  ingredients: string;
  steps: string;
  servings: string;
  prepMinutes: string;
  alertProduct: string;
  alertBrand: string;
  alertLots: string;
  alertReason: string;
  alertSourceUrl: string;
}

export function emptyValues(kind: PostKindItem | null, author: AccountTypeCode): PostFormValues {
  return {
    title: '',
    body: '',
    altText: '',
    establishmentId: null,
    cuisine: '',
    foodFormat: '',
    dietaryStyles: [],
    allergens: {},
    warnings: [],
    ageRating: 'LIVRE',
    // promoção de criador leva o chip Publicidade (CDC art. 36): já vem marcado
    advertising: kind?.code === 'PROMOCAO' && author === 'CRIADOR',
    city: '',
    startsAt: '',
    endsAt: '',
    price: '',
    promoMode: 'FROM_TO',
    priceFrom: '',
    priceTo: '',
    discount: '',
    eventMode: 'PAID',
    ingredients: '',
    steps: '',
    servings: '',
    prepMinutes: '',
    alertProduct: '',
    alertBrand: '',
    alertLots: '',
    alertReason: '',
    alertSourceUrl: '',
  };
}

const pad = (n: number) => String(n).padStart(2, '0');

/** ISO → valor do datetime-local no fuso de quem edita. */
export function toLocalInput(iso: string | null | undefined): string {
  if (!iso) return '';
  const d = new Date(iso);
  if (Number.isNaN(d.getTime())) return '';
  return `${d.getFullYear()}-${pad(d.getMonth() + 1)}-${pad(d.getDate())}T${pad(d.getHours())}:${pad(d.getMinutes())}`;
}

/** Valor do datetime-local → ISO-8601 em UTC; null para vazio ou inválido. */
export function fromLocalInput(value: string): string | null {
  if (!value) return null;
  const d = new Date(value);
  return Number.isNaN(d.getTime()) ? null : d.toISOString();
}

export function lines(text: string): string[] {
  return text
    .split('\n')
    .map((l) => l.trim())
    .filter(Boolean);
}

/** Post publicado → valores da tela de edição. */
export function valuesFromPost(p: PostDetailView): PostFormValues {
  const author = p.author.category?.code ?? 'COMENSAL';
  const v = emptyValues(null, author);
  const price = p.price;
  return {
    ...v,
    title: p.title,
    body: p.body ?? '',
    altText: p.altText ?? '',
    establishmentId: p.establishment?.id ?? null,
    cuisine: p.cuisine?.code ?? '',
    foodFormat: p.foodFormat?.code ?? '',
    dietaryStyles: p.dietaryStyles.map((d) => d.code),
    allergens: Object.fromEntries(p.allergens.map((a) => [a.code, a.level])),
    warnings: p.warnings.map((w) => w.code),
    ageRating: p.ageRating,
    advertising: p.advertising,
    city: p.city ?? '',
    startsAt: toLocalInput(p.startsAt),
    endsAt: toLocalInput(p.endsAt),
    price: centsToInput(price?.priceCents),
    promoMode: price?.discountPercent != null ? 'DISCOUNT' : 'FROM_TO',
    priceFrom: centsToInput(price?.priceFromCents),
    priceTo: centsToInput(price?.priceToCents),
    discount: price?.discountPercent != null ? String(price.discountPercent) : '',
    eventMode: price?.free ? 'FREE' : 'PAID',
    ingredients: p.recipe?.ingredients.join('\n') ?? '',
    steps: p.recipe?.steps.join('\n') ?? '',
    servings: p.recipe?.servings != null ? String(p.recipe.servings) : '',
    prepMinutes: p.recipe?.prepMinutes != null ? String(p.recipe.prepMinutes) : '',
    alertProduct: p.alert?.product ?? '',
    alertBrand: p.alert?.brand ?? '',
    alertLots: p.alert?.lots.join('\n') ?? '',
    alertReason: p.alert?.reason ?? '',
    alertSourceUrl: p.alert?.sourceUrl ?? '',
  };
}

// ------------------------------------------------------------ montar o pedido

/** Erros por campo da tela (ids dos controles) e os gerais. */
export type FormErrors = Record<string, string[]>;

export interface PhotoValue {
  /** Chave do envio novo (POST /api/v1/media/uploads), ou null. */
  key: string | null;
  /** Na edição: o post já tem foto e ela continua. */
  keepCurrent: boolean;
}

export interface BuildResult {
  draft: PostDraft;
  errors: FormErrors;
}

function intOrNull(text: string, field: string, label: string, errors: FormErrors): number | null {
  const t = text.trim();
  if (!t) return null;
  if (!/^\d+$/.test(t)) {
    (errors[field] ??= []).push(`${label}: use um número inteiro.`);
    return null;
  }
  return Number(t);
}

function centsOrNull(text: string, field: string, label: string, errors: FormErrors): number | null {
  const c = parseReaisToCents(text);
  if (c !== null && Number.isNaN(c)) {
    (errors[field] ??= []).push(`${label}: escreva o valor em reais, ex.: 25,90.`);
    return null;
  }
  return c;
}

/**
 * Valores → PostDraft, com as conferências que dá para fazer antes de enviar (obrigatórios, números, datas). Só vão
 * os campos que o tipo aceita: o servidor recusa campo de outro tipo ("não se aplica").
 */
export function buildDraft(spec: FormSpec, v: PostFormValues, photo: PhotoValue, background: FrameConfig | null): BuildResult {
  const errors: FormErrors = {};
  const need = (field: string, ok: boolean, msg: string) => {
    if (!ok) (errors[field] ??= []).push(msg);
  };
  const has = (k: FieldKey) => spec.fields.has(k);
  const req = (k: FieldKey) => spec.required.has(k);
  const code = spec.kind.code;
  const draft: PostDraft = { kind: code, title: v.title.trim() };

  need('title', !!draft.title, code === 'PERGUNTA' ? 'Escreva a pergunta.' : 'Dê um título ao post.');
  if (has('body')) draft.body = v.body.trim() || null;

  if (has('photo')) {
    const hasPhoto = !!photo.key || photo.keepCurrent;
    if (photo.key) draft.mediaObjectKey = photo.key;
    draft.altText = hasPhoto ? v.altText.trim() || null : null;
    need('photo', !req('photo') || hasPhoto, 'Envie a foto do prato.');
    need('altText', !hasPhoto || !!v.altText.trim(), 'Descreva a foto para quem usa leitor de tela.');
  }
  if (has('place')) {
    draft.establishmentId = v.establishmentId;
    need('place', !req('place') || v.establishmentId !== null, 'Escolha o lugar.');
  }
  if (has('cuisine')) draft.cuisine = v.cuisine || null;
  if (has('foodFormat')) draft.foodFormat = v.foodFormat || null;
  if (has('dietaryStyles')) {
    draft.dietaryStyles = v.dietaryStyles;
    need('dietaryStyles', v.dietaryStyles.length <= 8, 'No máximo 8 estilos alimentares.');
  }
  if (has('allergens')) draft.allergens = { ...v.allergens };
  if (has('warnings')) draft.warnings = v.warnings;
  if (has('ageRating')) draft.ageRating = v.ageRating;
  if (has('advertising')) {
    draft.advertising = v.advertising;
    need('advertising', !req('advertising') || v.advertising, 'Promoção de criador leva o chip Publicidade (CDC, art. 36).');
  }
  if (has('city')) draft.city = v.city.trim() || null;

  if (has('period')) {
    draft.startsAt = fromLocalInput(v.startsAt);
    draft.endsAt = fromLocalInput(v.endsAt);
    need('startsAt', !!draft.startsAt, 'Informe o início.');
    need('endsAt', !!draft.endsAt, 'Informe o fim.');
    if (draft.startsAt && draft.endsAt) need('endsAt', draft.endsAt > draft.startsAt, 'O fim precisa ser depois do início.');
  }

  const eventPrice = has('free');
  if (eventPrice) {
    if (v.eventMode === 'FREE') {
      draft.free = true;
      draft.priceCents = null;
    } else {
      draft.free = null;
      draft.priceCents = centsOrNull(v.price, 'price', 'Preço', errors);
      need('price', draft.priceCents !== null || !!errors.price, 'Informe o preço ou marque "Gratuito".');
    }
  } else if (has('priceCents')) {
    draft.priceCents = centsOrNull(v.price, 'price', 'Preço', errors);
    need('price', !req('priceCents') || draft.priceCents !== null || !!errors.price, 'Informe o preço.');
  }

  if (has('promoPrice')) {
    if (v.promoMode === 'DISCOUNT') {
      draft.discountPercent = intOrNull(v.discount, 'discount', 'Desconto', errors);
      draft.priceFromCents = null;
      draft.priceToCents = null;
      const d = draft.discountPercent;
      need('discount', (d !== null && d >= 1 && d <= 90) || !!errors.discount, 'Desconto entre 1% e 90%.');
    } else {
      draft.priceFromCents = centsOrNull(v.priceFrom, 'priceFrom', 'Preço "de"', errors);
      draft.priceToCents = centsOrNull(v.priceTo, 'priceTo', 'Preço "por"', errors);
      draft.discountPercent = null;
      need('priceFrom', draft.priceFromCents !== null || !!errors.priceFrom, 'Informe o preço "de".');
      need('priceTo', draft.priceToCents !== null || !!errors.priceTo, 'Informe o preço "por".');
      if (draft.priceFromCents != null && draft.priceToCents != null) {
        need('priceTo', draft.priceFromCents > draft.priceToCents, 'O preço "de" precisa ser maior que o "por".');
      }
    }
  }

  if (has('recipe')) {
    const recipe = {
      ingredients: lines(v.ingredients),
      steps: lines(v.steps),
      servings: has('servings') ? intOrNull(v.servings, 'servings', 'Rendimento', errors) : null,
      prepMinutes: has('prepMinutes') ? intOrNull(v.prepMinutes, 'prepMinutes', 'Tempo de preparo', errors) : null,
    };
    draft.recipe = recipe;
    need('ingredients', recipe.ingredients.length > 0, 'Liste ao menos um ingrediente.');
    need('steps', recipe.steps.length > 0, 'Escreva ao menos um passo do modo de preparo.');
  }

  if (has('alert')) {
    const alert = {
      product: v.alertProduct.trim(),
      brand: v.alertBrand.trim(),
      lots: lines(v.alertLots),
      reason: v.alertReason.trim(),
      sourceUrl: v.alertSourceUrl.trim(),
    };
    draft.alert = alert;
    need('alertProduct', !!alert.product, 'Informe o produto.');
    need('alertBrand', !!alert.brand, 'Informe a marca.');
    need('alertLots', alert.lots.length > 0, 'Informe os lotes afetados (ou "todos os lotes").');
    need('alertReason', !!alert.reason, 'Informe o motivo do alerta.');
    need('alertSourceUrl', /^https:\/\/[^\s/]+\.[^\s]+/.test(alert.sourceUrl), 'A fonte precisa ser um endereço https, ex.: https://www.gov.br/anvisa/…');
  }

  if (has('background')) draft.background = background;
  return { draft, errors };
}

/**
 * Edição: o PATCH leva os campos da tela (o tipo não muda). A foto só vai quando muda (chave nova ou null para tirar)
 * e a moldura só quando mudou: chave ausente mantém o que está gravado.
 */
export function buildPatch(
  spec: FormSpec,
  v: PostFormValues,
  photo: PhotoValue & { removed: boolean },
  background: FrameConfig | null,
  backgroundChanged: boolean,
): { patch: PostPatch; errors: FormErrors } {
  const { draft, errors } = buildDraft(spec, v, photo, background);
  const patch: PostPatch & { kind?: unknown } = { ...draft };
  delete patch.kind;
  if (spec.fields.has('photo')) {
    if (photo.key) patch.mediaObjectKey = photo.key;
    else if (photo.removed) patch.mediaObjectKey = null;
    else delete patch.mediaObjectKey;
  }
  if (!backgroundChanged) delete patch.background;
  return { patch, errors };
}
