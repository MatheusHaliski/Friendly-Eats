import type { Metadata } from 'next';
import { notFound } from 'next/navigation';
import { PlaceFormView } from '@/components/place/PlaceForm';

export const metadata: Metadata = { title: 'Editar lugar' };

export default async function EditarLugarPage({ params }: { params: Promise<{ id: string }> }) {
  const { id } = await params;
  if (!/^\d{1,18}$/.test(id)) notFound();
  return <PlaceFormView editId={id} />;
}
