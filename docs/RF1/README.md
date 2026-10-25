---
rf: RF1
titulo: Cadastro com tipo de conta e aceite dos Termos de Uso
status: parcial
cards: [FE-01, FE-07]
paths:
  - src/main/java/com/friendlyeats/domain/model/UserAccount.java
  - src/main/java/com/friendlyeats/domain/model/UserCategoryEnum.java
  - src/main/java/com/friendlyeats/domain/model/AccountStatus.java
  - src/main/java/com/friendlyeats/domain/model/CategoryVerification.java
  - src/main/java/com/friendlyeats/domain/model/Cnpj.java
  - src/main/java/com/friendlyeats/domain/model/Cpf.java
  - src/main/java/com/friendlyeats/domain/model/TermsAcceptance.java
  - src/main/java/com/friendlyeats/application/account/RegistrationService.java
  - src/main/java/com/friendlyeats/application/account/UserTypeDetailsValidator.java
  - src/main/java/com/friendlyeats/application/account/AgePolicy.java
  - src/main/java/com/friendlyeats/application/account/PasswordPolicy.java
  - src/main/java/com/friendlyeats/application/account/EmailVerificationService.java
  - src/main/java/com/friendlyeats/application/account/LegalDocuments.java
  - src/main/java/com/friendlyeats/api/AuthController.java
  - src/main/java/com/friendlyeats/api/LegalController.java
  - src/main/java/com/friendlyeats/infrastructure/persistence/postgres/UserAccountRepository.java
  - src/main/java/com/friendlyeats/infrastructure/persistence/postgres/TermsAcceptanceRepository.java
  - src/main/resources/db/migration/V1__baseline.sql
  - docs/legal/TERMOS_DE_USO.md
---

# RF1 · Cadastro com tipo de conta

> **Como** visitante, **quero** criar uma conta escolhendo meu tipo (comensal, criador, chef, estabelecimento,
> varejo, comunidade ou órgão oficial) e aceitando os Termos de Uso **para** avaliar, publicar e ser encontrado com
> o selo certo.

| | |
|---|---|
| **Base legal (LGPD)** | Art. 7º, V (execução de contrato) para os dados do cadastro e o aceite dos Termos, inclusive o CPF do negócio informal, usado só para conferir o selo e nunca exibido (Art. 6º, III); Art. 7º, I (consentimento) para os usos opcionais, que começam desligados ([RF3](../RF3/README.md)) |
| **Termos de Uso** | [`docs/legal/TERMOS_DE_USO.md`](../legal/TERMOS_DE_USO.md), com marcadores `{{…}}` para os dados da empresa; aceite obrigatório, caixa desmarcada |
| **ECA Digital** | Mínimo 13 anos; menor de 16 informa o e-mail do responsável e nasce com proteções travadas; qualquer tipo que não seja comensal exige 18+ |
| **Status** | 🟡 Parcial: API, regras, verificação de e-mail e testes prontos; envio real de e-mail, consulta automática de CNPJ e telas ainda não |

## Critérios de aceite

1. Dados mínimos: nome de exibição, `@usuário` único (3–30, `a-z 0-9 . _`), e-mail único (minúsculas), senha de 12 a
   128 caracteres (Argon2id), data de nascimento, **tipo de conta** e aceite da **versão vigente** dos Termos.
2. Campos específicos do tipo em `type_details`; chave fora da lista é recusada (minimização).
   - **Criador:** formato (`CRITICO`, `BLOG`, `VIDEO`, `PODCAST`, `GUIA`), nome do canal e site.
   - **Chef:** função (`CHEF`, `COZINHEIRO`, `CONFEITEIRO`, `PADEIRO`, `BARISTA`, `SOMMELIER`, `BARTENDER`, `NUTRICIONISTA`);
     registro profissional opcional e privado.
   - **Estabelecimento:** CNPJ, razão social, nome fantasia, **CNAE** (formato `5611-2/01`), cidade/UF e representante.
   - **Estabelecimento sem CNPJ** (negócio informal): `informalFormat` (`AMBULANTE`, `FOOD_TRUCK`, `COZINHA_CASEIRA`),
     **CPF do titular** (dígitos verificadores conferidos, guardado sem pontuação e **privado**), nome fantasia e
     cidade/UF; site opcional. Razão social, CNAE, representante e CNPJ não se aplicam e são recusados. Sem
     `informalFormat` e sem CNPJ, o erro explica a alternativa.
   - **Varejo & Marca:** segmento (`MERCADO`, `EMPORIO`, `INDUSTRIA`, `DISTRIBUIDOR`, `PRODUTOR`), CNPJ, razão social, CNAE e representante.
   - **Comunidade:** tipo (`CONFRARIA`, `CLUBE`, `COLETIVO`, `FEIRA`, `GRUPO_INFORMAL`), cidade/UF e contato.
   - **Oficial:** órgão (`ANVISA`, `VIGILANCIA_ESTADUAL`, `VIGILANCIA_MUNICIPAL`, `PROCON`, `MINISTERIO_AGRICULTURA`, `OUTRO_ORGAO`), CNPJ, site e representante.
   - CNPJ aceita o formato **alfanumérico** (IN RFB 2.229/2024).
3. Estabelecimento (com CNPJ ou com CPF), varejo e oficial nascem com selo `PENDING`: leem e interagem, mas só publicam
   como organização depois da moderação. A página de um estabelecimento só fica pública com o selo `VERIFIED` da conta
   dona ([RF6](../RF6/README.md)). Os demais nascem `NOT_REQUIRED`. No negócio com CPF, a moderação confere a
   identidade do titular e, quando houver, o alvará ou a licença sanitária.
4. Idade: menos de 13 é recusado; menos de 16 exige `guardianEmail` e preferências protetivas; tipo diferente de
   comensal exige 18+.
5. Na mesma transação: conta `PENDING_VERIFICATION`, linha em `terms_acceptance` (versão, canal, HMAC do IP),
   **5 consentimentos com `granted = false`** e preferências padrão.
6. O cadastro já abre a sessão; um código de 6 dígitos vai por e-mail (30 min, 5 tentativas, reenvio 1/min) e
   confirmar leva a conta a `ACTIVE`. Sem confirmar, a pessoa lê mas não interage (`403`).
7. Todos os erros de validação voltam juntos (`422` com `errors[]`).

## Tipos de conta

Cor + glifo + rótulo andam sempre juntos: a cor nunca é o único sinal (WCAG 1.4.1). Todas as cores passam 4,5:1
contra o fundo do seu tema (calculado na pesquisa visual; daltonismo simulado).

| Código | Rótulo | Glifo | Ícone (Lucide) | Tema escuro (`#17120D`) | Tema claro (`#FFFFFF`) | Selo |
|---|---|---|---|---|---|---|
| `COMENSAL` (A) | Comensal | ◇ losango | `user-round` | `#48A8FF` | `#05558F` | `NOT_REQUIRED` |
| `CRIADOR` (B) | Criador | ✦ estrela | `camera` | `#F7C638` | `#876A06` | `NOT_REQUIRED` |
| `CHEF` (C) | Chef | ⬠ pentágono | `chef-hat` | `#D6C5FF` | `#7D5BBA` | `NOT_REQUIRED` |
| `ESTABELECIMENTO` (D) | Estabelecimento | ▭ retângulo | `store` | `#E88F12` | `#7F4D06` | **`PENDING`** |
| `VAREJO` (E) | Varejo & Marca | △ triângulo | `shopping-basket` | `#EE559F` | `#96075A` | **`PENDING`** |
| `COMUNIDADE` (F) | Comunidade | ⬢ hexágono | `users` | `#08DABD` | `#0B8170` | `NOT_REQUIRED` |
| `OFICIAL` (G) | Oficial | ◎ círculo duplo | `landmark` | `#0C0A07` + anel `#D6D0C8` | `#0C0A07` | **`PENDING`** |

O preto da conta Oficial dá 1,06:1 sobre o fundo escuro; o anel de 2 px `#D6D0C8` é obrigatório (12,15:1).

## Diagramas

[Casos de uso](casos-de-uso.md) · [Atividades](atividades.md) · [Classes](classes.md) · [Componentes](componentes.md) ·
[Sequência](sequencia.md) · [Estados](estados.md)

## Rastreabilidade

| Camada | Artefato | Situação |
|---|---|---|
| Domínio | `UserAccount` (e-mail normalizado, `markEmailVerified`), `UserCategoryEnum` (7 tipos, `needsSeal`, `canPublishFoodAlerts`), `CategoryVerification`, `Cnpj`, `Cpf`, `TermsAcceptance` | ✅ |
| Regras | `RegistrationService`, `UserTypeDetailsValidator` (campos por tipo, CNAE, estabelecimento com CPF), `AgePolicy`, `PasswordPolicy`, `EmailVerificationService`, `LegalDocuments` | ✅ |
| Persistência | `V1__baseline.sql`: `user_account`, `terms_acceptance`, `consent_event`, `user_preference` | ✅ |
| API | `POST /api/v1/auth/register`, `GET /api/v1/legal/terms`, `POST /api/v1/me/verify-email` (+ `/resend`), `POST /api/v1/me/terms` | ✅ |
| Testes | `AccountRulesTest` (campos por tipo, CNAE, selo, CPF, `informalPlaceRegistersWithAPrivateCpfInsteadOfCnpj`), `DomainModelTest`, `AuthApiIT.registerWithChosenTypeAndTermsStartsASessionThatAnIncognitoWindowDoesNotSee`, `AuthApiIT.registrationRulesTermsTypeFieldsAgeAndUniqueness`, `AuthApiIT.minorsKeepProtectiveDefaults` | ✅ |
| Termos | preencher os `{{…}}` e passar por revisão jurídica | 🟡 |
| Pendências | e-mail transacional real; eliminar cadastro não confirmado em 7 dias; consulta de CNPJ e CNAE (BrasilAPI/Receita) | ⏳ |
| Tela | protótipo e app | ⏳ |
