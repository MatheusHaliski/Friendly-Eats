import type { Metadata } from 'next';
import { PlaceFormView } from '@/components/place/PlaceForm';

export const metadata: Metadata = { title: 'Cadastrar lugar' };

export default function NovoLugarPage() {
  return <PlaceFormView />;
}
