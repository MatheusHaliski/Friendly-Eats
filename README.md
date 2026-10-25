# Friendly Eats

Rede social de comida e varejo de restaurantes: culinárias, tipos de comida, estabelecimentos, preço, local e
avaliações de 1 a 5 estrelas. Comensais, criadores, chefs, estabelecimentos, varejo, comunidades e órgãos oficiais
publicam com cor + glifo + rótulo, e a cor nunca é o único sinal.

## Stack

| Camada | Tecnologia |
|---|---|
| Backend | Java 21+ (CI em 21 e 25), Spring Boot 4.1, Spring MVC, Bean Validation |
| Web | [`frontend/`](frontend/README.md): Next.js 16 (App Router), React 19, TypeScript, Tailwind v4, TanStack Query v5, lucide-react; tokens DTCG → Style Dictionary 5; Vitest e Playwright + axe |
| Segurança | Spring Security 7 (CSRF SPA), sessão opaca no Valkey (Spring Session), Argon2id, TOTP + códigos de recuperação |
| Dados | PostgreSQL 17 + PostGIS 3.5 (Flyway), Valkey 9, Cassandra 5 |
| Mídia | API S3 (AWS SDK v2): AWS S3 em produção, Garage em dev e nos testes; envio por URL pré-assinada, foto sem EXIF/GPS |
| Testes | JUnit 5, AssertJ, Testcontainers (PostGIS, Valkey, Cassandra, Garage) |
| Qualidade | GitHub Actions, CodeQL, Gitleaks, Trivy, CycloneDX, Jib, Dependabot |
| Docs | uma pasta por RF em [`docs/`](docs/README.md), com casos de uso, atividades, classes, componentes, sequência e estados em Mermaid |

## API (base)

| Endpoint | RF | O que faz |
|---|---|---|
| `GET /api/v1/auth/csrf` · `POST /api/v1/auth/register` · `POST /api/v1/auth/login` · `…/login/2fa` · `…/reactivate` · `…/logout` · `GET /api/v1/auth/session` | RF1, RF4 | Cadastro com tipo de conta e aceite dos Termos; login em etapas (senha → 2FA → reativar) |
| `GET/PATCH /api/v1/me/profile` · `GET/PATCH /api/v1/me/preferences` · `POST /api/v1/me/verify-email` | RF1, RF2 | Perfil, configurações (tema, fundo, aba inicial, privacidade, notificações) e verificação de e-mail |
| `POST /api/v1/media/uploads` · `PUT/DELETE /api/v1/me/photo` | RF2 | Foto de perfil por URL pré-assinada; recorte quadrado de 512 px, sem EXIF/GPS |
| `POST /api/v1/me/password` · `GET/DELETE /api/v1/me/sessions` · `/api/v1/me/mfa/**` | RF4 | Senha, sessões ativas e 2FA |
| `POST /api/v1/me/deletion` | RF5 | Excluir conta: 30 dias para reativar, depois eliminação |
| `GET /api/v1/legal/terms` | RF1 | Versão vigente dos [Termos de Uso](docs/legal/TERMOS_DE_USO.md) |
| `GET /api/v1/taxonomy` | RF7 | Vocabulários da [taxonomia](docs/TAXONOMIA_FRIENDLY_EATS.md): culinárias, formatos, tipos, preço, estrelas e chips |
| `GET /api/v1/establishments` | RF10 | Busca: nome, chip, culinária (com as regionais), formato, tipo, preço, nota mínima, estilo, município e raio |
| `POST /api/v1/establishments` · `GET/PATCH /api/v1/establishments/{id}` · `PUT …/classification` · `GET /api/v1/me/establishments` | RF6, RF7 | Cadastro do lugar, página e classificação |
| `GET /api/v1/establishments/{id}/reviews` · `PUT/GET/DELETE …/reviews/me` | RF8 | Avaliações de 1 a 5 estrelas e nota agregada |

Erros em Problem Details (RFC 9457) com mensagem em pt-BR. Toda escrita leva `X-XSRF-TOKEN` (pegue em
`GET /api/v1/auth/csrf`).

## Ambiente local

```bash
docker compose up -d                  # PostgreSQL+PostGIS :5432 · Valkey :6379 · Garage (S3 :3900, leitura :3902)
docker compose --profile full up -d   # + Cassandra 5 :9042
mvn spring-boot:run                   # perfil dev: dados de exemplo (DevSeed) e o código de e-mail no log
cd frontend && npm install && npm run dev   # app web em http://localhost:3000 (repassa /api para :8080)
```

No perfil `dev`, o banco vazio recebe dados de exemplo (`DevSeed`): 10 lugares em São Paulo, Belo Horizonte e Recife
com avaliações. Entre com `ana@friendlyeats.dev` (comensal), `dono@friendlyeats.dev` (estabelecimento) ou
`lu@friendlyeats.dev` (cozinha caseira com CPF); a senha está em `DevSeed.PASSWORD`. O perfil `dev` sobe sem
Cassandra, que ainda não é usado pelo código.

Fora do perfil `dev`, defina `FRIENDLY_EATS_APP_SECRET` (Base64, 32+ bytes, ex.: `openssl rand -base64 32`).
Credenciais de desenvolvimento: PostgreSQL `friendly`/`friendly`; Garage com a chave pública de dev do
`application-dev.yml` (criada por `infra/garage/init.sh`, só vale na sua máquina), bucket `friendly-eats-media`.

## Build e testes

```bash
mvn test                        # unitários (sem Docker)
mvn verify                      # + integração *IT com Testcontainers
python3 scripts/check_rf_docs.py
cd frontend && npm run lint && npm run typecheck && npm test && npm run build && npm run e2e
```
