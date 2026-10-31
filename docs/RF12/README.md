---
rf: RF12
titulo: Interações · curtir, salvar, comentar e compartilhar
status: parcial
cards: [FE-18]
paths:
  - src/main/java/com/friendlyeats/domain/model/PostComment.java
  - src/main/java/com/friendlyeats/domain/model/CommentStatus.java
  - src/main/java/com/friendlyeats/domain/model/ReactionKind.java
  - src/main/java/com/friendlyeats/domain/model/ShareChannel.java
  - src/main/java/com/friendlyeats/application/post/PostInteractionService.java
  - src/main/java/com/friendlyeats/application/post/InteractionViews.java
  - src/main/java/com/friendlyeats/application/post/CommentPolicy.java
  - src/main/java/com/friendlyeats/api/PostInteractionController.java
  - src/main/java/com/friendlyeats/infrastructure/persistence/postgres/PostCommentRepository.java
  - frontend/apps/web/components/post/InteractionBar.tsx
  - frontend/apps/web/components/post/Comments.tsx
  - frontend/apps/web/lib/share.ts
  - frontend/apps/web/app/p/*/page.tsx
  - frontend/apps/web/e2e/post.spec.ts
---

# RF12 · Interações: curtir, salvar, comentar e compartilhar

> **Como** pessoa com conta, **quero** curtir, salvar, comentar e compartilhar um post vendo quantas pessoas já fizeram
> o mesmo **para** guardar o prato para depois, tirar dúvidas na caixa de mensagens e levar a dica para fora do app.

| | |
|---|---|
| **Origem** | RF26 da Orange Social (contadores atômicos e idempotentes, caixa de mensagens, folha de compartilhamento) |
| **Base legal (LGPD)** | Art. 7º, V. Curtidas e comentários são públicos; **salvar é privado** (ninguém mais vê nem conta). O link compartilhado não diz quem compartilhou |
| **ECA Digital** | Menor de 16 nasce com perfil privado e comentários só de seguidores, travados ([RF2](../RF2/README.md)): ninguém de fora vê nem comenta os posts dele |
| **Eliminação** | Na eliminação da conta ([RF5](../RF5/README.md)) as curtidas e os salvos somem, os comentários perdem o texto, os compartilhamentos perdem o vínculo e os contadores dos posts de terceiros são corrigidos |
| **Status** | 🟡 Parcial: API idempotente, contadores atômicos, regra de quem comenta, alerta só com compartilhar e salvar, "em alta" e testes prontos; seguir perfis, resposta identificada do dono, notificações e tempo real ainda não |

## Critérios de aceite

1. **Um ícone, um significado**, todos `<button>` com `aria-label` por extenso e `aria-pressed` quando alternam:
   curtir (`heart`, com contagem), comentar (`message-circle`, com contagem), compartilhar (`share-2`, com contagem) e
   salvar (`bookmark`, sem contagem pública).
2. **Curtir e salvar** (`PUT`/`DELETE /api/v1/posts/{id}/like` e `/save`) são idempotentes: repetir não muda o número.
   A linha de associação e o contador mudam **na mesma transação**, e o contador só se move quando a linha entrou ou
   saiu de fato. Nunca `COUNT(*)` na leitura. Descurtir e tirar dos salvos funcionam mesmo em post arquivado.
3. **Caixa de mensagens.** `GET /comments?after=&limit=` é pública e lista do mais antigo para o mais novo, paginada
   por id (`nextAfter`), com o `total`. `POST /comments {body}` (até 2.000 caracteres, sem só espaços: `400`).
   `DELETE /comments/{commentId}` só por quem escreveu ou pelo dono do post (`403` para os outros); a remoção apaga o
   texto e mantém o id para a moderação. A troca para `REMOVED` é atômica (`removeIfVisible`: `UPDATE … WHERE status =
   'VISIBLE'`): se quem escreveu e o dono apagam ao mesmo tempo, só uma exclusão vale (a outra recebe `404`) e o
   contador desce uma vez só (`InteractionApiIT.removingTheSameCommentTwiceDecrementsTheCounterOnce`).
4. **Quem comenta** (`CommentPolicy`), pelo `who_can_comment` de quem publicou:
   - `EVERYONE`: qualquer conta ativa;
   - `NOBODY`: só o autor (`403` "Quem publicou fechou os comentários deste post.");
   - `FOLLOWERS`: hoje também só o autor, porque seguir perfis ainda não existe (⏳);
   - o autor sempre comenta na própria caixa;
   - menor de 16: perfil privado travado, então o post nem aparece para os outros (`404`). Sem linha de preferências,
     valem os padrões do cadastro pela idade.
   A página do post traz `interactions.commentAudience`, `viewerCanComment` e o motivo em `commentNotice`.
5. **Compartilhar** (`POST /shares {channel}`): `COPY_LINK`, `NATIVE`, `WHATSAPP`, `TELEGRAM`, `X` ou `EMAIL` (outro
   valor: `400`). Devolve o link público `PublicLinks.postShareUrl` (`{public-base-url}/p/{id}`, sem rastreio) e conta
   **pares distintos (conta, canal)** (`counted`).
6. **Conta e post.** Escrever exige sessão (`401`) e conta ativa (`403`); o post precisa estar visível para quem
   interage, com as mesmas regras da página do post ([RF14](../RF14/README.md): 18+, perfil privado, autor ativo).
   Post arquivado: leitura sim, novas curtidas, comentários e compartilhamentos não (`409`).
7. **Alerta sanitário** aceita só compartilhar e salvar: curtir e comentar dão `409`.
8. Cada interação que entra ou sai soma ou subtrai o seu peso no ranking "em alta" ([RF14](../RF14/README.md)) depois
   do commit.
9. **Moderação e segurança** ([RF13](../RF13/README.md), [RF19](../RF19/README.md)):
   - comentário retirado pela moderação (`moderation_state` preenchido) não aparece, não conta e não é apagado pelo
     autor (`404`); voltar ao ar devolve o contador;
   - na lista, quem vê não recebe comentários de quem o bloqueou, foi bloqueado ou silenciado por ele;
   - comentários com palavras filtradas por quem publicou o post (lista dele e, se ligado, o filtro de ofensas) somem
     para todos, menos para quem escreveu; quem publicou não é filtrado na própria caixa;
   - o menu "…" de cada comentário dos outros tem "Denunciar".

## Contrato

| Rota | Corpo | Resposta (`InteractionViews`) |
|---|---|---|
| `PUT`/`DELETE /api/v1/posts/{id}/like` | — | `LikeStateView {liked, likes}` |
| `PUT`/`DELETE /api/v1/posts/{id}/save` | — | `SaveStateView {saved}` |
| `GET /api/v1/posts/{id}/comments?after=0&limit=20` | — | `CommentPageView {items: [CommentView], nextAfter, total}` |
| `POST /api/v1/posts/{id}/comments` | `{body}` | `201 CommentCreatedView {comment: CommentView, comments}` |
| `DELETE /api/v1/posts/{id}/comments/{commentId}` | — | `204` |
| `POST /api/v1/posts/{id}/shares` | `{channel}` | `ShareResultView {shareUrl, channel, shares, counted}` |

`CommentView {id, author: AuthorView, body, createdAt, canDelete}`; o `AuthorView` está no [RF14](../RF14/README.md).

## Diagramas

[Casos de uso](casos-de-uso.md) · [Atividades](atividades.md) · [Classes](classes.md) · [Componentes](componentes.md) ·
[Sequência](sequencia.md) · [Estados](estados.md)

## Rastreabilidade

| Camada | Artefato | Situação |
|---|---|---|
| Domínio | `PostComment` (texto aparado, até 2.000, `canBeRemovedBy`, remoção lógica), `CommentStatus`, `ReactionKind` (`LIKE`, `SAVE`), `ShareChannel` (com rótulo) | ✅ |
| Regras | `PostInteractionService` (contadores na mesma transação, idempotência, arquivado `409`, alerta só compartilhar e salvar), `CommentPolicy` (`who_can_comment`, autor, conta ativa) | ✅ |
| Persistência | `post_reaction` (PK post + conta + tipo), `post_comment` (CHECK do texto por status), `post_share` (único por post + conta + canal, `user_id` vira `NULL` na eliminação), na `V3__posts.sql` do [RF9](../RF9/README.md); `PostCommentRepository`; escritas atômicas no `PostRepository` | ✅ |
| API | `PostInteractionController`: `PUT`/`DELETE` like e save, `GET`/`POST` comments, `DELETE` comments/{id}, `POST` shares; `GET` de comentários público no `SecurityConfig` ([RF4](../RF4/README.md)) | ✅ |
| Links | `PublicLinks.postShareUrl` ([RF2](../RF2/README.md)) | ✅ |
| Testes | `PostDomainTest.commentIsTrimmedValidatedAndRemovableByWriterOrPostOwner`, `PostDomainTest.alertTakesOnlySharesAndSavesAndExpiryIsByTheDeadline`; `InteractionApiIT.likeAndSaveAreIdempotentCountedOnceAndNeedAnActiveLogin`, `InteractionApiIT.commentsFollowTheAuthorsAudienceAndMinorsAreProtected`, `InteractionApiIT.shareReturnsThePublicLinkAndCountsEachAccountAndChannelOnce`, `InteractionApiIT.foodAlertTakesOnlySharesAndSaves`, `InteractionApiIT.accountEliminationRemovesPostsCommentsLikesAndPhotos` | ✅ |
| Moderação e segurança | `PostComment.moderationState` e `isVisible` (sem medida), `PostCommentRepository.findByPostIdAndStatusAndModerationStateIsNull…` e `discountCommentsOf` sem os moderados ([RF13](../RF13/README.md)); filtro de bloqueio, silêncio e palavras em `listComments` com o `SafetyGuard` ([RF19](../RF19/README.md)); `ModerationApiIT.childAtRiskHidesAtOnceGoesFirstAndIsFlaggedForAuthorities`, `SafetyApiIT.filteredWordsHideCommentsOnMyPostsForEveryoneButTheirAuthor` | ✅ |
| Pendências | seguir perfis (libera `FOLLOWERS`); resposta identificada do estabelecimento (`RULE_OWNER_REPLY`) e "melhor resposta" da pergunta; "Meus salvos"; notificações (Cassandra) e tempo real, respeitando bloqueio e silêncio (`SafetyGuard.mayReach`); limite de ritmo por conta | ⏳ |
| Tela | app web: curtir e salvar com contagem e `aria-live` (`InteractionBar`), comentários paginados por `after` com envio e exclusão do próprio e o aviso quando fechados (`Comments`; o novo só entra direto na lista quando a última página carregada é a final, senão chega na sua vez, e a lista é única por id), compartilhar pelos canais do vocabulário ou copiando o link (`share.ts`); alerta sanitário só salva e compartilha; o link público `/p/[id]` redireciona para o post; e2e `post.spec.ts` com axe (inclusive comentar com páginas pendentes sem duplicar) | ✅ |
