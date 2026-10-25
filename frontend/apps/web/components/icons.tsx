// Ícones Lucide (0.460) usados pelo web, sempre decorativos (aria-hidden): o sentido vem do texto ao lado.
// "Um ícone, um significado" (docs/DESIGN_SYSTEM.md, seção 1):
//   tipos de conta → user-round, camera, chef-hat, store, shopping-basket, users, landmark
//   selo VERIFIED → badge-check · nota → star / star-half · chips de culinária → cooking-pot, wine, soup, beef,
//   bean, pizza, coffee, vegan · sem glúten → wheat-off · sem lactose → milk-off
//   erro de formulário → circle-alert (triangle-alert e shield-alert são do alerta sanitário)
// Os demais (search, map-pin, locate-fixed, sun, moon, log-in, log-out, circle-user, circle-check, info, pencil,
// trash-2, chevron-left, chevron-right, mail, x) têm um sentido só cada um na interface.
// Configurações: settings (Configurações), user-pen (editar perfil), palette (exibição), shield (privacidade),
//   shield-check (2FA ativada), key-round (senha e segurança), lock (bloqueado até consentir), clock (prazo),
//   copy (copiar), download (baixar), image-up (enviar foto; camera é da conta Criador), monitor e smartphone
//   (aparelho da sessão).
// Posts (RF9 · RF12 · RF14 · RF15 · RF16): tipos de post → clapperboard (foto de prato: camera é da conta Criador),
//   book-open, message-circle-question, hand-platter, badge-percent, calendar-days, triangle-alert (alerta) e
//   shield-alert (fonte oficial); heart (curtir), message-circle (comentar), share-2 (compartilhar), bookmark e
//   bookmark-check (salvar), send (enviar comentário), archive e archive-restore (arquivar e restaurar),
//   newspaper (Feed), square-plus (Publicar), trending-up (em alta; flame fica reservado), hourglass (encerrado),
//   calendar-clock (vigência), eye-off (perfil privado), external-link (abre outro site), link (link do perfil).
import {
  Archive,
  ArchiveRestore,
  BadgeCheck,
  BadgePercent,
  BookOpen,
  Bookmark,
  BookmarkCheck,
  CalendarClock,
  CalendarDays,
  Clapperboard,
  ExternalLink,
  EyeOff,
  HandPlatter,
  Heart,
  Hourglass,
  Link as LinkIcon,
  MessageCircle,
  MessageCircleQuestion,
  Newspaper,
  Send,
  Share2,
  ShieldAlert,
  SquarePlus,
  TrendingUp,
  TriangleAlert,
  Bean,
  Beef,
  Camera,
  ChefHat,
  ChevronLeft,
  ChevronRight,
  CircleAlert,
  CircleCheck,
  CircleUser,
  Coffee,
  CookingPot,
  Clock,
  Copy,
  Download,
  ImageUp,
  Info,
  KeyRound,
  Landmark,
  Lock,
  Monitor,
  Palette,
  Settings,
  Shield,
  ShieldCheck,
  Smartphone,
  UserPen,
  LocateFixed,
  LogIn,
  LogOut,
  Mail,
  MapPin,
  MilkOff,
  Moon,
  Pencil,
  Pizza,
  Search,
  ShoppingBasket,
  Soup,
  Star,
  StarHalf,
  Store,
  Sun,
  Trash2,
  UserRound,
  Users,
  Vegan,
  WheatOff,
  Wine,
  X,
  type LucideIcon,
  type LucideProps,
} from 'lucide-react';

function decorative(Icon: LucideIcon) {
  function DecorativeIcon(props: LucideProps) {
    return <Icon aria-hidden="true" focusable="false" {...props} />;
  }
  DecorativeIcon.displayName = Icon.displayName;
  return DecorativeIcon;
}

export const IconVerified = decorative(BadgeCheck);
export const IconStar = decorative(Star);
export const IconStarHalf = decorative(StarHalf);
export const IconFormError = decorative(CircleAlert);
export const IconSuccess = decorative(CircleCheck);
export const IconInfo = decorative(Info);
export const IconSearch = decorative(Search);
export const IconPlace = decorative(MapPin);
export const IconNearMe = decorative(LocateFixed);
export const IconThemeLight = decorative(Sun);
export const IconThemeDark = decorative(Moon);
export const IconLogin = decorative(LogIn);
export const IconLogout = decorative(LogOut);
export const IconAccount = decorative(CircleUser);
export const IconEdit = decorative(Pencil);
export const IconDelete = decorative(Trash2);
export const IconPrev = decorative(ChevronLeft);
export const IconNext = decorative(ChevronRight);
export const IconEmail = decorative(Mail);
export const IconClose = decorative(X);
export const IconGlutenFree = decorative(WheatOff);
export const IconLactoseFree = decorative(MilkOff);
export const IconSettings = decorative(Settings);
export const IconProfileEdit = decorative(UserPen);
export const IconDisplay = decorative(Palette);
export const IconPrivacy = decorative(Shield);
export const IconMfaOn = decorative(ShieldCheck);
export const IconSecurity = decorative(KeyRound);
export const IconLocked = decorative(Lock);
export const IconDeadline = decorative(Clock);
export const IconCopy = decorative(Copy);
export const IconDownload = decorative(Download);
export const IconPhotoUpload = decorative(ImageUp);
export const IconDesktop = decorative(Monitor);
export const IconPhone = decorative(Smartphone);

export const IconLike = decorative(Heart);
export const IconComment = decorative(MessageCircle);
export const IconShare = decorative(Share2);
export const IconSave = decorative(Bookmark);
export const IconSaved = decorative(BookmarkCheck);
export const IconSend = decorative(Send);
export const IconArchive = decorative(Archive);
export const IconRestore = decorative(ArchiveRestore);
export const IconFeed = decorative(Newspaper);
export const IconPublish = decorative(SquarePlus);
export const IconTrending = decorative(TrendingUp);
export const IconExpired = decorative(Hourglass);
export const IconPeriod = decorative(CalendarClock);
export const IconPrivate = decorative(EyeOff);
export const IconExternal = decorative(ExternalLink);
export const IconLink = decorative(LinkIcon);
export const IconAlert = decorative(TriangleAlert);
export const IconOfficialSource = decorative(ShieldAlert);

/**
 * Ícone de cada tipo de post, pelo código. A API manda {@code camera} na foto de prato, mas camera é da conta
 * Criador (DESIGN_SYSTEM.md, seção 1): o post de foto usa clapperboard.
 */
export const POST_KIND_ICONS: Record<string, ReturnType<typeof decorative>> = {
  FOTO_PRATO: decorative(Clapperboard),
  RECEITA: decorative(BookOpen),
  PERGUNTA: decorative(MessageCircleQuestion),
  PRATO_DO_DIA: decorative(HandPlatter),
  PROMOCAO: decorative(BadgePercent),
  EVENTO: decorative(CalendarDays),
  ALERTA_SANITARIO: decorative(TriangleAlert),
};

/** Pictogramas dos 8 chips de culinária, pelo nome que a API manda em ChipView.lucideIcon. */
export const CHIP_ICONS: Record<string, ReturnType<typeof decorative>> = {
  'cooking-pot': decorative(CookingPot),
  wine: decorative(Wine),
  soup: decorative(Soup),
  beef: decorative(Beef),
  bean: decorative(Bean),
  pizza: decorative(Pizza),
  coffee: decorative(Coffee),
  vegan: decorative(Vegan),
};

/** Ícone reservado de cada tipo de conta. */
export const ACCOUNT_ICONS: Record<string, ReturnType<typeof decorative>> = {
  'user-round': decorative(UserRound),
  camera: decorative(Camera),
  'chef-hat': decorative(ChefHat),
  store: decorative(Store),
  'shopping-basket': decorative(ShoppingBasket),
  users: decorative(Users),
  landmark: decorative(Landmark),
};
