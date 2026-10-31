import type { Metadata } from 'next';
import { MyModerationView } from '@/components/moderation/MyModerationView';

export const metadata: Metadata = { title: 'Denúncias e decisões · Configurações' };

export default function Page() {
  return <MyModerationView />;
}
