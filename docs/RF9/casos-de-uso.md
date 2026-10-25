# RF9 · Casos de uso

Atores primários à esquerda, secundários à direita.

```mermaid
flowchart LR
    PES["Pessoa com conta ativa<br/>comensal · criador · chef · comunidade"]:::actor
    EST["Estabelecimento verificado"]:::actor
    VAR["Varejo e Marca verificado"]:::actor
    OFI["Oficial verificado<br/>Anvisa · vigilância · Procon"]:::actor

    UC1(["Publicar foto de prato"])
    UC2(["Publicar receita"])
    UC3(["Fazer uma pergunta"])
    UC4(["Publicar prato do dia"])
    UC5(["Publicar promoção"])
    UC6(["Publicar evento"])
    UC7(["Publicar alerta sanitário"])
    UC8(["Enviar a foto<br/>URL pré-assinada · RF2"])
    UC9(["Citar o lugar e a taxonomia<br/>culinária · formato · estilos"])
    UC10(["Declarar alergênicos<br/>CONTEM ou PODE_CONTER"])
    UC11(["Escolher a moldura · RF16"])
    UC12(["Editar · arquivar · restaurar · apagar"])
    UC13(["Marcar Publicidade<br/>criador · CDC art. 36"])
    UC14(["Ficar 18+ sozinho<br/>bebida alcoólica"])
    UC15(["Publicar vídeo ⏳"]):::planned
    UC16(["Cardápio por item ⏳"]):::planned

    BKT["Bucket S3 · Garage"]:::actor
    MOD["Moderação ⏳"]:::actor

    PES --- UC1 & UC2 & UC3 & UC12
    PES --- UC6
    EST --- UC4 & UC5 & UC6 & UC16
    VAR --- UC5 & UC6
    OFI --- UC7
    UC1 -. «include» .-> UC8
    UC1 & UC2 & UC4 -. «include» .-> UC9
    UC10 -. «extend» prato com alergênico .-> UC9
    UC13 -. «extend» promoção de criador .-> UC5
    UC14 -. «extend» formato alcoólico ou bar .-> UC9
    UC11 -. «extend» exceto alerta .-> UC1
    UC8 --- BKT
    UC7 -.- MOD

    classDef actor fill:#FFF1E8,stroke:#E88F12,stroke-width:2px,color:#17120D,font-weight:bold
    classDef planned stroke-dasharray:5 4
```

O chef e a comunidade publicam evento; o criador publica promoção só com o chip Publicidade; a conta Oficial não faz
pergunta. Organizações com o selo em conferência leem e interagem, mas não publicam (`403`).
