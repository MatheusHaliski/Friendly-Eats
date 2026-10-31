---
rf: RF11
titulo: Página do estabelecimento · horários, cobranças, facetas e botões de ação
status: parcial
cards: [FE-17]
paths:
  - src/main/resources/db/migration/V6__pagina_do_lugar.sql
  - src/main/java/com/friendlyeats/domain/model/OpeningHours.java
  - src/main/java/com/friendlyeats/domain/model/PlaceTimeZone.java
  - src/main/java/com/friendlyeats/domain/model/PlaceDetails.java
  - src/main/java/com/friendlyeats/domain/taxonomy/Facet.java
  - src/main/java/com/friendlyeats/domain/taxonomy/ActionProvider.java
  - src/main/java/com/friendlyeats/application/establishment/PlaceDetailsRules.java
  - src/main/java/com/friendlyeats/application/establishment/PlaceDetailsViews.java
  - src/main/java/com/friendlyeats/application/establishment/PlaceEditors.java
  - src/main/java/com/friendlyeats/application/establishment/EstablishmentDrafts.java
  - src/main/java/com/friendlyeats/application/establishment/EstablishmentViews.java
  - src/test/java/com/friendlyeats/application/establishment/PlacePageRulesTest.java
  - frontend/apps/web/app/lugares/novo/page.tsx
  - frontend/apps/web/app/lugares/*/editar/page.tsx
  - frontend/apps/web/components/place/PlaceView.tsx
  - frontend/apps/web/components/place/PlaceSections.tsx
  - frontend/apps/web/components/place/OpenStatusBadge.tsx
  - frontend/apps/web/components/place/PlaceForm.tsx
  - frontend/apps/web/components/place/PlaceMap.tsx
  - frontend/apps/web/components/place/place-icons.tsx
  - frontend/apps/web/lib/place-form.ts
  - frontend/apps/web/lib/place-info.ts
  - frontend/apps/web/e2e/lugar-pagina.spec.ts
  - frontend/apps/web/e2e/lugar-dono.spec.ts
---

# RF11 · Página do estabelecimento: horários, cobranças, facetas e botões de ação

> **Como** comensal, **quero** ver na página do lugar se ele está aberto agora, o horário da semana, as cobranças
> além do cardápio, o que ele oferece (acessibilidade, pet, pagamento) e um botão para reservar, pedir ou chamar no
> WhatsApp **para** decidir sem surpresa. **Como** dono, **quero** declarar tudo isso num formulário, com o pino no
> mapa, sem a plataforma intermediar reserva nem pagamento.

| | |
|---|---|
| **Consumidor (CDC)** | Horário, cobranças e facetas são informação ao consumidor (art. 6º, III; art. 31). A taxa de serviço é gorjeta e **opcional** (Lei 13.419/2017, CLT art. 457): o texto público é fixo, "Taxa de serviço de N% (opcional)". Couvert artístico só com apresentação ao vivo e aviso na entrada; couvert de mesa "cobrado só se você aceitar". Consumação mínima é recusada (orientação dos Procons; CDC art. 39). Desconto ou acréscimo por forma de pagamento à vista do cliente (Lei 10.962/2004, art. 5º-A) |
| **Preço na porta** | A página não substitui a lista afixada na entrada (Decreto 5.903/2006, art. 8º, § 2º): o formulário lembra o dono, e a página mostra o aviso |
| **Acessibilidade e cão-guia** | O cão de assistência é sempre permitido (Lei 11.126/2005) e o atendimento prioritário é garantido (Lei 10.048/2000): as duas facetas são fixas, não se editam. "Não aceita pets" diz junto que o cão de assistência entra. Estado sempre em texto e com ícone, nunca só cor; "declarado pelo estabelecimento", nunca "conforme NBR 9050" |
| **Botões** | Só links, com domínios permitidos por provedor (HTTPS) e WhatsApp como `https://wa.me/<E.164>`. Sem pagamento no app (a plataforma não vira fornecedora solidária na venda). Abrem em outra aba com `rel="noopener noreferrer nofollow"` e o aviso "Você vai sair do Friendly Eats…" |
| **LGPD** | Os dados do lugar não são dado pessoal; a exceção do endereço da cozinha caseira segue o [RF6](../RF6/README.md): o mapa mostra uma área aproximada, nunca o ponto exato, e "Como chegar" só aparece com o endereço completo e público. O mapa usa tiles do OpenStreetMap carregados pelo navegador de quem vê |
| **Taxonomia** | [`TAXONOMIA_FRIENDLY_EATS.md`](../TAXONOMIA_FRIENDLY_EATS.md): facetas (seção 8), preço e origem (9), estrelas e histograma (10), local (11) |
| **Pesquisa** | RC-EST-04, RC-EST-05 e RC-EST-09 em [`pesquisa/requisitos/req-02-estabelecimento-dono.md`](../pesquisa/requisitos/req-02-estabelecimento-dono.md) |
| **Status** | 🟡 Parcial: API, regras, página completa e formulário do dono prontos e testados; confirmações da comunidade, lembrete de feriado, verificação de link quebrado, filtro "aberto agora" na busca e equipe do RF20 ainda não |

## Critérios de aceite

1. **Horários.** `hours.weekly` tem de 0 a 3 faixas por dia (`MONDAY`…`SUNDAY`), "HH:MM"; fechar em "00:00" (ou
   "24:00") é meia-noite e "00:00 a 00:00" é 24 horas. Faixa que fecha antes de abrir **passa da meia-noite e pertence
   ao dia em que começa** (sexta 18:00–02:00 cobre a madrugada de sábado). Faixas do mesmo dia não se sobrepõem, e a da
   madrugada não invade a primeira do dia seguinte. `weekly` nulo = "Horário não informado" (nunca "Fechado").
2. **Datas especiais** (`hours.special`): até 60, até 1 ano à frente, sem repetir a data, cada uma fechada ou com
   faixas e um motivo opcional (até 60 caracteres). Substituem a semana naquela data. As que já passaram são
   descartadas ao gravar e não voltam na página. **Mais de 7 dias seguidos fechados** dão `422` pedindo "fechado
   temporariamente".
3. **Fechado temporariamente** (`temporaryClosure`): `closed` com `reopensOn` opcional, depois de hoje e até 1 ano.
   Passada a data sem o dono desmarcar, o estado vira **"Pode estar fechado"**. "Fechou de vez" continua sendo
   `status: CLOSED` ([RF6](../RF6/README.md)).
4. **Aberto agora** (`openStatus`), calculado **no fuso do lugar** (`PlaceTimeZone`: pela UF, Fernando de Noronha
   pelo código IBGE), não no de quem olha. A ordem: `PERMANENTLY_CLOSED` ("Fechou de vez"), `TEMPORARILY_CLOSED`
   ("· volta em dd/mm/aaaa"), `MAYBE_CLOSED`, e então `OPEN` ("Aberto agora · fecha às 23:00", "(em N min)" na última
   hora, "à meia-noite", "Aberto 24 horas"), `CLOSED` ("Fechado agora · abre às 18:00 / amanhã às 11:30 / terça às
   11:30 / em 25/12 às 11:00") ou `UNKNOWN` ("Horário não informado"). Faixas encostadas (24:00 + 00:00) se juntam
   para dizer quando fecha de fato. `note` traz o horário especial de hoje.
5. **Cobranças declaradas** (`charges`): taxa de serviço de 1% a 20% (0 = sem taxa); couvert `ARTISTICO` (valor por
   pessoa e os dias com apresentação ao vivo, recusado se "Música ao vivo" estiver como Não) ou `ENTRADA_MESA`; até uma
   condição por forma de pagamento (`DESCONTO` ou `ACRESCIMO`, 1% a 20%, recusada se a página diz que o lugar não
   aceita aquela forma). `minimumSpendCents` sempre dá `422` ("Consumação mínima não é aceita…"). A página mostra as
   linhas com o texto fixo, "Declarado pelo estabelecimento em dd/mm/aaaa" (a data muda só quando as cobranças mudam)
   e o aviso do Decreto 5.903. Mudar as facetas sem mandar as cobranças revalida as cobranças gravadas.
6. **Facetas** (`facets`): mapa código → valor das facetas declaráveis da seção 8 (atendimento, ambiente,
   acessibilidade e pagamento, com Wi-Fi): `SIM`/`NAO`, `SIM`/`PARCIAL`/`NAO` (acessibilidade), `DENTRO`/
   `SO_AREA_EXTERNA`/`NAO` (pet), `OBRIGATORIA`/`RECOMENDADA`/`ACEITA`/`NAO_ACEITA` (reserva). Ausente ou
   `DESCONHECIDO` = "Sem informação". **Recusadas** (`422`): as fixas por lei (`CAO_ASSISTENCIA`,
   `ATENDIMENTO_PRIORITARIO`), as **derivadas** (refeições pelo horário; `DELIVERY_APP` pelos links) e as fora do grupo
   (`VALE_REFEICAO` não vale para varejo e polo; `VALE_ALIMENTACAO` só para eles). Trocar o tipo revalida as facetas.
   A página agrupa por seção, cada item com ícone, rótulo, valor em texto e a origem ("Declarado pelo
   estabelecimento", "Garantido por lei", "Pelo horário de funcionamento", "Pelos links de delivery").
7. **Botões de ação** (`actions`): um link por provedor, HTTPS, sem usuário nem porta estranha, no domínio do
   provedor ou num subdomínio (comparação exata: `ifood.com.br.golpe.com` e `falsoifood.com.br` são recusados); até 2
   de reserva (TheFork, Tagme, Get In, OpenTable) e 5 de delivery (iFood, Rappi, aiqfome, 99Food), com no máximo um
   delivery **preferido**, que vem primeiro; os outros em ordem embaralhada estável no dia. WhatsApp pelo número com
   DDD (8 ou 9 dígitos), gravado em E.164 e exibido como `https://wa.me/55…`. Cada botão tem o rótulo ("Pedir pelo
   iFood") e o aviso de saída.
8. **Página completa** (`GET /api/v1/establishments/{id}`): além do RF6–RF8, `openStatus`, `hours`, `charges`,
   `facets`, `actions` e `viewerCanEdit`. O app mostra cabeçalho, chip, preço com a origem (declarado × pelas
   avaliações, RF6), nota com histograma 1–5 só a partir de 5 avaliações (RF8), facetas, horários, cobranças, botões,
   mapa (pino ou área aproximada, com a atribuição do OpenStreetMap), "Como chegar" só com o endereço completo, "Ver no
   OpenStreetMap", posts do lugar (RF14) e avaliações. O card da busca (RF10) e o modal do Explorar trazem o mesmo
   `openStatus`; o modal resume as cobranças.
9. **Quem edita**: `POST` e `PATCH /api/v1/establishments[/id]` aceitam os blocos `hours`, `temporaryClosure`,
   `charges`, `facets` e `actions`; no `PATCH`, bloco ausente não muda e bloco presente troca por inteiro. Só quem o
   `PlaceEditors` aceita edita (hoje, o dono; os outros recebem `404`) — o ponto de extensão da equipe do
   [RF20](../PESQUISA_REQUISITOS.md). Todos os erros voltam juntos (`422` com `errors[]`) e nada é gravado.
10. **Formulário do dono** (`/lugares/novo` e `/lugares/[id]/editar`, link em "Meus lugares" da `/conta`): tipo e até
    3 secundários (do grupo da conta), classificação do [RF7](../RF7/README.md), local com UF, código IBGE, endereço,
    visibilidade e **pino arrastável** num mapa Leaflet com tiles do OpenStreetMap (sem chave paga), com alternativas
    ao arrasto: clique no mapa, latitude e longitude digitadas e "Pôr o pino no centro do mapa" (WCAG 2.2, 2.5.7);
    preço; horários da semana, datas especiais e pausa; cobranças, com o lembrete do preço na porta; facetas por seção
    (as fixas aparecem como "fixo, não se edita"); links e WhatsApp; e, na edição, a situação (funcionando ou fechou
    de vez). Erros locais e do `422` vão para o campo (resumo no topo com o foco, `aria-describedby` em cada campo); a
    página nasce fora do ar até o selo da conta ser conferido (`publishBlockers`, RF6).

## Diagramas

[Casos de uso](casos-de-uso.md) · [Atividades](atividades.md) · [Classes](classes.md) · [Componentes](componentes.md) ·
[Sequência](sequencia.md) · [Estados](estados.md)

## Rastreabilidade

| Camada | Artefato | Situação |
|---|---|---|
| Domínio | `OpeningHours` (faixas, datas especiais, `status`, refeições derivadas), `PlaceTimeZone` (fuso por UF, Noronha pelo IBGE), `PlaceDetails` (o JSON gravado), `Facet` (39 facetas com seção, valores, ícone, fixa/derivada e grupos), `ActionProvider` (provedores e domínios); `Establishment` com `opening_hours`, `temporarily_closed`, `reopens_on`, `charges`, `charges_declared_at`, `facets` e `action_links` | ✅ |
| Regras | `PlaceDetailsRules.hours/temporaryClosure/charges/facets/actions/url/whatsapp`; `EstablishmentService.details` (aplica só depois de não haver erro, revalida facetas e cobranças) | ✅ |
| Páginas | `PlaceDetailsViews.openStatus/hours/charges/facets/actions` (textos fixos, ordem dos deliveries), `EstablishmentView.viewerCanEdit`, `PlaceEditors.canEdit` (ponto de extensão do RF20); card da busca com `openStatus` ([RF10](../RF10/README.md)) | ✅ |
| Persistência | `V6__pagina_do_lugar.sql` (colunas JSONB com CHECK de formato, `reopens_on` só com a pausa) | ✅ |
| API | `POST`, `GET` e `PATCH /api/v1/establishments[/id]` com os blocos novos; `GET /api/v1/taxonomy` com `facets`, `actionProviders` e `couvertKinds` ([RF7](../RF7/README.md)) | ✅ |
| Tela | `/lugares/[id]` (`PlaceView` com `OpenStatusBadge`, `ActionButtons`, `HoursSection`, `ChargesSection`, `FacetsSection`, `PlaceLocation` e `PlaceMap`), `/lugares/novo` e `/lugares/[id]/editar` (`PlaceForm`, `lib/place-form`), "Meus lugares" com cadastrar e editar ([RF4](../RF4/README.md)); `PlaceDialog` e `EstablishmentCard` com o aberto agora | ✅ |
| Dados de exemplo | `DevSeed`: horário de almoço e jantar e facetas de pagamento em todos os lugares; taxa de serviço, desconto no Pix, iFood e WhatsApp no Sabor Mineiro | ✅ |
| Testes | `PlacePageRulesTest` (aberto agora com virada da meia-noite e fuso de Manaus, datas especiais, mais de 7 dias, pausa e "pode estar fechado", 24 horas, erros de horário, cobranças, facetas, links e WhatsApp); `EstablishmentApiIT.placePageHasHoursChargesFacetsAndActionButtonsEditableOnlyByTheOwner` (gravação JSON de ida e volta, card da busca, 404 para outra conta, 422 com tudo junto, revalidação das cobranças, pausa); vitest `place-form.test.ts` e `place-info.test.ts`; e2e `lugar-pagina.spec.ts` e `lugar-dono.spec.ts` (CSRF, corpo do POST e do PATCH, erros por campo, axe nos dois temas) | ✅ |
| Pendências | confirmações da comunidade nas facetas e "estava fechado" com limiar (RC-EST-04, 5); lembrete de feriado 14 dias antes (RC-EST-04, 3); filtro "aberto agora" na busca (precisa do cálculo no SQL ou de um índice de faixas por fuso); municípios do oeste do Amazonas em UTC−5; verificação periódica de link quebrado, "link errado" pelos comensais e contagem de cliques (RC-EST-08, 9); botões Ligar, Site e Rota; `SINAL_RESERVA`, `BENEFICIO_BANDEIRA` e horários adicionais (cozinha, happy hour); "cobrança diferente da página" na avaliação (RF8); equipe da página (RF20) | ⏳ |

## Decisões a confirmar ⚖️

- **Domínios dos provedores** (`ActionProvider`): `ifood.com.br`, `rappi.com.br`, `aiqfome.com`, `99app.com`,
  `thefork.com.br`/`thefork.com`, `tagme.com.br`, `getinapp.com.br`, `opentable.com.br`/`opentable.com` precisam ser
  conferidos com cada provedor; domínio fora da lista hoje é recusado (a pesquisa sugeria "exige revisão").
- **Tiles do OpenStreetMap**: o servidor público tem política de uso justo; para produção, contratar um provedor de
  tiles ou servir os próprios. A atribuição "© colaboradores do OpenStreetMap" (ODbL) fica sempre visível.
- **"Pode estar fechado"** já no dia da volta prevista (conservador); pode passar a valer só no dia seguinte.
- **Acréscimo por forma de pagamento** aceito junto com o desconto (Lei 13.455/2017 permite a diferenciação); conferir
  se o produto quer só o desconto.
