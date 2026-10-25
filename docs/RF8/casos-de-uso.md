# RF8 · Casos de uso

Atores primários à esquerda, secundários à direita.

```mermaid
flowchart LR
    PES["Pessoa com conta ativa<br/>comensal · criador · chef · comunidade"]:::actor
    VIS["Visitante"]:::actor
    ORG["Conta de organização<br/>estabelecimento · varejo · oficial"]:::actor
    DONO["Dono do lugar"]:::actor

    UC1(["Avaliar com 1 a 5 estrelas<br/>PUT /reviews/me"])
    UC2(["Escolher como foi<br/>no local · retirada · entrega"])
    UC3(["Dar notas às dimensões do modo"])
    UC4(["Informar o gasto por pessoa"])
    UC5(["Editar a avaliação<br/>versão anterior guardada"])
    UC6(["Apagar a avaliação"])
    UC7(["Ler as avaliações e a nota agregada"])
    UC8(["Recusar: organização ou dono · 403"])
    UC9(["Verificar a visita<br/>check-in · nota fiscal ⏳"]):::planned
    UC10(["Responder em público · RF12 ⏳"]):::planned
    UC11(["Denunciar e moderar · RF13 ⏳"]):::planned

    MOD["Moderação"]:::actor
    NFE["NFC-e · geolocalização"]:::actor

    PES --- UC1 & UC6 & UC7
    VIS --- UC7
    ORG --- UC8
    DONO --- UC8
    DONO -.- UC10
    UC1 -. «include» .-> UC2
    UC3 -. «extend» opcional .-> UC1
    UC4 -. «extend» opcional · alimenta o preço do RF6 .-> UC1
    UC5 -. «extend» já avaliou .-> UC1
    UC9 -. «extend» ⏳ .-> UC1
    UC11 -.- MOD
    UC9 -.- NFE

    classDef actor fill:#FFF1E8,stroke:#E88F12,stroke-width:2px,color:#17120D,font-weight:bold
    classDef planned stroke-dasharray:5 4
```
