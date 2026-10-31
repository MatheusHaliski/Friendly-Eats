// Formulário do dono do lugar (RF6 + RF11): valores da tela ⇄ EstablishmentDraft, validação local e o mapa das
// frases do 422 (EstablishmentRules e PlaceDetailsRules) para os campos. Puro: testado em place-form.test.ts.
import type {
  ActionProviderItem,
  AdjustmentKind,
  ChargesDraft,
  CouvertKind,
  EstablishmentDraft,
  EstablishmentView,
  RangeDraft,
  WeekDay,
} from '@friendly-eats/api';
import { centsToInput, parseReaisToCents } from './format';

export const WEEK_DAYS: ReadonlyArray<{ code: WeekDay; label: string; short: string }> = [
  { code: 'MONDAY', label: 'Segunda', short: 'seg' },
  { code: 'TUESDAY', label: 'Terça', short: 'ter' },
  { code: 'WEDNESDAY', label: 'Quarta', short: 'qua' },
  { code: 'THURSDAY', label: 'Quinta', short: 'qui' },
  { code: 'FRIDAY', label: 'Sexta', short: 'sex' },
  { code: 'SATURDAY', label: 'Sábado', short: 'sáb' },
  { code: 'SUNDAY', label: 'Domingo', short: 'dom' },
];

export const MAX_RANGES = 3;
export const MAX_SECONDARY_TYPES = 3;
export const MAX_SECONDARY_CUISINES = 2;
export const MAX_SECONDARY_FORMATS = 4;
/** Formas de pagamento que aceitam desconto ou acréscimo (códigos das facetas de pagamento). */
export const PAYMENT_METHODS = ['PIX', 'DINHEIRO', 'CARTAO_DEBITO', 'CARTAO_CREDITO', 'APROXIMACAO', 'VALE_REFEICAO', 'VALE_ALIMENTACAO'];

export type Visibility = 'ADDR_PUBLIC_FULL' | 'ADDR_PUBLIC_BAIRRO' | 'ADDR_HIDDEN';

export interface SpecialValues {
  date: string;
  closed: boolean;
  ranges: RangeDraft[];
  note: string;
}

export interface PlaceFormValues {
  name: string;
  description: string;
  primaryType: string;
  secondaryTypes: string[];
  primaryCuisine: string;
  secondaryCuisines: string[];
  primaryFormat: string;
  secondaryFormats: string[];
  approach: string;
  dietary: Record<string, '' | 'OFERECE' | 'EXCLUSIVO'>;
  uf: string;
  municipalityIbge: string;
  city: string;
  neighborhood: string;
  street: string;
  streetNumber: string;
  complement: string;
  cep: string;
  latitude: string;
  longitude: string;
  addressVisibility: Visibility;
  priceMin: string;
  priceMax: string;
  priceNotApplicable: boolean;
  hoursInformed: boolean;
  weekly: Record<WeekDay, RangeDraft[]>;
  special: SpecialValues[];
  tempClosed: boolean;
  reopensOn: string;
  serviceFee: string;
  couvertKind: '' | CouvertKind;
  couvertValue: string;
  couvertDays: WeekDay[];
  adjustments: Array<{ method: string; kind: AdjustmentKind; percent: string }>;
  facets: Record<string, string>;
  /** Provedor → link (vazio = sem botão). */
  links: Record<string, string>;
  preferredDelivery: string;
  whatsapp: string;
  status: 'ACTIVE' | 'CLOSED';
}

export type PlaceErrors = Record<string, string[]>;

function emptyWeek(): Record<WeekDay, RangeDraft[]> {
  return { MONDAY: [], TUESDAY: [], WEDNESDAY: [], THURSDAY: [], FRIDAY: [], SATURDAY: [], SUNDAY: [] };
}

export function emptyPlaceValues(): PlaceFormValues {
  return {
    name: '',
    description: '',
    primaryType: '',
    secondaryTypes: [],
    primaryCuisine: '',
    secondaryCuisines: [],
    primaryFormat: '',
    secondaryFormats: [],
    approach: 'TRADICIONAL',
    dietary: {},
    uf: '',
    municipalityIbge: '',
    city: '',
    neighborhood: '',
    street: '',
    streetNumber: '',
    complement: '',
    cep: '',
    latitude: '',
    longitude: '',
    addressVisibility: 'ADDR_PUBLIC_FULL',
    priceMin: '',
    priceMax: '',
    priceNotApplicable: false,
    hoursInformed: false,
    weekly: emptyWeek(),
    special: [],
    tempClosed: false,
    reopensOn: '',
    serviceFee: '',
    couvertKind: '',
    couvertValue: '',
    couvertDays: [],
    adjustments: [],
    facets: {},
    links: {},
    preferredDelivery: '',
    whatsapp: '',
    status: 'ACTIVE',
  };
}

const coord = (n: number | null) => (n === null ? '' : String(n));

/** A página do dono (GET com sessão) de volta nos campos. */
export function valuesFromPlace(p: EstablishmentView): PlaceFormValues {
  const c = p.classification;
  const l = p.location;
  const weekly = emptyWeek();
  for (const d of p.hours.weekly) weekly[d.day] = d.ranges.map((r) => ({ opens: r.opens, closes: r.closes }));
  const facets: Record<string, string> = {};
  for (const s of p.facets) for (const f of s.items) if (f.origin === 'DECLARADO_DONO') facets[f.code] = f.value;
  const links: Record<string, string> = {};
  let preferred = '';
  let whatsapp = '';
  for (const a of p.actions) {
    if (a.kind === 'WHATSAPP') whatsapp = a.phone ?? '';
    else links[a.provider] = a.url;
    if (a.preferred) preferred = a.provider;
  }
  const dietary: PlaceFormValues['dietary'] = {};
  for (const d of c.dietaryOffers) dietary[d.code] = d.level;
  return {
    ...emptyPlaceValues(),
    name: p.name,
    description: p.description ?? '',
    primaryType: p.primaryType.code,
    secondaryTypes: p.secondaryTypes.map((t) => t.code),
    primaryCuisine: c.primaryCuisine?.code ?? '',
    secondaryCuisines: c.secondaryCuisines.map((x) => x.code),
    primaryFormat: c.primaryFormat?.code ?? '',
    secondaryFormats: c.secondaryFormats.map((x) => x.code),
    approach: c.approach?.code ?? 'TRADICIONAL',
    dietary,
    uf: l.uf ?? '',
    municipalityIbge: l.municipalityIbge ?? '',
    city: l.city ?? '',
    neighborhood: l.neighborhood ?? '',
    street: l.street ?? '',
    streetNumber: l.streetNumber ?? '',
    complement: l.complement ?? '',
    cep: l.cep ?? '',
    latitude: coord(l.latitude),
    longitude: coord(l.longitude),
    addressVisibility: (l.addressVisibility as Visibility | null) ?? 'ADDR_PUBLIC_FULL',
    priceMin: centsToInput(p.price.declaredMinCents),
    priceMax: centsToInput(p.price.declaredMaxCents),
    priceNotApplicable: p.price.level === 'PRICE_NA',
    hoursInformed: p.hours.informed,
    weekly,
    special: p.hours.special.map((s) => ({
      date: s.date,
      closed: s.closed,
      ranges: s.ranges.map((r) => ({ opens: r.opens, closes: r.closes })),
      note: s.note ?? '',
    })),
    tempClosed: p.hours.temporaryClosure.closed,
    reopensOn: p.hours.temporaryClosure.reopensOn ?? '',
    serviceFee: p.charges.serviceFeePercent === null ? '' : String(p.charges.serviceFeePercent),
    couvertKind: p.charges.couvert?.kind ?? '',
    couvertValue: p.charges.couvert ? centsToInput(p.charges.couvert.cents) : '',
    couvertDays: (p.charges.couvert?.days ?? []).map((d) => d.code as WeekDay),
    adjustments: p.charges.paymentAdjustments.map((a) => ({ method: a.method, kind: a.kind, percent: String(a.percent) })),
    facets,
    links,
    preferredDelivery: preferred,
    whatsapp,
    status: p.status === 'CLOSED' ? 'CLOSED' : 'ACTIVE',
  };
}

const TIME = /^([01]\d|2[0-3]):[0-5]\d$/;

function intOrNaN(s: string): number | null {
  const t = s.trim();
  if (!t) return null;
  return /^\d{1,3}$/.test(t) ? Number(t) : Number.NaN;
}

function add(errors: PlaceErrors, field: string, msg: string) {
  (errors[field] ??= []).push(msg);
}

/**
 * Monta o corpo do POST (ou do PATCH, que manda tudo de novo: cada bloco troca por inteiro) e confere o que dá para
 * conferir sem o servidor. O servidor revalida tudo.
 */
export function buildPlaceDraft(v: PlaceFormValues, editing: boolean, providers: ActionProviderItem[]): { draft: EstablishmentDraft; errors: PlaceErrors } {
  const errors: PlaceErrors = {};
  if (!v.name.trim()) add(errors, 'name', 'Informe o nome do lugar.');
  if (!v.primaryType) add(errors, 'primaryType', 'Escolha o tipo principal.');
  if (!v.uf) add(errors, 'uf', 'Escolha a UF.');
  if (!/^\d{7}$/.test(v.municipalityIbge.trim())) add(errors, 'municipalityIbge', 'Município pelo código IBGE de 7 dígitos, ex.: 3106200 (Belo Horizonte).');
  if (!v.city.trim()) add(errors, 'city', 'Informe o nome da cidade.');
  const lat = v.latitude.trim() ? Number(v.latitude.replace(',', '.')) : null;
  const lng = v.longitude.trim() ? Number(v.longitude.replace(',', '.')) : null;
  if ((lat === null) !== (lng === null) || Number.isNaN(lat) || Number.isNaN(lng)) add(errors, 'pin', 'Informe latitude e longitude juntas (ou marque o pino no mapa).');
  const min = v.priceNotApplicable ? null : parseReaisToCents(v.priceMin);
  const max = v.priceNotApplicable ? null : parseReaisToCents(v.priceMax);
  if (Number.isNaN(min) || Number.isNaN(max)) add(errors, 'priceMin', 'Gasto por pessoa em reais, ex.: 45 ou 45,90.');

  // horários
  const weekly: Partial<Record<WeekDay, RangeDraft[]>> = {};
  if (v.hoursInformed) {
    for (const d of WEEK_DAYS) {
      const ranges = v.weekly[d.code].filter((r) => r.opens || r.closes);
      for (const r of ranges) {
        if (!TIME.test(r.opens) || !TIME.test(r.closes)) add(errors, `hours-${d.code}`, `${d.label}: use HH:MM nas faixas, ex.: 11:30.`);
      }
      if (ranges.length) weekly[d.code] = ranges;
    }
  }
  const special = v.special
    .filter((s) => s.date)
    .map((s) => ({ date: s.date, closed: s.closed, ranges: s.closed ? [] : s.ranges.filter((r) => r.opens || r.closes), note: s.note.trim() || null }));
  if (v.tempClosed && v.reopensOn && !/^\d{4}-\d{2}-\d{2}$/.test(v.reopensOn)) add(errors, 'reopensOn', 'Data de volta inválida.');

  // cobranças
  const fee = intOrNaN(v.serviceFee);
  if (Number.isNaN(fee) || (fee !== null && fee > 20)) add(errors, 'serviceFee', 'Taxa de serviço de 1% a 20% (0 ou vazio = sem taxa).');
  let couvert: ChargesDraft['couvert'] = null;
  if (v.couvertKind) {
    const cents = parseReaisToCents(v.couvertValue);
    if (cents === null || Number.isNaN(cents) || cents < 1) add(errors, 'couvert', 'Couvert: informe o valor por pessoa.');
    if (v.couvertKind === 'ARTISTICO' && v.couvertDays.length === 0) add(errors, 'couvert', 'Couvert artístico só nos dias com apresentação ao vivo: escolha os dias.');
    couvert = { kind: v.couvertKind, cents: cents === null || Number.isNaN(cents) ? null : cents, days: v.couvertKind === 'ARTISTICO' ? v.couvertDays : [] };
  }
  const adjustments = v.adjustments.filter((a) => a.method).map((a) => {
    const p = intOrNaN(a.percent);
    if (p === null || Number.isNaN(p) || p < 1 || p > 20) add(errors, 'adjustments', 'Desconto ou acréscimo de 1% a 20%.');
    return { method: a.method, kind: a.kind, percent: p === null || Number.isNaN(p) ? null : p };
  });

  // botões
  const links = providers
    .filter((p) => p.kind !== 'WHATSAPP' && v.links[p.code]?.trim())
    .map((p) => ({ provider: p.code, url: v.links[p.code]!.trim(), preferred: p.kind === 'DELIVERY' && v.preferredDelivery === p.code }));
  for (const l of links) {
    if (!/^https:\/\//i.test(l.url)) add(errors, `link-${l.provider}`, 'Use o endereço completo, começando com https://.');
  }

  const dietaryOffers: Record<string, 'OFERECE' | 'EXCLUSIVO'> = {};
  for (const [k, lvl] of Object.entries(v.dietary)) if (lvl) dietaryOffers[k] = lvl;
  const facets: Record<string, string> = {};
  for (const [k, val] of Object.entries(v.facets)) if (val) facets[k] = val;
  const trimOrNull = (s: string) => (s.trim() ? s.trim() : null);

  const draft: EstablishmentDraft = {
    name: v.name.trim(),
    description: v.description.trim() || (editing ? '' : null),
    primaryType: v.primaryType,
    secondaryTypes: v.secondaryTypes.filter(Boolean),
    location: {
      uf: v.uf,
      municipalityIbge: v.municipalityIbge.trim(),
      city: v.city.trim(),
      neighborhood: trimOrNull(v.neighborhood),
      street: trimOrNull(v.street),
      streetNumber: trimOrNull(v.streetNumber),
      complement: trimOrNull(v.complement),
      cep: trimOrNull(v.cep),
      latitude: lat,
      longitude: lng,
      addressVisibility: v.addressVisibility,
    },
    price: v.priceNotApplicable
      ? { minCents: null, maxCents: null, notApplicable: true }
      : { minCents: Number.isNaN(min) ? null : min, maxCents: Number.isNaN(max) ? null : max, notApplicable: false },
    classification: {
      primaryCuisine: v.primaryCuisine || null,
      secondaryCuisines: v.secondaryCuisines.filter(Boolean),
      primaryFormat: v.primaryFormat || null,
      secondaryFormats: v.secondaryFormats.filter(Boolean),
      approach: v.approach || null,
      dietaryOffers,
    },
    hours: { weekly: v.hoursInformed ? weekly : null, special },
    temporaryClosure: { closed: v.tempClosed, reopensOn: v.tempClosed && v.reopensOn ? v.reopensOn : null },
    charges: { serviceFeePercent: fee === null || Number.isNaN(fee) ? null : fee, couvert, paymentAdjustments: adjustments },
    facets,
    actions: { links, whatsapp: trimOrNull(v.whatsapp) },
  };
  if (editing) draft.status = v.status;
  return { draft, errors };
}

// ------------------------------------------------------------ 422 → campos

type Rule = { test: RegExp; field: string };

/** Frase do EstablishmentRules/PlaceDetailsRules → campo da tela (ordem importa: o mais específico primeiro). */
const RULES: Rule[] = [
  { test: /^Consumação mínima/, field: 'serviceFee' },
  { test: /Taxa de serviço/, field: 'serviceFee' },
  { test: /[Cc]ouvert|"Música ao vivo"/, field: 'couvert' },
  { test: /^Condição para|forma de pagamento|^Forma de pagamento|: diga se é DESCONTO|: percentual de 1%/, field: 'adjustments' },
  { test: /WhatsApp/, field: 'whatsapp' },
  { test: /app de delivery|links de reserva|Provedor desconhecido|Um link por provedor/, field: 'links' },
  { test: /fechado temporariamente|^Data de volta|data de volta/, field: 'reopensOn' },
  { test: /Data especial|Datas especiais|^Em \d{2}\/\d{2}\/\d{4}/, field: 'special' },
  { test: /garantido por lei|vem do horário|vem dos links|^Faceta desconhecida|não se aplica a este tipo de lugar|: use SIM/, field: 'facets' },
  { test: /"name"|nome do lugar|nome não pode/, field: 'name' },
  { test: /"description"/, field: 'description' },
  { test: /tipo principal|tipos secundários|"primaryType"|"secondaryTypes"|Varejo|varejo é cadastrada|Mercado, feira|Cadastro com CPF/, field: 'primaryType' },
  { test: /culinária|Culinária|Fusion|contemporânea|"primaryCuisine"|"secondaryCuisines"|já inclui/, field: 'primaryCuisine' },
  { test: /formato|tipo de comida|"primaryFormat"|"secondaryFormats"/, field: 'primaryFormat' },
  { test: /Estilo alimentar|^Nível de/, field: 'dietary' },
  { test: /^UF inválida/, field: 'uf' },
  { test: /IBGE/, field: 'municipalityIbge' },
  { test: /nome da cidade|"city"/, field: 'city' },
  { test: /^CEP/, field: 'cep' },
  { test: /pino|latitude/i, field: 'pin' },
  { test: /endereço completo|só o bairro|"street"/, field: 'addressVisibility' },
  { test: /[Gg]asto|não se aplica/, field: 'priceMin' },
  { test: /^Status/, field: 'status' },
];

const DAY_BY_NAME: Record<string, WeekDay> = {
  segunda: 'MONDAY',
  terça: 'TUESDAY',
  quarta: 'WEDNESDAY',
  quinta: 'THURSDAY',
  sexta: 'FRIDAY',
  sábado: 'SATURDAY',
  domingo: 'SUNDAY',
};

export function mapPlaceErrors(errors: string[]): { fields: PlaceErrors; general: string[] } {
  const fields: PlaceErrors = {};
  const general: string[] = [];
  for (const raw of errors) {
    // horário de um dia da semana: "Em segunda, …", "Horário inválido em terça…", "A faixa de sábado…"
    const day = /^(?:Em|Horário inválido em|A faixa de|No máximo 3 faixas de horário por dia \()\s*(segunda|terça|quarta|quinta|sexta|sábado|domingo)/.exec(raw);
    if (day) {
      add(fields, `hours-${DAY_BY_NAME[day[1]!]}`, raw);
      continue;
    }
    if (/^Link do ([^:]+):/.test(raw)) {
      add(fields, 'links', raw);
      continue;
    }
    const rule = RULES.find((r) => r.test.test(raw));
    if (rule) add(fields, rule.field, raw);
    else general.push(raw);
  }
  return { fields, general };
}
