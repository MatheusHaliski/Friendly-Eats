'use client';

import Form from 'next/form';
import Image from 'next/image';
import Link from 'next/link';
import { usePathname, useSearchParams } from 'next/navigation';
import { Suspense } from 'react';
import { useRoles } from '@/lib/moderation';
import { useSession } from '@/lib/queries';
import { IconAccount, IconFeed, IconLogin, IconModeration, IconPublish, IconSearch, IconSettings } from './icons';
import { ThemeToggle } from './ThemeToggle';

function HeaderSearch() {
  const pathname = usePathname();
  const params = useSearchParams();
  // No Explorar (e na aba Restaurantes do feed) a busca por nome fica no próprio formulário de filtros
  if (pathname === '/' || (pathname === '/feed' && params.get('aba') === 'restaurantes')) return null;
  return (
    <Form action="/" role="search" aria-label="Buscar lugares" className="order-last flex min-w-0 basis-full items-center gap-2 sm:order-none sm:max-w-md sm:flex-1 sm:basis-0">
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
  const roles = useRoles();
  const pathname = usePathname();
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
    <>
      <Link href="/publicar" className="fe-btn fe-btn-sm" data-testid="link-publicar" aria-current={pathname === '/publicar' ? 'page' : undefined}>
        <IconPublish size={18} />
        <span className="sr-only sm:not-sr-only">Publicar</span>
      </Link>
      {roles.data?.moderator && (
        <Link href="/moderacao" className="fe-btn fe-btn-sm" data-testid="link-moderacao" aria-current={pathname === '/moderacao' ? 'page' : undefined}>
          <IconModeration size={18} />
          <span className="sr-only md:not-sr-only">Moderação</span>
        </Link>
      )}
      <Link
        href="/configuracoes"
        className="fe-btn fe-btn-sm"
        data-testid="link-configuracoes"
        aria-current={pathname.startsWith('/configuracoes') ? 'page' : undefined}
      >
        <IconSettings size={18} />
        <span className="sr-only md:not-sr-only">Configurações</span>
      </Link>
      <Link href="/conta" className="fe-btn fe-btn-sm" data-testid="link-conta">
        <IconAccount size={18} />
        <span>
          Conta<span className="sr-only"> de</span> <span className="hidden md:inline">@{user.handle}</span>
          <span className="sr-only md:hidden">@{user.handle}</span>
        </span>
      </Link>
    </>
  );
}

/** Navegação principal: Explorar (lugares) e Feed (posts, alertas, perfis e restaurantes). */
function MainNav() {
  const pathname = usePathname();
  return (
    <nav aria-label="Principal" className="flex flex-wrap items-center gap-1">
      <Link href="/feed" className="fe-btn fe-btn-sm" aria-current={pathname === '/feed' ? 'page' : undefined} data-testid="link-feed">
        <IconFeed size={18} />
        Feed
      </Link>
    </nav>
  );
}

export function SiteHeader() {
  return (
    <header className="sticky top-0 z-20 border-b border-line bg-bg/95 backdrop-blur supports-[backdrop-filter]:bg-bg/85">
      <div className="mx-auto flex max-w-6xl flex-wrap items-center gap-x-4 gap-y-2 px-4 py-3">
        <Link
          href="/"
          className="mr-auto flex items-center gap-2 text-xl font-extrabold tracking-tight no-underline sm:mr-0"
        >
          {/* logo oficial; decorativo porque o nome vem escrito ao lado */}
          <Image src="/brand/logo-96.webp" alt="" width={40} height={40} unoptimized priority className="h-10 w-10" />
          <span>
            Friendly <span className="text-brand">Eats</span>
          </span>
        </Link>
        <MainNav />
        <Suspense fallback={<div className="flex-1" />}>
          <HeaderSearch />
        </Suspense>
        <nav aria-label="Conta e preferências" className="ml-auto flex min-w-0 flex-wrap items-center justify-end gap-2">
          <ThemeToggle />
          <SessionLink />
        </nav>
      </div>
    </header>
  );
}
