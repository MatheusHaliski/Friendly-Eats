import type { Metadata } from 'next';
import { ModerationView } from '@/components/moderation/ModerationView';

export const metadata: Metadata = { title: 'Moderação' };

export default function Page() {
  return <ModerationView />;
}
