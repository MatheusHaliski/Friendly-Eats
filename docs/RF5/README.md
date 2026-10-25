---
rf: RF5
titulo: Direitos do titular · excluir conta com 30 dias para reativar e eliminação
status: parcial
cards: [FE-12, FE-13]
paths:
  - src/main/java/com/friendlyeats/domain/model/AccountStatus.java
  - src/main/java/com/friendlyeats/domain/model/UserAccount.java
  - src/main/java/com/friendlyeats/application/account/AccountService.java
  - src/main/java/com/friendlyeats/application/account/AccountDeletionJob.java
  - src/main/java/com/friendlyeats/application/account/AccountViews.java
  - src/main/java/com/friendlyeats/application/media/MediaJanitor.java
  - src/main/java/com/friendlyeats/config/SchedulingConfig.java
  - src/main/resources/cassandra/schema.cql
---

# RF5 · Excluir conta, reativar e eliminar

> **Como** titular, **quero** apagar minha conta, com um prazo para mudar de ideia, **para** exercer o direito de
> eliminação que a LGPD me garante.

| | |
|---|---|
| **Base legal (LGPD)** | Art. 18, VI (eliminação) · Art. 16 (conservação permitida) · Art. 37 (registro das operações) |
| **Retenção legal** | Registros de acesso: 6 meses (Marco Civil, art. 15) |
| **Status** | 🟡 Parcial: carência de 30 dias com reativação, job de eliminação (2FA, preferências, anonimização, foto de perfil no S3) e `user_key` (DEK) prontos; exportação, avaliações e fotos dos posts (chegam com RF8/RF9), Cassandra e destruição da DEK ainda não |

## Critérios de aceite

1. `POST /api/v1/me/deletion {password, code, confirm: true}` exige o **modo sudo** (senha + código do 2FA, se ativo).
2. A conta vai para `PENDING_DELETION` com `deletion_scheduled_for = agora + 30 dias`; **todas** as sessões são
   encerradas, inclusive a atual. A resposta é `202 {deletionScheduledFor, graceDays: 30}`.
3. Entrar de novo dentro do prazo oferece **reativar** ([RF4](../RF4/README.md)); reativar volta ao status anterior e
   limpa as datas.
4. Vencido o prazo, o job diário (03:15, `friendly-eats.account.deletion-cron`) elimina uma conta por transação:
   - apaga 2FA, códigos de recuperação e preferências;
   - anonimiza a conta (`ANONYMOUS_LGPD_DELETED`, terminal): nome "Usuário removido", e-mail técnico, sem foto, sem
     gosto (culinárias e estilos alimentares), sem localização, sem campos do tipo;
   - encerra as sessões;
   - **depois do commit**, apaga a foto de perfil do bucket.
5. A saga é idempotente: rodar de novo uma conta já eliminada não muda nada.

## Diagramas

[Casos de uso](casos-de-uso.md) · [Atividades](atividades.md) · [Classes](classes.md) · [Componentes](componentes.md) ·
[Sequência](sequencia.md) · [Estados](estados.md)

## Rastreabilidade

| Camada | Artefato | Situação |
|---|---|---|
| Domínio | `UserAccount.requestDeletion/canReactivate/reactivate/anonymizeForLgpdDeletion`, `AccountStatus` (terminal) | ✅ |
| Aplicação | `AccountService.requestDeletion/eliminate`, `AccountDeletionJob`, `MediaJanitor` | ✅ |
| API | `POST /api/v1/me/deletion`, `POST /api/v1/auth/reactivate` | ✅ |
| Persistência | colunas de exclusão em `user_account`, índice das contas vencidas, `user_key` (DEK) | ✅ |
| Testes | `AccountRulesTest.deletionGraceAllowsReactivationOnlyWithinTheWindow`, `DomainModelTest.lgpdDeletionIsTerminalAndErasesPersonalData`, `AuthApiIT.deletionHidesAccountAllowsReactivationWithin30DaysAndEliminatesAfter`, `ProfilePhotoApiIT.accountEliminationDeletesTheProfilePhotoFromTheBucket` | ✅ |
| Pendências | exportar dados (Art. 18, V) por URL pré-assinada; apagar avaliações, posts e fotos (RF8, RF9); Cassandra; destruir a DEK (crypto-shredding); reconciliação diária | ⏳ |
| Tela | modal de exclusão (`alertdialog` com consequências, data final, "Baixar uma cópia", caixa "Entendo" + senha + código) e oferta de reativação no login | ⏳ |
