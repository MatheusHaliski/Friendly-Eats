---
rf: RF6
titulo: Cadastrar estabelecimento · tipo, local e preço declarado
status: parcial
cards: [FE-12]
paths:
  - src/main/java/com/friendlyeats/domain/model/Establishment.java
  - src/main/java/com/friendlyeats/domain/taxonomy/EstablishmentType.java
  - src/main/java/com/friendlyeats/domain/taxonomy/EstablishmentGroup.java
  - src/main/java/com/friendlyeats/domain/taxonomy/Uf.java
  - src/main/java/com/friendlyeats/domain/taxonomy/GeoPrecision.java
  - src/main/java/com/friendlyeats/domain/taxonomy/AddressVisibility.java
  - src/main/java/com/friendlyeats/domain/taxonomy/PriceLevel.java
  - src/main/java/com/friendlyeats/application/establishment/EstablishmentService.java
  - src/main/java/com/friendlyeats/application/establishment/EstablishmentRules.java
  - src/main/java/com/friendlyeats/application/establishment/EstablishmentDrafts.java
  - src/main/java/com/friendlyeats/application/establishment/EstablishmentViews.java
  - src/main/java/com/friendlyeats/application/establishment/PriceScale.java
  - src/main/java/com/friendlyeats/api/EstablishmentController.java
  - src/main/java/com/friendlyeats/infrastructure/persistence/postgres/EstablishmentRepository.java
  - src/main/resources/db/migration/V2__estabelecimento_e_avaliacao.sql
  - frontend/apps/web/app/lugares/*/page.tsx
  - frontend/apps/web/components/place/PlaceView.tsx
  - frontend/apps/web/components/PriceTag.tsx
---

# RF6 · Cadastrar estabelecimento: tipo, local e preço declarado

> **Como** dono de um restaurante, bar, loja ou negócio informal, **quero** cadastrar o meu lugar com o tipo, o
> endereço e quanto se gasta por pessoa **para** ser encontrado e avaliado, com a página no ar só depois de o selo da
> minha conta ser conferido.

| | |
|---|---|
| **Base legal (LGPD)** | Os dados do estabelecimento não são dado pessoal (Art. 5º, I). A exceção é a cozinha caseira com CPF, em que o endereço é a casa do titular: o público vê só o bairro e um ponto arredondado (Art. 6º, III, necessidade) |
| **Consumidor (CDC)** | A faixa de preço é informação ao consumidor (art. 6º, III). Com 5 relatos ou mais, a faixa vem das avaliações e não do dono, o que dificulta a faixa enganosa (art. 37, §1º) |
| **Termos de Uso / ECA Digital** | Página de tipo com foco em bebida alcoólica é só para maiores de 18: o visitante precisa entrar e, para menor, ela não existe (`404`) |
| **Taxonomia** | [`TAXONOMIA_FRIENDLY_EATS.md`](../TAXONOMIA_FRIENDLY_EATS.md): conta dona (2.4), tipo e grupo (7), preço (9) e local (11) |
| **Status** | 🟡 Parcial: API, regras, página pública com selo, 18+, endereço protegido e preço derivado prontos; verificação automática de CNPJ e CNAE, moderação, busca e telas ainda não |

## Critérios de aceite

1. **Quem é dono.** `POST /api/v1/establishments` exige sessão (`401`) e `X-XSRF-TOKEN`. Só a conta `ESTABELECIMENTO`
   ou `VAREJO` cadastra; os outros tipos recebem `403` com a sugestão de avaliar ou publicar sobre o lugar. A conta
   precisa estar `ACTIVE` (e-mail confirmado); senão, `403`. Resposta: `201` com a página do dono.
2. **Negócio com CPF** (`type_details.informalFormat`, [RF1](../RF1/README.md)) tem um **único ponto**: o segundo
   cadastro dá `409`, com a orientação de cadastrar o negócio com CNPJ. A linha da conta é travada (`SELECT … FOR
   UPDATE`) antes da contagem, então dois cadastros simultâneos ficam em fila e o segundo também dá `409`. Os tipos
   ficam presos ao formato (R9):
   `AMBULANTE` → `AMBULANTE`, `FOOD_TRUCK` → `FOOD_TRUCK`, `COZINHA_CASEIRA` → `COZINHA_ARTESANAL` ou `MARMITARIA`.
3. **Tipo.** 1 principal (obrigatório) e até 3 secundários distintos, sem repetir o principal (R1). Os 48 tipos ficam
   em 7 grupos, e o grupo (`type_group`) deriva do principal (R3). Conta `VAREJO` só cadastra tipo principal do grupo
   `VAREJO`, e conta `ESTABELECIMENTO` cadastra os demais. Um tipo `POLO` só é secundário de outro `POLO` (R4).
4. **Local.** UF pela sigla e município pelo **código IBGE de 7 dígitos**, que começa pelo código da UF (o município
   de outra UF é recusado); a cidade é obrigatória e o CEP, opcional, tem 8 dígitos. Latitude e longitude vêm juntas e
   dentro do Brasil. Com o pino do dono, a precisão é `GEO_PIN_CONFIRMED`; sem ele, `GEO_MUNICIPIO_CENTROID`.
5. **Visibilidade do endereço** (`addressVisibility`):
   - `ADDR_PUBLIC_FULL` é o padrão e exige a rua;
   - `ADDR_PUBLIC_BAIRRO` mostra UF, município, cidade e bairro, e o ponto vira o **centro da célula de 0,005°**
     (cerca de 550 m), igual em toda consulta, com `approximate: true` e `geoPrecision: GEO_BAIRRO_CENTROID`;
   - `ADDR_HIDDEN` mostra só UF, município e cidade, sem ponto;
   - a **cozinha caseira** nasce com `ADDR_PUBLIC_BAIRRO`, e `ADDR_PUBLIC_FULL` é recusado. A regra vale pelo cadastro
     **atual** da conta dona: se ela passar a ser `COZINHA_CASEIRA` depois, um `ADDR_PUBLIC_FULL` já gravado é
     mostrado ao público como `ADDR_PUBLIC_BAIRRO`;
   - o dono sempre vê o endereço completo.
6. **Preço declarado.** É o gasto por pessoa em centavos: o mínimo é obrigatório quando há máximo, os valores ficam
   entre R$ 0 e R$ 5.000, e o máximo não pode ser menor que o mínimo. `notApplicable` vale só nos grupos `VAREJO` e
   `POLO`, sem valores.
7. **Nível derivado** (`price` na página). A escala é R$ 58 × 0,7 / 1,4 / 2,8, arredondada para R$ 5: até R$ 40,
   R$ 40 a 80, R$ 80 a 160 e acima de R$ 160, com limites semiabertos. A primeira regra que se aplica vence:
   1. `notApplicable` → `PRICE_NA`;
   2. **5 ou mais relatos** de gasto nas avaliações que contam ([RF8](../RF8/README.md)), editadas nos últimos
      **12 meses** → nível da **mediana**, com origem `PRICE_SRC_REVIEWS` e a contagem;
   3. faixa declarada → nível do ponto médio (ou do mínimo, sem máximo), com origem `PRICE_SRC_DECLARED`;
   4. senão, `PRICE_UNKNOWN`.

   O `accessibleLabel` traz o texto completo ("Faixa de preço: moderado, R$ 40 a R$ 80 por pessoa, segundo 6
   avaliações").
8. **Página pública.** `GET /api/v1/establishments/{id}` não exige sessão. Para quem não é o dono, a página só existe
   com o status diferente de `REMOVED`, a **conta dona `ACTIVE`** e o **selo `VERIFIED`**. Fora isso, a resposta é
   `404`, sem dizer qual regra barrou. `CLOSED` continua visível. O dono sempre vê a própria página, com
   `published` e `publishBlockers` (a conta não está ativa, o selo está em conferência ou foi recusado, ou o lugar foi
   removido). Para os demais, os dois campos vêm nulos.
9. **18+.** Os 8 tipos com foco em bebida alcoólica (`BAR`, `BOTECO`, `GASTROBAR`, `BAR_COQUETELARIA`, `BAR_VINHOS`,
   `CERVEJARIA`, `VINICOLA_DESTILARIA` e `ADEGA`), quando são o **tipo principal**, marcam a página `adultsOnly`. O
   visitante anônimo recebe `401` com a mensagem "Entre na sua conta para ver: conteúdo de bebida alcoólica é só para
   maiores de 18." e quem tem menos de 18 recebe `404`. A mesma regra vale para a lista de avaliações (RF8).
10. **Editar.** `PATCH /api/v1/establishments/{id}` é só do dono (para os outros, `404`):
    - campo nulo não muda nada; tipos, local e preço são trocados por inteiro;
    - trocar o tipo revalida a classificação atual ([RF7](../RF7/README.md)) e o preço declarado no grupo novo (um polo
      com "não se aplica" que vira restaurante precisa informar o gasto por pessoa);
    - `status` aceita `ACTIVE` (aberto) e `CLOSED` (fechou de vez); qualquer outro valor dá `422`, e um lugar `REMOVED`
      pela moderação dá `409`.
11. `GET /api/v1/me/establishments` lista os lugares do dono, na ordem de cadastro.
12. Todos os erros de validação voltam juntos (`422` com `errors[]`), com o código desconhecido apontando para
    `GET /api/v1/taxonomy`.
13. A eliminação da conta dona ([RF5](../RF5/README.md)) apaga os estabelecimentos dela, e as avaliações deles saem
    junto (`ON DELETE CASCADE`).

## Diagramas

[Casos de uso](casos-de-uso.md) · [Atividades](atividades.md) · [Classes](classes.md) · [Componentes](componentes.md) ·
[Sequência](sequencia.md) · [Estados](estados.md)

## Rastreabilidade

| Camada | Artefato | Situação |
|---|---|---|
| Domínio | `Establishment` (`setTypes` com o grupo derivado, `setLocation`, `setDeclaredPrice`, `isAlcoholFocused`, `Status`), `EstablishmentType` (48 tipos, grupo, 18+, CNAE típico), `EstablishmentGroup`, `Uf` (código IBGE), `GeoPrecision`, `AddressVisibility`, `PriceLevel` (`Source`) | ✅ |
| Regras | `EstablishmentRules.types/location/price/publicCoordinate`, `PriceScale` (limites, `levelOf`, `rangeLabel`), `EstablishmentService` (dono, CPF com um ponto, `requireVisible`, `publishBlockers`, preço derivado, endereço por visibilidade) | ✅ |
| Persistência | `V2__estabelecimento_e_avaliacao.sql`: tabela `establishment` com CHECKs, `point` gerado (`geography(Point,4326)`) e índices GiST, GIN e trigrama; `EstablishmentRepository` (`countByOwnerId`, `deleteAllOwnedBy`) | ✅ |
| API | `POST /api/v1/establishments`, `GET` e `PATCH /api/v1/establishments/{id}`, `GET /api/v1/me/establishments`; GET público no `SecurityConfig` ([RF4](../RF4/README.md)). O `GET /api/v1/establishments` (busca) é do [RF10](../RF10/README.md), que monta os cards com `price`, `rating`, `location` e `classification` deste serviço: o card mostra o mesmo que a página | ✅ |
| Configuração | `friendly-eats.price.reference-brl` (58), `multipliers` (0.7,1.4,2.8), `round-brl` (5), `min-reports` (5) | ✅ |
| Testes | `TaxonomyRulesTest.vocabulariesHaveTheResearchedSizeAndEveryCuisineDescendsFromItsMacroGeneric`, `TaxonomyRulesTest.typesFollowTheAccountKindAndPolosAreNeverSecondary`, `TaxonomyRulesTest.locationUsesTheIbgeCodeKeepsPinsInBrazilAndHidesTheHomeKitchenStreet`, `TaxonomyRulesTest.priceScaleDoublesFromTheReferenceMealAndRoundsToFiveReais`; `EstablishmentApiIT.establishmentPageIsPublicOnlyWithTheOwnerSealAndHidesTheHomeKitchenStreet` (inclui 6 cadastros simultâneos com CPF e a conta que vira cozinha caseira depois), `EstablishmentApiIT.reviewsAreOnePerPersonEditableAndFeedTheRatingAndThePrice` (preço pelas avaliações), `EstablishmentApiIT.organizationsAndOwnersDoNotReviewAndBarsAreForAdults` (18+), `EstablishmentApiIT.accountEliminationRemovesReviewsAndOwnedEstablishments` | ✅ |
| Pendências | verificação automática de CNPJ e CNAE (BrasilAPI, matriz R8); moderação: `REMOVED` e revisão do selo (RF13); auditoria de troca de tipo principal (R10); facetas operacionais (seção 8 da taxonomia); preço pelo cardápio (`PRICE_SRC_MENU`) e cardápio por item (RF9); CEP pelo ViaCEP e geocodificação; contêiner `dentro_de → POLO` (R5) | ⏳ |
| Tela | app web: página `/lugares/[id]` (tipo, grupo, preço com origem, local com "Localização aproximada", selo do dono, aviso 18+); e2e `lugar.spec.ts` com axe. Formulário de cadastro do lugar com pino no mapa | ✅ página · ⏳ formulário |
