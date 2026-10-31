# RF1 · Casos de uso

Atores primários à esquerda, secundários à direita.

```mermaid
flowchart LR
    V["Visitante"]:::actor
    M["Menor de 16"]:::actor

    UC1(["Criar conta"])
    UC2(["Escolher tipo de conta<br/>cor · glifo · rótulo"])
    UC3(["Preencher campos do tipo<br/>CNPJ · CNAE · função · canal"])
    UC8(["Cadastrar negócio informal<br/>só com CPF"])
    UC4(["Ler e aceitar os Termos de Uso"])
    UC5(["Confirmar e-mail com código"])
    UC6(["Informar e-mail do responsável<br/>o convite sai no cadastro · RF17"])
    UC7(["Conferir selo de estabelecimento,<br/>varejo e órgão oficial<br/>(CPF: identidade e alvará) ⏳"]):::planned

    MOD["Moderação"]:::actor
    MAIL["Serviço de e-mail"]:::actor

    V --- UC1
    M --- UC1
    UC1 -. «include» .-> UC2
    UC1 -. «include» .-> UC4
    UC1 -. «include» .-> UC5
    UC2 -. «include» .-> UC3
    UC6 -. «extend» menor de 16 .-> UC1
    UC8 -. «extend» ambulante · food truck · cozinha caseira .-> UC3
    UC7 -. «extend» tipo com selo .-> UC2
    UC7 --- MOD
    UC5 --- MAIL

    classDef actor fill:#FFF1E8,stroke:#E88F12,stroke-width:2px,color:#17120D,font-weight:bold
    classDef planned stroke-dasharray:5 4
```
