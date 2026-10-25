// Posts, interações, perfis e moldura na API simulada (RF9 · RF12 · RF14 · RF15 · RF16). Segue os records Java
// (PostViews, InteractionViews, ProfileViews, BackgroundCatalog) e as regras que a tela precisa ver: 401 do post 18+
// sem login, 404 do invisível, 409 de curtir alerta, 403 dos posts de perfil privado, 422 com errors[].
import type {
  AccountTypeCode,
  CommentView,
  FrameCatalog,
  FrameConfig,
  PostCardView,
  PostDetailView,
  PostDraft,
  PostKindCode,
  PostVocabulary,
  PublicProfileView,
  SessionUserView,
} from '@friendly-eats/api';
import { fromConfig, previewBackground, validateFrame } from '../../lib/frame';
import type { Recorded, Reply } from './mock-api';

type Fixture = <T>(name: string) => T;

export interface PostsHost {
  session: SessionUserView | null;
  uploads(): Set<string> | null;
}

function problem(status: number, detail: string, extra: Record<string, unknown> = {}): Reply {
  const titles: Record<number, string> = { 401: 'Login necessário', 403: 'Sem permissão', 404: 'Não encontrado', 409: 'Ação não disponível agora', 422: 'Revise os dados', 400: 'Pedido inválido' };
  return { status, body: { type: 'about:blank', title: titles[status] ?? 'Erro', status, detail, ...extra } };
}

const brl = (c: number) => `R$ ${(c / 100).toLocaleString('pt-BR', { minimumFractionDigits: 2, maximumFractionDigits: 2 })}`;
const ORGS: AccountTypeCode[] = ['ESTABELECIMENTO', 'VAREJO', 'OFICIAL'];
const BY_CATEGORY: Record<AccountTypeCode, Array<[string, string]>> = {
  COMENSAL: [['toalha-xadrez', 'linho'], ['feira', 'papel-manteiga']],
  CRIADOR: [['neon', 'vidro'], ['azulejo', 'papel-manteiga']],
  CHEF: [['forno', 'inox'], ['horta', 'linho']],
  ESTABELECIMENTO: [['azulejo', 'papel-manteiga'], ['forno', 'inox']],
  VAREJO: [['feira', 'papel-manteiga'], ['favo', 'vidro']],
  COMUNIDADE: [['feira', 'linho'], ['toalha-xadrez', 'papel-manteiga']],
  OFICIAL: [['horta', 'papel-manteiga']],
};

/** PostKind.denialFor: quem publica cada tipo. */
export function denial(kind: PostKindCode, s: SessionUserView): string | null {
  const t = s.category.code;
  if (ORGS.includes(t) && s.categoryVerification !== 'VERIFIED') {
    return `Sua conta de ${s.category.label} ainda está em verificação: você já pode ler e interagir, e publica depois do selo.`;
  }
  switch (kind) {
    case 'PERGUNTA':
      return t === 'OFICIAL' ? 'Conta Oficial não publica pergunta: publique um alerta sanitário ou responda nos comentários.' : null;
    case 'PRATO_DO_DIA':
      return t === 'ESTABELECIMENTO' ? null : 'Prato do dia é só de conta de Estabelecimento, sobre um lugar dela.';
    case 'PROMOCAO':
      return ['ESTABELECIMENTO', 'VAREJO', 'CRIADOR'].includes(t) ? null : 'Promoção é publicada pelo estabelecimento ou pela marca; criador, só com o chip Publicidade.';
    case 'EVENTO':
      return ['ESTABELECIMENTO', 'VAREJO', 'COMUNIDADE', 'CHEF'].includes(t) ? null : 'Evento é publicado por estabelecimento, varejo, comunidade ou chef.';
    case 'ALERTA_SANITARIO':
      return t === 'OFICIAL' ? null : 'Alerta sanitário só sai de conta Oficial verificada (Anvisa, vigilância sanitária, Procon).';
    default:
      return null;
  }
}

export class PostsState {
  posts: PostDetailView[];
  comments: Map<string, CommentView[]>;
  profiles: PublicProfileView[];
  readonly vocabulary: PostVocabulary;
  readonly catalog: FrameCatalog;
  liked = new Set<string>();
  saved = new Set<string>();
  shared = new Set<string>();
  private nextComment = 100;
  private nextPost = 500;

  constructor(
    private readonly fixture: Fixture,
    private readonly host: PostsHost,
  ) {
    this.posts = fixture<PostDetailView[]>('posts');
    const comments = fixture<Record<string, CommentView[]>>('comentarios');
    this.comments = new Map(Object.entries(comments));
    this.profiles = fixture<PublicProfileView[]>('perfis');
    this.vocabulary = fixture<PostVocabulary>('vocabulario-posts');
    this.catalog = fixture<FrameCatalog>('catalogo-molduras');
  }

  post(id: string): PostDetailView | undefined {
    return this.posts.find((p) => p.id === id);
  }

  private get viewerId(): number | null {
    return this.host.session?.id ?? null;
  }

  private adult(): boolean {
    return !!this.host.session && !this.host.session.minor;
  }

  /** O post como quem está vendo o recebe (viewer e interactions dependem da sessão). */
  view(p: PostDetailView): PostDetailView {
    const s = this.host.session;
    const isAuthor = s?.id === p.author.id;
    const open = p.status === 'PUBLISHED';
    const alert = p.kind.code === 'ALERTA_SANITARIO';
    let notice: string | null = null;
    if (!open) notice = 'Este post está arquivado e não recebe mais comentários.';
    else if (alert) notice = 'Alerta sanitário não recebe curtidas nem comentários: compartilhe ou salve.';
    else if (!s) notice = 'Entre na sua conta para comentar.';
    else if (!isAuthor && p.interactions.commentAudience === 'NOBODY') notice = 'Quem publicou fechou os comentários deste post.';
    return {
      ...p,
      counts: { ...p.counts, comments: this.comments.get(p.id)?.length ?? p.counts.comments },
      viewer: { liked: !!s && this.liked.has(p.id), saved: !!s && this.saved.has(p.id), isAuthor },
      interactions: { ...p.interactions, open, likes: open && !alert, comments: open && !alert, shares: open, viewerCanComment: notice === null, commentNotice: notice },
    };
  }

  card(p: PostDetailView): PostCardView {
    const v = this.view(p);
    return {
      id: v.id,
      kind: v.kind,
      feed: v.feed,
      mediaType: v.mediaType,
      title: v.title,
      excerpt: v.body && v.body.length > 160 ? `${v.body.slice(0, 159).trimEnd()}…` : v.body,
      mediaUrl: v.mediaUrl,
      altText: v.altText,
      establishment: v.establishment,
      cuisine: v.cuisine,
      foodFormat: v.foodFormat,
      dietaryStyles: v.dietaryStyles,
      allergens: v.allergens,
      warnings: v.warnings,
      ageRating: v.ageRating,
      ageRatingLabel: v.ageRatingLabel,
      advertising: v.advertising,
      advertisingLabel: v.advertisingLabel,
      startsAt: v.startsAt,
      endsAt: v.endsAt,
      price: v.price,
      alert: v.alert,
      expiresAt: v.expiresAt,
      expired: v.expired,
      status: v.status,
      publishedAt: v.publishedAt,
      author: v.author,
      counts: v.counts,
      viewer: v.viewer,
      detailPath: `/api/v1/posts/${v.id}`,
      background: v.background,
    };
  }

  private page(items: PostDetailView[], sp: URLSearchParams): Reply {
    const size = Math.min(50, Math.max(1, Number(sp.get('size') ?? 20)));
    const cursor = sp.get('cursor');
    const start = cursor ? Number(cursor.replace(/^c/, '')) : 0;
    const slice = items.slice(start, start + size);
    const next = start + size < items.length ? `c${start + size}` : null;
    return { status: 200, body: { items: slice.map((p) => this.card(p)), nextCursor: next } };
  }

  private visibleInFeed(p: PostDetailView): boolean {
    if (p.status !== 'PUBLISHED' || p.expired) return false;
    if (p.ageRating === 'AGE_18' && !this.adult()) return false;
    return !this.profiles.find((x) => x.id === p.author.id)?.privateProfile || p.author.id === this.viewerId;
  }

  private sorted(items: PostDetailView[]): PostDetailView[] {
    return [...items].sort((a, b) => b.publishedAt.localeCompare(a.publishedAt));
  }

  /** Respostas das rotas de posts, perfis e moldura; null = não é daqui. */
  handle(rec: Recorded): Reply | null {
    const { method: m, path: p, search: sp } = rec;
    const s = this.host.session;

    if (m === 'GET' && p === '/api/v1/posts/vocabulary') {
      const publishable = s && s.accountStatus === 'ACTIVE' ? this.vocabulary.kinds.map((k) => k.code).filter((k) => !denial(k, s)) : [];
      return { status: 200, body: { ...this.vocabulary, publishableKinds: publishable } };
    }
    if (m === 'GET' && p === '/api/v1/backgrounds/catalog') {
      return { status: 200, body: this.catalog, headers: { ETag: `"${this.catalog.version}-${this.catalog.activeSeasonId}"`, 'Cache-Control': 'max-age=3600, public' } };
    }
    if (m === 'GET' && p === '/api/v1/backgrounds/recommendations') {
      const cat = (sp.get('category') ?? 'COMENSAL') as AccountTypeCode;
      if (sp.get('feed') === 'ALERT') return { status: 200, body: [] };
      const image = sp.get('media') === 'IMAGE' || sp.get('media') === 'VIDEO';
      const label = this.vocabularyLabel(cat);
      const recs = (BY_CATEGORY[cat] ?? []).map(([aura, material]) => ({
        auraId: aura,
        materialId: image ? null : material,
        seasonId: null,
        reason: `Combina com ${label}${image ? ' e deixa o prato em destaque' : ' e post de texto'}`,
      }));
      const season = this.catalog.seasons.find((x) => x.id === this.catalog.activeSeasonId);
      if (season) recs.push({ auraId: null as never, materialId: null, seasonId: season.id as never, reason: `Cartela da época: ${season.name}` });
      return { status: 200, body: recs };
    }

    if (m === 'GET' && p === '/api/v1/posts') {
      const feed = sp.get('feed') ?? 'POST';
      const kind = sp.get('kind');
      if (kind && this.vocabulary.kinds.find((k) => k.code === kind)?.feed !== feed) return problem(400, 'Tipo de post fora deste feed.');
      const items = this.sorted(this.posts.filter((x) => x.feed === feed && (!kind || x.kind.code === kind) && this.visibleInFeed(x)));
      return this.page(items, sp);
    }
    if (m === 'GET' && p === '/api/v1/posts/trending') {
      const feed = sp.get('feed') ?? 'POST';
      const category = sp.get('category');
      const score = (x: PostDetailView) => x.counts.likes * 0.8 + x.counts.comments * 1.2 + x.counts.shares * 1.5;
      const items = this.posts
        .filter((x) => x.feed === feed && this.visibleInFeed(x) && (!category || x.author.category?.code === category))
        .sort((a, b) => score(b) - score(a))
        .slice(0, Number(sp.get('size') ?? 20));
      return { status: 200, body: { items: items.map((x) => this.card(x)), nextCursor: null } };
    }

    const placePosts = /^\/api\/v1\/establishments\/(\d+)\/posts$/.exec(p);
    if (placePosts && m === 'GET') {
      const id = Number(placePosts[1]);
      if (id === 18 && !s) return problem(401, 'Entre na sua conta para ver: conteúdo de bebida alcoólica é só para maiores de 18.');
      const items = this.sorted(this.posts.filter((x) => x.establishment?.id === id && this.visibleInFeed(x)));
      return this.page(items, sp);
    }

    if (m === 'POST' && p === '/api/v1/posts') return this.create(rec.body as PostDraft);

    const one = /^\/api\/v1\/posts\/([0-9a-f-]{36})(\/[a-z]+)?(\/(\d+))?$/.exec(p);
    if (one) {
      const [, id, sub, , commentId] = one;
      const post = this.post(id!);
      const visible = post && post.status !== 'REMOVED';
      if (!visible) return problem(404, 'Post não encontrado.');
      if (post.author.id !== this.viewerId && post.ageRating === 'AGE_18') {
        if (!s) return problem(401, 'Entre na sua conta para ver: conteúdo de bebida alcoólica é só para maiores de 18.');
        if (s.minor) return problem(404, 'Post não encontrado.');
      }
      if (!sub && m === 'GET') return { status: 200, body: this.view(post) };
      if (!sub && m === 'PATCH') return this.update(post, rec.body as Record<string, unknown>);
      if (!sub && m === 'DELETE') {
        if (post.author.id !== this.viewerId) return problem(404, 'Post não encontrado.');
        post.status = 'REMOVED';
        return { status: 204 };
      }
      if (sub === '/comments' && m === 'GET') {
        const after = Number(sp.get('after') ?? 0);
        const limit = Math.min(50, Math.max(1, Number(sp.get('limit') ?? 20)));
        const all = (this.comments.get(post.id) ?? []).filter((c) => c.id > after);
        const items = all.slice(0, limit).map((c) => ({ ...c, canDelete: !!s && (c.author.id === s.id || post.author.id === s.id) }));
        return { status: 200, body: { items, nextAfter: all.length > limit ? items.at(-1)!.id : null, total: this.comments.get(post.id)?.length ?? 0 } };
      }
      if (!s) return problem(401, 'Entre na sua conta para continuar.');
      if (sub === '/archive' && m === 'POST') {
        if (post.status !== 'PUBLISHED') return problem(409, 'Só posts publicados podem ser arquivados.');
        post.status = 'ARCHIVED';
        return { status: 200, body: this.view(post) };
      }
      if (sub === '/restore' && m === 'POST') {
        if (post.status !== 'ARCHIVED') return problem(409, 'Só posts arquivados podem voltar ao feed.');
        post.status = 'PUBLISHED';
        return { status: 200, body: this.view(post) };
      }
      const alert = post.kind.code === 'ALERTA_SANITARIO';
      if (sub === '/like') {
        if (m === 'PUT') {
          if (alert) return problem(409, 'Alerta sanitário não recebe curtidas nem comentários: compartilhe ou salve.');
          if (post.status !== 'PUBLISHED') return problem(409, 'Este post está arquivado e não recebe mais curtidas, comentários ou compartilhamentos.');
          if (!this.liked.has(post.id)) {
            this.liked.add(post.id);
            post.counts.likes += 1;
          }
          return { status: 200, body: { liked: true, likes: post.counts.likes } };
        }
        if (m === 'DELETE') {
          if (this.liked.delete(post.id)) post.counts.likes -= 1;
          return { status: 200, body: { liked: false, likes: post.counts.likes } };
        }
      }
      if (sub === '/save') {
        if (m === 'PUT') this.saved.add(post.id);
        else this.saved.delete(post.id);
        return { status: 200, body: { saved: m === 'PUT' } };
      }
      if (sub === '/comments' && m === 'POST') {
        const v = this.view(post);
        if (!v.interactions.viewerCanComment) {
          return problem(alert || post.status !== 'PUBLISHED' ? 409 : 403, v.interactions.commentNotice ?? 'Sem permissão.');
        }
        const body = String((rec.body as { body?: string })?.body ?? '').trim();
        if (!body) return problem(400, 'Escreva o comentário.', { errors: ['Escreva o comentário.'] });
        const comment: CommentView = {
          id: this.nextComment++,
          author: { id: s.id, name: s.name, handle: s.handle, category: this.badge(s.category.code), verified: s.categoryVerification === 'VERIFIED', avatarUrl: null, profilePath: `/api/v1/users/${s.id}/profile`, available: true },
          body,
          createdAt: new Date().toISOString(),
          canDelete: true,
        };
        const list = [...(this.comments.get(post.id) ?? []), comment];
        this.comments.set(post.id, list);
        post.counts.comments = list.length;
        return { status: 201, body: { comment, comments: list.length } };
      }
      if (sub === '/comments' && m === 'DELETE' && commentId) {
        const list = this.comments.get(post.id) ?? [];
        const c = list.find((x) => x.id === Number(commentId));
        if (!c) return problem(404, 'Comentário não encontrado.');
        if (c.author.id !== s.id && post.author.id !== s.id) return problem(403, 'Só quem escreveu ou o dono do post pode apagar este comentário.');
        this.comments.set(post.id, list.filter((x) => x !== c));
        post.counts.comments = list.length - 1;
        return { status: 204 };
      }
      if (sub === '/shares' && m === 'POST') {
        if (post.status !== 'PUBLISHED') return problem(409, 'Este post está arquivado e não recebe mais curtidas, comentários ou compartilhamentos.');
        const channel = String((rec.body as { channel?: string })?.channel ?? '');
        const key = `${s.id}:${post.id}:${channel}`;
        const counted = !this.shared.has(key);
        if (counted) {
          this.shared.add(key);
          post.counts.shares += 1;
        }
        return { status: 200, body: { shareUrl: post.shareUrl, channel, shares: post.counts.shares, counted } };
      }
      return problem(404, `Rota não simulada: ${m} ${p}`);
    }

    // ---- perfis (RF15)
    if (m === 'GET' && p === '/api/v1/users') {
      const type = sp.get('type');
      const city = sp.get('city')?.normalize('NFD').replace(/\p{M}/gu, '').toLowerCase();
      const q = sp.get('q')?.toLowerCase();
      const items = this.profiles
        .filter((x) => !x.privateProfile)
        .filter((x) => !type || x.category.code === type)
        .filter((x) => !city || x.city?.normalize('NFD').replace(/\p{M}/gu, '').toLowerCase() === city)
        .filter((x) => !q || x.name.toLowerCase().includes(q) || x.handle.includes(q));
      const size = Number(sp.get('size') ?? 20);
      const start = Number((sp.get('cursor') ?? 'u0').replace(/^u/, ''));
      const slice = items.slice(start, start + size);
      return {
        status: 200,
        body: {
          items: slice.map((x) => ({ id: x.id, name: x.name, handle: x.handle, category: x.category, verified: x.verified, avatarUrl: x.avatarUrl, bio: x.bio, city: x.city, uf: x.uf, profilePath: `/api/v1/users/${x.id}/profile` })),
          nextCursor: start + size < items.length ? `u${start + size}` : null,
        },
      };
    }
    const user = /^\/api\/v1\/users\/(\d+)\/(profile|posts)$/.exec(p);
    if (user && m === 'GET') {
      const id = Number(user[1]);
      const prof = this.profiles.find((x) => x.id === id) ?? (s?.id === id ? this.selfProfile(s) : undefined);
      if (!prof) return problem(404, 'Perfil não encontrado.');
      const self = s?.id === id;
      if (user[2] === 'profile') {
        if (prof.privateProfile && !self) {
          return {
            status: 200,
            body: { ...prof, avatarUrl: null, bio: null, pronouns: null, links: null, city: null, uf: null, memberSince: null, postCount: null, postsPath: null, shareUrl: null },
          };
        }
        return { status: 200, body: { ...prof, postCount: this.posts.filter((x) => x.author.id === id && x.status !== 'REMOVED').length } };
      }
      if (prof.privateProfile && !self) return problem(403, 'Este perfil é privado: os posts aparecem só para o titular.');
      const items = this.sorted(this.posts.filter((x) => x.author.id === id && (x.status === 'PUBLISHED' || x.status === 'ARCHIVED') && (self || x.ageRating !== 'AGE_18' || this.adult())));
      return this.page(items, sp);
    }
    return null;
  }

  private vocabularyLabel(code: AccountTypeCode): string {
    return { COMENSAL: 'Comensal', CRIADOR: 'Criador', CHEF: 'Chef', ESTABELECIMENTO: 'Estabelecimento', VAREJO: 'Varejo & Marca', COMUNIDADE: 'Comunidade', OFICIAL: 'Oficial' }[code];
  }

  private badge(code: AccountTypeCode) {
    const known = this.posts.find((x) => x.author.category?.code === code)?.author.category;
    return known ?? { code, label: this.vocabularyLabel(code), color: '#48A8FF', lightColor: '#05558F', glyph: '◇', glyphName: 'losango', lucideIcon: 'user-round' };
  }

  private selfProfile(s: SessionUserView): PublicProfileView {
    return {
      id: s.id, name: s.name, handle: s.handle, category: this.badge(s.category.code), verified: s.categoryVerification === 'VERIFIED', privateProfile: false, avatarUrl: null,
      bio: null, pronouns: [], links: [], city: null, uf: null, memberSince: '2026-08-30T12:00:00Z', postCount: 0, postsPath: `/api/v1/users/${s.id}/posts`, shareUrl: `https://friendlyeats.app/@${s.handle}`,
    };
  }

  // ------------------------------------------------------------ publicar e editar (RF9)

  private validate(kind: PostKindCode, d: Partial<PostDraft>, hasPhoto: boolean): string[] {
    const errors: string[] = [];
    if (!d.title?.trim()) errors.push(kind === 'PERGUNTA' ? 'Escreva a pergunta no título.' : 'Dê um título ao post.');
    if (kind === 'FOTO_PRATO' && !hasPhoto) errors.push('Foto de prato precisa da foto: envie antes (POST /api/v1/media/uploads) e informe "mediaObjectKey".');
    if (hasPhoto && !d.altText?.trim()) errors.push('Descreva a foto em "altText" (texto alternativo para quem usa leitor de tela).');
    if (kind === 'PRATO_DO_DIA' && d.priceCents == null) errors.push('Informe o preço do prato do dia em centavos ("priceCents"): o preço exibido é o cobrado (CDC art. 31).');
    if (kind !== 'PROMOCAO' && d.discountPercent != null) errors.push(`"discountPercent" não se aplica a ${this.vocabulary.kinds.find((k) => k.code === kind)?.label}.`);
    if (d.background != null) {
      const feed = kind === 'ALERTA_SANITARIO' ? 'ALERT' : 'POST';
      errors.push(...validateFrame(fromConfig(d.background as FrameConfig), this.catalog, feed));
    }
    return errors;
  }

  private create(d: PostDraft): Reply {
    const s = this.host.session;
    if (!s) return problem(401, 'Entre na sua conta para continuar.');
    if (s.accountStatus !== 'ACTIVE') return problem(403, 'Sua conta precisa estar ativa para publicar. Confirme seu e-mail ou fale com o suporte.');
    const kindItem = this.vocabulary.kinds.find((k) => k.code === d.kind);
    if (!kindItem) return problem(422, 'Escolha o tipo do post.', { errors: ['Escolha o tipo do post.'] });
    const denied = denial(d.kind, s);
    if (denied) return problem(403, denied);
    const uploads = this.host.uploads();
    const key = d.mediaObjectKey ?? null;
    if (key && !uploads?.has(key)) return problem(422, 'Envie a foto de novo.', { title: 'Revise a foto', errors: ['Envie a foto de novo.'] });
    const errors = this.validate(d.kind, d, !!key);
    if (errors.length) return problem(422, errors[0]!, { errors });
    if (key) uploads!.delete(key);
    const n = this.nextPost++;
    const id = `0199a1b2-c3d4-7e5f-8a9b-${String(n).padStart(12, '0')}`;
    const now = new Date().toISOString();
    const base: PostDetailView = {
      ...this.posts[0]!,
      id,
      kind: { code: kindItem.code, label: kindItem.label, lucideIcon: kindItem.lucideIcon, border: kindItem.border },
      feed: kindItem.feed,
      status: 'PUBLISHED',
      publishedAt: now,
      updatedAt: now,
      expired: false,
      expiresAt: null,
      counts: { likes: 0, comments: 0, shares: 0 },
      author: { id: s.id, name: s.name, handle: s.handle, category: this.badge(s.category.code), verified: s.categoryVerification === 'VERIFIED', avatarUrl: null, profilePath: `/api/v1/users/${s.id}/profile`, available: true },
      interactions: { ...this.posts[0]!.interactions, commentAudience: 'EVERYONE' },
      shareUrl: `https://friendlyeats.app/p/${id}`,
      mediaUrl: key ? `/media-teste/posts/${n}.png` : null,
      mediaType: key ? 'IMAGE' : 'TEXT',
    };
    const post = this.apply(base, d);
    this.posts.unshift(post);
    return { status: 201, body: this.view(post) };
  }

  private update(post: PostDetailView, patch: Record<string, unknown>): Reply {
    const s = this.host.session;
    if (!s) return problem(401, 'Entre na sua conta para continuar.');
    if (post.author.id !== s.id) return problem(404, 'Post não encontrado.');
    if ('kind' in patch) return problem(422, 'Campo "kind" não pode ser alterado por aqui: para mudar o tipo, apague e publique outro.', { errors: ['Campo "kind" não pode ser alterado por aqui: para mudar o tipo, apague e publique outro.'] });
    const key = patch.mediaObjectKey as string | null | undefined;
    const hasPhoto = key === undefined ? !!post.mediaUrl : !!key;
    const errors = this.validate(post.kind.code, { title: post.title, altText: post.altText, ...patch } as Partial<PostDraft>, hasPhoto);
    if (errors.length) return problem(422, errors[0]!, { errors });
    let next = this.apply({ ...post, updatedAt: new Date().toISOString() }, patch as Partial<PostDraft>);
    if (key === null) next = { ...next, mediaUrl: null, altText: null, mediaType: 'TEXT' };
    else if (key) next = { ...next, mediaUrl: `/media-teste/posts/${post.id.slice(-3)}.png`, mediaType: 'IMAGE' };
    if (!('background' in patch)) next.background = post.background;
    this.posts = this.posts.map((x) => (x.id === post.id ? next : x));
    return { status: 200, body: this.view(next) };
  }

  /** Campos do pedido → página do post (rótulos do vocabulário, preço pronto, moldura resolvida). */
  private apply(base: PostDetailView, d: Partial<PostDraft>): PostDetailView {
    const out: PostDetailView = { ...base };
    if (d.title !== undefined) out.title = String(d.title).trim();
    if (d.body !== undefined) out.body = d.body?.trim() || null;
    if (d.altText !== undefined) out.altText = d.altText?.trim() || null;
    if (d.allergens !== undefined) {
      out.allergens = Object.entries(d.allergens ?? {})
        .map(([code, level]) => ({
          code,
          label: this.vocabulary.allergens.find((a) => a.code === code)?.label ?? code,
          level,
          levelLabel: level === 'CONTEM' ? 'Contém' : 'Pode conter',
        }))
        .sort((a, b) => a.code.localeCompare(b.code));
    }
    if (d.warnings !== undefined) out.warnings = (d.warnings ?? []).map((w) => ({ code: w, label: this.vocabulary.warnings.find((x) => x.code === w)?.label ?? w, group: 'ADVERTENCIA' }));
    out.allergenNotice = out.allergens.length || out.warnings.length ? this.vocabulary.allergenNotice : null;
    if (d.ageRating !== undefined) {
      out.ageRating = d.ageRating ?? 'LIVRE';
      out.ageRatingLabel = out.ageRating === 'AGE_18' ? '18+' : 'Livre';
    }
    if (d.advertising !== undefined) {
      out.advertising = !!d.advertising;
      out.advertisingLabel = d.advertising ? 'Publicidade' : null;
    }
    if (d.city !== undefined) out.city = d.city ?? null;
    if (d.startsAt !== undefined) out.startsAt = d.startsAt ?? null;
    if (d.endsAt !== undefined) out.endsAt = d.endsAt ?? null;
    if (d.recipe !== undefined) out.recipe = d.recipe ? { ...d.recipe } : null;
    if (d.alert !== undefined) out.alert = d.alert ? { ...d.alert, sourceHost: new URL(d.alert.sourceUrl).host } : null;
    if (d.establishmentId !== undefined) {
      const places: Record<number, [string, string, string]> = { 1: ['Sabor Mineiro', 'Belo Horizonte', 'MG'], 2: ['Cantina Nonna Rosa', 'São Paulo', 'SP'], 7: ['Sabor Mineiro – Pampulha', 'Belo Horizonte', 'MG'] };
      const pl = d.establishmentId ? places[d.establishmentId] : null;
      out.establishment = pl && d.establishmentId ? { id: d.establishmentId, name: pl[0], city: pl[1], uf: pl[2], adultsOnly: false, pagePath: `/api/v1/establishments/${d.establishmentId}` } : null;
    }
    if (d.cuisine !== undefined) out.cuisine = d.cuisine ? { code: d.cuisine, label: d.cuisine === 'MINEIRA' ? 'Mineira' : d.cuisine, group: null } : null;
    if (d.foodFormat !== undefined) out.foodFormat = d.foodFormat ? { code: d.foodFormat, label: d.foodFormat, group: null } : null;
    if (d.dietaryStyles !== undefined) out.dietaryStyles = (d.dietaryStyles ?? []).map((c) => ({ code: c, label: c, group: null }));
    const price = { priceCents: d.priceCents, priceFromCents: d.priceFromCents, priceToCents: d.priceToCents, discountPercent: d.discountPercent, free: d.free };
    if (Object.values(price).some((v) => v !== undefined)) {
      if (price.priceFromCents && price.priceToCents) {
        out.price = { priceCents: null, priceFromCents: price.priceFromCents, priceToCents: price.priceToCents, discountPercent: null, free: false, label: `De ${brl(price.priceFromCents)} por ${brl(price.priceToCents)}` };
      } else if (price.discountPercent) {
        out.price = { priceCents: null, priceFromCents: null, priceToCents: null, discountPercent: price.discountPercent, free: false, label: `${price.discountPercent}% de desconto` };
      } else if (price.free) {
        out.price = { priceCents: null, priceFromCents: null, priceToCents: null, discountPercent: null, free: true, label: 'Gratuito' };
      } else if (price.priceCents != null) {
        out.price = { priceCents: price.priceCents, priceFromCents: null, priceToCents: null, discountPercent: null, free: false, label: brl(price.priceCents) };
      } else {
        out.price = null;
      }
    }
    if (!this.post(base.id)) {
      // criação: o que não veio no pedido fica vazio (a base copia só a forma)
      out.recipe = d.recipe ? { ...d.recipe } : null;
      out.alert = d.alert ? { ...d.alert, sourceHost: new URL(d.alert.sourceUrl).host } : null;
      if (d.establishmentId === undefined) out.establishment = null;
      if (d.cuisine === undefined) out.cuisine = null;
      if (d.foodFormat === undefined) out.foodFormat = null;
      if (d.body === undefined) out.body = null;
      if (d.allergens === undefined) out.allergens = [];
      if (d.warnings === undefined) out.warnings = [];
      if (d.dietaryStyles === undefined) out.dietaryStyles = [];
      if (d.city === undefined) out.city = null;
      if (d.startsAt === undefined) out.startsAt = null;
      if (d.endsAt === undefined) out.endsAt = null;
      if (d.ageRating === undefined) {
        out.ageRating = 'LIVRE';
        out.ageRatingLabel = 'Livre';
      }
      if (d.advertising === undefined) {
        out.advertising = false;
        out.advertisingLabel = null;
      }
      out.allergenNotice = out.allergens.length || out.warnings.length ? this.vocabulary.allergenNotice : null;
      if (!Object.values(price).some((v) => v !== undefined)) out.price = null;
      if (d.background === undefined) out.background = null;
      if (!d.mediaObjectKey) out.altText = null;
    }
    if (d.background !== undefined) {
      out.background = d.background ? previewBackground(fromConfig(d.background as FrameConfig), this.catalog) : null;
    }
    return out;
  }
}
