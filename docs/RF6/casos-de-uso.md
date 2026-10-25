# RF6 · Casos de uso

Atores primários à esquerda, secundários à direita.

```mermaid
flowchart LR
    DONO["Dono<br/>conta ESTABELECIMENTO ou VAREJO ativa"]:::actor
    INF["Negócio com CPF<br/>ambulante · food truck · cozinha caseira"]:::actor
    VIS["Visitante"]:::actor
    PES["Pessoa com conta"]:::actor

    UC1(["Cadastrar estabelecimento"])
    UC2(["Escolher tipo principal<br/>e até 3 secundários"])
    UC3(["Informar o local<br/>UF · município IBGE · pino"])
    UC4(["Declarar o gasto por pessoa"])
    UC5(["Classificar · RF7"])
    UC6(["Mostrar só o bairro<br/>ponto arredondado"])
    UC7(["Editar · fechar ou reabrir"])
    UC8(["Ver por que a página<br/>não está no ar"])
    UC9(["Ver a página do estabelecimento"])
    UC10(["Entrar para ver lugar 18+"])
    UC11(["Conferir o selo da conta dona ⏳"]):::planned
    UC12(["Consultar CNPJ e CNAE ⏳"]):::planned
    UC13(["Buscar por raio · RF10 ⏳"]):::planned

    MOD["Moderação"]:::actor
    RFB["BrasilAPI · Receita"]:::actor

    DONO --- UC1 & UC7 & UC8
    INF --- UC1
    VIS --- UC9 & UC13
    PES --- UC9
    UC1 -. «include» .-> UC2
    UC1 -. «include» .-> UC3
    UC1 -. «include» .-> UC4
    UC1 -. «include» .-> UC5
    UC6 -. «extend» cozinha caseira ou escolha do dono .-> UC3
    UC10 -. «extend» tipo principal com bebida alcoólica .-> UC9
    UC11 -. «extend» selo PENDING .-> UC8
    UC11 --- MOD
    UC12 -.- RFB
    UC11 -. «include» .-> UC12

    classDef actor fill:#FFF1E8,stroke:#E88F12,stroke-width:2px,color:#17120D,font-weight:bold
    classDef planned stroke-dasharray:5 4
```

O negócio com CPF tem um único ponto e só os tipos do seu `informalFormat`. A pessoa com conta e menos de 18 anos
não vê a página 18+ (`404`).
