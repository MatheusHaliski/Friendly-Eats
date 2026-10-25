import type { Metadata } from 'next';
import { Suspense } from 'react';
import { FeedView } from '@/components/feed/FeedView';

export const metadata: Metadata = { title: 'Feed' };

export default function FeedPage() {
  return (
    <Suspense fallback={<p>Carregando…</p>}>
      <FeedView />
    </Suspense>
  );
}
