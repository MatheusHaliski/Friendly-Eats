---
rf: RF5
titulo: Direitos do titular · excluir conta com 30 dias para reativar e eliminação
status: parcial
cards: [FE-05]
paths:
  - src/main/java/com/friendlyeats/domain/model/AccountStatus.java
  - src/main/java/com/friendlyeats/domain/model/UserAccount.java
  - src/main/java/com/friendlyeats/application/account/AccountService.java
  - src/main/java/com/friendlyeats/application/account/AccountDeletionJob.java
  - src/main/java/com/friendlyeats/application/account/AccountViews.java
  - src/main/java/com/friendlyeats/application/media/MediaJanitor.java
  - src/main/java/com/friendlyeats/application/post/PostContentEraser.java
  - src/main/java/com/friendlyeats/config/SchedulingConfig.java
  - src/main/resources/cassandra/schema.cql
  - frontend/apps/web/app/configuracoes/conta/page.tsx
  - frontend/apps/web/components/settings/AccountSection.tsx
  - frontend/apps/web/components/Dialog.tsx
  - frontend/apps/web/components/auth/LoginView.tsx
  - frontend/apps/web/components/Form.tsx
---

# RF5 · Excluir conta, reativar e eliminar

> **Como** titular, **quero** apagar minha conta, com um prazo para mudar de ideia, **para** exercer o direito de
> eliminação que a LGPD me garante.

| | |
|---|---|
| **Base legal (LGPD)** | Art. 18, VI (eliminação) · Art. 16 (conservação permitida) · Art. 37 (registro das operações) |
| **Retenção legal** | Registros de acesso: 6 meses (Marco Civil, art. 15) |
| **Status** | 🟡 Parcial: carência de 30 dias com reativação, job de eliminação (avaliações escritas, posts, comentários e curtidas, estabelecimentos da conta, 2FA, preferências, anonimização, foto de perfil e fotos dos posts no S3) e `user_key` (DEK) prontos; exportação, Cassandra e destruição da DEK ainda não |

## Critérios de aceite

1. `POST /api/v1/me/deletion {password, code, confirm: true}` exige o **modo sudo** (senha + código do 2FA, se ativo).
2. A conta vai para `PENDING_DELETION` com `deletion_scheduled_for = agora + 30 dias`; **todas** as sessões são
   encerradas, inclusive a atual. A resposta é `202 {deletionScheduledFor, graceDays: 30}`.
   - Durante a carência, as avaliações da pessoa saem da nota agregada e da lista ([RF8](../RF8/README.md)) e as
     páginas dos estabelecimentos dela saem do ar (`404`, [RF6](../RF6/README.md)), assim como os posts e o perfil
     público ([RF14](../RF14/README.md), [RF15](../RF15/README.md)). Nada é apagado: tudo volta se ela reativar.
3. Entrar de novo dentro do prazo oferece **reativar** ([RF4](../RF4/README.md)); reativar volta ao status anterior e
   limpa as datas.
4. Vencido o prazo, o job diário (03:15, `friendly-eats.account.deletion-cron`) elimina uma conta por transação:
   - apaga as **avaliações escritas** pela conta ([RF8](../RF8/README.md)), com as versões anteriores
     (`review_revision`, `ON DELETE CASCADE`);
   - apaga os **estabelecimentos** de que a conta é dona ([RF6](../RF6/README.md)), e as avaliações desses lugares,
     de qualquer autor, saem junto por `ON DELETE CASCADE`;
   - apaga o conteúdo dos **posts** da conta ([RF9](../RF9/README.md)): viram `REMOVED`, com o título "Conteúdo
     removido" e sem texto, foto, receita, alerta nem preço, e saem do ranking "em alta"
     ([RF14](../RF14/README.md)); os **comentários** da conta perdem o texto, as **curtidas e os salvos** saem e os
     contadores dos posts de terceiros são corrigidos; os compartilhamentos perdem o vínculo, mas o total fica
     ([RF12](../RF12/README.md)) — `PostContentEraser`, idempotente;
   - apaga 2FA, códigos de recuperação e preferências;
   - anonimiza a conta (`ANONYMOUS_LGPD_DELETED`, terminal): nome "Usuário removido", e-mail técnico, sem foto, sem
     gosto (culinárias e estilos alimentares), sem localização, sem campos do tipo;
   - encerra as sessões;
   - **depois do commit**, apaga do bucket a foto de perfil e as fotos dos posts.
5. A saga é idempotente: rodar de novo uma conta já eliminada não muda nada.

## Diagramas

[Casos de uso](casos-de-uso.md) · [Atividades](atividades.md) · [Classes](classes.md) · [Componentes](componentes.md) ·
[Sequência](sequencia.md) · [Estados](estados.md)

## Rastreabilidade

| Camada | Artefato | Situação |
|---|---|---|
| Domínio | `UserAccount.requestDeletion/canReactivate/reactivate/anonymizeForLgpdDeletion`, `AccountStatus` (terminal) | ✅ |
| Aplicação | `AccountService.requestDeletion/eliminate` (avaliações, posts, estabelecimentos, 2FA, preferências, anonimização, sessões e fotos), `PostContentEraser` (posts, comentários, curtidas, salvos e compartilhamentos, RF9 e RF12), `AccountDeletionJob`, `MediaJanitor` | ✅ |
| API | `POST /api/v1/me/deletion`, `POST /api/v1/auth/reactivate` | ✅ |
| Persistência | colunas de exclusão em `user_account`, índice das contas vencidas, `user_key` (DEK); `ReviewRepository.deleteAllWrittenBy` e `EstablishmentRepository.deleteAllOwnedBy`, com `ON DELETE CASCADE` de `establishment` para `review` e de `review` para `review_revision` (V2) | ✅ |
| Testes | `AccountRulesTest.deletionGraceAllowsReactivationOnlyWithinTheWindow`, `DomainModelTest.lgpdDeletionIsTerminalAndErasesPersonalData`, `AuthApiIT.deletionHidesAccountAllowsReactivationWithin30DaysAndEliminatesAfter`, `ProfilePhotoApiIT.accountEliminationDeletesTheProfilePhotoFromTheBucket`, `EstablishmentApiIT.accountEliminationRemovesReviewsAndOwnedEstablishments`, `InteractionApiIT.accountEliminationRemovesPostsCommentsLikesAndPhotos` | ✅ |
| Exportar dados | Art. 18, V: pronto no [RF18](../RF18/README.md) ("Seus dados" em Configurações → Conta, ZIP por URL pré-assinada) | ✅ |
| Dados do RF17 e do RF18 | `eliminate` chama `AgeVerificationService.eraseFor`, `GuardianService.eraseFor` (vínculos como menor saem; os que a conta supervisiona terminam e o menor volta às proteções máximas) e `DataExportService.eraseFor` (ZIPs apagados depois do commit); `access_log` fica os 6 meses do Marco Civil; `PrivacyApiIT.eliminationErasesExportsAndAgeVerificationButKeepsTheAccessLog` | ✅ |
| Pendências | Cassandra; destruir a DEK (crypto-shredding); reconciliação diária | ⏳ |
| Tela | modal de exclusão (`alertdialog` com consequências, data final, "Baixar uma cópia", caixa "Entendo" + senha + código) e oferta de reativação no login | ⏳ |
| Telas | app web `/configuracoes/conta`: diálogo "Excluir sua conta?" com as consequências, a caixa de confirmação, senha e código 2FA (`Dialog` com foco preso, Esc fecha e o foco volta); depois do 202 mostra a data-limite para reativar; `/entrar` em exclusão mostra "Sua conta está em exclusão" com "Reativar minha conta" e "Manter a exclusão e sair"; e2e `configuracoes.spec.ts` e `entrar.spec.ts` | ✅ |
| Seus dados | `/configuracoes/conta` abre com o cartão **Seus dados** do [RF18](../RF18/README.md) (pedir a cópia com senha e baixar o ZIP); o diálogo de exclusão aponta para ele e cita os registros de acesso com IP e porta cifrados | ✅ |
| Foco no diálogo | Excluir conta: o resumo de erros e o botão de concluir recebem o foco depois de montados (`useDeferredFocus`); a reativação em `/entrar` segue igual | ✅ |
