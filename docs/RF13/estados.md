# RF13 · Estados

## Caso da fila (`moderation_case.status`)

```mermaid
stateDiagram-v2
    direction LR
    [*] --> Open : 1ª denúncia ou sinal do alvo
    Open --> Open : nova denúncia soma (prioridade e prazo)
    Open --> Decided : decisão FIRST com motivo
    Decided --> InAppeal : recurso do autor ou de quem denunciou (até 30 dias)
    InAppeal --> Final : outra pessoa mantém ou reverte
    Decided --> [*] : prazo de recurso termina
    Final --> [*]
    note right of Open
        Criança em risco: conteúdo já fora do ar
        (decisão PREVENTIVE automática).
    end note
```

## Conteúdo sob moderação (`post` e `post_comment.moderation_state`)

```mermaid
stateDiagram-v2
    direction LR
    [*] --> Normal
    Normal --> Preventive : denúncia de criança em risco
    Normal --> Hidden : decisão HIDE
    Normal --> Removed : decisão REMOVE
    Preventive --> Normal : decisão KEEP ou WARN
    Preventive --> Hidden : decisão HIDE
    Preventive --> Removed : decisão REMOVE
    Hidden --> Normal : recurso revertido
    Removed --> Normal : recurso revertido
    note right of Removed
        Some para os outros, o autor ainda abre.
        Texto guardado para recurso e prova.
        Comentário: contador - 1 ao sair, + 1 ao voltar.
    end note
```

## Avaliação na moderação (`review.status`, RF8)

```mermaid
stateDiagram-v2
    direction LR
    [*] --> Published
    Published --> UnderReview : sinal antifraude ou criança em risco
    Published --> Removed : decisão REMOVE
    UnderReview --> Published : decisão KEEP ou WARN
    UnderReview --> Removed : decisão REMOVE
    Removed --> Published : recurso revertido
    note right of Published
        Contestação do dono e denúncia comum
        não tiram do ar até a decisão.
    end note
```

## Página do lugar (`establishment_alert`)

```mermaid
stateDiagram-v2
    direction LR
    [*] --> SemAlerta
    SemAlerta --> Congelado : 5 avaliações de contas novas em 24 h, ou moderação com dias
    SemAlerta --> ComAlerta : moderação publica alerta sem congelar
    Congelado --> SemAlerta : fim do prazo ou moderação encerra
    ComAlerta --> SemAlerta : moderação encerra
    note right of Congelado
        Avaliação nova 409, editar a própria continua.
        Histórico público com início e fim.
    end note
```

## Denúncia e recurso

```mermaid
stateDiagram-v2
    direction LR
    state Denuncia {
        [*] --> Aberta
        Aberta --> Procedente : HIDE · REMOVE · WARN
        Aberta --> Improcedente : KEEP
        Procedente --> Improcedente : recurso revertido para KEEP
        Improcedente --> Procedente : recurso de quem denunciou revertido
    }
    state Recurso {
        [*] --> EmAnalise
        EmAnalise --> Mantido
        EmAnalise --> Revertido
    }
```
