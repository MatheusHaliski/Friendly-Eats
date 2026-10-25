---
rf: RF3
titulo: Consentimentos e privacidade (Privacy by Default)
status: parcial
cards: [FE-03]
paths:
  - src/main/java/com/friendlyeats/application/account/RegistrationService.java
  - src/main/java/com/friendlyeats/application/consent/ConsentService.java
  - src/main/java/com/friendlyeats/api/ConsentController.java
  - src/main/java/com/friendlyeats/domain/model/ConsentPurpose.java
  - src/main/java/com/friendlyeats/application/account/ProfileService.java
  - src/main/java/com/friendlyeats/infrastructure/persistence/postgres/UserAccountRepository.java
  - src/main/resources/db/migration/V1__baseline.sql
  - frontend/apps/web/app/configuracoes/privacidade/page.tsx
  - frontend/apps/web/components/settings/PrivacySection.tsx
  - frontend/apps/web/components/settings/ConsentSwitch.tsx
  - frontend/apps/web/components/settings/TasteFields.tsx
  - frontend/apps/web/components/Switch.tsx
  - frontend/apps/web/components/Form.tsx
---

# RF3 · Consentimentos e privacidade

> **Como** titular, **quero** ligar e desligar cada uso opcional dos meus dados (localização, feed personalizado,
> estilos alimentares, marketing, compartilhamento) **para** decidir o que o Friendly Eats faz com eles.

| | |
|---|---|
| **Base legal (LGPD)** | Art. 7º, I (consentimento) · Art. 8º, §5º (revogação facilitada) · Art. 11 (dado sensível) |
| **Princípio** | Privacy by Default: todo tratamento opcional começa **desligado** |
| **Status** | 🟡 Parcial: API de consentimentos, histórico append-only, bloqueio para menor de 16 e efeitos imediatos da revogação (estilos alimentares e última localização) prontos; reconsentimento por nova versão da política e consumidores do feed ainda não |

## Finalidades

| Finalidade (`purpose`) | Base | Padrão | Para quê no Friendly Eats | Bloqueios |
|---|---|---|---|---|
| `LOCATION_TRACKING` | Art. 7º, I | desligado | guardar a última localização para "perto de mim" e alertas da região. Busca pontual por raio não precisa disso (execução do serviço) | menores |
| `PERSONALIZED_FEED` | Art. 7º, I | desligado | ordenar o feed e a busca pelo gosto (culinárias, histórico) | menores: sem perfilamento |
| `SENSITIVE_PROFILE_DATA` | Art. 11, I | desligado | guardar estilos alimentares que revelam saúde (alergia, doença celíaca, intolerância) ou religião (halal, kosher) | menores de 18, até existir o consentimento do responsável (Art. 14) |
| `MARKETING` | Art. 7º, I | desligado | ofertas de estabelecimentos e marcas | menores |
| `THIRD_PARTY_SHARING` | Art. 7º, I | desligado | compartilhar com parceiros (reserva, delivery) | — |

Dado de saúde nunca é compartilhado para ganho econômico (Art. 11, §4º). Aviso de alergia num pedido vai como nota
pontual, não como o perfil inteiro.

## Critérios de aceite

1. Toda conta nasce com as 5 finalidades desligadas; sem evento gravado, a finalidade vale **desligada**.
2. `GET /api/v1/me/consents` → `{"items":[{"purpose","label","description","legalBasis","sensitive","granted",
   "updatedAt","lockedForMinor"}]}`, uma linha por finalidade, na ordem da tela.
3. `PUT /api/v1/me/consents/{purpose}` com `{"granted": true|false}` devolve o item atualizado. Mudar o valor grava
   uma linha nova em `consent_event` (base legal, versão da política, canal e HMAC do IP); repetir o mesmo valor
   não grava nada. Finalidade desconhecida → 404; sem `granted` → 422; sem sessão → 401; escrita sem `X-XSRF-TOKEN` → 403.
4. Menor de 16 não liga `LOCATION_TRACKING`, `PERSONALIZED_FEED` nem `MARKETING` (403, ECA Digital), e menor de 18
   não liga `SENSITIVE_PROFILE_DATA` enquanto não houver o consentimento do responsável (403, LGPD Art. 14); desligar
   continua livre. A tela mostra a chave travada pelo campo `lockedForMinor`.
5. Revogar tem efeito na mesma transação: `SENSITIVE_PROFILE_DATA` apaga `dietary_styles` da conta e
   `LOCATION_TRACKING` apaga `last_saved_location`.
6. O perfil ([RF2](../RF2/README.md)) só grava estilos alimentares com `SENSITIVE_PROFILE_DATA` ligado (senão 403).
   A conta fica travada (`SELECT … FOR UPDATE`) nas duas operações, então uma revogação nunca corre junto com uma
   gravação de estilos.

## Diagramas

[Casos de uso](casos-de-uso.md) · [Atividades](atividades.md) · [Classes](classes.md) · [Componentes](componentes.md) ·
[Sequência](sequencia.md) · [Estados](estados.md)

## Rastreabilidade

| Camada | Artefato | Situação |
|---|---|---|
| Persistência | `consent_event` (append-only) e view `user_consent_current` em `V1__baseline.sql` | ✅ |
| Cadastro | `RegistrationService` grava as 5 finalidades com `granted = false` na mesma transação da conta; `AuthApiIT` confere | ✅ |
| Preferências de privacidade | perfil privado, quem comenta e ano de nascimento em `user_preference` ([RF2](../RF2/README.md)); travadas para menor de 16 | ✅ |
| Domínio | `ConsentPurpose`: rótulo, descrição, base legal, `sensitive` e `MinorRule` (16 ou 18 anos) de cada finalidade; o cadastro usa o mesmo enum (`ConsentService.recordDefaults`) | ✅ |
| API | `GET /api/v1/me/consents` e `PUT /api/v1/me/consents/{purpose}` (`ConsentController` → `ConsentService`); `ConsentApiIT` | ✅ |
| Efeitos imediatos | revogar apaga os estilos alimentares e a última localização na mesma transação; `ConsentApiIT` | ✅ |
| Menores | `LOCATION_TRACKING`, `PERSONALIZED_FEED` e `MARKETING` travados para menor de 16; `SENSITIVE_PROFILE_DATA` para menor de 18 (`AgePolicy`); `ConsentApiIT` | ✅ |
| Responsável | consentimento do responsável para dado sensível de adolescente (Art. 14) | ⏳ |
| Perfil | estilos alimentares exigem o consentimento sensível; trava `UserAccountRepository.lockById` contra corrida | ✅ |
| Reconsentimento | nova `privacy-version` pede reconfirmação das finalidades ligadas | ⏳ |
| Consumidores | feed personalizado e marketing conferem `ConsentService.granted` quando existirem | ⏳ |
| Telas | app web `/configuracoes/privacidade`: preferências de privacidade e uma chave por finalidade (`ConsentSwitch`, `role=switch`, estado escrito "Ligado/Desligado"), base legal e selo "Dado sensível"; chave travada pelo `lockedForMinor` com "Disponível a partir dos 16/18 anos"; o cartão "Seu gosto" do perfil bloqueia os estilos alimentares sem o consentimento e recarrega o perfil depois de cada mudança; e2e `configuracoes.spec.ts` (ligar o sensível e salvar estilos) | ✅ |
| Foco no aviso | Privacidade: o aviso de resultado recebe o foco depois de montado (`useDeferredFocus`) | ✅ |
