import type { Metadata } from 'next';
import { SupervisionPanel } from '@/components/settings/SupervisionPanel';

export const metadata: Metadata = { title: 'Painel de supervisão · Configurações' };

export default async function Page({ params }: { params: Promise<{ id: string }> }) {
  const { id } = await params;
  return <SupervisionPanel minorId={id} />;
}
