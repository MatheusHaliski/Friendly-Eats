import type { Metadata } from 'next';
import { PrivacyView } from '@/components/privacy/PrivacyView';

export const metadata: Metadata = { title: 'Privacidade e seus dados' };

export default function PrivacidadePage() {
  return <PrivacyView />;
}
