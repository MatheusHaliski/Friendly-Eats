import type { Metadata } from 'next';
import { Suspense } from 'react';
import { LoginView } from '@/components/auth/LoginView';

export const metadata: Metadata = { title: 'Entrar' };

export default function EntrarPage() {
  return (
    <Suspense fallback={<p>Carregando…</p>}>
      <LoginView />
    </Suspense>
  );
}
