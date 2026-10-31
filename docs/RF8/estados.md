# RF8 · Estados

## Avaliação (`review.status`)

```mermaid
stateDiagram-v2
    direction LR
    [*] --> Published : PUT /reviews/me (201)
    Published --> Published : PUT de novo (200) · versão anterior em review_revision
    Published --> UnderReview : sinal antifraude ou criança em risco (RF13)
    Published --> Removed : moderação remove, com motivo e recurso (RF13)
    UnderReview --> Published : moderação mantém (RF13)
    UnderReview --> UnderReview : PUT do autor (200) · continua em análise
    UnderReview --> Removed : moderação remove, com motivo e recurso (RF13)
    Removed --> Published : recurso revertido (RF13)
    Removed --> Removed : PUT do autor (409)
    Removed --> Removed : DELETE do autor (409)
    Published --> [*] : DELETE /reviews/me (204) · eliminação da conta ou do lugar
    note right of UnderReview
        Fora do agregado
        até a decisão.
    end note
```

A edição gera uma nova versão e não é um estado. Avaliação `REMOVED` não é reescrita nem apagada pelo autor (`409`),
como manda a taxonomia (seção 10.1).

## Nota agregada do estabelecimento

```mermaid
stateDiagram-v2
    direction LR
    [*] --> RatingNone
    RatingNone --> RatingFew : 1ª avaliação que conta
    RatingFew --> Rated : 5ª avaliação que conta
    Rated --> RatingFew : apagada, ou autor suspenso ou em exclusão
    RatingFew --> RatingNone : última apagada, ou autor não ativo
    Rated --> Rated : nova, editada ou mais velha (peso de recência)
    note right of RatingNone
        nota null, nunca 0.
    end note
    note right of Rated
        média ponderada com 1 casa,
        histograma e dimensões com 5+ notas.
    end note
```
