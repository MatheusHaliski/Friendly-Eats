---
rf: RF19
titulo: Segurança entre usuários · bloquear, silenciar e palavras filtradas
status: parcial
cards: [FE-29]
paths:
  - src/main/java/com/friendlyeats/application/safety/*.java
  - src/main/java/com/friendlyeats/api/SafetyController.java
  - src/main/java/com/friendlyeats/infrastructure/persistence/postgres/SafetyRepository.java
  - src/test/java/com/friendlyeats/api/SafetyApiIT.java
  - src/test/java/com/friendlyeats/application/safety/WordFilterTest.java
  - frontend/apps/web/components/safety/*.tsx
  - frontend/apps/web/app/seguranca/page.tsx
---

# RF19 · Segurança entre usuários: bloquear, silenciar e palavras filtradas

> **Como** pessoa, **quero** bloquear quem me ataca, silenciar quem não quero ver e esconder comentários ofensivos nos
> meus posts **para** usar o app sem intimidação; **como** menor de 16, **quero** essa proteção ligada desde o início.

| | |
|---|---|
| **Origem** | Pesquisa de requisitos, eixo 03 (RC-SEG-09) · [PESQUISA_REQUISITOS](../PESQUISA_REQUISITOS.md) |
| **Base legal** | ECA Digital, art. 6º, §2º (política contra intimidação sistemática virtual, com apoio às vítimas), art. 17, §4º, I e IX, e art. 24, §5º (padrão protetivo para menor, que só o responsável reduz); Código Penal, art. 146-A (Lei 14.811/2024, cyberbullying) |
| **LGPD** | Listas pessoais, nunca mostradas a terceiros nem à pessoa bloqueada ou silenciada (art. 6º, III e VII). Saem por inteiro na eliminação da conta ([RF5](../RF5/README.md)) |
| **Status** | 🟡 Parcial: bloquear, silenciar, palavras filtradas com o filtro de ofensas travado para menor de 16, regras no feed, "em alta", post, comentários, perfil e busca de perfis, telas e a página `/seguranca` prontos; desfoque de rosto e placa, direito autoral (RC-SEG-10 e 11), "modo proteção" contra rajada de comentários, menção e notificações (RF21) ainda não |

## Critérios de aceite

1. **Bloquear** (`PUT`/`DELETE /api/v1/me/blocks/{userId}`, idempotente): vale **nos dois sentidos** e é sigiloso. Quem
   bloqueou e quem foi bloqueado deixam de se ver no feed e no "em alta" ([RF14](../RF14/README.md)), na página do post
   (`404`), na lista de comentários ([RF12](../RF12/README.md)), no perfil e nos posts do perfil (`404`) e na busca de
   perfis ([RF15](../RF15/README.md)); quem foi bloqueado não comenta nem interage (o post responde `404`). Bloquear a
   si mesmo dá `422`; conta inexistente ou eliminada, `404`.
2. **Avaliações não somem por bloqueio** (RC-SEG-09, riscos): o dono do lugar não esconde a crítica bloqueando quem
   avaliou ([RF8](../RF8/README.md), `RULE_NO_SUPPRESSION`).
3. **Silenciar** (`PUT`/`DELETE /api/v1/me/mutes/{userId}`): só para quem silenciou, no feed, no "em alta" e nos
   comentários; o perfil ainda abre se a pessoa quiser. O silenciado não sabe (`GET /me/relations/{id}` dele mostra
   `muted: false`).
4. **Palavras filtradas** (`PUT /api/v1/me/filtered-words {words}`): até 100 palavras ou expressões de 2 a 40 letras,
   guardadas sem acento e em minúsculas. Comentários **nos meus posts** que as contêm somem para todos, menos para quem
   escreveu e para mim no meu próprio comentário. A comparação ignora acentos, maiúsculas, trocas comuns
   (`1d10t4` = `idiota`) e letras repetidas, e é por palavra inteira (`lixo` não pega `lixeira`).
5. **Filtro de ofensas** (`PATCH /api/v1/me/safety {defaultFilter}`): a lista padrão (`WordFilter.DEFAULT_WORDS`, 18
   palavras e expressões de intimidação ⚖️ curadoria) vem **ligada e travada** para menor de 16 (`403` ao desligar) e é
   opcional para os demais.
6. **Telas**: menu "…" do perfil público com Denunciar, Silenciar e Bloquear (com confirmação em `alertdialog`, foco no
   Cancelar) e o desfazer; Configurações → Privacidade → "Bloqueados, silenciados e palavras" com as listas, desbloquear,
   o filtro de ofensas e as palavras; página pública `/seguranca` ("Sofreu intimidação?") com passos práticos, canais de
   apoio (CVV 188, SaferNet, Disque 100, 190 ⚖️), as regras da moderação e o link de denúncia sem conta; link no rodapé.
7. **Notificações**: ainda não existem (RF21 ⏳); `SafetyGuard.mayReach(actor, recipient)` é a regra que elas vão usar.

## Contrato

| Rota | Corpo | Resposta (`SafetyViews`) |
|---|---|---|
| `GET /api/v1/me/safety` | — | `SafetyView {blocked: [RelatedUserView {user: AuthorView, since}], muted, words, defaultFilter: {enabled, locked, size}, maxWords}` |
| `PATCH /api/v1/me/safety` | `{defaultFilter}` | `SafetyView` · `403` (menor de 16 desligando) |
| `PUT /api/v1/me/filtered-words` | `{words: [...]}` | `SafetyView` · `422` |
| `GET /api/v1/me/relations/{userId}` | — | `RelationView {userId, blocked, muted}` |
| `PUT`/`DELETE /api/v1/me/blocks/{userId}` | — | `RelationView` · `404` · `422` |
| `PUT`/`DELETE /api/v1/me/mutes/{userId}` | — | `RelationView` · `404` · `422` |

## Diagramas

[Casos de uso](casos-de-uso.md) · [Atividades](atividades.md) · [Classes](classes.md) · [Componentes](componentes.md) ·
[Sequência](sequencia.md) · [Estados](estados.md)

## Rastreabilidade

| Camada | Artefato | Situação |
|---|---|---|
| Regras | `SafetyService` (bloquear, silenciar, palavras, filtro padrão, sigilo), `SafetyGuard` (quem não vê quem, filtro de quem publicou, `mayReach`), `WordFilter` (normalização, palavra inteira, lista padrão) | ✅ |
| Persistência | `user_block`, `user_mute`, `user_filtered_word`, `user_safety_setting` na `V5__moderacao.sql` do [RF13](../RF13/README.md); `SafetyRepository`; regras no SQL do feed (`PostFeedQueries`) e da busca de perfis (`ProfileDirectoryQueries`) | ✅ |
| Leitura | `PostReader.requireVisible` (`404` com bloqueio), `PostInteractionService.listComments` (bloqueio, silêncio e palavras), `PublicProfileService` (perfil, posts e busca) | ✅ |
| Eliminação | `AccountService.eliminate` apaga listas e bloqueios nos dois sentidos ([RF5](../RF5/README.md)) | ✅ |
| API | `SafetyController` (`/api/v1/me/safety`, `/filtered-words`, `/relations`, `/blocks`, `/mutes`) | ✅ |
| Testes | `WordFilterTest`; `SafetyApiIT.blockWorksBothWaysOnFeedPostCommentsAndProfiles`, `.muteHidesOnlyForWhoMutedAndIsSilent`, `.filteredWordsHideCommentsOnMyPostsForEveryoneButTheirAuthor`, `.defaultFilterIsOnAndLockedForMinorsAndOptionalForAdults`; e2e `moderacao.spec.ts` (perfil e Privacidade, com axe) | ✅ |
| Tela | `ProfileSafetyActions` no perfil, `SafetySection` em Configurações → Privacidade, `/seguranca` | ✅ |
| Pendências | menção bloqueada para quem não segue e comentário só de seguidores aprovados para menor de 16 (dependem de seguir, RF21); "modo proteção" e alerta à moderação em rajada de comentários hostis; desfoque de rosto e placa e "Minha imagem foi publicada" (RC-SEG-10); notificação e contranotificação de direito autoral (RC-SEG-11); notificações respeitando o bloqueio (RF21) | ⏳ |
