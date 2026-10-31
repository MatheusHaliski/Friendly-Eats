---
rf: RF8
titulo: Avaliar com estrelas (1 a 5) e nota agregada
status: parcial
cards: [FE-14]
paths:
  - src/main/java/com/friendlyeats/domain/model/Review.java
  - src/main/java/com/friendlyeats/domain/model/ReviewRevision.java
  - src/main/java/com/friendlyeats/domain/taxonomy/StarRating.java
  - src/main/java/com/friendlyeats/domain/taxonomy/ReviewDimension.java
  - src/main/java/com/friendlyeats/domain/taxonomy/VisitMode.java
  - src/main/java/com/friendlyeats/application/establishment/ReviewService.java
  - src/main/java/com/friendlyeats/application/establishment/RatingCalculator.java
  - src/main/java/com/friendlyeats/infrastructure/persistence/postgres/ReviewRepository.java
  - src/main/java/com/friendlyeats/infrastructure/persistence/postgres/ReviewRevisionRepository.java
  - frontend/apps/web/components/place/MyReviewArea.tsx
  - frontend/apps/web/components/place/ReviewForm.tsx
  - frontend/apps/web/components/place/ReviewList.tsx
  - frontend/apps/web/components/StarRatingInput.tsx
  - frontend/apps/web/components/Stars.tsx
  - frontend/apps/web/components/RatingSummary.tsx
  - frontend/apps/web/lib/review.ts
  - frontend/apps/web/lib/format.ts
---

# RF8 · Avaliar com estrelas (1 a 5) e nota agregada

> **Como** comensal, criador, chef ou comunidade, **quero** dar de 1 a 5 estrelas a um lugar, dizer como foi (no local,
> retirada ou entrega) e quanto gastei **para** ajudar outras pessoas. **Como** visitante, **quero** uma nota que não
> engane: sem média com poucas avaliações e sem contar quem não é cliente.

| | |
|---|---|
| **Consumidor (CDC)** | A nota exibida vem só das avaliações e é explicável (art. 6º, III): média ponderada por recência, com os pesos publicados na taxonomia |
| **Marco Civil** | A avaliação nasce publicada, sem censura prévia (art. 19); a moderação vem depois (RF13) |
| **Conflito de interesse** | `RULE_NO_INSIDER`: contas de organização e o dono do lugar não avaliam |
| **Taxonomia** | [`TAXONOMIA_FRIENDLY_EATS.md`](../TAXONOMIA_FRIENDLY_EATS.md#10-estrelas), seção 10, e o preço pelas avaliações (9.3) |
| **Status** | 🟡 Parcial: avaliação única editável com histórico, dimensões por modo, nota agregada, score de ranking, vínculo declarado, sinais de fraude, congelamento e moderação ([RF13](../RF13/README.md)) prontos; verificação de visita e resposta do dono ainda não |

## Critérios de aceite

1. **Uma avaliação por pessoa e lugar** (`UNIQUE (establishment_id, author_id)`).
   `PUT /api/v1/establishments/{id}/reviews/me` exige sessão e `X-XSRF-TOKEN`:
   - responde `201` quando cria e `200` quando substitui;
   - ao substituir, a versão anterior vai para `review_revision` (nota, modo, dimensões, gasto, texto, `valid_from` e
     `replaced_at`), `edit_count` sobe e a resposta traz `edited: true`.
2. **Quem não avalia** (`403`): as contas de organização `ESTABELECIMENTO`, `VAREJO` e `OFICIAL`, e o **dono** do
   lugar. A conta precisa estar **`ACTIVE`**; com o e-mail não confirmado, o resultado é `403`.
3. **O lugar precisa estar visível** para quem avalia ([RF6](../RF6/README.md)): página fora do ar, ou 18+ para
   menor de idade, dá `404`.
4. **Nota inteira de 1 a 5 e obrigatória** (`STAR_1` Péssimo … `STAR_5` Excelente). Não existe nota 0 nem meia estrela.
5. **`visitMode`** é obrigatório (`DINE_IN`, `TAKEOUT` ou `DELIVERY`) e decide as dimensões aceitas, também inteiras
   de 1 a 5 e opcionais:

   | Dimensão | `DINE_IN` | `TAKEOUT` | `DELIVERY` |
   |---|---|---|---|
   | `COMIDA` | ✔ | ✔ | ✔ |
   | `ATENDIMENTO` | ✔ | ✔ | — |
   | `AMBIENTE` | ✔ | — | — |
   | `CUSTO_BENEFICIO` | ✔ | ✔ | ✔ |
   | `ENTREGA` | — | — | ✔ |

   Uma dimensão fora do modo dá `422`. As dimensões explicam a nota, mas não a compõem.
6. Opcionais: `spentPerPersonCents` (de R$ 0 a R$ 5.000), que alimenta a faixa de preço (mediana com 5 relatos ou mais
   em 12 meses, RF6), e `body` (até 2.000 caracteres). Todos os erros voltam juntos (`422`).
   **Vínculo declarado** (`disclosure`, [RF13](../RF13/README.md)): `NONE`, `COURTESY` ou `INVITATION` (viram
   `REVIEW_INCENTIVIZED`, peso 0,6 e selo escrito) ou `RELATIONSHIP` (vira `REVIEW_CONFLICT`, fora da nota); ausente
   mantém o que estava; outro valor dá `422`. O app pergunta "Você tem vínculo com o lugar?".
6a. **Integridade** ([RF13](../RF13/README.md)): a criação guarda o HMAC do IP (`origin_hash`, nunca o IP) e passa pelo
   filtro antifraude. Rajada de contas novas no mesmo lugar ou contas diferentes da mesma origem põem as envolvidas em
   `UNDER_REVIEW` (a resposta já traz `status: UNDER_REVIEW`). Com o lugar **congelado** (pico suspeito ou decisão da
   moderação), avaliação nova dá `409` com a data de fim; editar a própria continua.
7. Avaliação **removida pela moderação** não é reescrita: o `PUT` dá `409`.
8. **`DELETE /api/v1/establishments/{id}/reviews/me`** apaga a própria avaliação (`204`), com as versões (`ON DELETE
   CASCADE`). Sem avaliação, `404`. Avaliação `REMOVED` não é apagada pelo autor (`409`): apagar e avaliar de novo
   driblaria a moderação. `GET …/reviews/me` devolve a própria avaliação (`200`) ou `404`.
9. **`GET /api/v1/establishments/{id}/reviews?page&size`** é público, com a mesma regra de visibilidade e de 18+ da
   página (`401` sem sessão em lugar 18+, `404` para menor). Lista as mais recentes primeiro, com `size` de 1 a 50
   (padrão 20), `total` e a nota agregada.
10. **Só contam** as avaliações `PUBLISHED` de autores com conta `ACTIVE`. Com a conta suspensa ou em carência de
    exclusão ([RF5](../RF5/README.md)), as avaliações dessa pessoa saem do agregado e da lista sem ser apagadas, e
    voltam se a conta for reativada.
11. **Nota agregada** (`rating` na página e na lista):
    - `RATING_NONE` (0 avaliações): nota `null`, nunca 0, com o texto "Sem avaliações ainda";
    - `RATING_FEW` (1 a 4): sem média, só a contagem ("3 avaliações, ainda sem nota média");
    - `RATED` (5 ou mais): média com uma casa e vírgula ("4,3"), contagem, histograma de 5 a 1 e a média de cada
      dimensão que tenha 5 notas ou mais.
12. **Fórmulas** (`friendly-eats.rating`):
    - peso `w = confiança × recência`, com `recência = max(0,25 ; 0,5^(idade em dias / 365))`, contada da última edição;
    - confiança 0,6 em toda avaliação na v1 (`REVIEW_UNVERIFIED`);
    - média exibida `Σ w·r / Σ w`;
    - score de ranking bayesiano `(m·C + Σ w·r) / (m + Σ w)`, com m = 10 e C = 4,2. Ele é calculado, mas só vai
      ordenar a busca no RF10.
13. **Estados da avaliação:** `PUBLISHED`; `UNDER_REVIEW` (sinal antifraude ou criança em risco) e `REMOVED` (decisão
    da moderação) ficam fora da lista e do agregado ([RF13](../RF13/README.md)). A view traz `status`, que só difere de
    `PUBLISHED` para o próprio autor (`GET …/reviews/me`), que vê "Em análise" ou "Removida pela moderação".
14. A eliminação da conta ([RF5](../RF5/README.md)) apaga as avaliações que ela escreveu, e a do dono apaga as
    avaliações dos lugares dele (RF6).

## Diagramas

[Casos de uso](casos-de-uso.md) · [Atividades](atividades.md) · [Classes](classes.md) · [Componentes](componentes.md) ·
[Sequência](sequencia.md) · [Estados](estados.md)

## Rastreabilidade

| Camada | Artefato | Situação |
|---|---|---|
| Domínio | `Review` (`write`, `Status`, `Trust` com os pesos, `Disclosure` e `declare`, `originHash`, `putUnderReview`/`removeByModeration`/`restoreByModeration` do RF13), `ReviewRevision.of`, `StarRating` (âncoras 1 a 5), `ReviewDimension.appliesTo`, `VisitMode` | ✅ |
| Regras | `ReviewService.upsertMine/deleteMine/mine/list` (organizações, dono, conta ativa, `REMOVED`, congelamento, vínculo, `ReviewIntegrityService.afterNewReview` do RF13), `EstablishmentRules.review` (nota, modo, dimensões, gasto), `RatingCalculator.summarize` (estados, média ponderada, histograma, dimensões, score bayesiano) | ✅ |
| Persistência | `V2__estabelecimento_e_avaliacao.sql`: `review` (CHECKs, único por pessoa e lugar, `ON DELETE CASCADE` a partir do estabelecimento) e `review_revision`; `ReviewRepository.findCounting/pageCounting/deleteAllWrittenBy`, `ReviewRevisionRepository` | ✅ |
| Busca | `ReviewRepository.findCountingIn` traz as avaliações que contam de vários lugares de uma vez para os cards; o score bayesiano ordena a busca por relevância ([RF10](../RF10/README.md)), recalculado no SQL com a mesma fórmula | ✅ |
| API | `PUT`, `GET` e `DELETE /api/v1/establishments/{id}/reviews/me`; `GET /api/v1/establishments/{id}/reviews` (público no `SecurityConfig`, [RF4](../RF4/README.md)) | ✅ |
| Configuração | `friendly-eats.rating.min-display` (5), `prior-weight` (10), `prior-mean` (4.2), `half-life-days` (365), `recency-floor` (0.25) | ✅ |
| Testes | `TaxonomyRulesTest.starsAreIntegersAndDimensionsOnlyWhereTheyWereLived`, `TaxonomyRulesTest.ratingIsNoneThenFewThenAWeightedAverageAndRankingIsBayesian`; `EstablishmentApiIT.reviewsAreOnePerPersonEditableAndFeedTheRatingAndThePrice`, `EstablishmentApiIT.organizationsAndOwnersDoNotReviewAndBarsAreForAdults`, `EstablishmentApiIT.accountEliminationRemovesReviewsAndOwnedEstablishments` | ✅ |
| Moderação | `UNDER_REVIEW`, `REMOVED`, denúncia, contestação do dono, sinais de fraude, alerta ao consumidor e congelamento no [RF13](../RF13/README.md); `ModerationApiIT.burstOfNewAccountsGoesUnderReviewAndFreezesNewReviews`, `.declaredCourtesyLowersTheWeightAndIsShown` | ✅ |
| Pendências | avaliação verificada por check-in, nota fiscal (NFC-e) ou pedido, com confiança 1,0; resposta pública do dono (`RULE_OWNER_REPLY`, RF12); C por categoria e município calculado em job diário; página "Como calculamos as notas" | ⏳ |
| Tela | app web: formulário Avaliar (rádio de 5 estrelas rotulado, modo, dimensões do modo, gasto em reais, texto, vínculo com o lugar), editar e apagar a própria, "Em análise" e "Removida" para o autor, selo escrito do vínculo, aviso de avaliações pausadas no lugar congelado, histograma e "4,3 de 5 · 6 avaliações", lista paginada com o menu "…" (denunciar; contestar, para o dono); e2e `lugar.spec.ts` e `moderacao.spec.ts` com axe | ✅ |
