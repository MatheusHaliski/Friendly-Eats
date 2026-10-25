import type { ProfileCardView } from '@friendly-eats/api';
import Link from 'next/link';
import { AccountBadge, VerifiedSeal } from '@/components/AccountBadge';
import { IconPlace } from '@/components/icons';
import { Avatar } from '@/components/post/PostBits';

/** Card do feed de perfis (RF15): foto, nome, @, bolinha, selo, bio e cidade de atuação. */
export function ProfileCard({ profile }: { profile: ProfileCardView }) {
  const headingId = `perfil-${profile.id}`;
  return (
    <article aria-labelledby={headingId} className="fe-card relative flex h-full gap-3 p-4 hover:bg-sunken">
      <Avatar name={profile.name} url={profile.avatarUrl} size={48} />
      <div className="grid min-w-0 content-start gap-1">
        <h3 id={headingId} className="flex flex-wrap items-center gap-1.5 font-bold">
          <Link href={`/u/${profile.id}`} className="underline decoration-1 after:absolute after:inset-0 after:content-[''] hover:decoration-2">
            {profile.name}
          </Link>
          {profile.verified && <VerifiedSeal />}
        </h3>
        <p className="text-sm text-muted">@{profile.handle}</p>
        <p className="text-sm">
          <AccountBadge code={profile.category.code} label={profile.category.label} />
        </p>
        {profile.bio && <p className="text-sm">{profile.bio}</p>}
        {profile.city && (
          <p className="flex items-center gap-1.5 text-sm text-muted">
            <IconPlace size={14} />
            {[profile.city, profile.uf].filter(Boolean).join(' – ')}
          </p>
        )}
      </div>
    </article>
  );
}
