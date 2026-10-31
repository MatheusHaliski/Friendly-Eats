import type { Metadata } from 'next';
import { Suspense } from 'react';
import { AgeSection } from '@/components/settings/AgeSection';

export const metadata: Metadata = { title: 'Idade e responsável · Configurações' };

export default function Page() {
  return (
    <Suspense fallback={<p className="text-muted">Carregando…</p>}>
      <AgeSection />
    </Suspense>
  );
}
