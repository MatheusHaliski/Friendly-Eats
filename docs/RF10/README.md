---
rf: RF10
titulo: Buscar e filtrar estabelecimentos (taxonomia, preço, estrelas e raio)
status: parcial
cards: [FE-16]
paths:
  - src/main/java/com/friendlyeats/application/establishment/EstablishmentSearch.java
  - frontend/apps/web/app/page.tsx
  - frontend/apps/web/components/explore/ExploreView.tsx
  - frontend/apps/web/components/explore/FiltersPanel.tsx
  - frontend/apps/web/components/EstablishmentCard.tsx
  - frontend/apps/web/components/Pagination.tsx
  - frontend/apps/web/lib/explore-params.ts
---

# RF10 · Buscar e filtrar estabelecimentos

> **Como** visitante ou pessoa logada, **quero** achar lugares por nome, culinária, tipo de comida, tipo de lugar,
> faixa de preço, nota mínima, estilo alimentar, município ou "perto de mim" **para** decidir onde comer, sem que a
> busca exponha a minha localização nem a casa de quem cozinha.

| | |
|---|---|
| **Taxonomia** | Os mesmos códigos de [`TAXONOMIA_FRIENDLY_EATS.md`](../TAXONOMIA_FRIENDLY_EATS.md): culinária (seção 3), formato (4), tipo e grupo (7), estilo (6), preço (9), estrelas (10) e local (11) |
| **LGPD** | A coordenada de quem busca é usada só na requisição (Art. 7º, V; Art. 6º, III): não é gravada nem vai para log. Lugar com endereço aproximado é medido até o ponto público arredondado, e a distância sai de 0,5 em 0,5 km |
| **ECA Digital / Termos** | Lugar com foco em bebida alcoólica não aparece para visitante sem login nem para menor de 18 |
| **Status** | 🟡 Parcial: filtros, raio, ordenações e cards prontos e testados; sugestões, cache no Valkey, C por categoria e município e mapa ainda não |

## Critérios de aceite

1. **`GET /api/v1/establishments`** é público (sem sessão). Parâmetros opcionais; os repetíveis aceitam também lista
   separada por vírgula (`cuisine=MINEIRA,BAIANA`):

   | Parâmetro | Regra |
   |---|---|
   | `q` | nome, sem diferenciar acento nem maiúscula (`sao min` acha "São Mineiro"); até 80 caracteres |
   | `chip` | repetível; o **chip do card** com a mesma derivação do `CuisineChip` (RF7): `VEGETAL` = vegano ou vegetariano `EXCLUSIVO`; os outros pela macro da culinária principal ou, sem culinária, pela família do formato |
   | `cuisine` | repetível; inclui as **descendentes** (`BRASILEIRA` traz `MINEIRA`, `BAIANA`…; `NORDESTINA` traz `BAIANA`) |
   | `format` · `type` · `group` | repetíveis; `type` vale para o tipo principal ou um secundário |
   | `price` | repetível, `PRICE_1`…`PRICE_4`, `PRICE_NA`, `PRICE_UNKNOWN`: o **nível exibido** (mediana de 5+ relatos ou o declarado, RF6) |
   | `minRating` | de 1 a 5; só lugares `RATED` (5+ avaliações) com a **média exibida** (uma casa) maior ou igual |
   | `dietary` | repetível; o lugar oferece **todos** os estilos (`OFERECE` ou `EXCLUSIVO`) |
   | `lat`, `lng`, `radiusKm` | "perto de mim": os dois juntos, dentro do Brasil; raio padrão **5 km**, até **50 km** |
   | `municipality` | código IBGE de 7 dígitos |
   | `sort` | `RELEVANCE` (padrão), `DISTANCE` (exige lat/lng) ou `RATING` |
   | `page`, `size` | página a partir de 0; tamanho padrão 20, até 50 |

   Código desconhecido, raio fora de 0–50 km, ponto fora do Brasil ou `DISTANCE` sem localização dão `422` com todos os
   erros juntos.
2. **Só páginas públicas**: lugar `ACTIVE` (fechado sai), conta dona `ACTIVE` e selo **`VERIFIED`**. Lugar com foco em
   bebida alcoólica só para adulto logado.
3. **Raio**: o pré-filtro usa o índice GiST (`ST_DWithin` com folga de 600 m) e a distância final é medida até o
   **ponto público** — exato em `ADDR_PUBLIC_FULL`, centro da célula de 0,005° em `ADDR_PUBLIC_BAIRRO` ou quando a conta
   dona é cozinha caseira. Lugar `ADDR_HIDDEN` não entra na busca por raio. `distanceKm` tem uma casa; no endereço
   aproximado, passos de 0,5 km (mínimo 0,5).
4. **Ordenações**:
   - `RELEVANCE`: score bayesiano do RF8, `(m·C + Σwᵢrᵢ) / (m + Σwᵢ)` com m = 10 e C = 4,2; com "perto de mim", menos
     `0,3 · ln(1 + km)`. Um 4,6 com 20 avaliações passa um 5,0 com 2; perto de mim, 0,5 km contra 3 km vira o jogo;
   - `DISTANCE`: do mais perto ao mais longe;
   - `RATING`: média exibida (quem ainda não tem média vai para o fim), depois o número de avaliações.
5. A nota e o nível de preço usados no filtro e na ordem são calculados no SQL com **as mesmas fórmulas** do
   `RatingCalculator` (peso confiança × recência, idade em horas inteiras) e da `PriceScale` (limites `[início, fim)`).
6. **Card** de cada resultado: `id`, `name`, `primaryType`, `group`, `chip`, `primaryCuisine`, `primaryFormat`, `price`
   e `rating` **iguais aos da página do lugar** (montados pelo `EstablishmentService`), `location` (`uf`, `city`,
   `neighborhood`, `approximate`), `distanceKm`, `adultsOnly` e `status`. A resposta traz `total` e `radiusKm` (nulo sem
   "perto de mim").

## Diagramas

[Casos de uso](casos-de-uso.md) · [Atividades](atividades.md) · [Classes](classes.md) · [Componentes](componentes.md) ·
[Sequência](sequencia.md) · [Estados](estados.md)

## Rastreabilidade

| Camada | Artefato | Situação |
|---|---|---|
| Aplicação | `EstablishmentSearch` (filtros, SQL com PostGIS e as fórmulas de nota e preço, cards); `EstablishmentService.price`, `rating`, `location` e `classification` reaproveitados ([RF6](../RF6/README.md)); `ReviewRepository.findCountingIn` ([RF8](../RF8/README.md)) | ✅ |
| Persistência | índices da V2: GiST em `establishment.point`, GIN `jsonb_path_ops` em `cuisines_all` e `formats_all`, trigram no nome | ✅ |
| API | `GET /api/v1/establishments` em `EstablishmentController`; GET público no `SecurityConfig` ([RF4](../RF4/README.md)) | ✅ |
| Tela | app web: Explorar em `/` (busca por nome, chips, culinária específica, tipo de comida, preço com o rótulo em reais, nota mínima, estilos, "perto de mim" com raio e aviso de que a localização não é guardada, ordem e paginação). Os filtros ficam na URL; a coordenada só na memória da página, arredondada a ~110 m; e2e `explorar.spec.ts` com axe | ✅ |
| Dados de exemplo | `config/DevSeed` (perfil `dev`, banco vazio): 10 lugares em SP, BH e Recife com avaliações | ✅ |
| Testes | `SearchApiIT.searchShowsOnlyPublicPlacesAndCuisineIncludesItsDescendants` (inclui `chip`), `SearchApiIT.radiusSearchMeasuresTheHomeKitchenFromItsPublicPointAndSortsByDistance` | ✅ |
| Pendências | sugestões e autocompletar; cache dos agregados no Valkey; C por categoria e município (job); mapa com pins; busca por bairro; facetas operacionais como filtro; histórico de buscas só com consentimento | ⏳ |
