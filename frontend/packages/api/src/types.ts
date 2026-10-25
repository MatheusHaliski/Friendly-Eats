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

// ------------------------------------------------------------ Configurações (RF2 · RF3 · RF4 · RF5)

/** AccountViews.ProfileView (GET/PATCH /api/v1/me/profile): o perfil completo, só para o titular. */
export interface ProfileView {
  id: number;
  name: string;
  handle: string;
  email: string;
  recoveryEmail: string | null;
  cellphoneNumber: string | null;
  /** AAAA-MM-DD; só muda pelo suporte. */
  dateOfBirth: string | null;
  bio: string | null;
  pronouns: string[];
  links: string[];
  countryOfResidence: string | null;
  stateOfResidence: string | null;
  cityOfResidence: string | null;
  category: CategoryView;
  categoryVerification: CategoryVerification;
  typeDetails: Record<string, unknown>;
  avatarObjectKey: string | null;
  avatarUrl: string | null;
  /** Até 5 códigos de culinária da taxonomia. */
  favoriteCuisines: string[];
  /** Estilos alimentares (dado sensível, LGPD Art. 11): só com o consentimento SENSITIVE_PROFILE_DATA. */
  dietaryStyles: string[];
}

/** ProfileService.ProfilePatch: campo ausente (ou null) fica como está. */
export interface ProfilePatch {
  name?: string;
  handle?: string;
  bio?: string;
  pronouns?: string[];
  links?: string[];
  countryOfResidence?: string;
  stateOfResidence?: string;
  cityOfResidence?: string;
  recoveryEmail?: string;
  cellphoneNumber?: string;
  typeDetails?: Record<string, string>;
  favoriteCuisines?: string[];
  dietaryStyles?: string[];
}

/** UserPreference.Theme, AppBackground, HomeTab, CommentAudience e SensitiveContent. */
export type ThemePreference = 'SYSTEM' | 'DARK' | 'LIGHT';
export type AppBackground = 'PLAIN' | 'PATTERN';
export type HomeTab = 'FEED' | 'EXPLORE' | 'NEARBY' | 'SAVED';
export type CommentAudience = 'EVERYONE' | 'FOLLOWERS' | 'NOBODY';
export type SensitiveContent = 'LIMIT' | 'ALLOW';
/** Chaves de PreferencesService.NOTIFICATION_KEYS. */
export type NotificationKey = 'likes' | 'comments' | 'shares' | 'foodAlerts';

/** AccountViews.PreferencesView (GET/PATCH /api/v1/me/preferences). */
export interface PreferencesView {
  theme: ThemePreference;
  appBackground: AppBackground;
  language: string;
  /** 85 a 150 (%). */
  fontScale: number;
  reduceMotion: boolean;
  profilePrivate: boolean;
  whoCanComment: CommentAudience;
  showBirthYear: boolean;
  homeTab: HomeTab;
  sensitiveContent: SensitiveContent;
  /** 10 a 600; null = sem lembrete. */
  usageReminderMinutes: number | null;
  notifications: Partial<Record<NotificationKey, boolean>>;
  loginAlerts: boolean;
  /** Menor de 16 (ECA Digital): afrouxar uma proteção dá 403; só o responsável altera. */
  minorProtectionsLocked: boolean;
}

/** PreferencesService.PreferencesPatch: campo ausente fica como está. */
export interface PreferencesPatch {
  theme?: ThemePreference;
  appBackground?: AppBackground;
  language?: string;
  fontScale?: number;
  reduceMotion?: boolean;
  profilePrivate?: boolean;
  whoCanComment?: CommentAudience;
  showBirthYear?: boolean;
  homeTab?: HomeTab;
  sensitiveContent?: SensitiveContent;
  usageReminderMinutes?: number;
  clearUsageReminder?: boolean;
  notifications?: Partial<Record<NotificationKey, boolean>>;
  loginAlerts?: boolean;
}

/** Finalidades opcionais do tratamento (RF3), todas desligadas por padrão. */
export type ConsentPurpose =
  | 'MARKETING'
  | 'LOCATION_TRACKING'
  | 'PERSONALIZED_FEED'
  | 'SENSITIVE_PROFILE_DATA'
  | 'THIRD_PARTY_SHARING';

/** Um consentimento (GET /api/v1/me/consents, PUT /api/v1/me/consents/{purpose}). */
export interface ConsentView {
  purpose: ConsentPurpose;
  label: string;
  description: string;
  legalBasis: string;
  sensitive: boolean;
  granted: boolean;
  /** Instant em ISO-8601; null se nunca mudou. */
  updatedAt: string | null;
  /** Menor de 16: o PUT dá 403. */
  lockedForMinor: boolean;
}

export interface ConsentList {
  items: ConsentView[];
}

/** MeController.SudoRequest (modo sudo, StepUp): senha atual e, com 2FA ativo, o código. */
export interface SudoRequest {
  password: string;
  code: string | null;
}

/** MeController.PasswordChange. */
export interface PasswordChange {
  currentPassword: string;
  code: string | null;
  newPassword: string;
}

/** SessionService.SessionView: uma sessão ativa, com um identificador que não é o id real. */
export interface SessionView {
  id: string;
  /** "Chrome no Windows"; pode faltar. */
  device: string | null;
  /** WEB, ANDROID, IOS… */
  channel: string | null;
  createdAt: string;
  lastAccessAt: string;
  current: boolean;
}

/** AccountViews.MfaStatusView. */
export interface MfaStatusView {
  enabled: boolean;
  enabledAt: string | null;
  recoveryCodesLeft: number;
}

/** AccountViews.MfaSetupView: some ao confirmar ou depois de expiresAt. */
export interface MfaSetupView {
  secret: string;
  otpauthUri: string;
  issuer: string;
  account: string;
  expiresAt: string;
}

/** AccountViews.RecoveryCodesView: mostrados uma única vez. */
export interface RecoveryCodesView {
  codes: string[];
  generatedAt: string;
}

/** MeController.DeletionRequest (POST /api/v1/me/deletion). */
export interface DeletionRequest {
  password: string;
  code: string | null;
  confirm: boolean;
}

/** AccountViews.DeletionView (202). */
export interface DeletionView {
  accountStatus: AccountStatus;
  deletionScheduledFor: string;
  graceDays: number;
}

/** MediaType. */
export type MediaKind = 'TEXT' | 'IMAGE' | 'VIDEO' | 'AUDIO';

/** MediaUploadController.UploadRequest. */
export interface UploadRequest {
  kind: MediaKind;
  contentType: string;
  sizeBytes: number;
}

/** MediaUploads.UploadTicket: URL pré-assinada para o PUT direto ao bucket. */
export interface UploadTicket {
  objectKey: string;
  uploadUrl: string;
  method: string;
  headers: Record<string, string>;
  expiresAt: string;
  maxBytes: number;
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

// ------------------------------------------------------------ posts (application/post/PostViews.java, RF14 · RF9 · RF12)

/** PostKind: os 7 tipos de post de comida. */
export type PostKindCode =
  | 'FOTO_PRATO'
  | 'RECEITA'
  | 'PERGUNTA'
  | 'PRATO_DO_DIA'
  | 'PROMOCAO'
  | 'EVENTO'
  | 'ALERTA_SANITARIO';

/** PostKind.getBorder: a forma da borda do card (nunca a cor; só o alerta tem a faixa hazard). */
export type PostBorder = 'SEM_MOLDURA' | 'TRACEJADA' | 'BALAO' | 'DUPLA' | 'ETIQUETA' | 'DATA' | 'HAZARD';

/** FeedType: PROFILE fica em GET /api/v1/users (RF15). */
export type FeedCode = 'PROFILE' | 'POST' | 'ALERT';

export type PostStatus = 'DRAFT' | 'PUBLISHED' | 'ARCHIVED' | 'REMOVED';
export type AgeRatingCode = 'LIVRE' | 'AGE_18';
export type AllergenLevelCode = 'CONTEM' | 'PODE_CONTER';
export type ShareChannelCode = 'COPY_LINK' | 'NATIVE' | 'WHATSAPP' | 'TELEGRAM' | 'X' | 'EMAIL';

/**
 * profile/AccountBadge: a bolinha do tipo de conta. {@code color} é a do tema escuro e {@code lightColor} a do claro;
 * fora do card emoldurado a tela usa os tokens, que têm as mesmas cores.
 */
export interface AccountBadgeView {
  code: AccountTypeCode;
  label: string;
  color: string;
  lightColor: string;
  glyph: string;
  glyphName: string;
  lucideIcon: string;
}

/** PostViews.AuthorView: {@code profilePath} (caminho da API) nulo = conta excluída ou fora do ar, sem link. */
export interface AuthorView {
  id: number | null;
  name: string;
  handle: string | null;
  /** Nulo na conta excluída ("Conta excluída", sem bolinha). */
  category: AccountBadgeView | null;
  verified: boolean;
  avatarUrl: string | null;
  profilePath: string | null;
  available: boolean;
}

export interface KindView {
  code: PostKindCode;
  label: string;
  lucideIcon: string;
  border: PostBorder;
}

/** PostViews.PlaceRefView: só vem quando a página do lugar está no ar para quem vê. */
export interface PlaceRefView {
  id: number;
  name: string;
  city: string | null;
  uf: string | null;
  adultsOnly: boolean;
  pagePath: string;
}

export interface AllergenView {
  code: string;
  label: string;
  level: AllergenLevelCode;
  levelLabel: string;
}

/** PostViews.PriceView ({@code label} pronto: "R$ 25,00", "De R$ 50,00 por R$ 35,00", "20% de desconto", "Gratuito"). */
export interface PostPriceView {
  priceCents: number | null;
  priceFromCents: number | null;
  priceToCents: number | null;
  discountPercent: number | null;
  free: boolean;
  label: string;
}

export interface RecipeView {
  ingredients: string[];
  steps: string[];
  servings: number | null;
  prepMinutes: number | null;
}

/** PostViews.AlertView; {@code sourceHost} vai no rótulo "Fonte: …". */
export interface AlertView {
  product: string;
  brand: string;
  lots: string[];
  reason: string;
  sourceUrl: string;
  sourceHost: string | null;
}

export interface CountersView {
  likes: number;
  comments: number;
  shares: number;
}

export interface ViewerStateView {
  liked: boolean;
  saved: boolean;
  isAuthor: boolean;
}

/** PostViews.InteractionsView (RF12): {@code commentNotice} diz por que quem vê não pode comentar. */
export interface InteractionsView {
  open: boolean;
  likes: boolean;
  comments: boolean;
  shares: boolean;
  saves: boolean;
  commentAudience: CommentAudience;
  viewerCanComment: boolean;
  commentNotice: string | null;
}

/** BackgroundService.ContainerView: {@code contrast} já formatado ("12,1:1"). */
export interface FrameContainerView {
  color: string;
  ink: string;
  contrast: string;
}

export type FrameArtKind = 'COLOR' | 'GRADIENT' | 'SEASONAL' | 'AURA' | 'LAYOUT' | 'NONE';

/** BackgroundService.BackgroundView: a moldura resolvida (RF16); {@code config} é a forma canônica gravada. */
export interface BackgroundView {
  config: FrameConfig;
  artKind: FrameArtKind;
  artId: string | null;
  palette: string[];
  css: string | null;
  container: FrameContainerView;
}

/** PostViews.PostDetailView (GET /api/v1/posts/{id}). */
export interface PostDetailView {
  id: string;
  kind: KindView;
  feed: FeedCode;
  mediaType: MediaKind;
  title: string;
  body: string | null;
  mediaUrl: string | null;
  altText: string | null;
  establishment: PlaceRefView | null;
  cuisine: Coded | null;
  foodFormat: Coded | null;
  dietaryStyles: Coded[];
  allergens: AllergenView[];
  warnings: Coded[];
  allergenNotice: string | null;
  ageRating: AgeRatingCode;
  ageRatingLabel: string;
  advertising: boolean;
  advertisingLabel: string | null;
  startsAt: string | null;
  endsAt: string | null;
  price: PostPriceView | null;
  recipe: RecipeView | null;
  alert: AlertView | null;
  city: string | null;
  expiresAt: string | null;
  expired: boolean;
  status: PostStatus;
  publishedAt: string;
  updatedAt: string;
  author: AuthorView;
  counts: CountersView;
  viewer: ViewerStateView;
  interactions: InteractionsView;
  shareUrl: string;
  background: BackgroundView | null;
}

/** PostViews.PostCardView: {@code excerpt} (até 160) no lugar do texto; sem receita, aviso, cidade e interações. */
export interface PostCardView {
  id: string;
  kind: KindView;
  feed: FeedCode;
  mediaType: MediaKind;
  title: string;
  excerpt: string | null;
  mediaUrl: string | null;
  altText: string | null;
  establishment: PlaceRefView | null;
  cuisine: Coded | null;
  foodFormat: Coded | null;
  dietaryStyles: Coded[];
  allergens: AllergenView[];
  warnings: Coded[];
  ageRating: AgeRatingCode;
  ageRatingLabel: string;
  advertising: boolean;
  advertisingLabel: string | null;
  startsAt: string | null;
  endsAt: string | null;
  price: PostPriceView | null;
  alert: AlertView | null;
  expiresAt: string | null;
  expired: boolean;
  status: PostStatus;
  publishedAt: string;
  author: AuthorView;
  counts: CountersView;
  viewer: ViewerStateView;
  /** Caminho da API (/api/v1/posts/{id}); a tela usa /posts/{id}. */
  detailPath: string;
  background: BackgroundView | null;
}

/** PostViews.FeedPageView: {@code nextCursor} nulo = fim. */
export interface FeedPageView {
  items: PostCardView[];
  nextCursor: string | null;
}

/** GET /api/v1/posts. */
export interface FeedParams {
  feed?: 'POST' | 'ALERT';
  kind?: PostKindCode;
  establishmentId?: number;
  cursor?: string;
  size?: number;
}

/** GET /api/v1/posts/trending: {@code category} = tipo de conta do autor. */
export interface TrendingParams {
  feed?: 'POST' | 'ALERT';
  category?: AccountTypeCode;
  size?: number;
}

// ------------------------------------------------------------ vocabulário do formulário (application/post/PostVocabulary.java, RF9)

/**
 * PostVocabulary.KindItem. {@code requiredFields} e {@code optionalFields} são nomes de campo do PostDraft; os
 * obrigatórios podem trazer alternativas ("priceCents | free: true") e qualificadores ("establishmentId (dono)").
 */
export interface PostKindItem {
  code: PostKindCode;
  label: string;
  lucideIcon: string;
  border: PostBorder;
  feed: 'POST' | 'ALERT';
  whoCanPublish: string;
  requiredFields: string[];
  optionalFields: string[];
  photoRequired: boolean;
  expires: string | null;
}

export interface AllergenItem {
  code: string;
  label: string;
  kind: 'ALERGENICO' | 'ADVERTENCIA';
  status: 'VIGENTE' | 'PROPOSTO';
}

/** PostVocabulary.Vocabulary (GET /api/v1/posts/vocabulary); {@code publishableKinds} vazio sem sessão. */
export interface PostVocabulary {
  kinds: PostKindItem[];
  publishableKinds: PostKindCode[];
  allergens: AllergenItem[];
  warnings: AllergenItem[];
  allergenLevels: Coded[];
  ageRatings: Coded[];
  shareChannels: Coded[];
  allergenNotice: string;
}

// ------------------------------------------------------------ publicar e editar (application/post/PostDrafts.java, RF9)

export interface RecipeDraft {
  ingredients: string[];
  steps: string[];
  servings: number | null;
  prepMinutes: number | null;
}

export interface AlertDraft {
  product: string;
  brand: string;
  lots: string[];
  reason: string;
  sourceUrl: string;
}

/** PostDrafts.PostDraft (POST /api/v1/posts). Datas em ISO-8601; preços em centavos. */
export interface PostDraft {
  kind: PostKindCode;
  title: string;
  body?: string | null;
  mediaObjectKey?: string | null;
  altText?: string | null;
  establishmentId?: number | null;
  cuisine?: string | null;
  foodFormat?: string | null;
  dietaryStyles?: string[] | null;
  allergens?: Record<string, AllergenLevelCode> | null;
  warnings?: string[] | null;
  ageRating?: AgeRatingCode | null;
  advertising?: boolean | null;
  startsAt?: string | null;
  endsAt?: string | null;
  priceCents?: number | null;
  priceFromCents?: number | null;
  priceToCents?: number | null;
  discountPercent?: number | null;
  free?: boolean | null;
  recipe?: RecipeDraft | null;
  alert?: AlertDraft | null;
  city?: string | null;
  /** Moldura do RF16; null = padrão. Alerta sanitário não aceita moldura. */
  background?: FrameConfig | null;
}

/** PATCH /api/v1/posts/{id}: chave ausente mantém, null limpa; o tipo não muda. */
export type PostPatch = Partial<Omit<PostDraft, 'kind'>>;

// ------------------------------------------------------------ interações (application/post/InteractionViews.java, RF12)

export interface CommentView {
  id: number;
  author: AuthorView;
  body: string;
  createdAt: string;
  canDelete: boolean;
}

/** {@code nextAfter} vai no parâmetro {@code after} da próxima página; nulo = fim. */
export interface CommentPageView {
  items: CommentView[];
  nextAfter: number | null;
  total: number;
}

export interface CommentCreatedView {
  comment: CommentView;
  comments: number;
}

export interface LikeStateView {
  liked: boolean;
  likes: number;
}

export interface SaveStateView {
  saved: boolean;
}

/** {@code counted}: primeira vez desta conta neste canal (só então o número sobe). */
export interface ShareResultView {
  shareUrl: string;
  channel: ShareChannelCode;
  shares: number;
  counted: boolean;
}

// ------------------------------------------------------------ perfis (application/profile/ProfileViews.java, RF15)

/** Perfil privado (para quem não é o titular): só nome, @, bolinha e selo; o resto vem null. */
export interface PublicProfileView {
  id: number;
  name: string;
  handle: string;
  category: AccountBadgeView;
  verified: boolean;
  privateProfile: boolean;
  avatarUrl: string | null;
  bio: string | null;
  pronouns: string[] | null;
  links: string[] | null;
  city: string | null;
  uf: string | null;
  memberSince: string | null;
  postCount: number | null;
  postsPath: string | null;
  shareUrl: string | null;
}

export interface ProfileCardView {
  id: number;
  name: string;
  handle: string;
  category: AccountBadgeView;
  verified: boolean;
  avatarUrl: string | null;
  bio: string | null;
  city: string | null;
  uf: string | null;
  profilePath: string;
}

export interface ProfilePageView {
  items: ProfileCardView[];
  nextCursor: string | null;
}

/** GET /api/v1/users: {@code q} = parte do nome ou do @ (até 80). */
export interface ProfileListParams {
  type?: AccountTypeCode;
  city?: string;
  q?: string;
  cursor?: string;
  size?: number;
}

// ------------------------------------------------------------ moldura (application/background/*, RF16)

/** Configuração da moldura enviada no {@code background} do post (BackgroundValidator, v = 1). */
export interface FrameConfig {
  v: 1;
  color?: string | null;
  gradientId?: string | null;
  seasonal?: { presetId?: string | null; auto?: boolean } | null;
  animationId?: string | null;
  aura?: { presetId: string; variantId?: FrameAuraVariant; format?: FrameAuraFormat } | null;
  materialId?: string | null;
  layoutId?: FrameLayoutId | null;
  skinId?: string | null;
  container?: { color?: string | null; ink?: string | null } | null;
}

export type FrameAuraVariant = 'ORIGINAL' | 'SUAVE';
export type FrameAuraFormat = 'IMAGEM_UNICA' | 'MOSAICO';
export type FrameLayoutId = 'CLASSICO' | 'POLAROID' | 'CARDAPIO';

/** BackgroundCatalog.Item: cor (id = #RRGGBB), gradiente, animação ou material. */
export interface FrameCatalogItem {
  id: string;
  name: string;
  css: string | null;
  palette: string[];
}

/** BackgroundCatalog.Aura: o "padrão" de mesa; {@code css} nulo = arte gerada no cliente (Feira). */
export interface FrameAura {
  id: string;
  name: string;
  mood: string;
  palette: string[];
  technique: string;
  css: string | null;
}

export interface FrameSeason {
  id: string;
  name: string;
  window: string;
  priority: number;
  colors: string[];
  /** false = só por escolha (Black Friday nunca entra no modo automático). */
  auto: boolean;
}

export interface FrameLayout {
  id: FrameLayoutId;
  name: string;
  /** true = o layout já traz arte própria (Cardápio). */
  ownArt: boolean;
}

export interface FrameSkin {
  id: string;
  name: string;
  containerColor: string;
}

export interface FrameRules {
  minContrast: number;
  maxConfigBytes: number;
  maxAnimationSeconds: number;
  feedsWithoutFrame: FeedCode[];
}

/** BackgroundCatalog.CatalogView (GET /api/v1/backgrounds/catalog, com ETag). */
export interface FrameCatalog {
  version: string;
  activeSeasonId: string | null;
  colors: FrameCatalogItem[];
  gradients: FrameCatalogItem[];
  seasons: FrameSeason[];
  animations: FrameCatalogItem[];
  auras: FrameAura[];
  materials: FrameCatalogItem[];
  layouts: FrameLayout[];
  skins: FrameSkin[];
  containerColors: FrameCatalogItem[];
  rules: FrameRules;
}

/** BackgroundService.Recommendation: padrão + material, ou a cartela do dia. */
export interface FrameRecommendation {
  auraId: string | null;
  materialId: string | null;
  seasonId: string | null;
  reason: string;
}
