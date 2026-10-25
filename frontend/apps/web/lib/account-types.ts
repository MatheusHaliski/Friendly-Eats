// Os 7 tipos de conta (UserCategoryEnum) com cor (token), glifo, ícone e rótulo: a cor nunca é o único sinal
// (docs/DESIGN_SYSTEM.md, seção 3; docs/RF1/README.md).
import type { AccountTypeCode } from '@friendly-eats/api';

export interface AccountTypeInfo {
  code: AccountTypeCode;
  label: string;
  /** Glifo geométrico da bolinha. */
  glyph: string;
  glyphName: string;
  /** Ícone Lucide reservado ao tipo (rótulos e menus). */
  icon: 'user-round' | 'camera' | 'chef-hat' | 'store' | 'shopping-basket' | 'users' | 'landmark';
  description: string;
  /** Selo inicial: as organizações começam com a verificação em andamento. */
  needsSeal: boolean;
  adultOnly: boolean;
}

export const ACCOUNT_TYPES: readonly AccountTypeInfo[] = [
  {
    code: 'COMENSAL',
    label: 'Comensal',
    glyph: '◇',
    glyphName: 'losango',
    icon: 'user-round',
    description: 'Pessoa que come, avalia e compartilha',
    needsSeal: false,
    adultOnly: false,
  },
  {
    code: 'CRIADOR',
    label: 'Criador',
    glyph: '✦',
    glyphName: 'estrela',
    icon: 'camera',
    description: 'Crítico, blog, vídeo, podcast ou guia gastronômico',
    needsSeal: false,
    adultOnly: true,
  },
  {
    code: 'CHEF',
    label: 'Chef',
    glyph: '⬠',
    glyphName: 'pentágono',
    icon: 'chef-hat',
    description: 'Chef, cozinheiro, confeiteiro, padeiro, barista, sommelier ou nutricionista',
    needsSeal: false,
    adultOnly: true,
  },
  {
    code: 'ESTABELECIMENTO',
    label: 'Estabelecimento',
    glyph: '▭',
    glyphName: 'retângulo',
    icon: 'store',
    description: 'Restaurante, bar, café, padaria, food truck',
    needsSeal: true,
    adultOnly: true,
  },
  {
    code: 'VAREJO',
    label: 'Varejo & Marca',
    glyph: '△',
    glyphName: 'triângulo',
    icon: 'shopping-basket',
    description: 'Mercado, empório, indústria, distribuidor ou produtor',
    needsSeal: true,
    adultOnly: true,
  },
  {
    code: 'COMUNIDADE',
    label: 'Comunidade',
    glyph: '⬢',
    glyphName: 'hexágono',
    icon: 'users',
    description: 'Confraria, clube, coletivo ou feira gastronômica',
    needsSeal: false,
    adultOnly: true,
  },
  {
    code: 'OFICIAL',
    label: 'Oficial',
    glyph: '◎',
    glyphName: 'círculo duplo',
    icon: 'landmark',
    description: 'Anvisa, vigilância sanitária, Procon e outros órgãos públicos',
    needsSeal: true,
    adultOnly: true,
  },
];

export function accountType(code: string): AccountTypeInfo {
  return ACCOUNT_TYPES.find((t) => t.code === code) ?? ACCOUNT_TYPES[0]!;
}

/** Contas de organização não avaliam (RULE_NO_INSIDER). */
export function canReview(code: string): boolean {
  return code !== 'ESTABELECIMENTO' && code !== 'VAREJO' && code !== 'OFICIAL';
}

/** Só Estabelecimento e Varejo são donos de lugares. */
export function ownsPlaces(code: string): boolean {
  return code === 'ESTABELECIMENTO' || code === 'VAREJO';
}
