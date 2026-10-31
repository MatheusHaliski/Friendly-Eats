// Ícones Lucide da página do lugar (RF11), sempre decorativos (aria-hidden): o texto ao lado diz o estado.
// Sem repetir os reservados do DESIGN_SYSTEM.md (seção 1): store, users, coffee, sun, moon, clock, smartphone,
// message-circle e book-open já têm outro sentido. Aberto → door-open, fechado → door-closed, pausa → circle-pause,
// cobranças → receipt, reserva → calendar-check, delivery → bike, WhatsApp → message-square, mapa → map,
// como chegar → navigation, editar a página → pencil (IconEdit).
import {
  Accessibility,
  Armchair,
  Banknote,
  Baby,
  Bike,
  BookA,
  CalendarCheck,
  Car,
  CarFront,
  CircleHelp,
  CirclePause,
  ConciergeBell,
  CreditCard,
  Croissant,
  Dog,
  DoorClosed,
  DoorOpen,
  Hand,
  HeartHandshake,
  Laptop,
  Map as MapIcon,
  MessageSquare,
  MonitorSmartphone,
  MoonStar,
  Music,
  Navigation,
  Nfc,
  Package,
  PawPrint,
  QrCode,
  Receipt,
  Salad,
  ShoppingBag,
  ShoppingCart,
  SquareParking,
  Sunset,
  TabletSmartphone,
  Ticket,
  Toilet,
  Trees,
  Tv,
  UserCheck,
  UsersRound,
  Utensils,
  VolumeX,
  WalletCards,
  Wifi,
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

export const IconOpen = decorative(DoorOpen);
export const IconClosedNow = decorative(DoorClosed);
export const IconPaused = decorative(CirclePause);
export const IconUnknownHours = decorative(CircleHelp);
export const IconCharges = decorative(Receipt);
export const IconReserve = decorative(CalendarCheck);
export const IconDelivery = decorative(Bike);
export const IconWhatsapp = decorative(MessageSquare);
export const IconMap = decorative(MapIcon);
export const IconDirections = decorative(Navigation);

/** Pelo nome que a API manda em FacetView.lucideIcon (Facet.java). */
export const FACET_ICONS: Record<string, ReturnType<typeof decorative>> = {
  utensils: decorative(Utensils),
  'concierge-bell': decorative(ConciergeBell),
  'shopping-bag': decorative(ShoppingBag),
  car: decorative(Car),
  'car-front': decorative(CarFront),
  bike: decorative(Bike),
  'tablet-smartphone': decorative(TabletSmartphone),
  'calendar-check': decorative(CalendarCheck),
  package: decorative(Package),
  croissant: decorative(Croissant),
  salad: decorative(Salad),
  sunset: decorative(Sunset),
  'moon-star': decorative(MoonStar),
  baby: decorative(Baby),
  'users-round': decorative(UsersRound),
  laptop: decorative(Laptop),
  wifi: decorative(Wifi),
  'paw-print': decorative(PawPrint),
  trees: decorative(Trees),
  music: decorative(Music),
  tv: decorative(Tv),
  'heart-handshake': decorative(HeartHandshake),
  accessibility: decorative(Accessibility),
  armchair: decorative(Armchair),
  toilet: decorative(Toilet),
  'square-parking': decorative(SquareParking),
  'book-a': decorative(BookA),
  'monitor-smartphone': decorative(MonitorSmartphone),
  hand: decorative(Hand),
  'volume-x': decorative(VolumeX),
  dog: decorative(Dog),
  'user-check': decorative(UserCheck),
  'qr-code': decorative(QrCode),
  'credit-card': decorative(CreditCard),
  'wallet-cards': decorative(WalletCards),
  nfc: decorative(Nfc),
  banknote: decorative(Banknote),
  ticket: decorative(Ticket),
  'shopping-cart': decorative(ShoppingCart),
};
