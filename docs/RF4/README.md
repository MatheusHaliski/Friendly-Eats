---
rf: RF4
titulo: Login, sessão, controle de acesso e verificação em duas etapas (2FA)
status: parcial
cards: [FE-04]
paths:
  - src/main/java/com/friendlyeats/security/**
  - src/main/java/com/friendlyeats/application/account/AuthService.java
  - src/main/java/com/friendlyeats/application/account/MfaService.java
  - src/main/java/com/friendlyeats/application/account/StepUp.java
  - src/main/java/com/friendlyeats/application/account/AccountExceptions.java
  - src/main/java/com/friendlyeats/application/account/AccountNotifier.java
  - src/main/java/com/friendlyeats/application/account/LoggingAccountNotifier.java
  - src/main/java/com/friendlyeats/application/common/CommonExceptions.java
  - src/main/java/com/friendlyeats/domain/model/UserMfa.java
  - src/main/java/com/friendlyeats/domain/model/RecoveryCode.java
  - src/main/java/com/friendlyeats/infrastructure/persistence/postgres/UserMfaRepository.java
  - src/main/java/com/friendlyeats/infrastructure/persistence/postgres/RecoveryCodeRepository.java
  - src/main/java/com/friendlyeats/api/AuthController.java
  - src/main/java/com/friendlyeats/api/MeController.java
  - src/main/java/com/friendlyeats/api/ViewerResolver.java
  - src/main/java/com/friendlyeats/api/ApiExceptionHandler.java
  - frontend/apps/web/app/entrar/page.tsx
  - frontend/apps/web/app/conta/page.tsx
  - frontend/apps/web/components/auth/LoginView.tsx
  - frontend/apps/web/components/account/AccountView.tsx
  - frontend/apps/web/components/SiteHeader.tsx
  - frontend/apps/web/components/Providers.tsx
  - frontend/apps/web/lib/queries.ts
  - frontend/apps/web/lib/safe-redirect.ts
  - frontend/apps/web/lib/use-countdown.ts
  - frontend/apps/web/next.config.ts
  - frontend/packages/api/src/client.ts
  - frontend/apps/web/app/configuracoes/seguranca/page.tsx
  - frontend/apps/web/components/settings/SecuritySection.tsx
  - frontend/apps/web/components/settings/MfaPanel.tsx
  - frontend/apps/web/components/settings/QrCode.tsx
  - frontend/apps/web/components/settings/RecoveryCodes.tsx
  - frontend/apps/web/components/settings/SudoFields.tsx
  - frontend/apps/web/lib/use-leave.ts
  - frontend/apps/web/components/Form.tsx
---

# RF4 · Login, sessão, controle de acesso e 2FA

> **Como** usuário, **quero** entrar com segurança, ver e encerrar minhas sessões e proteger a conta com um segundo
> fator **para** que ninguém use minha conta, e **sair de verdade** quando clico em "Sair" ou fecho o navegador.

| | |
|---|---|
| **Base** | OWASP Session/Authentication/MFA Cheat Sheets · NIST SP 800-63B-4 (AAL2) · RFC 6238 |
| **Decisão** | Spring Security 7 + **sessão opaca no Valkey** (Spring Session indexado). Nunca JWT nem token em `localStorage` |
| **Status** | 🟡 Parcial: login em etapas, 2FA (TOTP + códigos de recuperação), sessões, CSRF, limite de tentativas e testes prontos; passkeys e e-mail transacional real ainda não |

## Critérios de aceite

1. **Cookie de sessão** sem `Max-Age`, `HttpOnly`, `Secure`, `SameSite=Lax`; em produção `__Host-fe_session`.
2. **Aba anônima abre deslogada**; fechar o navegador encerra a sessão; o servidor a mata depois de 60 min ociosa
   ou 24 h de vida, mesmo se o navegador "restaurar" o cookie.
3. **Sair** (`POST /api/v1/auth/logout`) invalida a sessão no servidor; as outras abas recebem `401` na próxima chamada.
4. **Login em etapas:** senha → código do 2FA, se ativo → oferta de reativação, se a conta estiver em exclusão
   ([RF5](../RF5/README.md)) → sessão com **id novo**. A meia-sessão não acessa nada e expira em 5 min / 5 tentativas.
5. **Mensagens neutras** ("E-mail ou senha incorretos.") e o mesmo custo de hash para e-mail inexistente.
6. **Limite de tentativas:** por conta, espera progressiva a partir da 5ª falha em 15 min, até 15 min (`429` +
   `Retry-After`); por IP, 20/min e 100/h.
7. **2FA por app autenticador:** TOTP SHA-1, 6 dígitos, 30 s, ±1 passo; segredo de 160 bits cifrado com AES-256-GCM;
   **10 códigos de recuperação** de uso único, guardados em Argon2id. Ativar, regenerar e desativar exigem senha
   (+ código). Sem SMS.
   - **Uso único atômico:** o passo do TOTP e o código de recuperação são consumidos por `UPDATE` condicional no banco;
     com várias requisições ao mesmo tempo, só uma entra.
   - **Nunca sobrescreve o fator ativo:** confirmar uma configuração quando o 2FA já foi ligado em outra sessão dá
     `409` (`INSERT … ON CONFLICT DO NOTHING`).
8. **Controle de acesso:** são `GET` públicos os Termos (`/api/v1/legal/**`), a taxonomia (`/api/v1/taxonomy`), a
   busca (`/api/v1/establishments`, [RF10](../RF10/README.md)), a página do estabelecimento
   (`/api/v1/establishments/*`) e a lista de avaliações dele
   (`/api/v1/establishments/*/reviews`), o feed e a página do post (`/api/v1/posts`, `/api/v1/posts/*`,
   `/api/v1/posts/trending`, `/api/v1/establishments/*/posts`, [RF14](../RF14/README.md)), os comentários
   (`/api/v1/posts/*/comments`, [RF12](../RF12/README.md)), os perfis públicos (`/api/v1/users`,
   `/api/v1/users/*/profile`, `/api/v1/users/*/posts`, [RF15](../RF15/README.md)) e o catálogo de molduras
   (`/api/v1/backgrounds/**`, [RF16](../RF16/README.md)); todo o resto exige sessão; escrita exige `X-XSRF-TOKEN`; interagir e enviar
   foto exigem conta `ACTIVE`; conta suspensa não entra. A regra de 18+ dos lugares de bebida alcoólica **não** está no
   filtro, e sim no serviço ([RF6](../RF6/README.md), e o mesmo para o post 18+ no [RF14](../RF14/README.md)): o
   filtro deixa o `GET` passar e o serviço responde `401` com mensagem própria ao visitante e `404` ao menor. O perfil
   privado também é decidido no serviço.
9. **Sessões ativas:** lista com navegador/sistema, criação, último acesso e "esta sessão", **sem expor o id real**;
   encerrar uma ou todas as outras; trocar a senha encerra as outras.

## Matriz de acesso

| Rota | Visitante | Sessão (conta pendente) | Sessão (conta ativa) |
|---|---|---|---|
| `GET /api/v1/legal/**` | ✅ | ✅ | ✅ |
| `GET /api/v1/taxonomy` | ✅ | ✅ | ✅ |
| `GET /api/v1/establishments/{id}` e `GET /api/v1/establishments/{id}/reviews` | ✅ página no ar (lugar 18+: 401 do serviço) | ✅ (menor de 18 em lugar 18+: 404) | ✅ (menor de 18 em lugar 18+: 404) |
| `POST /api/v1/establishments`, `PATCH /api/v1/establishments/{id}`, `PUT …/classification` | 401 | 403 | ✅ só conta de estabelecimento ou varejo (senão 403), e o dono (senão 404) |
| `…/reviews/me` (`GET`, `PUT`, `DELETE`) | 401 | ✅ `GET`/`DELETE` · 403 no `PUT` | ✅ (organização e dono: 403 no `PUT`) |
| `GET /api/v1/posts`, `/api/v1/posts/{id}`, `/api/v1/posts/trending`, `/api/v1/posts/vocabulary`, `/api/v1/posts/{id}/comments`, `/api/v1/establishments/{id}/posts` | ✅ só `LIVRE` (post 18+: 401 do serviço) | ✅ (menor de 18: sem 18+) | ✅ (menor de 18: sem 18+) |
| `GET /api/v1/users`, `/api/v1/users/{id}/profile`, `/api/v1/users/{id}/posts` | ✅ (perfil privado: mínimo e 403 nos posts) | ✅ | ✅ |
| `GET /api/v1/backgrounds/**` (catálogo de molduras) | ✅ | ✅ | ✅ |
| `POST /api/v1/posts`, `PATCH`/`DELETE /api/v1/posts/{id}`, `…/archive`, `…/restore` | 401 | 403 | ✅ pelo tipo de conta e selo (senão 403); só o autor edita (senão 404) |
| `PUT`/`DELETE …/like` e `…/save`, `POST …/comments`, `DELETE …/comments/{id}`, `POST …/shares` | 401 | 403 | ✅ (arquivado ou alerta: 409; comentários fechados: 403) |
| `/api/v1/auth/**` (cadastro, login, 2FA, reativar, sair) | ✅ | ✅ | ✅ |
| `/api/v1/me/**` (perfil, preferências, sessões, 2FA, excluir) | 401 | ✅ | ✅ |
| `POST /api/v1/media/uploads` (URL pré-assinada da foto) | 401 | 403 | ✅ |
| `PUT`/`DELETE /api/v1/me/photo` | 401 | ✅ (o `PUT` dá 422: sem conta ativa não há envio) | ✅ |
| `PUT` na URL pré-assinada (direto no bucket) | 403 do bucket | 403 do bucket | ✅ com a assinatura recebida: até 15 min, mesmo tipo e tamanho |

## Diagramas

[Casos de uso](casos-de-uso.md) · [Atividades](atividades.md) · [Classes](classes.md) · [Componentes](componentes.md) ·
[Sequência](sequencia.md) · [Estados](estados.md)

## Rastreabilidade

| Camada | Artefato | Situação |
|---|---|---|
| Segurança | `SecurityConfig` (CSRF SPA, regras, cabeçalhos; `GET` público de `/api/v1/legal/**`, `/api/v1/taxonomy`, `/api/v1/establishments` (busca), `/api/v1/establishments/*` e `/api/v1/establishments/*/reviews`, dos posts com os comentários e o "em alta" (`/api/v1/posts`, `/api/v1/posts/*`, `/api/v1/posts/*/comments`, `/api/v1/posts/trending`, `/api/v1/establishments/*/posts`), dos perfis (`/api/v1/users`, `/api/v1/users/*/profile`, `/api/v1/users/*/posts`) e de `/api/v1/backgrounds/**`, com as regras de 18+ e de perfil privado no serviço), `SessionService`, `AbsoluteSessionTimeoutFilter`, `LoginThrottle`, `PendingLogin`, `FriendlyPrincipal`, `ClientInfo`, `AppSecrets`, `SecretCipher`, `Totp`, `ProblemResponses` | ✅ |
| Aplicação | `AuthService` (login em etapas), `MfaService`, `StepUp` (modo sudo), `AccountNotifier`; `CommonExceptions.Unauthenticated(String)` para o `401` com mensagem própria (lugar 18+, RF6) | ✅ |
| Persistência | `user_mfa`, `user_recovery_code` (V1); sessões no Valkey `fe:session` | ✅ |
| API | `/api/v1/auth/{csrf,session,register,login,login/2fa,reactivate,logout}`; `/api/v1/me/{password,sessions,mfa,…}`; `ApiExceptionHandler` em Problem Details pt-BR, com `409` (nunca `500`) quando dois pedidos correm para a mesma chave única ou a mesma versão (`DataIntegrityViolationException` com SQLState 23505, `ObjectOptimisticLockingFailureException`) | ✅ |
| Testes | `EstablishmentApiIT.establishmentPageIsPublicOnlyWithTheOwnerSealAndHidesTheHomeKitchenStreet` (GET público sem sessão), `EstablishmentApiIT.organizationsAndOwnersDoNotReviewAndBarsAreForAdults` (401 do serviço para o visitante em lugar 18+), `EstablishmentApiIT.reviewsAreOnePerPersonEditableAndFeedTheRatingAndThePrice` (6 envios simultâneos da mesma avaliação: só 200, 201 ou 409); `PostApiIT.feedPagesByCursorAndHidesExpiredAdultPrivateAndInactiveContent` (feed e página do post sem sessão, 401 do post 18+), `PostApiIT.eachKindHasItsPublishersAndTheOwnersPostsAreAboutTheirOwnPlace` (escrita sem sessão: 401), `UserProfileApiIT.profileFeedListsActivePublicAccountsWithBadgeAndSealAndFilters` (perfis sem sessão); `TotpTest` (vetores da RFC 6238, cifra, timeout absoluto); `AuthApiIT` (aba anônima, cookie, CSRF, limite, logout entre abas, 2FA com anti-replay e códigos de recuperação, sessões; `secondFactorIsSingleUseUnderConcurrencyAndSetupNeverOverwritesAnActiveFactor`: 8 logins simultâneos com o mesmo código e duas configurações concorrentes) | ✅ |
| Tela | app web: `/entrar` (senha, passo do código 2FA, reativação, 429 com contagem regressiva), `/conta` (sessão, sair, "Meus lugares" com `publishBlockers`); cliente da API com `credentials: same-origin`, `X-XSRF-TOKEN` a partir do cookie e nova tentativa se o token estiver velho; `next.config.ts` repassa `/api` ao backend (mesma origem) e manda `X-Content-Type-Options`, `Referrer-Policy: no-referrer` e `frame-ancestors 'none'`; nada de token no `localStorage`; `Providers` avisa as outras abas no logout (`BroadcastChannel`) e, com a sessão, aplica as preferências de exibição da conta ([RF2](../RF2/README.md)), voltando ao padrão quando a sessão acaba; e2e `entrar.spec.ts` e `conta.spec.ts` | ✅ |
| Pendências | passkeys/WebAuthn; e-mail transacional; CAPTCHA depois de muitas falhas | ⏳ |
| Telas de segurança | app web `/configuracoes/seguranca`: trocar a senha (modo sudo), sessões abertas com "Sair" e "Sair de todos os outros aparelhos", alertas de login e o 2FA de ponta a ponta (QR code gerado no navegador, confirmação, códigos de recuperação para copiar ou baixar, gerar novos, desligar); aviso ao sair com mudança não salva (`use-leave`); e2e `configuracoes.spec.ts` com axe | ✅ |
| Foco nos avisos | `/entrar`, senha e sessões e 2FA: o alerta, o resumo de erros e o aviso de concluído recebem o foco depois de montados (`useDeferredFocus`) | ✅ |
