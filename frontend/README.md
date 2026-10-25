# Friendly Eats · Web

Frontend web do Friendly Eats: Next.js 16 (App Router), React 19 e TypeScript `strict`, num workspace npm.
Os dados vêm da API Spring do repositório (`src/`); o navegador fala só com a origem do Next, que repassa
`/api/*` ao backend. Assim o cookie de sessão (`fe_session` no perfil dev) e o `XSRF-TOKEN` ficam na mesma origem.

## Como rodar com o backend

```bash
# 1. backend (na raiz do repositório): PostGIS e Valkey + Spring no perfil dev
docker compose up -d
mvn spring-boot:run

# 2. frontend
cd frontend
npm install
npm run dev            # http://localhost:3000 (repassa /api para http://localhost:8080)
```

Outra origem para a API: `API_ORIGIN=https://api.exemplo npm run dev` (vale também no `npm run build`, porque os
`rewrites` são gravados no build). No perfil dev o código de confirmação de e-mail aparece no log do Spring.

## Estrutura

```text
frontend/
├── package.json            workspaces e scripts da raiz
├── eslint.config.mjs       ESLint (flat config, eslint-config-next)
├── vitest.config.ts        um projeto Vitest por pacote
├── packages/
│   ├── tokens/             tokens DTCG (tokens/*.json) → Style Dictionary 5 → dist/tokens.css e dist/tokens.ts
│   └── api/                tipos escritos a partir dos records Java + fetch com sessão e CSRF
└── apps/web/
    ├── app/                rotas: / (Explorar), /lugares/[id], /entrar, /cadastro, /confirmar-email, /termos, /conta,
    │                       /configuracoes/*, /feed, /posts/[id], /posts/[id]/editar, /publicar, /u/[id], /p/[id]
    ├── components/         telas e componentes (explore/, place/, auth/, account/, feed/, post/, publish/, studio/, profile/)
    ├── lib/                filtros na URL, formatação pt-BR, erros por campo, Markdown dos Termos…
    └── e2e/                Playwright + axe; fixtures JSON e API simulada (support/mock-api.ts)
```

- **Tokens**: as cores de `docs/DESIGN_SYSTEM.md` nos dois temas. `tokens.css` tem o claro como padrão, o escuro
  por `prefers-color-scheme` e o escolhido à mão por `data-theme`. O Tailwind v4 lê as variáveis via `@theme`. O teste
  `packages/tokens/test/contraste.test.ts` confere 4,5:1 (texto) e 3:1 (ícones e contornos) contra o fundo.
- **API**: `credentials: 'same-origin'`; antes da primeira escrita busca `GET /api/v1/auth/csrf` se o cookie
  `XSRF-TOKEN` não existir e manda `X-XSRF-TOKEN` em toda escrita (e tenta de novo uma vez se o token estiver velho).
  Erros viram `ApiError` com o Problem Details (`detail`, `errors[]`, `Retry-After`).
- **Nada de token ou dado da conta no `localStorage`**: só a preferência de tema (`fe:tema`).
- **Termos de Uso**: `/termos` é gerada no build a partir de `docs/legal/TERMOS_DE_USO.md`.

## Comandos (dentro de `frontend/`)

| Comando | O que faz |
|---|---|
| `npm run dev` | gera os tokens e sobe o Next em modo dev |
| `npm run lint` | ESLint em todo o workspace |
| `npm run typecheck` | `tsc --noEmit` nos três pacotes (`next typegen` antes, no web) |
| `npm test` | Vitest: tokens (contraste), cliente da API e componentes |
| `npm run build` | `next build` |
| `npm run e2e` | Playwright contra `next build && next start`, com a API simulada por `page.route` |

O Playwright está fixado em **1.56.1**. Para usar um Chromium já instalado em vez de baixar um:

```bash
PW_CHROMIUM=/opt/pw-browsers/chromium-1194/chrome-linux/chrome npm run e2e
E2E_SKIP_BUILD=1 npm run e2e                     # reaproveita o build que acabou de rodar
SCREENSHOTS_DIR=/tmp/telas npm run e2e -- screenshots   # capturas das telas nos dois temas
```

Os testes ponta a ponta não precisam do backend nem de Docker: todo `/api/v1/**` é respondido pelas fixtures de
`apps/web/e2e/fixtures/` (mesmos campos dos records Java) e cada tela principal passa pelo axe nos dois temas, sem
violação `serious` ou `critical`.

## Observações

- O `package-lock.json` foi gerado com `npx npm@11 install`: o npm 10 tem um erro de resolução de peers opcionais
  (`Cannot read properties of null (reading 'edgesOut')`) ao adicionar o Vitest 4. O `npm ci` do npm 10 lê o lock
  normalmente. O `overrides` de `playwright-core` mantém o `@axe-core/playwright` na mesma versão do Playwright.
- `lucide-react` fica em **0.460.0**, a versão citada no `DESIGN_SYSTEM.md` ("um ícone, um significado").
