// Ícones Lucide (0.460) usados pelo web, sempre decorativos (aria-hidden): o sentido vem do texto ao lado.
// "Um ícone, um significado" (docs/DESIGN_SYSTEM.md, seção 1):
//   tipos de conta → user-round, camera, chef-hat, store, shopping-basket, users, landmark
//   selo VERIFIED → badge-check · nota → star / star-half · chips de culinária → cooking-pot, wine, soup, beef,
//   bean, pizza, coffee, vegan · sem glúten → wheat-off · sem lactose → milk-off
//   erro de formulário → circle-alert (triangle-alert e shield-alert são do alerta sanitário)
// Os demais (search, map-pin, locate-fixed, sun, moon, log-in, log-out, circle-user, circle-check, info, pencil,
// trash-2, chevron-left, chevron-right, mail, x) têm um sentido só cada um na interface.
import {
  BadgeCheck,
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
  Info,
  Landmark,
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
