# RF11 · Casos de uso

Atores primários à esquerda, secundários à direita.

```mermaid
flowchart LR
    DONO["Dono<br/>conta ESTABELECIMENTO ou VAREJO"]:::actor
    VIS["Visitante ou pessoa com conta"]:::actor
    EQ["Equipe da página ⏳ RF20"]:::actor

    UC1(["Ver a página completa do lugar"])
    UC2(["Ver se está aberto agora<br/>no fuso do lugar"])
    UC3(["Ver horários da semana<br/>e datas especiais"])
    UC4(["Ver cobranças declaradas"])
    UC5(["Ver facetas: atendimento, ambiente,<br/>acessibilidade e pagamento"])
    UC6(["Reservar, pedir delivery<br/>ou chamar no WhatsApp"])
    UC7(["Ver o mapa e como chegar"])
    UC8(["Cadastrar ou editar o lugar<br/>/lugares/novo · /lugares/id/editar"])
    UC9(["Declarar horários, datas especiais<br/>e fechado temporariamente"])
    UC10(["Declarar cobranças<br/>com o lembrete do preço na porta"])
    UC11(["Declarar facetas"])
    UC12(["Cadastrar links e WhatsApp"])
    UC13(["Marcar o pino no mapa"])
    UC14(["Confirmar facetas e avisar<br/>'estava fechado' ⏳"]):::planned
    UC15(["Lembrete de feriado ⏳"]):::planned

    PARC["Parceiro externo<br/>iFood · Rappi · TheFork · WhatsApp…"]:::actor
    OSM["OpenStreetMap<br/>tiles"]:::actor

    VIS --- UC1 & UC6 & UC14
    DONO --- UC8 & UC15
    EQ -.- UC8
    UC1 -. «include» .-> UC2
    UC1 -. «include» .-> UC3
    UC1 -. «include» .-> UC4
    UC1 -. «include» .-> UC5
    UC1 -. «include» .-> UC7
    UC8 -. «include» .-> UC9
    UC8 -. «include» .-> UC10
    UC8 -. «include» .-> UC11
    UC8 -. «include» .-> UC12
    UC8 -. «include» .-> UC13
    UC6 --- PARC
    UC7 --- OSM
    UC13 --- OSM

    classDef actor fill:#FFF1E8,stroke:#E88F12,stroke-width:2px,color:#17120D,font-weight:bold
    classDef planned stroke-dasharray:5 4
```

O app nunca processa reserva, pedido nem pagamento: o botão só abre o parceiro, em outra aba. Quem pode editar é
decidido pelo `PlaceEditors` (hoje, o dono).
