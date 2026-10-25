---
rf: RF15
titulo: Perfil público e feed de perfis
status: parcial
cards: [FE-26]
paths:
  - src/main/java/com/friendlyeats/application/profile/**
  - src/main/java/com/friendlyeats/infrastructure/persistence/postgres/ProfileDirectoryQueries.java
  - src/main/java/com/friendlyeats/api/UserProfileController.java
  - frontend/apps/web/app/u/*/page.tsx
  - frontend/apps/web/components/profile/ProfileView.tsx
  - frontend/apps/web/components/feed/ProfilesPanel.tsx
  - frontend/apps/web/components/feed/ProfileCard.tsx
  - frontend/apps/web/e2e/perfil.spec.ts
---

# RF15 · Perfil público e feed de perfis

> **Como** visitante ou pessoa com conta, **quero** abrir o perfil de quem publicou e descobrir criadores, chefs,
> comunidades, lugares e órgãos oficiais pelo tipo, pela cidade ou pelo nome **para** seguir as dicas de quem conhece.

| | |
|---|---|
| **Origem** | RF25 da Orange Social (perfil público com o feed do autor) e o `FeedType.PROFILE` |
| **Base legal (LGPD)** | Minimização (Art. 6º, III): nunca saem e-mail, telefone, nascimento, documentos nem a cidade de residência. A cidade mostrada é a de atuação declarada no cadastro do tipo de conta, e só para quem não é comensal |
| **ECA Digital** | Menor de 16 tem o perfil privado travado ([RF2](../RF2/README.md)): não entra no feed de perfis e os posts dele não abrem para os outros |
| **Status** | 🟡 Parcial: perfil, posts do autor com cursor, perfil privado, feed de perfis com filtros, telas do perfil público e da aba Perfis e testes prontos; seguir e contadores de seguidores ainda não |

## Critérios de aceite

1. **Perfil** (`GET /api/v1/users/{id}/profile`, público). Abre só para conta `ACTIVE` (o titular sempre vê o seu);
   inexistente, suspensa, em exclusão ou eliminada dá `404`, sem dizer o motivo. Mostra nome, @, bolinha
   (`AccountBadge`: cor nos dois temas, glifo, ícone Lucide e rótulo), selo (`verified`), foto, bio, pronomes, links,
   cidade e UF de atuação, data de entrada, número de posts públicos (publicados e arquivados), o caminho dos posts e o
   link compartilhável (`{public-base-url}/u/{id}`, a rota do perfil no app web; um endereço por `@handle` fica para quando houver a busca por handle).
2. **Perfil privado** (`profile_private`): para quem não é o titular, só nome, @, bolinha, selo e
   `privateProfile: true`; o resto vem `null`. Sem linha de preferências, vale o padrão do cadastro (privado para
   menor de 16).
3. **Posts do autor** (`GET /api/v1/users/{id}/posts?cursor=&size=`): publicados e arquivados (os encerrados com
   `expired: true`), dos mais novos aos mais antigos, com o cursor do [RF14](../RF14/README.md). Os 18+ só para quem tem
   18 anos ou mais (ou o próprio autor). Perfil privado: `403` para quem não é o titular. Cursor inválido: `400`.
4. **Feed de perfis** (`GET /api/v1/users?type=&city=&q=&cursor=&size=`, `FeedType.PROFILE`, público): só contas
   ativas com o perfil público; organizações (`ESTABELECIMENTO`, `VAREJO`, `OFICIAL`) só com o selo `VERIFIED`, para
   uma conta "Anvisa" sem selo não aparecer na vitrine. Dos mais novos aos mais antigos (cursor opaco pelo id).
   - `type`: um dos 7 tipos de conta (outro valor: `400`);
   - `city`: cidade de atuação, sem diferenciar maiúscula nem acento; nunca alcança o comensal;
   - `q`: parte do nome ou do @ (até 80 caracteres; `%` e `_` são literais).

## Contrato (`ProfileViews`)

- `PublicProfileView {id, name, handle, category: AccountBadge, verified, privateProfile, avatarUrl, bio, pronouns,
  links, city, uf, memberSince, postCount, postsPath, shareUrl}`
- `ProfileCardView {id, name, handle, category: AccountBadge, verified, avatarUrl, bio, city, uf, profilePath}`
- `ProfilePageView {items: [ProfileCardView], nextCursor}`
- `AccountBadge {code, label, color, lightColor, glyph, glyphName, lucideIcon}`
- os posts do autor voltam como `FeedPageView` do [RF14](../RF14/README.md).

## Diagramas

[Casos de uso](casos-de-uso.md) · [Atividades](atividades.md) · [Classes](classes.md) · [Componentes](componentes.md) ·
[Sequência](sequencia.md) · [Estados](estados.md)

## Rastreabilidade

| Camada | Artefato | Situação |
|---|---|---|
| Regras | `PublicProfileService` (conta ativa, perfil privado, cidade de atuação, 18+ nos posts, cursor do feed de perfis), `AccountBadge` (cor, glifo e ícone dos 7 tipos), `ProfileViews` | ✅ |
| Persistência | `ProfileDirectoryQueries` (conta ativa, perfil público pelos padrões do cadastro, organizações com selo, `f_unaccent` na cidade e no nome); `PostRepository.findProfileFeed` ([RF9](../RF9/README.md)) | ✅ |
| API | `UserProfileController`: `GET /api/v1/users`, `/api/v1/users/{id}/profile`, `/api/v1/users/{id}/posts`; GET públicos no `SecurityConfig` ([RF4](../RF4/README.md)) | ✅ |
| Testes | `UserProfileApiIT.profileShowsOnlyPublicDataAndTheAuthorsPostsPageByCursor`, `UserProfileApiIT.privateProfileShowsTheMinimumAndClosesThePostsExceptForTheHolder`, `UserProfileApiIT.profileFeedListsActivePublicAccountsWithBadgeAndSealAndFilters` | ✅ |
| Pendências | seguir e deixar de seguir, com contadores; perfil privado liberado para seguidores; feed de perfis por relevância e perto de mim; perfil do estabelecimento com os lugares dele; denúncia de perfil (RF13) | ⏳ |
| Tela | app web `/u/[id]` (avatar, nome, @, tipo de conta, selo, bio, links, cidade, membro desde, posts e compartilhar; perfil privado mostra só o mínimo) e a aba Perfis do `/feed` com filtros de tipo, cidade e nome; e2e `perfil.spec.ts` com axe | ✅ |
