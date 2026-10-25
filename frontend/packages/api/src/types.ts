// Tipos escritos à mão a partir dos records Java (o Jackson serializa com os mesmos nomes de campo).
// Fonte de cada bloco indicada no comentário; ao mudar um record, mude aqui no mesmo PR.

// ------------------------------------------------------------ conta (application/account/AccountViews.java)

/** UserCategoryEnum: os 7 tipos de conta. */
export type AccountTypeCode =
  | 'COMENSAL'
  | 'CRIADOR'
  | 'CHEF'
  | 'ESTABELECIMENTO'
  | 'VAREJO'
  | 'COMUNIDADE'
  | 'OFICIAL';

export type CategoryVerification = 'NOT_REQUIRED' | 'PENDING' | 'VERIFIED' | 'REJECTED';

export type AccountStatus =
  | 'PENDING_VERIFICATION'
  | 'ACTIVE'
  | 'SUSPENDED'
  | 'PENDING_DELETION'
  | 'ANONYMOUS_LGPD_DELETED';

/** AccountViews.CategoryView: {@code color} é a cor do tema escuro; a tela usa os tokens dos dois temas. */
export interface CategoryView {
  code: AccountTypeCode;
  label: string;
  color: string;
}

/** AccountViews.SessionUserView (GET /api/v1/auth/session). */
export interface SessionUserView {
  id: number;
  name: string;
  handle: string;
  email: string;
  category: CategoryView;
  categoryVerification: CategoryVerification;
  accountStatus: AccountStatus;
  emailVerified: boolean;
  mfaEnabled: boolean;
  minor: boolean;
  termsUpToDate: boolean;
}

export type LoginStatus = 'AUTHENTICATED' | 'MFA_REQUIRED' | 'REACTIVATION_AVAILABLE';

/** AccountViews.LoginResult. */
export interface LoginResult {
  status: LoginStatus;
  user: SessionUserView | null;
  /** Instant em ISO-8601. */
  deletionScheduledFor: string | null;
  message: string | null;
}

/** AccountViews.TermsView (GET /api/v1/legal/terms). */
export interface TermsView {
  version: string;
  url: string;
}

/** AuthController.csrf. */
export interface CsrfView {
  headerName: string;
  token: string;
}

/** AuthController.RegisterRequest; {@code dateOfBirth} em AAAA-MM-DD. */
export interface RegisterRequest {
  name: string;
  handle: string;
  email: string;
  password: string;
  userType: AccountTypeCode;
  dateOfBirth: string;
  acceptTerms: boolean;
  termsVersion: string;
  typeDetails: Record<string, string>;
}

export interface LoginRequest {
  email: string;
  password: string;
}

// ------------------------------------------------------------ estabelecimento (application/establishment/EstablishmentViews.java)

/** Código estável + rótulo; {@code group} é a macro da culinária, a família do formato ou o grupo do tipo. */
export interface Coded {
  code: string;
  label: string;
  group: string | null;
}

export interface ChipView {
  code: CuisineChipCode;
  label: string;
  lucideIcon: string;
  darkHex: string;
  lightHex: string;
}

export type CuisineChipCode =
  | 'BRASILEIRA'
  | 'EUROPEIA'
  | 'ASIATICA'
  | 'AMERICAS'
  | 'ORIENTE_MEDIO_AFRICA'
  | 'LANCHES_PIZZA'
  | 'DOCES_CAFE'
  | 'VEGETAL';

export type DietaryLevel = 'OFERECE' | 'EXCLUSIVO';

export interface DietaryOfferView {
  code: string;
  label: string;
  level: DietaryLevel;
}

export interface ClassificationView {
  primaryCuisine: Coded | null;
  secondaryCuisines: Coded[];
  primaryFormat: Coded | null;
  secondaryFormats: Coded[];
  approach: Coded | null;
  dietaryOffers: DietaryOfferView[];
  chip: ChipView | null;
}

/** PriceLevel. */
export type PriceLevelCode = 'PRICE_UNKNOWN' | 'PRICE_1' | 'PRICE_2' | 'PRICE_3' | 'PRICE_4' | 'PRICE_NA';
export type PriceSource = 'PRICE_SRC_DECLARED' | 'PRICE_SRC_REVIEWS';

/** EstablishmentViews.PriceView. */
export interface PriceView {
  level: PriceLevelCode;
  glyph: string | null;
  label: string;
  rangeLabel: string | null;
  source: PriceSource | null;
  reportCount: number;
  declaredMinCents: number | null;
  declaredMaxCents: number | null;
  accessibleLabel: string;
}

/** EstablishmentViews.LocationView: o que o público pode ver; o dono vê tudo. */
export interface LocationView {
  country: string | null;
  uf: string | null;
  municipalityIbge: string | null;
  city: string | null;
  neighborhood: string | null;
  street: string | null;
  streetNumber: string | null;
  complement: string | null;
  cep: string | null;
  latitude: number | null;
  longitude: number | null;
  geoPrecision: string | null;
  addressVisibility: string | null;
  approximate: boolean;
}

export type RatingState = 'RATING_NONE' | 'RATING_FEW' | 'RATED';

/** EstablishmentViews.RatingView; as chaves do histograma (Map<Integer,Integer>) chegam como texto. */
export interface RatingView {
  state: RatingState;
  average: number | null;
  averageLabel: string | null;
  count: number;
  histogram: Partial<Record<'1' | '2' | '3' | '4' | '5', number>>;
  /** Média por dimensão (ReviewDimension → valor), só com 5 notas ou mais naquela dimensão. */
  dimensions: Record<string, number>;
  accessibleLabel: string;
}

export interface OwnerView {
  id: number;
  name: string;
  handle: string;
  category: CategoryView;
  verified: boolean;
}

export type EstablishmentStatus = 'ACTIVE' | 'CLOSED' | 'REMOVED';

/** EstablishmentViews.EstablishmentView (GET /api/v1/establishments/{id}). */
export interface EstablishmentView {
  id: number;
  name: string;
  description: string | null;
  primaryType: Coded;
  secondaryTypes: Coded[];
  group: Coded;
  classification: ClassificationView;
  price: PriceView;
  location: LocationView;
  rating: RatingView;
  owner: OwnerView | null;
  adultsOnly: boolean;
  status: EstablishmentStatus;
  /** Só para o dono; nulo para os demais. */
  published: boolean | null;
  publishBlockers: string[] | null;
  createdAt: string;
  updatedAt: string;
}

export interface ReviewAuthorView {
  name: string;
  handle: string;
  category: CategoryView;
}

export type VisitModeCode = 'DINE_IN' | 'TAKEOUT' | 'DELIVERY';
export type ReviewDimensionCode = 'COMIDA' | 'ATENDIMENTO' | 'AMBIENTE' | 'CUSTO_BENEFICIO' | 'ENTREGA';

/** EstablishmentViews.ReviewView. */
export interface ReviewView {
  id: number;
  rating: number;
  ratingLabel: string;
  visitMode: Coded;
  dimensions: Partial<Record<ReviewDimensionCode, number>>;
  spentPerPersonCents: number | null;
  body: string | null;
  trust: string;
  edited: boolean;
  author: ReviewAuthorView;
  createdAt: string;
  updatedAt: string;
}

/** EstablishmentViews.ReviewPage (GET /api/v1/establishments/{id}/reviews). */
export interface ReviewPage {
  items: ReviewView[];
  page: number;
  size: number;
  total: number;
  rating: RatingView;
}

/** EstablishmentDrafts.ReviewDraft (PUT …/reviews/me). */
export interface ReviewDraft {
  rating: number | null;
  visitMode: VisitModeCode | null;
  dimensions: Partial<Record<ReviewDimensionCode, number>>;
  spentPerPersonCents: number | null;
  body: string | null;
}

// ------------------------------------------------------------ busca (RF10, contrato combinado com o backend)

/** Card da busca: {@code price} e {@code rating} têm o formato de PriceView e RatingView. */
export interface EstablishmentCard {
  id: number;
  name: string;
  primaryType: Coded;
  group: Coded;
  chip: ChipView | null;
  primaryCuisine: Coded | null;
  primaryFormat: Coded | null;
  price: PriceView;
  rating: RatingView;
  location: Pick<LocationView, 'uf' | 'city' | 'neighborhood' | 'approximate'>;
  distanceKm: number | null;
  adultsOnly: boolean;
  status: EstablishmentStatus;
}

export interface SearchPage {
  items: EstablishmentCard[];
  page: number;
  size: number;
  total: number;
  radiusKm: number | null;
}

export type SearchSort = 'RELEVANCE' | 'DISTANCE' | 'RATING';

/** Parâmetros de GET /api/v1/establishments; os de lista repetem o nome na URL. */
export interface SearchParams {
  q?: string;
  /** Chip do card (mesma derivação do CuisineChip.java); repetível = um deles. */
  chip?: CuisineChipCode[];
  cuisine?: string[];
  format?: string[];
  type?: string[];
  group?: string[];
  price?: PriceLevelCode[];
  minRating?: number;
  dietary?: string[];
  lat?: number;
  lng?: number;
  radiusKm?: number;
  municipality?: string;
  sort?: SearchSort;
  page?: number;
  size?: number;
}

// ------------------------------------------------------------ taxonomia (application/establishment/TaxonomyCatalog.java)

export interface CuisineItem {
  code: string;
  label: string;
  macro: string;
  parent: string | null;
  ibgeRegion: string | null;
}

export interface FormatItem {
  code: string;
  label: string;
  family: string;
  suggestedCuisine: string | null;
  alcoholic: boolean;
}

export interface TypeItem {
  code: string;
  label: string;
  group: string;
  adultsOnly: boolean;
  typicalCnae: string[];
}

export interface PriceItem {
  code: PriceLevelCode;
  glyph: string | null;
  label: string;
  rangeLabel: string | null;
}

export interface DimensionItem {
  code: ReviewDimensionCode;
  label: string;
  visitModes: VisitModeCode[];
}

export interface StarItem {
  code: string;
  value: number;
  label: string;
}

/** TaxonomyCatalog.Catalog (GET /api/v1/taxonomy). */
export interface TaxonomyCatalog {
  cuisineMacros: Coded[];
  cuisines: CuisineItem[];
  foodFamilies: Coded[];
  formats: FormatItem[];
  approaches: Coded[];
  dietaryStyles: Coded[];
  dietaryLevels: DietaryLevel[];
  establishmentGroups: Coded[];
  establishmentTypes: TypeItem[];
  priceLevels: PriceItem[];
  stars: StarItem[];
  visitModes: Coded[];
  reviewDimensions: DimensionItem[];
  cuisineChips: ChipView[];
}

// ------------------------------------------------------------ erros (api/ApiExceptionHandler.java)

/** Problem Details (RFC 9457) com {@code errors[]} no 422. */
export interface Problem {
  type?: string;
  title?: string;
  status: number;
  detail?: string;
  instance?: string;
  errors?: string[];
}
