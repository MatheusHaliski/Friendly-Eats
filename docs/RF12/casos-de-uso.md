# RF12 · Casos de uso

Atores primários à esquerda, secundários à direita.

```mermaid
flowchart LR
    PES["Pessoa com conta ativa"]:::actor
    DONO["Autor do post"]:::actor
    VIS["Visitante"]:::actor

    UC1(["Curtir e descurtir"])
    UC2(["Salvar e tirar dos salvos<br/>privado"])
    UC3(["Comentar na caixa de mensagens"])
    UC4(["Ler os comentários"])
    UC5(["Apagar o meu comentário"])
    UC6(["Apagar comentário na minha caixa"])
    UC7(["Compartilhar<br/>link · WhatsApp · Telegram · X · e-mail"])
    UC8(["Fechar os comentários<br/>who_can_comment · RF2"])
    UC9(["Entrar para interagir · 401"])
    UC10(["Seguir perfis ⏳"]):::planned
    UC11(["Responder como estabelecimento ⏳"]):::planned

    TR["Valkey · em alta"]:::actor
    MOD["Moderação ⏳"]:::actor

    PES --- UC1 & UC2 & UC3 & UC5 & UC7
    DONO --- UC6 & UC8 & UC11
    VIS --- UC4
    VIS -.- UC9
    UC3 -. «include» .-> UC4
    UC8 -. «extend» NOBODY ou FOLLOWERS .-> UC3
    UC10 -. «extend» libera FOLLOWERS .-> UC3
    UC1 & UC3 & UC7 --- TR
    UC6 -.- MOD

    classDef actor fill:#FFF1E8,stroke:#E88F12,stroke-width:2px,color:#17120D,font-weight:bold
    classDef planned stroke-dasharray:5 4
```

No alerta sanitário só há compartilhar e salvar. O post de menor de 16 não aparece para os outros, então ninguém de
fora interage com ele.
