// O 422 da API traz errors[] como frases em pt-BR (sem nome de campo estruturado). Aqui cada frase é
// associada ao campo do formulário pelo texto; o que não casar aparece no resumo do topo.

export interface MappedErrors {
  fields: Record<string, string[]>;
  general: string[];
}

type Rule = { test: RegExp; field: string };

function add(out: MappedErrors, field: string | null, msg: string) {
  if (!field) {
    out.general.push(msg);
    return;
  }
  (out.fields[field] ??= []).push(msg);
}

/** Troca "chave" pelo rótulo da tela: 'Preencha o campo "cnpj"' → 'Preencha o campo “CNPJ”'. */
function humanize(msg: string, labels: Record<string, string>): string {
  return msg.replace(/"(\w+)"/g, (m, key: string) => (labels[key] ? `“${labels[key]}”` : m));
}

const REGISTER_RULES: Rule[] = [
  // a senha vem antes: "A senha não pode conter seu e-mail ou @usuário" é erro de senha
  { test: /senha/i, field: 'password' },
  { test: /Termos de Uso/, field: 'acceptTerms' },
  { test: /nome de exibição/i, field: 'name' },
  { test: /@usuário/, field: 'handle' },
  { test: /^Informe um e-mail válido/, field: 'email' },
  { test: /^Não foi possível usar este e-mail/, field: 'email' },
  { test: /tipo de usuário/i, field: 'userType' },
  { test: /data de nascimento|para maiores de|responsável maior de 18/i, field: 'dateOfBirth' },
  { test: /^Sem CNPJ\?|CNPJ inválido/, field: 'cnpj' },
  { test: /CPF inválido/, field: 'cpf' },
  { test: /site completo/, field: 'website' },
  { test: /CNAE/, field: 'cnae' },
];

/**
 * Cadastro (RF1): mensagens do RegistrationService, PasswordPolicy e UserTypeDetailsValidator.
 * {@code detailKeys} são os campos de type_details visíveis, com o rótulo de cada um.
 */
export function mapRegisterErrors(errors: string[], detailLabels: Record<string, string>): MappedErrors {
  const out: MappedErrors = { fields: {}, general: [] };
  for (const raw of errors) {
    const quoted = /"(\w+)"/.exec(raw)?.[1];
    if (quoted && detailLabels[quoted] && !/não se aplica/.test(raw)) {
      add(out, quoted, humanize(raw, detailLabels));
      continue;
    }
    const rule = REGISTER_RULES.find((r) => r.test.test(raw));
    add(out, rule?.field ?? null, humanize(raw, detailLabels));
  }
  return out;
}

/** Avaliação (RF8): mensagens do EstablishmentRules.review. */
export function mapReviewErrors(errors: string[], dimensionLabels: Record<string, string>): MappedErrors {
  const out: MappedErrors = { fields: {}, general: [] };
  const labels: Record<string, string> = { visitMode: 'Como foi a visita', body: 'Texto da avaliação', ...dimensionLabels };
  for (const raw of errors) {
    const msg = humanize(raw, labels);
    if (/^Dê uma nota/.test(raw)) {
      add(out, 'rating', msg);
    } else if (/"visitMode"/.test(raw)) {
      add(out, 'visitMode', /^Preencha/.test(raw) ? 'Escolha como foi a visita.' : msg);
    } else if (/^Gasto por pessoa/.test(raw)) {
      add(out, 'spent', msg);
    } else if (/"body"/.test(raw)) {
      add(out, 'body', msg);
    } else {
      const dim = Object.entries(dimensionLabels).find(([, label]) => raw.startsWith(`${label}:`) || raw.startsWith(`${label} não`));
      add(out, dim ? `dim-${dim[0]}` : null, msg);
    }
  }
  return out;
}

const PROFILE_RULES: Rule[] = [
  { test: /nome de exibição/i, field: 'name' },
  { test: /@usuário/, field: 'handle' },
  { test: /\bbio\b/i, field: 'bio' },
  { test: /pronome/i, field: 'pronouns' },
  { test: /^Até 5 links/, field: 'links' },
  { test: /^País/, field: 'countryOfResidence' },
  { test: /e-mail de recuperação/i, field: 'recoveryEmail' },
  { test: /^Celular/, field: 'cellphoneNumber' },
  { test: /culinária/i, field: 'favoriteCuisines' },
  { test: /estilos? alimentar/i, field: 'dietaryStyles' },
  { test: /^Sem CNPJ\?|CNPJ inválido/, field: 'cnpj' },
  { test: /CPF inválido/, field: 'cpf' },
  { test: /site completo/, field: 'website' },
  { test: /CNAE/, field: 'cnae' },
];

/**
 * Perfil (RF2): mensagens do ProfileService e do UserTypeDetailsValidator. {@code detailLabels} são os campos do
 * tipo de conta visíveis, com o rótulo de cada um.
 */
export function mapProfileErrors(errors: string[], detailLabels: Record<string, string>): MappedErrors {
  const out: MappedErrors = { fields: {}, general: [] };
  for (const raw of errors) {
    const quoted = /"(\w+)"/.exec(raw)?.[1];
    if (quoted && detailLabels[quoted] && !/não se aplica/.test(raw)) {
      add(out, quoted, humanize(raw, detailLabels));
      continue;
    }
    const rule = PROFILE_RULES.find((r) => r.test.test(raw));
    add(out, rule?.field ?? null, humanize(raw, detailLabels));
  }
  return out;
}

/** Modo sudo (StepUp): 403 "Senha incorreta." ou "Senha ou código do app autenticador incorretos.". */
export function mapSudoError(detail: string, withCode: boolean): MappedErrors {
  const field = withCode && /código/.test(detail) ? 'code' : 'password';
  return { fields: { [field]: [detail] }, general: [] };
}

// ------------------------------------------------------------ post (RF9): mensagens do PostRules, do PostPublishingService
// e do BackgroundValidator (RF16). Os campos vêm entre aspas ("priceCents"); o resto, pelo texto.

/** Nome do campo do PostDraft → rótulo da tela. */
export const POST_FIELD_LABELS: Record<string, string> = {
  title: 'Título',
  body: 'Texto',
  altText: 'Texto alternativo',
  mediaObjectKey: 'Foto',
  establishmentId: 'Lugar',
  cuisine: 'Culinária',
  foodFormat: 'Tipo de comida',
  dietaryStyles: 'Estilos alimentares',
  allergens: 'Alergênicos',
  warnings: 'Advertências',
  ageRating: 'Classificação',
  advertising: 'Publicidade',
  city: 'Cidade',
  startsAt: 'Início',
  endsAt: 'Fim',
  priceCents: 'Preço',
  priceFromCents: 'Preço de',
  priceToCents: 'Preço por',
  discountPercent: 'Desconto',
  free: 'Gratuito',
  recipe: 'Receita',
  'recipe.ingredients': 'Ingredientes',
  'recipe.steps': 'Modo de preparo',
  alert: 'Alerta',
  'alert.product': 'Produto',
  'alert.brand': 'Marca',
  'alert.lots': 'Lotes',
  'alert.reason': 'Motivo',
  'alert.sourceUrl': 'Fonte oficial',
  background: 'Moldura',
};

const POST_RULES: Rule[] = [
  { test: /"recipe\.ingredients"|ingrediente/i, field: 'ingredients' },
  { test: /"recipe\.steps"|passo do modo de preparo/i, field: 'steps' },
  { test: /^Rendimento/, field: 'servings' },
  { test: /^Tempo de preparo/, field: 'prepMinutes' },
  { test: /^Receita precisa/, field: 'ingredients' },
  { test: /"alert\.product"|produto do alerta/i, field: 'alertProduct' },
  { test: /"alert\.brand"|marca do produto/i, field: 'alertBrand' },
  { test: /"alert\.lots"|lotes afetados/i, field: 'alertLots' },
  { test: /"alert\.reason"|motivo do alerta/i, field: 'alertReason' },
  { test: /"alert\.sourceUrl"|fonte (oficial )?do alerta|fonte oficial|"sourceUrl"/i, field: 'alertSourceUrl' },
  { test: /^Alerta sanitário precisa/, field: 'alertProduct' },
  { test: /moldura|única arte|já traz arte própria|container|contraste|Cartela da época|^Padrão desconhecido|^Gradiente desconhecido|^Animação|^Material desconhecido|^Skin|^Variação de padrão|^Formato de padrão|^Layout desconhecido|^Cor dos textos|^Arte própria/i, field: 'background' },
  { test: /"title"|título|^Escreva a pergunta/i, field: 'title' },
  { test: /"body"/, field: 'body' },
  { test: /"altText"|^Descreva a foto/, field: 'altText' },
  { test: /foto|"mediaObjectKey"/i, field: 'photo' },
  { test: /"establishmentId"|^Estabelecimento não encontrado|^O lugar está fechado/, field: 'place' },
  { test: /"cuisine"/, field: 'cuisine' },
  { test: /"foodFormat"/, field: 'foodFormat' },
  { test: /"dietaryStyles"|estilos alimentares/i, field: 'dietaryStyles' },
  { test: /"warnings"|advertência/i, field: 'warnings' },
  { test: /^Alergênico|lista vigente da Anvisa|^Nível de/, field: 'allergens' },
  { test: /18\+|"ageRating"|sempre Livre/, field: 'ageRating' },
  { test: /Publicidade|"advertising"/, field: 'advertising' },
  { test: /"city"/, field: 'city' },
  { test: /"endsAt"/, field: 'endsAt' },
  { test: /"startsAt"|vigência|horário do evento/, field: 'startsAt' },
  { test: /o preço "de" precisa ser maior/, field: 'priceTo' },
  { test: /"priceFromCents"|"priceToCents"|"de\/por"/, field: 'priceFrom' },
  { test: /"discountPercent"|^Desconto/, field: 'discount' },
  { test: /"priceCents"|^Preço em centavos|^Evento: informe o preço|"free"/, field: 'price' },
];

/** Campo do PostDraft citado entre aspas → campo da tela. */
const POST_QUOTED: Record<string, string> = {
  title: 'title',
  body: 'body',
  altText: 'altText',
  mediaObjectKey: 'photo',
  establishmentId: 'place',
  cuisine: 'cuisine',
  foodFormat: 'foodFormat',
  dietaryStyles: 'dietaryStyles',
  allergens: 'allergens',
  warnings: 'warnings',
  ageRating: 'ageRating',
  advertising: 'advertising',
  city: 'city',
  startsAt: 'startsAt',
  endsAt: 'endsAt',
  priceCents: 'price',
  free: 'price',
  priceFromCents: 'priceFrom',
  priceToCents: 'priceTo',
  discountPercent: 'discount',
  recipe: 'ingredients',
  'recipe.ingredients': 'ingredients',
  'recipe.steps': 'steps',
  alert: 'alertProduct',
  'alert.product': 'alertProduct',
  'alert.brand': 'alertBrand',
  'alert.lots': 'alertLots',
  'alert.reason': 'alertReason',
  'alert.sourceUrl': 'alertSourceUrl',
  background: 'background',
};

/** Troca "priceCents" por “Preço” (aceita os nomes com ponto, como "recipe.steps"). */
export function humanizePostMessage(msg: string): string {
  return msg.replace(/"([\w.]+)"/g, (m, key: string) => (POST_FIELD_LABELS[key] ? `“${POST_FIELD_LABELS[key]}”` : m));
}

/** Publicar e editar post (RF9 · RF16): cada frase do 422 vai para o campo da tela; o resto, para o resumo. */
export function mapPostErrors(errors: string[], visible?: Set<string>): MappedErrors {
  const out: MappedErrors = { fields: {}, general: [] };
  for (const raw of errors) {
    const quoted = [...raw.matchAll(/"([\w.]+)"/g)].map((m) => POST_QUOTED[m[1]!]).find(Boolean);
    const target = quoted ?? POST_RULES.find((r) => r.test.test(raw))?.field;
    const field = target && (!visible || visible.has(target)) ? target : null;
    add(out, field, humanizePostMessage(raw));
  }
  return out;
}
