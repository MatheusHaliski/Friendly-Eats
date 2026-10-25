---
rf: RF9
titulo: Publicar conteúdo · posts de comida com foto, taxonomia e alergênicos
status: parcial
cards: [FE-15]
paths:
  - src/main/java/com/friendlyeats/domain/model/Post.java
  - src/main/java/com/friendlyeats/domain/model/PostKind.java
  - src/main/java/com/friendlyeats/domain/model/PostStatus.java
  - src/main/java/com/friendlyeats/domain/model/AgeRating.java
  - src/main/java/com/friendlyeats/domain/taxonomy/Allergen.java
  - src/main/java/com/friendlyeats/application/post/PostPublishingService.java
  - src/main/java/com/friendlyeats/application/post/PostRules.java
  - src/main/java/com/friendlyeats/application/post/PostDrafts.java
  - src/main/java/com/friendlyeats/application/post/PostVocabulary.java
  - src/main/java/com/friendlyeats/api/PostPublishingController.java
  - src/main/java/com/friendlyeats/infrastructure/persistence/postgres/PostRepository.java
  - src/main/resources/db/migration/V3__posts.sql
---

# RF9 · Publicar conteúdo: posts de comida

> **Como** pessoa, criador, chef, comunidade, estabelecimento, marca ou órgão oficial, **quero** publicar a foto de
> um prato, uma receita, uma pergunta, o prato do dia, uma promoção, um evento ou um alerta sanitário, ligado ao lugar e
> à taxonomia, **para** mostrar o que se come, avisar quem tem alergia e ser encontrado no feed.

| | |
|---|---|
| **Origem** | Estrutura de posts da Orange Social (RF29 e RF24 de lá: ciclo de vida, foto por URL pré-assinada, PATCH parcial), adaptada aos 7 tipos de conteúdo de comida da pesquisa 04 (seção 1.3) |
| **Base legal (LGPD)** | Art. 7º, V (execução do serviço). A foto sai sem EXIF nem GPS (Art. 6º, III). O estilo alimentar e os alergênicos descrevem o **prato**, nunca a pessoa: não viram atributo do perfil ([taxonomia, 6.3](../TAXONOMIA_FRIENDLY_EATS.md#63-lgpd-quando-o-atributo-ligado-a-uma-pessoa-é-sensível)) |
| **Consumidor (CDC)** | Promoção de criador leva o chip **Publicidade** (art. 36); o "de/por" precisa ter o "de" maior que o "por" (art. 37); o prato do dia mostra o preço cobrado (art. 31) |
| **ECA Digital** | Post de bebida alcoólica é 18+ sozinho; menor de 18 não publica 18+; menor de 16 usa só molduras do catálogo ([RF16](../RF16/README.md)) |
| **Anvisa** | Alergênicos da RDC 26/2015 (RDC 727/2022, Anexo III), autodeclarados, sempre com o aviso de contaminação cruzada; alerta sanitário só de conta Oficial, com a fonte oficial (RDC 655/2022) |
| **Status** | 🟡 Parcial: API, regras dos 7 tipos, foto sem EXIF, alergênicos, 18+, expiração, moldura e testes prontos; vídeo, cardápio por item, lista/guia e check-in, rascunho pela API e as telas ainda não |

## Critérios de aceite

1. **Quem publica.** `POST /api/v1/posts` exige sessão (`401`), `X-XSRF-TOKEN` e conta `ACTIVE` (`403`).
   Organizações (`ESTABELECIMENTO`, `VAREJO`, `OFICIAL`) só publicam com o selo `VERIFIED` (`403` "ainda está em
   verificação"). Cada tipo tem os seus autores (`PostKind.denialFor`, `403`):

   | Tipo (`kind`) | Quem publica | Obrigatório | Sai do feed |
   |---|---|---|---|
   | `FOTO_PRATO` | qualquer conta ativa | foto + `altText` | nunca |
   | `RECEITA` | qualquer conta ativa | `recipe.ingredients` e `recipe.steps` | nunca |
   | `PERGUNTA` | qualquer conta ativa, menos `OFICIAL` | a pergunta no título | nunca |
   | `PRATO_DO_DIA` | só `ESTABELECIMENTO` verificada, sobre um lugar **dela** e aberto | `establishmentId`, `priceCents` | às 23h59 de `America/Sao_Paulo` do dia |
   | `PROMOCAO` | `ESTABELECIMENTO` ou `VAREJO` verificados, sobre um lugar seu; ou `CRIADOR` com `advertising: true` | `startsAt`, `endsAt` e o "de/por" (`priceFromCents` > `priceToCents`) **ou** `discountPercent` (1 a 90) | depois de `endsAt` |
   | `EVENTO` | `ESTABELECIMENTO`, `VAREJO`, `COMUNIDADE` ou `CHEF` | `startsAt`, `endsAt` e `priceCents` **ou** `free: true` | depois de `endsAt` |
   | `ALERTA_SANITARIO` | só `OFICIAL` verificada (`canPublishFoodAlerts`) | `alert.product`, `brand`, `lots`, `reason` e `sourceUrl` https | nunca (feed `ALERT`) |

   Lugar de outra conta no prato do dia ou na promoção do dono dá `403`; sem `establishmentId`, `422`.
2. **Campos de outro tipo são recusados** (`"discountPercent" não se aplica a Foto de prato`, `422`). Todos os erros
   voltam juntos em `errors[]`.
3. **Foto.** Opcional, obrigatória em `FOTO_PRATO`. A chave vem de `POST /api/v1/media/uploads`
   ([RF2](../RF2/README.md)); a API confere o dono, limpa a foto (`ImageSanitizer`, `Fit.POST`: sem EXIF/GPS, lado
   maior até 2048 px, orientação aplicada) e grava a versão pública em `posts/`. O original sai do bucket depois do
   commit. Chave de outra conta, expirada ou já usada: `422` com a mesma mensagem. Com foto, `altText` (até 500
   caracteres) é obrigatório; sem foto, ele é recusado.
4. **Ligação com a taxonomia.** `establishmentId` precisa ser um lugar **visível para o autor** (as regras do
   [RF6](../RF6/README.md), inclusive 18+: um menor não cita um bar); `cuisine` e `foodFormat` são códigos da
   [taxonomia](../TAXONOMIA_FRIENDLY_EATS.md); `dietaryStyles` até 8 estilos.
5. **Alergênicos** (`allergens: {"LEITE": "CONTEM", "AMENDOIM": "PODE_CONTER"}`): só os 21 códigos **vigentes** da
   seção 6.2 da taxonomia; os propostos em 2025 (`GERGELIM`, `AIPO`…) estão reservados e são recusados. As
   advertências `LACTOSE` e `GLUTEN` não são alergênicos e vão em `warnings`. A página do post mostra o aviso
   `allergenNotice` ("Informação declarada por quem publicou…") sempre que há alergênico, advertência ou "sem
   glúten/lactose".
6. **Classificação etária** (`ageRating`: `LIVRE` ou `AGE_18`). Fica `AGE_18` sozinho quando o formato é alcoólico
   (`CERVEJA`, `VINHO`, `COQUETEIS_DESTILADOS`) ou o lugar tem foco em bebida alcoólica. Menor de 18 não publica 18+
   (`422`). O alerta sanitário é sempre `LIVRE` (um recall de cachaça precisa chegar a todos). Quem vê o post 18+ está
   no [RF14](../RF14/README.md).
7. **Moldura** (`background`, [RF16](../RF16/README.md)): objeto opcional validado pelo `BackgroundValidator` e gravado
   na forma canônica (só ids do catálogo); `null` = moldura padrão. Alerta sanitário com moldura dá `422` (também há
   um CHECK na V3).
8. **Ciclo de vida** (o mesmo da Orange): `DRAFT → PUBLISHED ⇄ ARCHIVED → REMOVED`. A criação publica direto.
   - `PATCH /api/v1/posts/{id}`: chave ausente mantém, `null` limpa; o resultado passa pelas mesmas regras da criação.
     O tipo não muda (`422`). Trocar `mediaObjectKey` grava a foto nova e apaga a anterior do bucket depois do commit.
     Um `endsAt` novo precisa estar no futuro; o prato do dia continua expirando no dia em que foi publicado.
   - `POST /{id}/archive` (só de `PUBLISHED`, senão `409`): sai do feed e do "em alta", a página continua.
   - `POST /{id}/restore` (só de `ARCHIVED`, senão `409`): volta ao feed.
   - `DELETE /{id}` (`204`): vira `REMOVED`, o título vira "Conteúdo removido", o conteúdo some e a foto sai do bucket
     depois do commit; o post responde `404` para todos.
   - Post de outra pessoa, apagado ou inexistente: `404`, sem dizer qual.
9. **Vocabulário do formulário.** `GET /api/v1/posts/vocabulary` (público) lista os tipos com quem publica e os campos,
   os alergênicos com a situação, as advertências, os níveis, as classificações e os canais; com sessão, traz em
   `publishableKinds` os tipos que a conta pode publicar.
10. A eliminação da conta ([RF5](../RF5/README.md)) apaga o conteúdo dos posts e as fotos.

## Contrato

- **Pedido** (`PostDrafts.PostDraft`): `kind`, `title`, `body`, `mediaObjectKey`, `altText`, `establishmentId`,
  `cuisine`, `foodFormat`, `dietaryStyles[]`, `allergens{}`, `warnings[]`, `ageRating`, `advertising`, `startsAt`,
  `endsAt` (ISO-8601), `priceCents`, `priceFromCents`, `priceToCents`, `discountPercent`, `free`,
  `recipe{ingredients[], steps[], servings, prepMinutes}`, `alert{product, brand, lots[], reason, sourceUrl}`, `city`,
  `background{…}`.
- **Resposta**: `201` (criar) ou `200` com a página do post (`PostViews.PostDetailView`, descrita no
  [RF14](../RF14/README.md)), com `background` já resolvido (`BackgroundService.BackgroundView`).

## Diagramas

[Casos de uso](casos-de-uso.md) · [Atividades](atividades.md) · [Classes](classes.md) · [Componentes](componentes.md) ·
[Sequência](sequencia.md) · [Estados](estados.md)

## Rastreabilidade

| Camada | Artefato | Situação |
|---|---|---|
| Domínio | `Post` (ciclo de vida, `remove` apaga o conteúdo, `isExpired`, `acceptsLikesAndComments`), `PostKind` (7 tipos, ícone, borda, feed, `denialFor`, `needsOwnedPlace`, `expires`), `PostStatus`, `AgeRating`, `Allergen` (26 códigos, `Kind`, `Status`, `Level`, `NOTICE`) | ✅ |
| Regras | `PostRules.kind/validate/ageRating/expiresAt` (campos por tipo, preço, vigência, receita, alerta, alergênicos, 18+), `PostPublishingService` (conta ativa, selo, lugar visível e próprio, foto, moldura, PATCH pela mesma regra) | ✅ |
| Persistência | `V3__posts.sql`: tabela `post` com CHECKs (tipo × feed, foto com `altText`, preços, desconto, vigência, JSON, alerta sem moldura) e índices do feed, do autor e do lugar; `PostRepository` | ✅ |
| API | `POST /api/v1/posts`, `PATCH /api/v1/posts/{id}`, `POST /{id}/archive`, `POST /{id}/restore`, `DELETE /{id}`, `GET /api/v1/posts/vocabulary` (`PostPublishingController`) | ✅ |
| Integrações | `MediaUploads.claimImage` e `discardAfterCommit` ([RF2](../RF2/README.md)), `EstablishmentService.requireVisible` ([RF6](../RF6/README.md)), `BackgroundValidator` ([RF16](../RF16/README.md)), `TrendingService` ([RF14](../RF14/README.md)) | ✅ |
| Testes | `PostRulesTest` (`eachKindHasItsPublishersAndOrganizationsWaitForTheSeal`, `dishPhotoNeedsThePhotoWithAltTextAndFieldsOfOtherKindsAreRefused`, `allergensFollowTheCurrentAnvisaListAndWarningsHaveTheirOwnField`, `alcoholicFormatOrBarMakesThePostAdultsOnlyButTheAlertIsAlwaysForEveryone`, `promotionNeedsPeriodAndFromToOrDiscountAndTheCreatorNeedsTheAdvertisingChip`, `eventIsPaidOrFreeTheDailyDishHasAPriceAndTheAlertNeedsAnOfficialHttpsSource`, `dailyDishExpiresAt2359InSaoPauloOnThePublicationDay`, `recipeNeedsIngredientsAndSteps`); `PostDomainTest` (transições, remoção, alerta, expiração); `PostApiIT.dishPhotoGoesStraightToTheBucketWithoutExifAndTheOldPhotoLeavesIt` (Garage real), `PostApiIT.eachKindHasItsPublishersAndTheOwnersPostsAreAboutTheirOwnPlace`, `PostApiIT.authorEditsArchivesRestoresAndDeletesOwnPost`, `PostApiIT.frameFromTheCatalogIsValidatedAndResolvedButAlertsNeverHaveOne`, `PostApiIT.vocabularyListsKindsAllergensAndWhatTheViewerCanPublish`; `SchemaConsistencyTest` | ✅ |
| Dados de exemplo | `DevSeed` (perfil `dev`): um post de cada tipo e um alerta da conta Oficial verificada "Vigilância Sanitária de Exemplo", com curtidas e comentários | ✅ |
| Pendências | vídeo (falta o processamento sem metadados); cardápio por item com alergênicos e `PRICE_SRC_MENU`; lista/guia, check-in e avaliação como post (pesquisa 04); rascunho pela API; chef vinculado publicando prato do dia; hash perceptual contra repostagem; moderação e denúncia (RF13); fila prioritária do alerta e encerramento pela fonte | ⏳ |
| Tela | formulário de publicação por tipo, com o aviso de alergênicos | ⏳ |
