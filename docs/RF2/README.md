---
rf: RF2
titulo: Perfil, preferências e foto de perfil
status: parcial
cards: [FE-02]
paths:
  - src/main/java/com/friendlyeats/domain/model/UserAccount.java
  - src/main/java/com/friendlyeats/domain/model/UserPreference.java
  - src/main/java/com/friendlyeats/domain/model/GeoLocation.java
  - src/main/java/com/friendlyeats/application/account/ProfileService.java
  - src/main/java/com/friendlyeats/application/account/PreferencesService.java
  - src/main/java/com/friendlyeats/application/account/AccountViews.java
  - src/main/java/com/friendlyeats/application/common/PublicLinks.java
  - src/main/java/com/friendlyeats/application/media/**
  - src/main/java/com/friendlyeats/infrastructure/storage/**
  - src/main/java/com/friendlyeats/infrastructure/persistence/postgres/UserPreferenceRepository.java
  - src/main/java/com/friendlyeats/infrastructure/persistence/postgres/UserAccountRepository.java
  - src/main/java/com/friendlyeats/api/MeController.java
  - src/main/java/com/friendlyeats/api/MediaUploadController.java
  - infra/garage/**
---

# RF2 · Perfil, preferências e foto

> **Como** titular, **quero** corrigir meus dados, trocar a foto e ajustar tema, privacidade e notificações **para**
> manter o perfil exato e decidir o que o app usa sobre mim.

| | |
|---|---|
| **Base legal (LGPD)** | Art. 18, III (correção). Foto sem EXIF/GPS: o local e o aparelho não vão junto (Art. 6º, III, necessidade) |
| **ECA Digital** | Menor de 16: perfil privado, comentários só de seguidores e lembrete de uso travados (`403`); tema e fundo livres |
| **Status** | 🟡 Parcial: perfil, gosto (culinárias favoritas e estilos alimentares), preferências e foto com testes; troca de e-mail e auditoria ainda não |

## Critérios de aceite

1. **Editáveis** (`PATCH /api/v1/me/profile`): nome, `@usuário` (único), bio (até 150), até 4 pronomes, até 5 links
   `https`, país/UF/cidade, e-mail de recuperação, celular E.164 e os campos do tipo de conta (revalidados como no
   [RF1](../RF1/README.md)).
   - **Gosto:** `favoriteCuisines` (até 5 códigos de `Cuisine`, sem repetição, na ordem escolhida) e `dietaryStyles`
     (códigos de `DietaryStyle`). Estilos alimentares podem revelar saúde ou religião: só são gravados com o
     consentimento `SENSITIVE_PROFILE_DATA` ligado ([RF3](../RF3/README.md)), senão `403`; lista vazia apaga sempre.
     Código desconhecido ou mais de 5 culinárias → `422`. Os dois aparecem no `ProfileView`.
2. **Não editáveis por aqui:** e-mail e senha (fluxo com modo sudo, [RF4](../RF4/README.md)), data de nascimento
   (suporte) e tipo de conta.
3. Conta **com selo** que troca nome, `@` ou dados do tipo volta para `PENDING` (nova conferência).
4. **Preferências** (`PATCH /api/v1/me/preferences`, campo ausente = mantém):
   - **Exibição:** tema (sistema/escuro/claro), fundo (`PLAIN` liso, padrão, ou `PATTERN`), idioma, fonte 85–150 %, reduzir movimento;
   - **Privacidade:** perfil privado, quem comenta, mostrar o ano de nascimento;
   - **Conteúdo e bem-estar:** aba inicial (`FEED`, `EXPLORE`, `NEARBY`, `SAVED`), conteúdo sensível, lembrete de 10 a 600 min;
   - **Notificações:** curtidas, comentários, compartilhamentos e **alertas de comida** (recall e vigilância sanitária);
   - **Segurança:** alertas de login.
5. **Foto de perfil:**
   - `POST /api/v1/media/uploads {kind: IMAGE, contentType, sizeBytes}` devolve uma URL de `PUT` direto ao bucket
     (15 min; JPEG/PNG até 10 MB; tipo e tamanho dentro da assinatura). A API nunca recebe os bytes do envio.
   - `PUT /api/v1/me/photo {objectKey}`: a chave precisa ser do titular e existir; senão `422` "Envie a foto de novo".
   - A foto é conferida pelos bytes, recortada em **quadrado no centro**, fica com **512 px**, perde todo metadado
     (EXIF, GPS, aparelho) e vai para `avatars/…`. A anterior sai do bucket depois do commit.
   - `DELETE /api/v1/me/photo` volta às iniciais.

## Diagramas

[Casos de uso](casos-de-uso.md) · [Atividades](atividades.md) · [Classes](classes.md) · [Componentes](componentes.md) ·
[Sequência](sequencia.md) · [Estados](estados.md)

## Rastreabilidade

| Camada | Artefato | Situação |
|---|---|---|
| Domínio | `UserAccount` (perfil público, `favoriteCuisines`, `dietaryStyles`), `UserPreference` (`Theme`, `AppBackground`, `HomeTab`, `CommentAudience`, `SensitiveContent`) | ✅ |
| Aplicação / API | `ProfileService`, `PreferencesService`; `GET/PATCH /api/v1/me/profile`, `GET/PATCH /api/v1/me/preferences`, `PUT/DELETE /api/v1/me/photo` | ✅ |
| Foto | `MediaUploadController`, `MediaUploads` (`Fit.AVATAR`), `ImageSanitizer`, `MediaJanitor`, `S3MediaStorage` (AWS SDK v2) | ✅ |
| Ambiente | Garage no Compose e nos testes (`infra/garage/`, `support/GarageContainer`); `uploads/` expira em 1 dia e CORS para a origem web | ✅ |
| Gosto | `ProfilePatch`/`ProfileView` com `favoriteCuisines` e `dietaryStyles`; estilos conferem `ConsentService.granted` com a conta travada (`UserAccountRepository.lockById`); `ConsentApiIT` | ✅ |
| Testes | `AuthApiIT.preferencesIncludingAppBackgroundAndProfileEditing`, `AuthApiIT.minorsKeepProtectiveDefaults`, `ImageSanitizerTest` (EXIF/GPS some, 8 orientações, recorte de avatar, bomba de descompressão), `ProfilePhotoApiIT` (Garage real: envio pré-assinado, chave alheia, tamanho fora da assinatura, arquivo que não é foto, troca e remoção), `UserAccountRepositoryIT` | ✅ |
| Pendências | troca de e-mail; auditoria | ⏳ |
