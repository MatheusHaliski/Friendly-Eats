# RF3 · Estados de um consentimento

```mermaid
stateDiagram-v2
    [*] --> NaoConcedido : cadastro (RF1) ou sem evento
    NaoConcedido --> Concedido : titular liga (PUT granted true)
    Concedido --> Revogado : titular desliga, o dado ligado é apagado na hora
    Revogado --> Concedido : titular liga de novo
    Concedido --> Reconsentir : nova versão da política ⏳
    Reconsentir --> Concedido : aceita a nova versão
    Reconsentir --> Revogado : recusa
    NaoConcedido --> Travado : idade abaixo da MinorRule (16 ou 18)
    Travado --> NaoConcedido : completa a idade
    note right of Revogado
        Cada transição é uma linha nova em consent_event.
        Repetir o mesmo valor não grava nada.
        Nada é apagado: o histórico prova o consentimento (Art. 8º §2º).
    end note
```
