import type { Metadata } from 'next';
import { Suspense } from 'react';
import { ExploreView } from '@/components/explore/ExploreView';

export const metadata: Metadata = {
  title: 'Explorar',
};

export default function ExplorarPage() {
  return (
    <Suspense fallback={<p>Carregando…</p>}>
      <ExploreView />
    </Suspense>
  );
}
