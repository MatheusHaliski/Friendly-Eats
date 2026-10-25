---
rf: RF3
titulo: Consentimentos e privacidade (Privacy by Default)
status: parcial
cards: [FE-03]
paths:
  - src/main/java/com/friendlyeats/application/account/RegistrationService.java
  - src/main/resources/db/migration/V1__baseline.sql
---

# RF3 · Consentimentos e privacidade

> **Como** titular, **quero** ligar e desligar cada uso opcional dos meus dados (localização, feed personalizado,
> estilos alimentares, marketing, compartilhamento) **para** decidir o que o Friendly Eats faz com eles.

| | |
|---|---|
| **Base legal (LGPD)** | Art. 7º, I (consentimento) · Art. 8º, §5º (revogação facilitada) · Art. 11 (dado sensível) |
| **Princípio** | Privacy by Default: todo tratamento opcional começa **desligado** |
| **Status** | 🟡 Parcial: tabela `consent_event` (append-only), view `user_consent_current` e as 5 finalidades gravadas desligadas no cadastro; API e efeitos ainda não |

## Finalidades

| Finalidade (`purpose`) | Base | Padrão | Para quê no Friendly Eats | Bloqueios |
|---|---|---|---|---|
| `LOCATION_TRACKING` | Art. 7º, I | desligado | guardar a última localização para "perto de mim" e alertas da região. Busca pontual por raio não precisa disso (execução do serviço) | menores |
| `PERSONALIZED_FEED` | Art. 7º, I | desligado | ordenar o feed e a busca pelo gosto (culinárias, histórico) | menores: sem perfilamento |
| `SENSITIVE_PROFILE_DATA` | Art. 11, I | desligado | guardar estilos alimentares que revelam saúde (alergia, doença celíaca, intolerância) ou religião (halal, kosher) | — |
| `MARKETING` | Art. 7º, I | desligado | ofertas de estabelecimentos e marcas | menores |
| `THIRD_PARTY_SHARING` | Art. 7º, I | desligado | compartilhar com parceiros (reserva, delivery) | — |

Dado de saúde nunca é compartilhado para ganho econômico (Art. 11, §4º). Aviso de alergia num pedido vai como nota
pontual, não como o perfil inteiro.

## Diagramas

[Casos de uso](casos-de-uso.md) · [Atividades](atividades.md) · [Classes](classes.md) · [Componentes](componentes.md) ·
[Sequência](sequencia.md) · [Estados](estados.md)

## Rastreabilidade

| Camada | Artefato | Situação |
|---|---|---|
| Persistência | `consent_event` (append-only) e view `user_consent_current` em `V1__baseline.sql` | ✅ |
| Cadastro | `RegistrationService` grava as 5 finalidades com `granted = false` na mesma transação da conta; `AuthApiIT` confere | ✅ |
| Preferências de privacidade | perfil privado, quem comenta e ano de nascimento em `user_preference` ([RF2](../RF2/README.md)); travadas para menor de 16 | ✅ |
| API | `GET/PUT /api/v1/me/consents/{purpose}` (`ConsentController`) | ⏳ |
| Efeitos | consumidores de `ConsentRevoked` pelo `outbox_event`: apagar a última localização, os estilos alimentares, parar a personalização | ⏳ |
| Menores | bloqueio por idade das finalidades marcadas | ⏳ |
