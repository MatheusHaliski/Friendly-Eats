# CLAUDE.md — Friendly Eats

Rede social de comida e varejo de restaurantes. Backend Spring Boot 4.1 / Java 21+ (alvo 25) com PostgreSQL + PostGIS,
Valkey, Cassandra e API S3. Documentação e mensagens ao usuário em **português (pt-BR)**.

## Regra obrigatória: uma pasta por RF, com os diagramas sempre atualizados

Todo requisito funcional (RF) tem a pasta `docs/RFn/` com:

| Arquivo | Conteúdo |
|---|---|
| `README.md` | cabeçalho (`rf`, `titulo`, `status`, `cards`, `paths`), critérios de aceite, base legal e rastreabilidade |
| `casos-de-uso.md` | diagrama de casos de uso (atores à esquerda, secundários à direita) |
| `atividades.md` | diagrama de atividades (fluxo e validações) |
| `classes.md` | classes de domínio, serviços e views que o RF usa |
| `componentes.md` | componentes (controller → serviço → repositório → armazenamento) |
| `sequencia.md` | sequência das chamadas principais |
| `estados.md` | máquina de estados do que o RF muda |

Ao trabalhar em qualquer RF:

- **RF novo** → criar `docs/RFn/` com os sete arquivos e adicionar a linha no catálogo `docs/README.md`.
- **Mudança de comportamento** em arquivo listado em `paths` de um RF → atualizar no **mesmo commit/PR** os diagramas
  e a rastreabilidade daquele RF.
- **Arquivo novo que implementa um RF** → incluí-lo em `paths` do RF correspondente.
- Ator, atributo, categoria ou card novo → `docs/TAXONOMIA_FRIENDLY_EATS.md`.
- `[rf-docs-skip: RFn, …] motivo` (ou `[rf-docs-skip]` para todos), **no início de uma linha** da mensagem de
  commit, só para mudanças sem efeito no comportamento daqueles RFs.

Depois de commitar, rode `python3 scripts/check_rf_docs.py --base <último commit enviado>`. O job **Docs por RF** do CI
repete essa verificação e renderiza todo Mermaid de `docs/`; diagrama inválido ou desatualizado falha o build.

## Comandos

```bash
mvn test                                   # unitários
mvn verify                                 # + integração com Testcontainers (exige Docker)
python3 scripts/check_rf_docs.py           # estrutura das pastas docs/RFn
python3 scripts/wcag_contrast.py           # contraste das cores dos tipos de conta
docker compose up -d                       # PostGIS, Valkey, Garage (+ --profile full para Cassandra)
```

## Convenções

- `schema.sql` é a DDL de referência e deve ser a concatenação exata de `db/migration/V1..Vn` (`SchemaConsistencyTest`).
  Mudança de schema = nova migração `V{n}__*.sql` (começando com uma linha em branco) + o mesmo texto anexado a `schema.sql`.
- Identidade na API: sessão opaca no Valkey (Spring Session) criada por `/api/v1/auth/*` (RF4); escrita exige
  `X-XSRF-TOKEN`. Nunca JWT/token em localStorage. Testes de integração entram com login real (`support/ApiClient`).
- Segredos: `FRIENDLY_EATS_APP_SECRET` (Base64, 32+ bytes) é obrigatório fora dos perfis `dev`/`it`; nada de segredo em yml.
- Termos de Uso do Friendly Eats em `docs/legal/TERMOS_DE_USO.md` (marcadores `{{…}}`); mudar o texto = nova
  `friendly-eats.legal.terms-version`.
- Estilos alimentares de uma pessoa podem revelar saúde ou religião: dado sensível (LGPD Art. 11), só com consentimento.
- Cores, glifos e contraste seguem `docs/DESIGN_SYSTEM.md`; a cor nunca é o único sinal.
- Decisões de tecnologia ficam em `docs/PESQUISA_TECNOLOGIAS.md` (ADRs); backlog no quadro Trello
  "Friendly Eats · Rede Social de Comida".
