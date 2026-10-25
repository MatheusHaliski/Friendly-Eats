---
rf: RF4
titulo: Login, sessão, controle de acesso e verificação em duas etapas (2FA)
status: parcial
cards: [FE-09, FE-10, FE-11]
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
8. **Controle de acesso:** os Termos são públicos; todo o resto exige sessão; escrita exige `X-XSRF-TOKEN`; interagir
   e enviar foto exigem conta `ACTIVE`; conta suspensa não entra.
9. **Sessões ativas:** lista com navegador/sistema, criação, último acesso e "esta sessão", **sem expor o id real**;
   encerrar uma ou todas as outras; trocar a senha encerra as outras.

## Matriz de acesso

| Rota | Visitante | Sessão (conta pendente) | Sessão (conta ativa) |
|---|---|---|---|
| `GET /api/v1/legal/**` | ✅ | ✅ | ✅ |
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
| Segurança | `SecurityConfig` (CSRF SPA, regras, cabeçalhos), `SessionService`, `AbsoluteSessionTimeoutFilter`, `LoginThrottle`, `PendingLogin`, `FriendlyPrincipal`, `ClientInfo`, `AppSecrets`, `SecretCipher`, `Totp`, `ProblemResponses` | ✅ |
| Aplicação | `AuthService` (login em etapas), `MfaService`, `StepUp` (modo sudo), `AccountNotifier` | ✅ |
| Persistência | `user_mfa`, `user_recovery_code` (V1); sessões no Valkey `fe:session` | ✅ |
| API | `/api/v1/auth/{csrf,session,register,login,login/2fa,reactivate,logout}`; `/api/v1/me/{password,sessions,mfa,…}` | ✅ |
| Testes | `TotpTest` (vetores da RFC 6238, cifra, timeout absoluto); `AuthApiIT` (aba anônima, cookie, CSRF, limite, logout entre abas, 2FA com anti-replay e códigos de recuperação, sessões; `secondFactorIsSingleUseUnderConcurrencyAndSetupNeverOverwritesAnActiveFactor`: 8 logins simultâneos com o mesmo código e duas configurações concorrentes) | ✅ |
| Pendências | passkeys/WebAuthn; e-mail transacional; CAPTCHA depois de muitas falhas | ⏳ |
