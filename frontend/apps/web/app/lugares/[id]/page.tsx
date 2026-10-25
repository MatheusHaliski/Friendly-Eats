import type { Metadata } from 'next';
import { notFound } from 'next/navigation';
import { PlaceView } from '@/components/place/PlaceView';

export const metadata: Metadata = {
  title: 'Lugar',
};

export default async function LugarPage({ params }: { params: Promise<{ id: string }> }) {
  const { id } = await params;
  if (!/^\d{1,18}$/.test(id)) notFound();
  return <PlaceView id={id} />;
}
