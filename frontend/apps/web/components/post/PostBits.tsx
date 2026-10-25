// Peças do card e da página do post (RF14): autor com a bolinha, selo do tipo, chips de classificação, preço,
// alergênicos e o bloco do alerta sanitário. Nenhuma informação só pela cor: sempre ícone ou glifo + texto.
import type { AlertView, AllergenView, AuthorView, Coded, KindView, PlaceRefView, PostPriceView } from '@friendly-eats/api';
import Link from 'next/link';
import { AccountDot, VerifiedSeal } from '@/components/AccountBadge';
import { IconAlert, IconExpired, IconExternal, IconOfficialSource, IconPeriod, IconPlace, POST_KIND_ICONS } from '@/components/icons';
import { initials } from '@/lib/photo';
import { allergenGroups, alertSource, formatDateTime, formatPeriod, formatRelative, placeHref, profileHref } from '@/lib/post-format';

export function Avatar({ name, url, size = 40 }: { name: string; url: string | null; size?: number }) {
  const style = { width: size, height: size, fontSize: size * 0.38 };
  if (url) {
    // eslint-disable-next-line @next/next/no-img-element -- URL pública do bucket, já recortada
    return <img src={url} alt="" width={size} height={size} style={style} className="flex-none rounded-full border border-line object-cover" />;
  }
  return (
    <span aria-hidden="true" style={style} className="grid flex-none place-items-center rounded-full border-2 border-line-strong bg-sunken font-bold">
      {initials(name)}
    </span>
  );
}

/** Bolinha + glifo + rótulo do tipo de conta (numa pílula com o fundo do tema dentro do card emoldurado). */
export function AuthorBadge({ author }: { author: Pick<AuthorView, 'category'> }) {
  if (!author.category) return null;
  return (
    <span className="fe-badge-pill" data-testid="tipo-conta">
      <AccountDot code={author.category.code} />
      <span>{author.category.label}</span>
    </span>
  );
}

/** Foto, nome (leva ao perfil público, RF15), selo, @, bolinha e quando foi publicado. */
export function AuthorLine({ author, publishedAt, absolute = false }: { author: AuthorView; publishedAt: string; absolute?: boolean }) {
  const href = profileHref(author);
  return (
    <div className="flex min-w-0 items-start gap-3">
      <Avatar name={author.name} url={author.avatarUrl} />
      <div className="grid min-w-0 gap-0.5">
        <p className="flex flex-wrap items-center gap-x-1.5 gap-y-0.5">
          {href ? (
            <Link href={href} className="inline-flex min-h-6 items-center font-bold underline decoration-1 hover:decoration-2">
              {author.name}
            </Link>
          ) : (
            <span className="font-bold">{author.name}</span>
          )}
          {author.verified && <VerifiedSeal />}
          {author.handle && <span className="text-sm text-muted">@{author.handle}</span>}
        </p>
        <p className="flex flex-wrap items-center gap-x-2 gap-y-1 text-sm text-muted">
          <AuthorBadge author={author} />
          <time dateTime={publishedAt} title={formatDateTime(publishedAt)}>
            {absolute ? formatDateTime(publishedAt) : formatRelative(publishedAt)}
          </time>
        </p>
      </div>
    </div>
  );
}

/** Selo do tipo de post: ícone Lucide + rótulo (a borda do card muda de forma, nunca de cor). */
export function KindBadge({ kind }: { kind: KindView }) {
  const Icon = POST_KIND_ICONS[kind.code];
  return (
    <span className="fe-tag" data-testid="tipo-post" data-kind={kind.code}>
      {Icon && <Icon size={16} />}
      <span className="sr-only">Tipo de post: </span>
      {kind.label}
    </span>
  );
}

interface TagsProps {
  ageRating: string;
  ageRatingLabel: string;
  advertisingLabel: string | null;
  expired: boolean;
  status: string;
}

/** Chips de classificação: 18+, Publicidade, Encerrado e Arquivado, sempre em texto. */
export function ClassificationTags({ ageRating, ageRatingLabel, advertisingLabel, expired, status }: TagsProps) {
  return (
    <>
      {ageRating === 'AGE_18' && (
        <span className="fe-tag" data-testid="chip-18">
          {ageRatingLabel}
          <span className="sr-only"> (só para maiores de 18 anos)</span>
        </span>
      )}
      {advertisingLabel && (
        <span className="fe-tag" data-testid="chip-publicidade">
          {advertisingLabel}
        </span>
      )}
      {expired && (
        <span className="fe-tag" data-testid="chip-encerrado">
          <IconExpired size={16} />
          Encerrado
        </span>
      )}
      {status === 'ARCHIVED' && (
        <span className="fe-tag" data-testid="chip-arquivado">
          Arquivado
        </span>
      )}
    </>
  );
}

export function TaxonomyLine({ cuisine, foodFormat, dietaryStyles }: { cuisine: Coded | null; foodFormat: Coded | null; dietaryStyles: Coded[] }) {
  const parts = [cuisine?.label, foodFormat?.label, ...dietaryStyles.map((d) => d.label)].filter(Boolean);
  if (!parts.length) return null;
  return <p className="text-sm text-muted">{parts.join(' · ')}</p>;
}

export function PriceLine({ price }: { price: PostPriceView | null }) {
  if (!price) return null;
  return (
    <p className="font-bold" data-testid="preco-post">
      <span className="sr-only">Preço: </span>
      {price.label}
    </p>
  );
}

export function PeriodLine({ startsAt, endsAt, label }: { startsAt: string | null; endsAt: string | null; label: string }) {
  const text = formatPeriod(startsAt, endsAt);
  if (!text) return null;
  return (
    <p className="flex items-start gap-1.5 text-sm">
      <IconPeriod size={16} className="mt-0.5 flex-none" />
      <span>
        <span className="font-semibold">{label}:</span> {text}
      </span>
    </p>
  );
}

export function PlaceLine({ place, city }: { place: PlaceRefView | null; city?: string | null }) {
  if (!place && !city) return null;
  return (
    <p className="flex items-start gap-1.5 text-sm">
      <IconPlace size={16} className="mt-0.5 flex-none" />
      {place ? (
        <span>
          <Link href={placeHref(place.id)} className="font-semibold underline">
            {place.name}
          </Link>
          {place.city ? <span className="text-muted"> · {[place.city, place.uf].filter(Boolean).join(' – ')}</span> : null}
        </span>
      ) : (
        <span>{city}</span>
      )}
    </p>
  );
}

/** Alergênicos com o nível por extenso ("Contém: Ovos · Pode conter: Leite") e as advertências. */
export function AllergenList({ allergens, warnings, notice }: { allergens: AllergenView[]; warnings: Coded[]; notice?: string | null }) {
  if (!allergens.length && !warnings.length) return null;
  return (
    <div className="grid gap-1 text-sm" data-testid="alergenicos">
      {allergenGroups(allergens).map((g) => (
        <p key={g.level}>
          <span className="font-bold">{g.label}:</span> {g.items.join(', ')}
        </p>
      ))}
      {warnings.length > 0 && <p className="font-bold">{warnings.map((w) => w.label).join(' · ')}</p>}
      {notice && <p className="text-muted">{notice}</p>}
    </div>
  );
}

/** Faixa hazard do alerta sanitário (DESIGN_SYSTEM.md, seção 7): sempre com ícone e texto, nunca só a cor. */
export function HazardBand({ author, host }: { author: AuthorView; host: string | null }) {
  return (
    <div className="fe-hazard" data-testid="faixa-alerta">
      <span className="fe-hazard-label">
        <span className="inline-flex items-center gap-1.5 whitespace-nowrap">
          <IconAlert size={18} />
          Alerta sanitário
        </span>
        <span aria-hidden="true">·</span>
        <span className="inline-flex items-center gap-1.5">
          <IconOfficialSource size={16} className="flex-none" />
          {alertSource(author, host)}
        </span>
      </span>
    </div>
  );
}

export function AlertDetails({ alert, compact = false }: { alert: AlertView; compact?: boolean }) {
  return (
    <dl className={`grid gap-2 ${compact ? 'text-sm' : ''}`} data-testid="dados-alerta">
      <div className="grid gap-0.5 sm:grid-cols-[8rem_1fr]">
        <dt className="font-bold">Produto</dt>
        <dd>{alert.product}</dd>
      </div>
      <div className="grid gap-0.5 sm:grid-cols-[8rem_1fr]">
        <dt className="font-bold">Marca</dt>
        <dd>{alert.brand}</dd>
      </div>
      <div className="grid gap-0.5 sm:grid-cols-[8rem_1fr]">
        <dt className="font-bold">Lotes</dt>
        <dd>{alert.lots.join(', ')}</dd>
      </div>
      {!compact && (
        <div className="grid gap-0.5 sm:grid-cols-[8rem_1fr]">
          <dt className="font-bold">Motivo</dt>
          <dd>{alert.reason}</dd>
        </div>
      )}
      <div className="grid gap-0.5 sm:grid-cols-[8rem_1fr]">
        <dt className="font-bold">Fonte oficial</dt>
        <dd>
          <a href={alert.sourceUrl} target="_blank" rel="noopener noreferrer" className="inline-flex items-center gap-1 font-semibold text-alert underline">
            {alert.sourceHost ?? alert.sourceUrl}
            <IconExternal size={14} />
            <span className="sr-only">(abre em outra aba)</span>
          </a>
        </dd>
      </div>
    </dl>
  );
}
