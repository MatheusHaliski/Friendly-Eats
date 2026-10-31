# RF13 · Casos de uso

Atores primários à esquerda, secundários à direita.

```mermaid
flowchart LR
    PES["Pessoa com conta"]:::actor
    VIS["Pessoa sem conta"]:::actor
    AUT["Autor do conteúdo"]:::actor
    DONO["Dono do lugar"]:::actor
    MOD["Moderador"]:::actor

    UC1(["Denunciar post, comentário,<br/>avaliação, perfil ou lugar"])
    UC2(["Denunciar pelo formulário público<br/>nome e e-mail"])
    UC3(["Receber protocolo e<br/>acompanhar Minhas denúncias"])
    UC4(["Ver a decisão com motivo,<br/>origem e prazos"])
    UC5(["Contestar decisão"])
    UC6(["Contestar avaliação<br/>só motivos da política"])
    UC7(["Tratar a fila priorizada"])
    UC8(["Decidir com motivo<br/>manter · ocultar · remover · avisar"])
    UC9(["Rever recurso de outra pessoa"])
    UC10(["Registrar comunicação<br/>às autoridades"])
    UC11(["Publicar ou encerrar alerta<br/>e congelamento"])
    UC12(["Ver relatório agregado"])
    UC13(["Retirada preventiva<br/>criança em risco"])
    UC14(["Sinal de fraude em avaliação<br/>em análise e congelamento"])
    UC15(["Ver alerta ao consumidor<br/>na página do lugar"])

    ANTI["Filtro antifraude"]:::actor
    NOT["Aviso ao autor e a quem denunciou<br/>app e e-mail ⏳"]:::actor
    PF["Autoridades<br/>registro manual"]:::actor

    PES --- UC1 & UC3 & UC5 & UC15
    VIS --- UC2 & UC15
    AUT --- UC4 & UC5
    DONO --- UC6
    MOD --- UC7 & UC8 & UC9 & UC10 & UC11 & UC12
    UC1 -. «include» .-> UC3
    UC1 -. «extend» criança em risco .-> UC13
    UC6 -. «include» .-> UC3
    UC8 -. «include» .-> UC4
    UC5 -. «include» .-> UC9
    UC14 -. «extend» pico .-> UC11
    ANTI --- UC14
    UC4 & UC3 --- NOT
    UC10 --- PF

    classDef actor fill:#FFF1E8,stroke:#E88F12,stroke-width:2px,color:#17120D,font-weight:bold
```

Quem publicou nunca sabe quem denunciou. O dono do lugar contesta, mas não vê dados de quem avaliou e não esconde a
avaliação bloqueando a pessoa (RF19).
