// Campos de type_details por tipo de conta, espelhando UserTypeDetailsValidator.java (RF1). Só as chaves da
// regra do tipo vão para a API: chave a mais é recusada (minimização).
import type { AccountTypeCode } from '@friendly-eats/api';

export interface DetailField {
  key: string;
  label: string;
  required: boolean;
  kind: 'text' | 'email' | 'url' | 'select' | 'uf' | 'document';
  options?: ReadonlyArray<{ value: string; label: string }>;
  hint?: string;
  autoComplete?: string;
  /** Nunca aparece no perfil público. */
  private?: boolean;
}

const city = (required: boolean): DetailField => ({ key: 'city', label: 'Cidade', required, kind: 'text', autoComplete: 'address-level2' });
const state = (required: boolean): DetailField => ({ key: 'state', label: 'UF', required, kind: 'uf' });
const website = (required: boolean, label = 'Site'): DetailField => ({
  key: 'website',
  label,
  required,
  kind: 'url',
  hint: 'Endereço completo, começando com https://',
  autoComplete: 'url',
});
const cnpj = (required: boolean): DetailField => ({
  key: 'cnpj',
  label: 'CNPJ',
  required,
  kind: 'document',
  hint: '14 caracteres; desde 2026 o CNPJ pode ter letras.',
});
const legalName: DetailField = { key: 'legalName', label: 'Razão social', required: true, kind: 'text', autoComplete: 'organization' };
const cnae: DetailField = { key: 'cnae', label: 'CNAE principal', required: true, kind: 'text', hint: 'No formato da Receita, ex.: 5611-2/01.' };
const representative: DetailField[] = [
  { key: 'representativeName', label: 'Nome do representante', required: true, kind: 'text', private: true },
  { key: 'representativeRole', label: 'Cargo do representante', required: true, kind: 'text', private: true },
  { key: 'representativeEmail', label: 'E-mail do representante', required: true, kind: 'email', private: true },
];

export const INFORMAL_FORMATS = [
  { value: 'AMBULANTE', label: 'Ambulante (barraca, carrinho, tabuleiro)' },
  { value: 'FOOD_TRUCK', label: 'Food truck ou trailer' },
  { value: 'COZINHA_CASEIRA', label: 'Cozinha caseira (produção em casa, encomenda, marmita)' },
] as const;

/** Estabelecimento sem CNPJ: o CPF do titular substitui CNPJ, razão social, CNAE e representante. */
export const INFORMAL_PLACE_FIELDS: DetailField[] = [
  { key: 'informalFormat', label: 'Formato do negócio', required: true, kind: 'select', options: INFORMAL_FORMATS },
  {
    key: 'cpf',
    label: 'CPF do titular',
    required: true,
    kind: 'document',
    private: true,
    hint: 'Usado só para conferir o selo; nunca aparece no perfil.',
  },
  { key: 'tradeName', label: 'Nome do negócio', required: true, kind: 'text' },
  city(true),
  state(true),
  website(false),
];

export const TYPE_FIELDS: Record<AccountTypeCode, DetailField[]> = {
  COMENSAL: [city(false), state(false), website(false)],
  CRIADOR: [
    {
      key: 'format',
      label: 'Formato',
      required: true,
      kind: 'select',
      options: [
        { value: 'CRITICO', label: 'Crítico' },
        { value: 'BLOG', label: 'Blog' },
        { value: 'VIDEO', label: 'Vídeo' },
        { value: 'PODCAST', label: 'Podcast' },
        { value: 'GUIA', label: 'Guia' },
      ],
    },
    { key: 'channelName', label: 'Nome do canal', required: true, kind: 'text' },
    website(true, 'Site ou perfil do canal'),
    cnpj(false),
    city(false),
    state(false),
  ],
  CHEF: [
    {
      key: 'role',
      label: 'Função',
      required: true,
      kind: 'select',
      options: [
        { value: 'CHEF', label: 'Chef' },
        { value: 'COZINHEIRO', label: 'Cozinheiro(a)' },
        { value: 'CONFEITEIRO', label: 'Confeiteiro(a)' },
        { value: 'PADEIRO', label: 'Padeiro(a)' },
        { value: 'BARISTA', label: 'Barista' },
        { value: 'SOMMELIER', label: 'Sommelier' },
        { value: 'BARTENDER', label: 'Bartender' },
        { value: 'NUTRICIONISTA', label: 'Nutricionista' },
      ],
    },
    { key: 'workplace', label: 'Onde trabalha', required: false, kind: 'text' },
    {
      key: 'professionalRegistration',
      label: 'Registro profissional',
      required: false,
      kind: 'text',
      private: true,
      hint: 'Privado: só você e a moderação veem.',
    },
    website(false),
    city(false),
    state(false),
  ],
  ESTABELECIMENTO: [
    cnpj(true),
    legalName,
    { key: 'tradeName', label: 'Nome fantasia', required: true, kind: 'text' },
    cnae,
    city(true),
    state(true),
    ...representative,
    website(false),
  ],
  VAREJO: [
    {
      key: 'segment',
      label: 'Segmento',
      required: true,
      kind: 'select',
      options: [
        { value: 'MERCADO', label: 'Mercado' },
        { value: 'EMPORIO', label: 'Empório' },
        { value: 'INDUSTRIA', label: 'Indústria' },
        { value: 'DISTRIBUIDOR', label: 'Distribuidor' },
        { value: 'PRODUTOR', label: 'Produtor' },
      ],
    },
    cnpj(true),
    legalName,
    cnae,
    ...representative,
    { key: 'tradeName', label: 'Nome fantasia', required: false, kind: 'text' },
    website(false),
    city(false),
    state(false),
  ],
  COMUNIDADE: [
    {
      key: 'communityType',
      label: 'Tipo de comunidade',
      required: true,
      kind: 'select',
      options: [
        { value: 'CONFRARIA', label: 'Confraria' },
        { value: 'CLUBE', label: 'Clube' },
        { value: 'COLETIVO', label: 'Coletivo' },
        { value: 'FEIRA', label: 'Feira' },
        { value: 'GRUPO_INFORMAL', label: 'Grupo informal' },
      ],
    },
    city(true),
    state(true),
    { key: 'contactName', label: 'Nome do contato', required: true, kind: 'text', private: true },
    { key: 'contactEmail', label: 'E-mail do contato', required: true, kind: 'email', private: true },
    cnpj(false),
    website(false),
  ],
  OFICIAL: [
    {
      key: 'agency',
      label: 'Órgão',
      required: true,
      kind: 'select',
      options: [
        { value: 'ANVISA', label: 'Anvisa' },
        { value: 'VIGILANCIA_ESTADUAL', label: 'Vigilância sanitária estadual' },
        { value: 'VIGILANCIA_MUNICIPAL', label: 'Vigilância sanitária municipal' },
        { value: 'PROCON', label: 'Procon' },
        { value: 'MINISTERIO_AGRICULTURA', label: 'Ministério da Agricultura' },
        { value: 'OUTRO_ORGAO', label: 'Outro órgão' },
      ],
    },
    cnpj(true),
    legalName,
    website(true, 'Site oficial'),
    ...representative,
    city(false),
    state(false),
  ],
};

/** E-mail do responsável: obrigatório para menor de 16 (ECA Digital), só na conta Comensal. */
export const GUARDIAN_FIELD: DetailField = {
  key: 'guardianEmail',
  label: 'E-mail do responsável',
  required: true,
  kind: 'email',
  private: true,
  hint: 'Para menores de 16 anos. A conta começa com as proteções mais fortes ligadas.',
};

export function fieldsFor(type: AccountTypeCode, informal: boolean, minorUnder16: boolean): DetailField[] {
  if (type === 'ESTABELECIMENTO' && informal) return INFORMAL_PLACE_FIELDS;
  const base = TYPE_FIELDS[type];
  return type === 'COMENSAL' && minorUnder16 ? [...base, GUARDIAN_FIELD] : base;
}

/** Monta type_details só com as chaves do tipo, sem vazios. */
export function buildTypeDetails(fields: DetailField[], values: Record<string, string>): Record<string, string> {
  const out: Record<string, string> = {};
  for (const f of fields) {
    const v = values[f.key]?.trim();
    if (v) out[f.key] = v;
  }
  return out;
}
