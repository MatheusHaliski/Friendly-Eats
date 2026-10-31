---
rf: RF14
titulo: Feed e página do post, com o ranking em alta
status: parcial
cards: [FE-25]
paths:
  - src/main/java/com/friendlyeats/domain/model/FeedType.java
  - src/main/java/com/friendlyeats/application/post/PostQueryService.java
  - src/main/java/com/friendlyeats/application/post/PostReader.java
  - src/main/java/com/friendlyeats/application/post/PostViews.java
  - src/main/java/com/friendlyeats/application/post/FeedCursor.java
  - src/main/java/com/friendlyeats/application/post/AuthorDirectory.java
  - src/main/java/com/friendlyeats/application/post/TrendingService.java
  - src/main/java/com/friendlyeats/application/post/TrendingJob.java
  - src/main/java/com/friendlyeats/infrastructure/cache/redis/**
  - src/main/java/com/friendlyeats/infrastructure/persistence/postgres/PostFeedQueries.java
  - src/main/java/com/friendlyeats/api/PostFeedController.java
  - frontend/apps/web/app/feed/page.tsx
  - frontend/apps/web/app/posts/*/page.tsx
  - frontend/apps/web/components/feed/FeedView.tsx
  - frontend/apps/web/components/feed/Tabs.tsx
  - frontend/apps/web/components/post/PostCard.tsx
  - frontend/apps/web/components/post/PostBits.tsx
  - frontend/apps/web/components/post/PostList.tsx
  - frontend/apps/web/components/post/PostPage.tsx
  - frontend/apps/web/components/place/PlacePosts.tsx
  - frontend/apps/web/components/place/PlaceView.tsx
  - frontend/apps/web/components/SiteHeader.tsx
  - frontend/apps/web/lib/post-queries.ts
  - frontend/apps/web/lib/post-format.ts
  - frontend/packages/api/src/types.ts
  - frontend/packages/api/src/endpoints.ts
  - frontend/apps/web/e2e/feed.spec.ts
  - frontend/apps/web/e2e/support/mock-posts.ts
---

# RF14 · Feed e página do post, com o ranking em alta

> **Como** visitante ou pessoa com conta, **quero** rolar o feed de comida e o de alertas, abrir um post com tudo o
> que ele traz (lugar, alergênicos, preço, vigência) e ver o que está em alta **para** decidir onde e o que comer.

| | |
|---|---|
| **Origem** | RF24 e RF25 da Orange Social (página do post, cursor `(created_at, id)`, 404 sem dizer o motivo) e o Trending Score no Valkey (ADR-03 de lá) |
| **Base legal (LGPD)** | Art. 7º, V. O autor aparece com nome, @, bolinha e selo; conta eliminada vira "Conta excluída", sem link. Perfil privado não entra no feed |
| **ECA Digital / Termos** | Post 18+ (bebida alcoólica) não aparece para o visitante nem para o menor de 18, a mesma regra da página do lugar ([RF6](../RF6/README.md)) |
| **Status** | 🟡 Parcial: página do post, feed com cursor e filtros, posts do lugar, "em alta" com recálculo, telas do feed (abas e segmentos) e da página do post e testes prontos; timeline no Cassandra, feed de quem eu sigo, descoberta local por distância e visualizações únicas ainda não |

## Critérios de aceite

1. **Página do post** (`GET /api/v1/posts/{id}`, pública). Abre com status `PUBLISHED` ou `ARCHIVED`, autor com a
   conta `ACTIVE` e o perfil público; o autor sempre vê o próprio post. Fora disso (inexistente, removido, rascunho
   de outro, autor suspenso ou em exclusão, perfil privado), `404` sem dizer qual. Post `AGE_18`: o visitante recebe
   `401` ("Entre na sua conta para ver: conteúdo de bebida alcoólica é só para maiores de 18.") e o menor, `404`.
2. **O que a página mostra** (`PostDetailView`): tipo com ícone e forma da borda, foto com `altText`, lugar citado
   (só se a página dele estiver no ar para quem vê), culinária, formato, estilos, alergênicos com nível, advertências e
   o aviso `allergenNotice`, classificação com rótulo, chip Publicidade, vigência, preço com o texto pronto ("De R$
   50,00 por R$ 35,00"), receita, alerta com a fonte, `expired` ("Encerrado"), autor, contadores, estado dos botões
   de quem vê, `interactions` ([RF12](../RF12/README.md)), `shareUrl` e a moldura resolvida (`background`,
   [RF16](../RF16/README.md)).
3. **Feed** (`GET /api/v1/posts?feed=POST|ALERT&kind=&establishmentId=&cursor=&size=`, público): só `PUBLISHED`, sem
   os encerrados (`expires_at` no passado), de autores ativos com perfil público (o próprio autor se vê), e para o
   visitante e o menor de 18 só `LIVRE`. Mais novos primeiro, paginação por cursor opaco `(created_at, id)`:
   `nextCursor` nulo é o fim; cursor inválido dá `400`. `feed` padrão `POST`; `PROFILE` dá `400` (fica no
   [RF15](../RF15/README.md)); `size` de 1 a 50 (padrão 20). O card (`PostCardView`) não traz o texto corrido (só
   `excerpt`, até 160 caracteres) nem a receita.
4. **Posts sobre um lugar** (`GET /api/v1/establishments/{id}/posts?kind=&cursor=&size=`): primeiro a página do lugar
   precisa estar no ar para quem vê (as regras do RF6: `404`, e `401`/`404` no lugar 18+); depois, o mesmo feed
   filtrado pelo lugar.
5. **Em alta** (`GET /api/v1/posts/trending?feed=POST|ALERT&category=&size=`): ranking em ZSET no Valkey
   (`fe:trending:{feed}` e `fe:trending:{feed}:{tipo de conta}`). Cada interação soma o seu peso depois do commit
   (curtir 0,8, comentar 1,2, compartilhar 1,5, salvar 1,0; desfazer subtrai); publicar ou restaurar entra com 0;
   arquivar, apagar e a eliminação da conta tiram o post. A cada 10 min o `TrendingJob` recalcula com a fórmula
   completa (`engajamento / (horas + 2)^1,6`) os posts das últimas 72 h. O Valkey é só índice: cada post do ranking
   passa pela mesma regra de visibilidade do feed no PostgreSQL. `category` filtra pela cor do autor.
6. **Autor no card** (`AuthorView`): nome, @, bolinha (`AccountBadge`: cor nos dois temas, glifo, ícone e rótulo), selo
   e link do perfil; conta eliminada vira "Conta excluída" sem bolinha nem link; suspensa fica sem link.

## Contrato (`PostViews`)

- `PostDetailView {id, kind: KindView, feed, mediaType, title, body, mediaUrl, altText, establishment: PlaceRefView,
  cuisine: Coded, foodFormat: Coded, dietaryStyles: [Coded], allergens: [AllergenView], warnings: [Coded],
  allergenNotice, ageRating, ageRatingLabel, advertising, advertisingLabel, startsAt, endsAt, price: PriceView,
  recipe: RecipeView, alert: AlertView, city, expiresAt, expired, status, publishedAt, updatedAt, author: AuthorView,
  counts: CountersView, viewer: ViewerStateView, interactions: InteractionsView, shareUrl, background: BackgroundView}`
- `PostCardView {id, kind, feed, mediaType, title, excerpt, mediaUrl, altText, establishment, cuisine, foodFormat,
  dietaryStyles, allergens, warnings, ageRating, ageRatingLabel, advertising, advertisingLabel, startsAt, endsAt, price,
  alert, expiresAt, expired, status, publishedAt, author, counts, viewer, detailPath, background}`
- `FeedPageView {items: [PostCardView], nextCursor}`
- `KindView {code, label, lucideIcon, border}` · `PlaceRefView {id, name, city, uf, adultsOnly, pagePath}` ·
  `Coded {code, label, group}` · `AllergenView {code, label, level, levelLabel}` · `PriceView {priceCents,
  priceFromCents, priceToCents, discountPercent, free, label}` · `RecipeView {ingredients, steps, servings,
  prepMinutes}` · `AlertView {product, brand, lots, reason, sourceUrl, sourceHost}` · `CountersView {likes, comments,
  shares}` · `ViewerStateView {liked, saved, isAuthor}` · `InteractionsView {open, likes, comments, shares, saves,
  commentAudience, viewerCanComment, commentNotice}` · `AuthorView {id, name, handle, category: AccountBadge,
  verified, avatarUrl, profilePath, available}` · `AccountBadge {code, label, color, lightColor, glyph, glyphName,
  lucideIcon}`

## Diagramas

[Casos de uso](casos-de-uso.md) · [Atividades](atividades.md) · [Classes](classes.md) · [Componentes](componentes.md) ·
[Sequência](sequencia.md) · [Estados](estados.md)

## Rastreabilidade

| Camada | Artefato | Situação |
|---|---|---|
| Domínio | `FeedType` (`PROFILE`, `POST`, `ALERT`) | ✅ |
| Regras | `PostReader` (quem vê, 401/404 do 18+, lugar no ar, montagem da página e dos cards sem N+1), `AuthorDirectory` (conta ativa, perfil privado com os padrões do cadastro, `AuthorView`), `FeedCursor`, `PostQueryService` (feed, lugar, em alta) | ✅ |
| Persistência | `PostFeedQueries` (SQL com a regra de visibilidade em `VISIBLE` e índices `idx_post_feed` e `idx_post_establishment` da V3) | ✅ |
| Cache | `TrendingScoreCalculator` (fórmula da Orange), `TrendingScoreRepository` (ZSET por feed e por tipo de conta, TTL 72 h), `TrendingService` (depois do commit, melhor esforço), `TrendingJob` (10 min, desligável com `friendly-eats.jobs.enabled`) | ✅ |
| API | `PostFeedController`: `GET /api/v1/posts`, `/api/v1/posts/{id}`, `/api/v1/posts/trending`, `/api/v1/establishments/{id}/posts`; GET públicos no `SecurityConfig` ([RF4](../RF4/README.md)) | ✅ |
| Testes | `FeedCursorTest` (cursor e preço em reais), `TrendingScoreTest` (fórmula e chaves); `PostApiIT.feedPagesByCursorAndHidesExpiredAdultPrivateAndInactiveContent`, `PostApiIT.trendingRanksByInteractionsAndOnlyShowsWhatTheViewerCanSee`, `PostApiIT.frameFromTheCatalogIsValidatedAndResolvedButAlertsNeverHaveOne` (moldura no card e na página) | ✅ |
| Pendências | timeline no Cassandra (o perfil `dev` sobe sem ele); feed de quem eu sigo e mistura 70/30 com a descoberta local; alertas da região fixados no topo; distância e diversidade (MMR) no ranking; visualizações únicas (HyperLogLog); "por que estou vendo isto?" | ⏳ |
| Tela | app web `/feed` com as abas **Posts** (segmentado por tipo de post, na URL como `?tipo=`), **Alertas sanitários** (faixa hazard, sem moldura), **Perfis** e **Restaurantes** (busca do [RF10](../RF10/README.md) com o modal do lugar); "Em alta" com filtro por tipo de conta (`?conta=`); card de post com moldura, selo do tipo, preço, alergênicos, 18+, publicidade e expirado; página `/posts/[id]` (401 convida a entrar, 404); "Posts deste lugar" na página do lugar, que o [RF11](../RF11/README.md) enriqueceu com horários, cobranças, facetas e botões sem mudar a seção de posts (o `packages/api` ganhou a seção própria do RF11, sem tocar nos tipos de post); links Feed e Publicar no cabeçalho; e2e `feed.spec.ts` com axe nos dois temas | ✅ |
