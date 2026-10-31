import type { Metadata } from 'next';
import Link from 'next/link';
import { Suspense } from 'react';
import { PublicReportForm } from '@/components/moderation/PublicReportForm';

export const metadata: Metadata = { title: 'Denunciar sem conta' };

/** Canal de denúncia para quem não tem conta (RF13 · STF, Temas 987 e 533, item 9). */
export default function Page() {
  return (
    <div className="grid gap-6">
      <header className="grid max-w-2xl gap-2">
        <h1 className="text-3xl font-extrabold">Denunciar sem conta</h1>
        <p>
          Viu algo ilegal ou que fere os Termos de Uso? Mande por aqui, mesmo sem conta. Com conta, use o botão &quot;Denunciar&quot; no menu &quot;…&quot;
          do conteúdo e acompanhe em Minhas denúncias. Veja também{' '}
          <Link href="/seguranca" className="font-semibold underline">
            Segurança e denúncias
          </Link>
          .
        </p>
      </header>
      <Suspense fallback={<p className="text-muted">Carregando…</p>}>
        <PublicReportForm />
      </Suspense>
    </div>
  );
}
