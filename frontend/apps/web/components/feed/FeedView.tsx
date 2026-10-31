'use client';

import type { AccountTypeCode, PostKindCode } from '@friendly-eats/api';
import Link from 'next/link';
import { usePathname, useRouter, useSearchParams } from 'next/navigation';
import { useId } from 'react';
import { AlcoholHiddenNotice } from '@/components/age/AlcoholNotice';
import { AccountDot } from '@/components/AccountBadge';
import { PlaceSearch } from '@/components/explore/PlaceSearch';
import { Notice } from '@/components/Form';
import { IconAlert, IconPublish, IconTrending, POST_KIND_ICONS } from '@/components/icons';
import { AuthorBadge, KindBadge } from '@/components/post/PostBits';
import { PostList } from '@/components/post/PostList';
import { ACCOUNT_TYPES } from '@/lib/account-types';
import { postHref } from '@/lib/post-format';
import { useFeed, useTrending, useVocabulary } from '@/lib/post-queries';
import { useSession } from '@/lib/queries';
import { ProfilesPanel } from './ProfilesPanel';
import { TabPanel, Tabs } from './Tabs';

export type FeedTab = 'posts' | 'alertas' | 'perfis' | 'restaurantes';
const TABS: FeedTab[] = ['posts', 'alertas', 'perfis', 'restaurantes'];
/** Tipos do feed POST (o alerta tem o feed dele). */
const POST_KINDS: PostKindCode[] = ['FOTO_PRATO', 'RECEITA', 'PERGUNTA', 'PRATO_DO_DIA', 'PROMOCAO', 'EVENTO'];

export function parseFeedTab(v: string | null): FeedTab {
  return (TABS as string[]).includes(v ?? '') ? (v as FeedTab) : 'posts';
}

/** Feed (RF14 · RF15 · RF10): Posts, Alertas sanitários, Perfis e Restaurantes, cada um com o seu feed. */
export function FeedView() {
  const router = useRouter();
  const pathname = usePathname();
  const sp = useSearchParams();
  const tab = parseFeedTab(sp.get('aba'));
  const session = useSession();

  /** Muda a URL com push: o link fica compartilhável e o voltar do navegador desfaz a escolha. */
  const go = (changes: Record<string, string | null>) => {
    const next = new URLSearchParams(sp.toString());
    for (const [k, v] of Object.entries(changes)) {
      if (v === null || v === '') next.delete(k);
      else next.set(k, v);
    }
    const q = next.toString();
    router.push(`${pathname}${q ? `?${q}` : ''}`, { scroll: false });
  };

  return (
    <div className="grid gap-6">
      <div className="flex flex-wrap items-end justify-between gap-3">
        <div className="grid gap-1">
          <h1 className="text-3xl font-extrabold tracking-tight sm:text-4xl">Feed</h1>
          <p className="text-muted">O que se come por aí, os alertas oficiais, quem publica e onde comer.</p>
        </div>
        {session.data && (
          <Link href="/publicar" className="fe-btn fe-btn-primary">
            <IconPublish size={18} />
            Publicar
          </Link>
        )}
      </div>
      <Tabs<FeedTab>
        label="Feeds"
        idPrefix="feed"
        selected={tab}
        onSelect={(k) => go({ aba: k === 'posts' ? null : k })}
        items={[
          { key: 'posts', label: 'Posts', testId: 'aba-posts' },
          {
            key: 'alertas',
            label: (
              <>
                <IconAlert size={18} />
                Alertas sanitários
              </>
            ),
            testId: 'aba-alertas',
          },
          { key: 'perfis', label: 'Perfis', testId: 'aba-perfis' },
          { key: 'restaurantes', label: 'Restaurantes', testId: 'aba-restaurantes' },
        ]}
      />
      <TabPanel idPrefix="feed" tab={tab}>
        {tab === 'posts' && <PostsPanel kindParam={sp.get('tipo')} accountParam={sp.get('conta')} go={go} />}
        {tab === 'alertas' && <AlertsPanel />}
        {tab === 'perfis' && <ProfilesPanel go={go} type={sp.get('tipoConta')} city={sp.get('cidade') ?? ''} name={sp.get('nome') ?? ''} />}
        {tab === 'restaurantes' && <PlaceSearch basePath={pathname} keep={{ aba: 'restaurantes' }} />}
      </TabPanel>
    </div>
  );
}

function PostsPanel({ kindParam, accountParam, go }: { kindParam: string | null; accountParam: string | null; go: (c: Record<string, string | null>) => void }) {
  const kind = (POST_KINDS as string[]).includes(kindParam ?? '') ? (kindParam as PostKindCode) : '';
  const account = ACCOUNT_TYPES.some((t) => t.code === accountParam) ? (accountParam as AccountTypeCode) : '';
  const vocabulary = useVocabulary();
  const feed = useFeed('POST', kind);
  const kinds = (vocabulary.data?.kinds ?? []).filter((k) => k.feed === 'POST');
  const kindLabel = kinds.find((k) => k.code === kind)?.label;
  return (
    <div className="grid gap-6 lg:grid-cols-[minmax(0,1fr)_20rem] lg:items-start">
      <section aria-labelledby="titulo-posts" className="grid min-w-0 gap-4">
        <h2 id="titulo-posts" className="sr-only">
          Posts{kindLabel ? `: ${kindLabel}` : ''}
        </h2>
        <div role="group" aria-label="Tipo de post" className="-mx-4 flex gap-2 overflow-x-auto px-4 pb-1 sm:mx-0 sm:flex-wrap sm:overflow-visible sm:px-0" data-testid="segmento-tipo">
          <button type="button" className="fe-pill flex-none" aria-pressed={kind === ''} onClick={() => go({ tipo: null })}>
            Todos
          </button>
          {kinds.map((k) => {
            const Icon = POST_KIND_ICONS[k.code];
            return (
              <button key={k.code} type="button" className="fe-pill flex-none" aria-pressed={kind === k.code} onClick={() => go({ tipo: k.code })}>
                {Icon && <Icon size={16} />}
                {k.label}
              </button>
            );
          })}
        </div>
        <AlcoholHiddenNotice />
        <PostList
          query={feed}
          label={kindLabel ? `Posts: ${kindLabel}` : 'Posts'}
          testId="lista-posts"
          empty={
            <>
              <p className="font-semibold">Nenhum post {kindLabel ? `do tipo ${kindLabel.toLowerCase()} ` : ''}por enquanto.</p>
              {kind && (
                <p>
                  <button type="button" className="fe-btn fe-btn-sm" onClick={() => go({ tipo: null })}>
                    Ver todos os tipos
                  </button>
                </p>
              )}
            </>
          }
        />
      </section>
      <Trending account={account} go={go} />
    </div>
  );
}

/** "Em alta" (RF14): o ranking aceita o tipo de conta do autor (category=); o feed por cursor não aceita. */
function Trending({ account, go }: { account: AccountTypeCode | ''; go: (c: Record<string, string | null>) => void }) {
  const uid = useId();
  const trending = useTrending('POST', account);
  const accountLabel = ACCOUNT_TYPES.find((t) => t.code === account)?.label;
  return (
    <aside aria-labelledby={`${uid}-titulo`} className="fe-card grid min-w-0 gap-3 p-4 lg:sticky lg:top-24 lg:order-none" data-testid="em-alta">
      <h2 id={`${uid}-titulo`} className="flex items-center gap-2 text-lg font-bold">
        <IconTrending size={20} />
        Em alta{accountLabel ? `: ${accountLabel}` : ''}
      </h2>
      <div role="group" aria-label="Em alta por tipo de conta do autor" className="flex flex-wrap gap-1.5" data-testid="filtro-conta">
        <button type="button" className="fe-pill" aria-pressed={account === ''} onClick={() => go({ conta: null })}>
          Todas
        </button>
        {ACCOUNT_TYPES.map((t) => (
          <button
            key={t.code}
            type="button"
            className="fe-pill"
            aria-pressed={account === t.code}
            aria-label={t.label}
            title={t.label}
            onClick={() => go({ conta: t.code })}
          >
            <AccountDot code={t.code} />
            <span className="sr-only">{t.label}</span>
          </button>
        ))}
      </div>
      {trending.isPending && <p className="text-sm text-muted">Carregando…</p>}
      {trending.isError && <p className="text-sm text-muted">Não foi possível carregar o ranking agora.</p>}
      {trending.data && trending.data.items.length === 0 && <p className="text-sm text-muted">Nada em alta por aqui nas últimas 72 horas.</p>}
      {trending.data && trending.data.items.length > 0 && (
        <ol className="grid gap-3" aria-label="Posts em alta">
          {trending.data.items.map((p, i) => (
            <li key={p.id} className="grid grid-cols-[1.5rem_1fr] gap-2">
              <span aria-hidden="true" className="text-lg font-extrabold text-muted">
                {i + 1}
              </span>
              <div className="grid min-w-0 gap-1">
                <Link href={postHref(p)} className="font-semibold underline decoration-1">
                  {p.title}
                </Link>
                <div className="flex flex-wrap items-center gap-1.5 text-sm text-muted">
                  <KindBadge kind={p.kind} />
                  <span>{p.author.name}</span>
                  <AuthorBadge author={p.author} />
                </div>
              </div>
            </li>
          ))}
        </ol>
      )}
    </aside>
  );
}

function AlertsPanel() {
  const feed = useFeed('ALERT', '');
  return (
    <section aria-labelledby="titulo-alertas" className="grid max-w-3xl gap-4">
      <h2 id="titulo-alertas" className="sr-only">
        Alertas sanitários
      </h2>
      <Notice>
        <p>
          Recolhimentos e alertas publicados só por contas oficiais verificadas (Anvisa, vigilância sanitária, Procon), com a
          fonte. Alertas não têm moldura nem curtidas: compartilhe ou salve.
        </p>
      </Notice>
      <PostList query={feed} label="Alertas sanitários" framed={false} testId="lista-alertas" empty={<p className="font-semibold">Nenhum alerta sanitário no momento.</p>} />
    </section>
  );
}
