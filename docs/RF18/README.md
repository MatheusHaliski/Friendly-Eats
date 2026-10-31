---
rf: RF18
titulo: Central do titular e conformidade
status: parcial
cards: [FE-28]
paths:
  - src/main/java/com/friendlyeats/application/privacy/*.java
  - src/main/java/com/friendlyeats/api/PrivacyController.java
  - src/main/java/com/friendlyeats/security/AccessLogCipher.java
  - src/main/java/com/friendlyeats/security/AccessLogFilter.java
  - src/main/java/com/friendlyeats/security/Aead.java
  - src/main/java/com/friendlyeats/application/media/MediaStorage.java
  - src/main/java/com/friendlyeats/infrastructure/storage/S3MediaStorage.java
  - src/main/resources/db/migration/V4__conta_lgpd.sql
  - docs/legal/INCIDENTES.md
  - frontend/apps/web/app/privacidade/page.tsx
  - frontend/apps/web/components/privacy/PrivacyView.tsx
  - frontend/apps/web/components/settings/DataExportCard.tsx
  - frontend/apps/web/lib/titular.ts
  - frontend/apps/web/e2e/titular.spec.ts
---

# RF18 · Central do titular e conformidade

> **Como** titular, com ou sem conta, **quero** baixar uma cópia dos meus dados e saber quem é o encarregado e como
> exercer meus direitos **para** controlar o que o Friendly Eats faz com eles. **Como** empresa, **quero** guardar os
> registros de acesso e os incidentes como a lei manda **para** atender ordens judiciais e a ANPD.

| | |
|---|---|
| **Base legal** | LGPD: art. 18, V (portabilidade e acesso), art. 19, II e §3º (cópia completa em até 15 dias, em formato legível por máquina), art. 41, §1º (encarregado público), art. 48 (incidentes) · Resolução CD/ANPD 15/2024 (3 dias úteis, registro por 5 anos) e 18/2024 (encarregado) · Marco Civil da Internet, art. 15 (registros de acesso por 6 meses, sob sigilo) · Decreto 8.771/2016, art. 13 (controle de acesso, inventário de consultas, cifragem) |
| **Pesquisa** | [RC-SEG-12, 15 e 16](../pesquisa/requisitos/req-03-confianca-legal.md#rc-seg-12--central-do-titular-exportar-meus-dados-pedidos-lgpd-e-canal-do-encarregado) |
| **Status** | 🟡 Parcial: exportação, contato do encarregado, registros de acesso com inventário e registro de incidentes prontos. Pedidos do art. 18 com protocolo, relatório de transparência (RC-SEG-17) e trilha de auditoria encadeada ainda não |

## Decisões (⚖️ = confirmar com o dono do produto ou o jurídico)

1. **Exportação:** um pedido por dia (contando os que não falharam), com o modo sudo (senha e, com 2FA, o código) e
   aviso por e-mail no pedido e quando fica pronto. Prazo de 15 dias gravado em `due_at`; o job roda a cada 30 s, então
   na prática fica pronto em segundos. Fotos vão como **links**, não como arquivos.
2. ⚖️ **Ficam fora da exportação** o hash da senha, o segredo do 2FA, os códigos de recuperação, os HMAC de IP
   (inclusive o `origin_hash` da avaliação, [RF13](../RF13/README.md)) e os **registros de acesso do Marco Civil**,
   que são sigilosos e saem só por ordem judicial. Confirmar se o titular pode pedi-los pelo art. 18, II. Da
   segurança ([RF19](../RF19/README.md)) entram as contas que o titular bloqueou ou silenciou e as palavras filtradas
   (`seguranca.json`), nunca quem bloqueou o titular; da moderação ([RF13](../RF13/README.md)), as denúncias que ele
   fez, os casos sobre o conteúdo dele com as decisões motivadas e os recursos (`moderacao.json`), sem quem denunciou,
   sem o id do moderador e ⚖️ sem o caso encaminhado às autoridades por indício de abuso sexual infantil.
3. **Bucket:** o ZIP fica em `exports/{usuário}/{pedido}.zip` como objeto privado (`Cache-Control: private, no-store`) e
   só sai por **URL pré-assinada de GET**, válida até 72 h depois de pronto. Depois disso o job apaga o objeto e o
   pedido vira `EXPIRED`. `writePrivate` e `presignGet` usam o `S3MediaStorage.bucketFor`: `exports/` (como
   `uploads/`) vai ao bucket **privado** (`friendly-eats.storage.uploads-bucket`), nunca ao de mídias servido pela CDN.
4. **Registros de acesso:** o login sempre grava; para quem está logado, um registro `ACCESS` por usuário e IP a cada
   hora. IP e **porta de origem** ficam cifrados com AES-256-GCM (chave `access-log-v1` derivada do
   `FRIENDLY_EATS_APP_SECRET`, id do usuário como dado associado). O IP segue só como HMAC no resto do sistema.
   ⚖️ Guardar a porta (entendimento do STJ) foi incluído por precaução.
5. **Sem endpoint para os registros:** a consulta é o serviço interno `AccessLogService.consult`, que exige quem
   consulta, motivo e número do processo ou da ordem, e grava o inventário (`access_log_query`). ⚖️ Falta o console
   administrativo com 2FA e papéis (Decreto 8.771, art. 13, I e II).
6. ⚖️/infra: atrás de proxy, ligar `server.forward-headers-strategy` para que o IP e a porta sejam os do cliente.
7. **Incidentes:** o prazo é o fim do 3º dia útil (seg. a sex., Brasília) depois de saber que atingiu dado pessoal.
   ⚖️ Feriados não entram (o prazo fica mais curto, nunca mais longo). Sem tela: runbook em
   [`docs/legal/INCIDENTES.md`](../legal/INCIDENTES.md).
8. **Encarregado:** `friendly-eats.legal.dpo-name`, `dpo-email`, `dpo-substitute-name`, `privacy-email` e
   `controller-name`. Vazio vira "a definir" na página: precisa ser preenchido antes de abrir ao público. Nos Termos o
   nome continua `{{ENCARREGADO_NOME}}`.

## Critérios de aceite

1. `GET /api/v1/legal/privacy-contact` (público, cache de 1 h) →
   `{"controllerName","dpoName","dpoEmail","dpoSubstituteName","privacyEmail","requestDeadlineDays":15,
   "exportLinkHours":72,"rights":[{"code","label","article","how"}]}`.
2. `POST /api/v1/me/data-exports {"password","code"}` → 202
   `{"id","status":"REQUESTED","statusLabel","requestedAt","dueAt","readyAt","expiresAt","sizeBytes","downloadUrl":null}`;
   senha errada → 403; segundo pedido em 24 h → 429 com `Retry-After`.
3. `GET /api/v1/me/data-exports` → os 10 últimos pedidos; `downloadUrl` só com `READY` dentro da validade.
4. O ZIP traz `LEIA-ME.txt` e um JSON por assunto: conta, perfil, preferências, consentimentos (atual e histórico),
   termos aceitos, sessões, posts (com o link da foto), comentários, curtidas, salvos, compartilhamentos, avaliações
   (com as versões anteriores), estabelecimentos, **idade** e **responsável** ([RF17](../RF17/README.md)) e os próprios
   pedidos de exportação.
5. Estados do pedido: `REQUESTED → PROCESSING → READY → EXPIRED`; `FAILED` depois de 3 tentativas; um pedido preso em
   `PROCESSING` por 30 min volta para a fila.
6. `access_log`: `LOGIN` em todo login (`SessionService.establish`) e `ACCESS` pelo `AccessLogFilter`; retenção de
   180 dias com expurgo diário (`ComplianceJob`), salvo `legal_hold_until` (art. 15, §2º).
7. `AccessLogService.consult` sem motivo, ordem ou período → erro; com eles, devolve IP e porta decifrados e grava
   `access_log_query` (quem, motivo, ordem, alvo, período, linhas).
8. `security_incident`: registro com prazo de comunicação, decisão com motivo (inclusive a de não comunicar), datas de
   comunicação, guarda de 5 anos e alerta diário dos vencidos sem decisão.
9. Termos de Uso revisados (versão `2026-10-31.1`): regime do STF (Temas 987 e 533), recurso como direito,
   verificação de idade, vínculo do responsável, exportação e registros de acesso.

## Diagramas

[Casos de uso](casos-de-uso.md) · [Atividades](atividades.md) · [Classes](classes.md) · [Componentes](componentes.md) ·
[Sequência](sequencia.md) · [Estados](estados.md)

## Rastreabilidade

| Camada | Artefato | Situação |
|---|---|---|
| Persistência | `data_export`, `access_log`, `access_log_query`, `security_incident` em `V4__conta_lgpd.sql` | ✅ |
| Encarregado | `PrivacyContact` + `GET /api/v1/legal/privacy-contact` (`PrivacyController`, público em `SecurityConfig`); `PrivacyApiIT` | ✅ |
| Exportação | `DataExportService`, `DataExportBuilder` (com `seguranca.json` do RF19 e `moderacao.json` do RF13), `DataExportJob`; `MediaStorage.writePrivate` e `presignGet` (`S3MediaStorage`, no bucket privado pelo `bucketFor`); `PrivacyApiIT` baixa pela URL pré-assinada (que aponta para o bucket privado) e lê o ZIP, com o bloqueio e a palavra filtrada da titular e sem quem a bloqueou | ✅ |
| Avisos | `AccountNotifier.dataExportRequested` e `dataExportReady` | ✅ |
| Registros de acesso | `AccessLogService`, `AccessLogFilter`, `AccessLogCipher` (`Aead`), `SessionService.establish`; `PrivacyApiIT`, `PrivacyRulesTest` | ✅ |
| Expurgo e alertas | `ComplianceJob` (diário): registros com mais de 6 meses, incidentes com mais de 5 anos, prazos vencidos | ✅ |
| Incidentes | `SecurityIncidentService` + runbook `docs/legal/INCIDENTES.md`; `PrivacyApiIT`, `PrivacyRulesTest` | ✅ |
| Termos de Uso | `docs/legal/TERMOS_DE_USO.md` e `friendly-eats.legal.terms-version` = `2026-10-31.1` (o app pede reaceite) | ✅ |
| Pedidos do art. 18 com protocolo e prazo (correção, anonimização, oposição) | — | ⏳ |
| Console administrativo com 2FA e papéis para consultar registros e incidentes | — | ⏳ ⚖️ |
| Trilha de auditoria append-only com hash encadeado | — | ⏳ |
| Relatório de transparência (RC-SEG-17) | — | ⏳ |
| Telas | app web: `/privacidade` pública (link no rodapé) com o encarregado, o prazo de 15 dias, os direitos com o caminho de cada um e o formulário que monta o e-mail ao encarregado (funciona sem conta, `privacyRequestMailto`); **Seus dados** em `/configuracoes/conta` (`DataExportCard`): pedido com a senha (e o código, com 2FA) num diálogo, um por dia, status que se atualiza enquanto prepara, link "Baixar ZIP" com a URL pré-assinada; axe nos dois temas; e2e `titular.spec.ts` com a fixture `privacidade-contato.json` | ✅ |
