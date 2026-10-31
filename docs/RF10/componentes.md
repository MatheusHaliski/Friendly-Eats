# RF10 · Componentes

```mermaid
flowchart LR
    WEB["App web Next.js · Explorar /<br/>FiltersPanel · EstablishmentCard<br/>filtros na URL · coordenada só em memória"] --> APP["Cliente da API<br/>packages/api"] --> SEC["SecurityFilterChain<br/>GET /api/v1/establishments público"]
    SEC --> EC["EstablishmentController<br/>GET /establishments"]
    EC --> ES["EstablishmentSearch<br/>validação · SQL · cards"]
    ES --> NJ["NamedParameterJdbcTemplate"]
    NJ --> PG[("PostgreSQL + PostGIS<br/>GiST point · GIN cuisines_all/formats_all<br/>trigram no nome")]
    ES --> SVC["EstablishmentService · RF6<br/>preço · nota · local · aberto agora RF11"]
    WEB --> DLG["PlaceDialog<br/>funcionamento e cobranças resumidas · RF11"]
    ES --> RR["ReviewRepository.findCountingIn · RF8"]
    ES --> PS["PriceScale · friendly-eats.price"]
    ES --> AP["AgeGate · 18+ verificado (RF17)"]
    ES -.-> VK[("Valkey · cache dos agregados ⏳")]
    ES -.-> MAP["Mapa com pins ⏳"]
```
