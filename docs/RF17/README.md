---
rf: RF17
titulo: Verificação de idade e conta do responsável
status: parcial
cards: [FE-27]
paths:
  - src/main/java/com/friendlyeats/application/age/*.java
  - src/main/java/com/friendlyeats/application/guardian/*.java
  - src/main/java/com/friendlyeats/api/AgeVerificationController.java
  - src/main/java/com/friendlyeats/api/GuardianController.java
  - src/main/java/com/friendlyeats/application/post/AuthorDirectory.java
  - src/main/java/com/friendlyeats/application/post/PostReader.java
  - src/main/java/com/friendlyeats/application/establishment/EstablishmentService.java
  - src/main/java/com/friendlyeats/application/establishment/EstablishmentSearch.java
  - src/main/java/com/friendlyeats/application/account/PreferencesService.java
  - src/main/java/com/friendlyeats/application/consent/ConsentService.java
  - src/main/resources/db/migration/V4__conta_lgpd.sql
---

# RF17 · Verificação de idade e conta do responsável

> **Como** pessoa adulta, **quero** verificar a minha idade sem mandar cópia de documento **para** ver bares, adegas e
> posts de bebida. **Como** responsável, **quero** supervisionar a conta do meu filho de até 16 anos **para**
> decidir quando afrouxar as proteções. **Como** adolescente, **quero** saber que a supervisão está ativa e o que o
> responsável fez.

| | |
|---|---|
| **Base legal** | ECA Digital (Lei 15.211/2025): art. 9º, §1º (verificação a cada acesso, sem autodeclaração), arts. 17 e 18 (supervisão parental), art. 24 (conta de até 16 vinculada) · Decreto 12.880/2026: art. 15, §1º, II (bebida é proibida a menores), art. 19 (versão sem o conteúdo ou verificação efetiva), art. 24, §3º (sem guardar imagem do documento), art. 27 (contestação) · LGPD, art. 14 (consentimento do responsável) |
| **Pesquisa** | [RC-SEG-13 e RC-SEG-14](../pesquisa/requisitos/req-03-confianca-legal.md#rc-seg-13--verificação-de-idade-proporcional-ao-risco) |
| **Status** | 🟡 Parcial: regra do 18+ por verificação, porta do fornecedor com o fake de dev/it, histórico, contestação com revisão humana, convite e vínculo, painel do responsável, consentimento sensível e aviso de vínculo pendente prontos. **Falta o fornecedor de produção** ⚖️ |

## Decisões (⚖️ = confirmar com o dono do produto ou o jurídico)

1. ⚖️ **Não há fornecedor contratado.** O `AgeVerificationProvider` é uma porta; o `FakeAgeVerificationProvider` só
   sobe nos perfis `dev` e `it`. Em produção, sem adaptador, `POST /start` responde **503** e todo o conteúdo de
   bebida fica oculto: é a "versão sem esse conteúdo" do art. 19 do Decreto. O contrato com o fornecedor precisa
   proibir guardar o documento e usar o dado para outra finalidade (ECA Digital, art. 13).
2. ⚖️ **Validade de 365 dias** (`friendly-eats.age-verification.validity`) como leitura do "a cada acesso" do art. 9º,
   §1º: o que se confere a cada acesso é o atributo verificado e vigente.
3. **A data declarada só restringe.** Quem declarou menos de 18 não vê bebida nem começa a verificação; o
   `VERIFIED_MINOR` também nega. Conflito entre as duas: vale a hipótese mais protetora (Decreto, art. 25, §4º).
4. **Publicar** post 18+ continua exigindo a data declarada de adulto (RF9); só **ver** exige a verificação. O autor
   sempre vê o próprio post.
5. ⚖️ **Contestação aceita volta a `UNVERIFIED`**: a pessoa refaz a verificação por outro método. Quem revisa não
   afere idade, só libera uma nova tentativa. Revisores = lista de e-mails em `friendly-eats.age-verification.reviewers`.
6. **Vínculo:** obrigatório para menor de 16 (o convite sai no cadastro) e opcional dos 16 aos 17 (o consentimento do
   dado sensível depende dele). O responsável precisa ter conta **ativa** e **idade verificada**. ⚖️ Como provar que
   a pessoa é *de fato* o responsável legal segue em aberto (o Decreto, art. 20, prevê uma solução pública).
7. **Vínculo pendente não bloqueia a conta**: ela já fica com as proteções máximas. Depois de 7 dias
   (`pending-reminder-after`) o job avisa o menor uma vez, e a tela mostra o aviso.
8. ⚖️ **Consentimento sensível pelo responsável vale para qualquer menor vinculado** (13 a 17), como já dizia a regra
   `UNDER_18` do RF3 ("até o responsável consentir"). As finalidades travadas até 16 (perfilamento, marketing,
   localização) **não** destravam com o responsável.
9. **Desvincular:** o responsável a qualquer momento; o adolescente só a partir dos 16. Ao desvincular, o menor de 16
   volta às proteções máximas e o consentimento sensível do menor de 18 é revogado (os estilos alimentares somem).

## Critérios de aceite

### Verificação de idade

1. `GET /api/v1/me/age-verification` →
   `{"status","statusLabel","method","verifiedAt","validUntil","alcoholContentVisible","declaredAdult","canStart",
   "canAppeal","providerAvailable","simulated","pending":{"reference","expiresAt"}|null,"appeal":{…}|null,
   "history":[{"status","statusLabel","method","note","occurredAt"}]}`.
2. `POST …/start` → `{"reference","redirectUrl","instructions","expiresAt","simulated"}`. 403 para quem declarou
   menos de 18; 409 se já verificado ou com contestação aberta; 429 depois de 5 tentativas em 24 h; 503 sem fornecedor.
3. `POST …/complete {"reference","params"}`: o servidor pergunta o resultado ao fornecedor; sem resultado segue
   `PENDING`; referência que não é a aberta → 409. Grava só status, método, fornecedor, id da verificação e datas.
4. `POST …/appeal {"reason"}` (10 a 1000 caracteres) com o status `VERIFIED_MINOR` ou `FAILED` → 201; outra aberta → 409.
5. Fila de revisão: `GET /api/v1/review/age-appeals` e `POST /api/v1/review/age-appeals/{id} {"accept","note"}`, só
   para os revisores (403 para os demais; 404 se já decidida). Aceitar volta a `UNVERIFIED`; o titular recebe aviso.
6. **Quem vê bebida** (`AgeGate`): maior de 18 pela data declarada **e** `VERIFIED_ADULT` vigente. Vale no feed e no
   "em alta" (RF14), na página do post, nos posts do lugar, no perfil (RF15), na busca (RF10) e na página do lugar
   (RF6/RF11). Visitante → 401 na página; maior sem verificação → **403 com `ageVerificationRequired: true`**; menor
   → 404, sem revelar o conteúdo.

### Responsável

7. Cadastro de menor de 16 (RF1) cria o vínculo `PENDING` com um token de uso único por e-mail
   (`AccountNotifier.guardianInvite`, link `/configuracoes/idade?convite=…`); só o HMAC do token fica no banco.
8. `GET /api/v1/me/guardian` → `{"required","eligible","status":"NONE|PENDING|ACTIVE","guardianEmail","guardian",
   "invitedAt","inviteExpiresAt","pendingDays","overdue","canUnlink","sensitiveConsent","protections","requests",
   "history"}`.
9. `POST /api/v1/me/guardian/invite {"guardianEmail"?}`: vazio reenvia; outro e-mail troca o convite (o antigo deixa de
   valer). Um envio a cada 10 min (429); com vínculo ativo → 409; conta de adulto → 403.
10. `POST /api/v1/guardian/links/accept {"token"}`: token inválido ou vencido (14 dias) → 404; sem idade verificada →
    403 com `ageVerificationRequired`; o próprio menor → 403.
11. `POST /api/v1/me/guardian/requests {"protection"}` (menor de 16, vínculo ativo) → 201; sem vínculo ativo, já
    pedido ou proteção já desligada → 409. O responsável recebe aviso.
12. Painel: `GET /api/v1/guardian/minors`, `GET /api/v1/guardian/minors/{id}`,
    `POST …/requests/{requestId} {"approve"}`, `PUT …/protections/{PUBLIC_PROFILE|SENSITIVE_CONTENT|COMMENTS_FROM_EVERYONE|NO_USAGE_REMINDER} {"allowed"}`,
    `PUT …/sensitive-consent {"granted"}`, `DELETE …` (204). Quem não supervisiona → 404.
13. O menor de 16 afrouxar sozinho segue 403 (`PreferencesService`); repetir o valor que o responsável liberou passa.
14. Tudo vai para `guardian_action` e aparece no histórico do menor e do painel.

## Diagramas

[Casos de uso](casos-de-uso.md) · [Atividades](atividades.md) · [Classes](classes.md) · [Componentes](componentes.md) ·
[Sequência](sequencia.md) · [Estados](estados.md)

## Rastreabilidade

| Camada | Artefato | Situação |
|---|---|---|
| Persistência | `age_verification`, `age_verification_event`, `age_appeal`, `guardian_link`, `guardian_request`, `guardian_action` em `V4__conta_lgpd.sql` | ✅ |
| Porta do fornecedor | `AgeVerificationProvider`; `FakeAgeVerificationProvider` (`@Profile dev, it`) | ✅ |
| Fornecedor de produção | adaptador real, contrato e método aceito pela ANPD | ⏳ ⚖️ |
| Regra do 18+ | `AgeGate` usado por `AuthorDirectory.adult`, `PostReader.requireVisible`, `EstablishmentService.requireVisible`, `EstablishmentSearch`; `PostApiIT`, `EstablishmentApiIT`, `SearchApiIT`, `UserProfileApiIT` | ✅ |
| Verificação | `AgeVerificationService` (`start`, `complete`, `appeal`, `queue`, `decide`) e `AgeVerificationController`; `AgeVerificationApiIT` | ✅ |
| Erro 403 próprio | `AgeExceptions.VerificationRequired` → `ageVerificationRequired: true` (`ApiExceptionHandler`) | ✅ |
| Vínculo | `GuardianService`, `GuardianController`, `GuardianConsents`, `Protection`; convite no cadastro (`RegistrationService`); `GuardianApiIT` | ✅ |
| Consentimento do responsável | `ConsentService` destrava `SENSITIVE_PROFILE_DATA` com `GuardianConsents` (RF3) | ✅ |
| Aviso de pendente | `GuardianReminderJob` (diário) → `AccountNotifier.guardianLinkPending` | ✅ |
| Exportação | dados de verificação e de vínculo entram no ZIP do [RF18](../RF18/README.md) | ✅ |
| Sinal de idade da loja (Decreto, art. 25) | — | ⏳ |
| Tempo de uso e perfis adultos com quem o menor interage, no painel (art. 18) | — | ⏳ |
| Aviso ao completar 16 | — | ⏳ |
