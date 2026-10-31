---
rf: RF13
titulo: Moderação · denúncia, decisão motivada, recurso e integridade das avaliações
status: parcial
cards: [FE-19]
paths:
  - src/main/resources/db/migration/V5__moderacao.sql
  - src/main/java/com/friendlyeats/domain/moderation/*.java
  - src/main/java/com/friendlyeats/application/moderation/*.java
  - src/main/java/com/friendlyeats/api/ReportController.java
  - src/main/java/com/friendlyeats/api/ModerationController.java
  - src/main/java/com/friendlyeats/infrastructure/persistence/postgres/ModerationRepository.java
  - src/main/java/com/friendlyeats/infrastructure/persistence/postgres/IntegrityRepository.java
  - src/main/java/com/friendlyeats/infrastructure/persistence/postgres/UserRoleRepository.java
  - src/test/java/com/friendlyeats/api/ModerationApiIT.java
  - src/test/java/com/friendlyeats/domain/moderation/ModerationDomainTest.java
---

# RF13 · Moderação: denúncia, decisão motivada, recurso e integridade das avaliações

> **Como** pessoa (com ou sem conta), **quero** denunciar o que fere a lei ou os Termos e acompanhar o resultado;
> **como** autor, **quero** saber por que meu conteúdo saiu e poder contestar; **como** moderador, **quero** uma fila
> priorizada com prazo e um histórico que não muda; **como** consumidor, **quero** saber quando as avaliações de um
> lugar estão sob suspeita.

| | |
|---|---|
| **Origem** | Pesquisa de requisitos, eixo 03 (RC-SEG-01 a 05, 07 e 08) e eixo 02 (RC-EST-14) · [PESQUISA_REQUISITOS](../PESQUISA_REQUISITOS.md) |
| **Base legal** | STF, Temas 987 e 533 (art. 19 do Marco Civil parcialmente inconstitucional: notificação extrajudicial, devido processo, canal para usuários e **não usuários**, relatório de transparência); ECA Digital, arts. 27 a 33 (notificação, retirada sem ordem judicial, contestação, abuso de denúncia); Decreto 12.880/2026, arts. 41 a 44 (canal de denúncia e retirada imediata para criança em risco); CDC, arts. 6º, III, e 37 (avaliação falsa é publicidade enganosa); MCI, arts. 10, 15, 20 e 22 |
| **LGPD** | Denúncia e decisão: legítimo interesse e cumprimento de obrigação legal (arts. 7º, II e IX) ⚖️. O IP nunca é guardado: origem da avaliação e limite do formulário público usam o HMAC do `AppSecrets`. O e-mail do formulário público só serve ao protocolo e ao resultado. Quem publicou nunca sabe quem denunciou |
| **Status** | 🟡 Parcial: papel de moderador, denúncia com protocolo (com conta e sem conta), fila priorizada com prazo, retirada preventiva para criança em risco, decisão motivada com histórico só de inclusão, recurso revisto por outra pessoa, limite de ritmo e peso por confiabilidade, sinais de fraude em avaliações, alerta ao consumidor, congelamento, contestação do dono, relatório agregado, app web e testes prontos; e-mail transacional, sinalizadores confiáveis, sanções por abuso de denúncia, relatório público de transparência, purga do removido e recurso de conta suspensa ainda não |

## Critérios de aceite

1. **Papel de moderador.** `user_role (user_id, role = 'MODERATOR')`, concedido por
   `friendly-eats.moderation.bootstrap-moderators` (e-mails, na subida, `ModerationRoles`) ou pelo DevSeed
   (`moderacao@` e `moderacao2@friendlyeats.dev`). O login põe `ROLE_MODERATOR` na sessão (`SessionService`), e o
   `SecurityConfig` fecha `/api/v1/moderation/**` (`401` sem sessão, `403` sem o papel). Cada ação confere de novo
   no banco: tirar o papel vale na hora (`ModerationApiIT.moderationAreaIsOnlyForModerators`).
2. **Denunciar** post, comentário, avaliação, perfil e página de lugar (`POST /api/v1/reports`), com um dos 14 motivos
   fechados (taxonomia, seção 15.1) que valham para o tipo do alvo (`422` senão), texto opcional até 1.000 caracteres
   (obrigatório em `OUTRO`) e recibo `DEN-aaaa-nnnnnn` com o prazo. Só se denuncia o que se consegue ver (mesmas regras
   de `404`/`401` das páginas) e nunca o próprio conteúdo (`422`). Uma denúncia aberta por pessoa e alvo (`409`; índice
   único parcial). Sem conta, `POST /api/v1/reports/public` exige nome e e-mail (a denúncia não é anônima, ECA Digital,
   art. 29, §2º) e aceita post, perfil e lugar pelo link colado em `/denunciar`, ou qualquer alvo vindo do botão.
3. **Fila e prazos.** Um caso por alvo enquanto aberto: várias denúncias somam no mesmo caso, que fica com a maior
   prioridade, o menor prazo e o motivo mais grave. Ordem: prioridade (`CRITICA` → `BAIXA`) e prazo (taxonomia, 15.2).
   **Criança em risco** tira post, comentário ou avaliação do ar na hora (`moderation_state = PREVENTIVE` ou
   `UNDER_REVIEW`), com a decisão preventiva automática no histórico; marcada a "exploração ou abuso sexual", o caso
   fica `authority_referral = PENDING` até a moderação registrar a comunicação às autoridades (só o registro, nada é
   enviado automaticamente). O caso guarda uma cópia do conteúdo (`snapshot`) para a prova e o recurso.
4. **Decisão motivada** (`POST /moderation/cases/{id}/decision`): `KEEP`, `HIDE`, `REMOVE` ou `WARN` (o que vale para
   o tipo, taxonomia 15.3), a regra da política (menos em `KEEP`) e a fundamentação (20 a 2.000 caracteres).
   `moderation_decision` é só de inclusão (o trigger recusa `UPDATE` e `DELETE`). Quem decide não pode ser o autor nem
   quem denunciou (`403`). O autor vê, em "Decisões sobre meu conteúdo", o aviso do ECA Digital, art. 30: o que
   aconteceu, a regra, a fundamentação, a origem (humana ou automática), como recorrer e os prazos, sem o nome de quem
   denunciou nem de quem decidiu.
5. **Recurso** (`POST /me/moderation/cases/{id}/appeal`): o autor contra a medida, ou quem denunciou contra o
   arquivamento, uma vez por decisão, em até 30 dias (`appeal-days`), com 20 a 2.000 caracteres. A revisão
   (`POST /moderation/cases/{id}/appeal-decision`) mantém ou reverte, com fundamentação, e é feita por **outra** pessoa
   moderadora (`403` para quem decidiu). Reverter aplica a nova medida e corrige o resultado das denúncias.
6. **Abuso de denúncia** (ECA Digital, arts. 32 e 33): até 10 denúncias por hora e 50 por dia por conta; 5 por hora
   no formulário público por origem (`429` com `Retry-After`). Quem tem 3 ou mais improcedentes em 90 dias e 70 % ou
   mais de improcedentes desce um nível na fila e deixa de provocar a retirada preventiva; não há sanção nem aviso
   (sem retaliação).
7. **Integridade das avaliações** ([RF8](../RF8/README.md)):
   - sinais na criação (`ReviewIntegrityService`): 3 ou mais avaliações de contas com menos de 7 dias no mesmo lugar em
     24 h (`BURST_NEW_ACCOUNTS`) e contas diferentes com o mesmo HMAC de IP no lugar em 30 dias (`SAME_ORIGIN`) põem as
     envolvidas em `UNDER_REVIEW` (fora da lista e do agregado) e abrem caso `SYSTEM` com a decisão preventiva
     automática; o vínculo declarado (`disclosure`) só registra o sinal e muda o peso;
   - a partir de 5 na janela, **congela** novas avaliações do lugar por 7 dias, com o alerta "Atividade fora do padrão"
     e um caso sobre a página; avaliação nova dá `409` com a data de fim, editar a própria continua;
   - a moderação publica e encerra alertas (`AVALIACAO_PAGA`, `ATIVIDADE_SUSPEITA`, `ATENCAO_PUBLICA`), com ou sem
     congelamento de 0 a 30 dias; `GET /establishments/{id}/integrity` é público, com o histórico e "Como verificamos
     as avaliações";
   - **contestação pelo dono** (RC-EST-14 / RC-SEG-08): só os motivos `ownerContest`, explicação de 20 a 1.000
     caracteres, até 10 por lugar em 30 dias; a avaliação fica no ar até a decisão; o dono não recebe dado de quem
     avaliou e o recurso de quem contestou segue o item 5.
8. **Relatório** (`GET /moderation/stats?days=30`): denúncias por motivo e por origem, decisões por etapa, ação e
   origem (humana ou automática), regras aplicadas, recursos e comunicações às autoridades, mais os casos na fila,
   em recurso e atrasados. É a base do relatório de transparência (RF18 ⏳).

## Contrato

| Rota | Corpo | Resposta (`ModerationViews`) |
|---|---|---|
| `GET /api/v1/reports/reasons` (público) | — | `[ReasonView {code, label, description, priority, priorityLabel, targets, ownerContest}]` |
| `POST /api/v1/reports` | `{targetType, targetId, reason, details?, childSexualAbuse?}` | `201 ReceiptView {protocol, status, message, createdAt, reviewBy}` · `409` · `422` · `429` |
| `POST /api/v1/reports/public` (público) | `{name, email, targetType, targetId, reason, details?, childSexualAbuse?}` | `201 ReceiptView` · `422` · `429` |
| `POST /api/v1/establishments/{id}/reviews/{reviewId}/contest` | `{reason, details}` | `201 ReceiptView` · `403` (não é o dono) · `422` · `429` |
| `GET /api/v1/establishments/{id}/integrity` (público) | — | `IntegrityView {establishmentId, frozen, frozenUntil, active, history, howWeVerify}` |
| `GET /api/v1/me/reports` | — | `[MyReportView {protocol, target, reason, status, statusLabel, outcome, outcomeLabel, caseId, canAppeal, appealDeadline, appealStatus, …}]` |
| `GET /api/v1/me/moderation/decisions` | — | `[MyDecisionView {caseId, protocol, target, decision, canAppeal, appealDeadline, appealResponseDays, howToAppeal, appeal, appealResult}]` |
| `POST /api/v1/me/moderation/cases/{id}/appeal` | `{reason}` | `201 AppealView` · `409` (fora do prazo, já recorrido, sem decisão) · `404` |
| `GET /api/v1/me/roles` | — | `RolesView {moderator}` |
| `GET /api/v1/moderation/cases?status=OPEN&page=&size=` | — | `CasePageView {items: [CaseSummaryView], page, size, total}` |
| `GET /api/v1/moderation/cases/{id}` | — | `CaseDetailView {summary, snapshot, currentState, reports, signals, decisions, appeals, allowedActions, viewerCanDecide, viewerBlockedReason, …}` |
| `POST /api/v1/moderation/cases/{id}/decision` | `{action, policyReason?, statement}` | `CaseDetailView` · `403` · `409` · `422` |
| `POST /api/v1/moderation/cases/{id}/appeal-decision` | `{outcome: MAINTAINED\|REVERSED, action?, policyReason?, statement}` | `CaseDetailView` · `403` (quem decidiu) · `409` · `422` |
| `POST /api/v1/moderation/cases/{id}/authority-referral` | `{reference}` | `CaseDetailView` |
| `POST /api/v1/moderation/establishments/{id}/alerts` | `{kind, message?, freezeDays?}` | `201 ConsumerAlertView` |
| `POST /api/v1/moderation/establishments/{id}/alerts/{alertId}/lift` | — | `ConsumerAlertView` |
| `GET /api/v1/moderation/stats?days=30` | — | `StatsView {openCases, inAppeal, overdue, reportsByReason, reportsBySource, decisionsByAction, decisionsByPolicy, appeals, authorityReferrals}` |

Na API o alerta ao consumidor é `AlertView`; no pacote do app web, `ConsumerAlertView` (o `AlertView` de lá é o alerta
sanitário do post).

## Decisões a confirmar ⚖️

- Prazos de revisão por prioridade (24 h a 14 dias), prazo de recurso (30 dias) e meta de resposta (7 dias).
- Retirada preventiva para **qualquer** denúncia identificada de criança em risco (o Decreto fala em vítima,
  representante, MP, polícia e entidade habilitada); o menos confiável não retira, só prioriza.
- Avisar o autor de uma retirada preventiva com indício de abuso sexual pode alertar o agressor: hoje ele vê
  "retirado preventivamente" sem o motivo detalhado até a decisão.
- Fluxo e formato da comunicação à Polícia Federal (Centro Nacional de Triagem) e o prazo de preservação.
- Perfil removido = conta suspensa: a pessoa não entra para recorrer no app; o recurso fica pelo canal de suporte ⏳.
- Texto dos alertas ao consumidor (risco de dano à imagem da empresa: factual e com prova guardada) e limiares do
  filtro (3 e 5 contas novas em 24 h; mesma origem em 30 dias).
- Lista de canais na página `/seguranca` (CVV 188, SaferNet, Disque 100, 190).

## Diagramas

[Casos de uso](casos-de-uso.md) · [Atividades](atividades.md) · [Classes](classes.md) · [Componentes](componentes.md) ·
[Sequência](sequencia.md) · [Estados](estados.md)

## Rastreabilidade

| Camada | Artefato | Situação |
|---|---|---|
| Domínio | `ReportReason` (motivo, prioridade, alvos, `ownerContest`), `ModerationPriority` (prazo, `lowered`), `TargetType` (ações por alvo, retirada preventiva), `ModerationAction`, `ReportSource`, `CaseStatus`, `ContentModeration`, `ReviewSignal`, `AlertKind`; `Review.Disclosure` e `putUnderReview`/`removeByModeration`/`restoreByModeration` ([RF8](../RF8/README.md)); `Post`/`PostComment.moderationState` ([RF9](../RF9/README.md), [RF12](../RF12/README.md)) | ✅ |
| Regras | `ReportService` (denúncia, formulário público, contestação, limites, confiabilidade, retirada preventiva, minhas denúncias), `ModerationService` (fila, decisão, conflito de interesse, recurso por outra pessoa, autoridades, relatório, aviso ao autor), `ReviewIntegrityService` (sinais, congelamento, alertas), `ModerationTargets` (cópia e efeito da decisão em cada alvo), `ModerationPolicy` (limites em `friendly-eats.moderation.*` e `friendly-eats.integrity.*`), `ModerationRoles` (papel e concessão por configuração), `ModerationNotifier` (`LoggingModerationNotifier`) | ✅ |
| Persistência | `V5__moderacao.sql`: `user_role`, `moderation_case` (único aberto por alvo), `moderation_report` (única aberta por pessoa e caso), `moderation_decision` (trigger só de inclusão), `moderation_appeal`, `review_signal`, `establishment_alert`, `review.origin_hash`, `post`/`post_comment.moderation_state`; `ModerationRepository`, `IntegrityRepository`, `UserRoleRepository` | ✅ |
| API | `ReportController`, `ModerationController`; `ROLE_MODERATOR` e rotas públicas no `SecurityConfig`, `429` no `ApiExceptionHandler` ([RF4](../RF4/README.md)) | ✅ |
| Testes | `ModerationDomainTest`; `ModerationApiIT.moderationAreaIsOnlyForModerators`, `.reportDecisionAndAppealByAnotherModerator`, `.childAtRiskHidesAtOnceGoesFirstAndIsFlaggedForAuthorities`, `.publicFormIsIdentifiedAndRateLimited`, `.ownerContestsAReviewOnlyForPolicyReasonsAndTheReviewStays`, `.burstOfNewAccountsGoesUnderReviewAndFreezesNewReviews`, `.declaredCourtesyLowersTheWeightAndIsShown`; e2e `moderacao.spec.ts` com axe nos dois temas | ✅ |
| Tela | menu "…" com "Denunciar" (`ActionsMenu`, `ContentActions`, `ReportDialog`) no post, comentário, avaliação, perfil e lugar; `/denunciar` sem conta; `/moderacao` (fila em abas, detalhe, decisão, recurso, autoridades, alertas, relatório); Configurações → Denúncias e decisões (`MyModerationView`, `AppealDialog`); alerta ao consumidor e congelamento na página do lugar (`IntegrityBanner`, `MyReviewArea`); contestação do dono (`ContestDialog`); vínculo declarado no formulário de avaliação; link "Moderação" no cabeçalho só para moderador | ✅ |
| Pendências | e-mail transacional (hoje o aviso fica no app e no log); sinalizadores confiáveis e sanções em escada por abuso (RC-SEG-03); resposta pública do dono (`RULE_OWNER_REPLY`); relatório público de transparência (RF18); purga do conteúdo removido depois do prazo de recurso e da preservação; recurso de conta suspensa; job que encerra o caso `DECIDED` vencido; similaridade de texto, aparelho e IA no antifraude; revisão humana de decisões automáticas além do caso na fila; R8 e R10 da taxonomia (verificação do lugar, RF20) | ⏳ |
