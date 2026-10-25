'use client';

import Form from 'next/form';
import Link from 'next/link';
import { usePathname, useSearchParams } from 'next/navigation';
import { Suspense } from 'react';
import { useSession } from '@/lib/queries';
import { IconAccount, IconLogin, IconSearch } from './icons';
import { ThemeToggle } from './ThemeToggle';

function HeaderSearch() {
  const pathname = usePathname();
  const params = useSearchParams();
  // No Explorar a busca por nome fica no próprio formulário de filtros
  if (pathname === '/') return null;
  return (
    <Form action="/" role="search" aria-label="Buscar lugares" className="flex min-w-0 flex-1 items-center gap-2 sm:max-w-md">
      <label htmlFor="busca-cabecalho" className="sr-only">
        Buscar lugares por nome
      </label>
      <input
        id="busca-cabecalho"
        name="q"
        type="search"
        className="fe-input min-w-0"
        placeholder="Buscar lugares"
        defaultValue={params.get('q') ?? ''}
        autoComplete="off"
      />
      <button type="submit" className="fe-btn fe-btn-sm" aria-label="Buscar">
        <IconSearch size={18} />
      </button>
    </Form>
  );
}

function SessionLink() {
  const { data: user, isPending } = useSession();
  if (isPending) return <span className="inline-block h-11 w-24" aria-hidden="true" />;
  if (!user) {
    return (
      <Link href="/entrar" className="fe-btn fe-btn-sm">
        <IconLogin size={18} />
        Entrar
      </Link>
    );
  }
  return (
    <Link href="/conta" className="fe-btn fe-btn-sm" data-testid="link-conta">
      <IconAccount size={18} />
      <span>
        Conta<span className="sr-only"> de</span> <span className="hidden md:inline">@{user.handle}</span>
        <span className="sr-only md:hidden">@{user.handle}</span>
      </span>
    </Link>
  );
}

export function SiteHeader() {
  return (
    <header className="sticky top-0 z-20 border-b border-line bg-bg/95 backdrop-blur supports-[backdrop-filter]:bg-bg/85">
      <div className="mx-auto flex max-w-6xl flex-wrap items-center gap-x-4 gap-y-2 px-4 py-3">
        <Link href="/" className="mr-auto text-xl font-extrabold tracking-tight no-underline sm:mr-0">
          Friendly <span className="text-brand">Eats</span>
        </Link>
        <Suspense fallback={<div className="flex-1" />}>
          <HeaderSearch />
        </Suspense>
        <nav aria-label="Conta e preferências" className="ml-auto flex items-center gap-2">
          <ThemeToggle />
          <SessionLink />
        </nav>
      </div>
    </header>
  );
}
