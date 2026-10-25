---
rf: RF7
titulo: Classificar estabelecimento · culinária, formato, abordagem e estilos
status: parcial
cards: [FE-10, FE-13]
paths:
  - src/main/java/com/friendlyeats/domain/taxonomy/Cuisine.java
  - src/main/java/com/friendlyeats/domain/taxonomy/CuisineMacro.java
  - src/main/java/com/friendlyeats/domain/taxonomy/FoodFormat.java
  - src/main/java/com/friendlyeats/domain/taxonomy/FoodFamily.java
  - src/main/java/com/friendlyeats/domain/taxonomy/Approach.java
  - src/main/java/com/friendlyeats/domain/taxonomy/DietaryStyle.java
  - src/main/java/com/friendlyeats/domain/taxonomy/CuisineChip.java
  - src/main/java/com/friendlyeats/application/establishment/TaxonomyCatalog.java
  - src/main/java/com/friendlyeats/api/TaxonomyController.java
---

# RF7 · Classificar estabelecimento: culinária, formato, abordagem e estilos

> **Como** dono, **quero** dizer de qual tradição é a minha comida, o que eu vendo, o jeito da cozinha e para quem
> ela serve **para** aparecer nos filtros certos e com o chip certo no card. **Como** app, **quero** ler os
> vocabulários de uma só fonte **para** montar formulários e filtros sem código fixo.

| | |
|---|---|
| **Princípio** | Eixos ortogonais: a culinária diz *de onde*, o formato *o quê*, a abordagem *o jeito* e o estilo *para quem serve*. "Vegano" nunca é culinária, e "fusion" nunca é culinária ([taxonomia, seção 1](../TAXONOMIA_FRIENDLY_EATS.md#1-princípios)) |
| **Base legal (LGPD)** | O estilo alimentar descreve a **oferta** do estabelecimento (pessoa jurídica) e não é dado pessoal. Ligado a uma pessoa, é sensível (Art. 11) e fica fora deste RF ([RF3](../RF3/README.md)) |
| **Códigos** | Estáveis, em MAIÚSCULAS ASCII, nunca renomeados nem reaproveitados; o rótulo pt-BR pode mudar |
| **Status** | 🟡 Parcial: vocabulários, regras, chip, `PUT` da classificação e `GET /api/v1/taxonomy` prontos; filtros da busca, alergênicos por item e telas ainda não |

## Critérios de aceite

1. **Onde se classifica.** No `POST /api/v1/establishments` (campo `classification`, [RF6](../RF6/README.md)), no
   `PATCH` e no `PUT /api/v1/establishments/{id}/classification`, que troca a classificação inteira. O `PUT` é só do
   dono: exige sessão, `X-XSRF-TOKEN` e conta `ESTABELECIMENTO` ou `VAREJO` ativa (`403`); para os outros, `404`.
   Resposta: `200` com a página.
2. **Vocabulários:** 56 culinárias em 7 macrorregiões, 33 formatos em 8 famílias (a família não é selecionável), 5
   abordagens e 11 estilos alimentares, cada estilo com o nível `OFERECE` ou `EXCLUSIVO`.
3. **Cardinalidade.** Culinária: 1 principal e até 2 secundárias. Formato: 1 principal e até 4 secundários. As
   secundárias exigem a principal do mesmo eixo, a principal não se repete nelas e um código repetido é recusado.
4. **Regra de ancestral.** Cada culinária específica tem um pai (`BAIANA` → `NORDESTINA` → `BRASILEIRA`,
   `ITALIANA` → `EUROPEIA`). Marcar uma culinária junto com um ancestral dela é recusado ("Baiana já inclui
   Nordestina: deixe só Baiana."). Irmãs e primas, como `BAIANA` + `PARAENSE`, são aceitas.
5. **Pelo menos culinária ou formato** principal. Ficam **dispensados** os grupos `VAREJO` e `POLO` (pelo tipo
   principal) e a abordagem `VARIADA`. Trocar o tipo no `PATCH` revalida a classificação atual no grupo novo.
6. **Abordagem** (0 ou 1, padrão `TRADICIONAL`):
   - `CONTEMPORANEA` exige culinária principal ("Brasileira contemporânea");
   - `FUSION` exige 2 culinárias ou mais, **ou** uma principal que seja a genérica de uma macro (`ASIATICA` +
     `FUSION` = asian fusion);
   - `VARIADA` deixa a culinária vazia;
   - `CASEIRA` não tem exigência extra.
7. **Chip** (8 grupos visuais, derivado e nunca escolhido à mão). A primeira regra que se aplica vence:
   1. `VEGANO` ou `VEGETARIANO` com `EXCLUSIVO` → `VEGETAL`;
   2. a macro da culinária principal (`BRASIL` → `BRASILEIRA`, `EUROPA` → `EUROPEIA`, `ASIA` → `ASIATICA`, as duas
      Américas → `AMERICAS`, Oriente Médio e África → `ORIENTE_MEDIO_AFRICA`);
   3. a família do formato principal (lanches e salgados → `LANCHES_PIZZA`; doces, padaria e bebidas → `DOCES_CAFE`;
      refeições e carnes → `BRASILEIRA`; orientais → `ASIATICA`);
   4. sem nada disso, sem chip (`null`).

   O chip vem com o ícone Lucide e as cores dos dois temas ([`DESIGN_SYSTEM.md`](../DESIGN_SYSTEM.md)).
8. **A culinária sugerida** pelo formato (`PIZZA` → `ITALIANA`) é só um padrão para a tela e nunca é aplicada sozinha.
9. **`GET /api/v1/taxonomy`** é público (sem sessão) e tem `Cache-Control: max-age=3600, public`. Devolve todos os
   vocabulários com código e rótulo: macros, culinárias (com macro, pai e região IBGE), famílias, formatos (com
   família, culinária sugerida e `alcoholic`), abordagens, estilos e níveis, grupos e tipos (com 18+ e CNAE típico),
   níveis de preço com a faixa em reais da escala vigente, estrelas, `visit_mode`, dimensões por modo e chips.
10. Todos os erros voltam juntos (`422` com `errors[]`). Código desconhecido aponta para `GET /api/v1/taxonomy`.

## Diagramas

[Casos de uso](casos-de-uso.md) · [Atividades](atividades.md) · [Classes](classes.md) · [Componentes](componentes.md) ·
[Sequência](sequencia.md) · [Estados](estados.md)

## Rastreabilidade

| Camada | Artefato | Situação |
|---|---|---|
| Domínio | `Cuisine` (56, pai, `isAncestorOf`, `isMacroGeneric`, região IBGE), `CuisineMacro` (7), `FoodFormat` (33, família, culinária sugerida, `isAlcoholic`), `FoodFamily` (8), `Approach` (5), `DietaryStyle` (11, `Level`), `CuisineChip` (8, `of`) | ✅ |
| Regras | `EstablishmentRules.classification` e `Establishment.classify` (no [RF6](../RF6/README.md)); `EstablishmentService.classify` (`PUT`) | ✅ |
| Persistência | `establishment.primary_cuisine`, `secondary_cuisines`, `primary_format`, `secondary_formats`, `approach`, `dietary_offers` e as colunas geradas `cuisines_all` e `formats_all` com índice GIN (V2) | ✅ |
| API | `PUT /api/v1/establishments/{id}/classification`; `classification` no `POST` e no `PATCH`; `GET /api/v1/taxonomy` (`TaxonomyController`, `TaxonomyCatalog`), público no `SecurityConfig` ([RF4](../RF4/README.md)) | ✅ |
| Testes | `TaxonomyRulesTest.vocabulariesHaveTheResearchedSizeAndEveryCuisineDescendsFromItsMacroGeneric`, `TaxonomyRulesTest.classificationRejectsAncestorsAndChecksTheApproach`; `EstablishmentApiIT.establishmentPageIsPublicOnlyWithTheOwnerSealAndHidesTheHomeKitchenStreet` (cadastro com classificação) | ✅ |
| Pendências | filtro por culinária com as descendentes, por região IBGE, formato e estilo (RF10); cardápio e alergênicos por item, com `CONTEM` e `PODE_CONTER` (RF9); tags de prato; moderação do uso de `VARIADA` com culinária dominante (RF13); aviso de contaminação cruzada em sem glúten e sem lactose (tela) | ⏳ |
| Tela | seletor com a hierarquia, sugestão de culinária pelo formato e chip no card | ⏳ |
