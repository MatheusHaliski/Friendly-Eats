---
rf: RF16
titulo: Moldura do post (estúdio de moldura)
status: parcial
cards: [FE-24]
paths:
  - src/main/java/com/friendlyeats/application/background/**
  - src/main/java/com/friendlyeats/api/BackgroundController.java
---

# RF16 · Moldura do post

> **Como** autor, **quero** escolher a arte que decora a moldura do meu post (cor, cartela da época, padrão de mesa,
> material e layout) **para** dar a cara do meu prato ou do meu lugar ao card sem atrapalhar a leitura de quem vê.

| | |
|---|---|
| **Origem** | Estúdio de moldura do Orange Social (RF28 de lá), adaptado à comida: cartelas da época com a cozinha de cada estação, padrões de mesa e o layout Cardápio |
| **Base legal (LGPD)** | Art. 7º, V (execução do serviço). A configuração da moldura não é dado pessoal. Arte enviada ou gerada por IA é conteúdo do usuário: depende da moderação ([RF13](../TAXONOMIA_FRIENDLY_EATS.md#13-mapa-para-os-rfs)) |
| **Acessibilidade** | WCAG 1.4.3 (tinta × container ≥ 4,5:1), 2.2.2 (animação para em ≤ 5 s), 2.3.1 (sem flashes), `prefers-reduced-motion` e a preferência `reduceMotion` ([RF2](../RF2/README.md)) |
| **Status** | 🟡 Parcial. Pronto, com testes: catálogo público com ETag, cartela automática pela data de São Paulo, recomendação por tipo de conta, validação e forma canônica, leitura para o card. Falta: arte própria (envio e IA) e as telas do estúdio no app |

## Critérios de aceite

1. **Duas camadas.** A arte fica só na faixa entre a borda externa e o container. O container é opaco e fica por
   cima, com autor, foto do prato, texto e ações. Nenhum texto do usuário fica sobre a arte.
2. **A moldura nunca é sinal de informação.** O tipo de conta do autor (filete, glifo e rótulo), o selo de
   publicidade e o "Fonte oficial" ficam no container, sempre visíveis.
3. **Fontes de arte (uma por vez):** cor do catálogo, gradiente, cartela da época (fixa ou "automática pela data"),
   padrão (com variação `ORIGINAL`/`SUAVE` e formato `IMAGEM_UNICA`/`MOSAICO`) ou arte própria. Mais de uma → `422`.
4. **Catálogo** (`GET /api/v1/backgrounds/catalog`, público, ETag = versão do catálogo + cartela do dia, `max-age` 1 h):
   - **Cores:** Carvão, Berinjela, Noite, Manjericão, Páprica, Laranja, Mel, Farinha.
   - **Gradientes:** Brasa, Açaí, Matcha, Caramelo, Pimenta, Café.
   - **Padrões:** Toalha xadrez, Azulejo, Forno a lenha, Maré, Favo de mel, Feira, Horta, Neon de boteco.
   - **Materiais:** Papel manteiga, Linho, Inox, Vidro. **Animações:** nenhuma, Fumegar, Deriva.
   - **Layouts:** Clássico, Polaroid e **Cardápio** (lousa), que já traz arte própria e não combina com outra arte
     nem material.
   - **Skins** do container (Padrão, Mono, Leve) e cores de container (Carvão, Noite, Farinha, Laranja).
5. **Cartelas da época** (Hemisfério Sul, data de São Paulo, janelas `[início, fim)`, prioridade 1 vence 4):

   | Cartela | Janela | Prioridade |
   |---|---|---|
   | Verão (manga, melancia, maracujá) | 21/12–20/03 | 4 |
   | Outono (abóbora, caqui) | 20/03–21/06 | 4 |
   | Inverno (caldos, pinhão, vinho) | 21/06–22/09 | 4 |
   | Primavera (morango, flores comestíveis) | 22/09–21/12 | 4 |
   | Carnaval | sexta a Quarta de Cinzas (Páscoa − 51 a − 46) | 2 |
   | Páscoa (chocolate) | Domingo de Ramos ao Domingo de Páscoa | 2 |
   | Festas Juninas e Julinas | 01/06–31/07 | 3 |
   | Dia dos Namorados | 05/06–12/06 | 1 |
   | Dia da Pizza | 10/07 | 1 |
   | Ceia de Natal e Réveillon | 01/12–06/01 | 3 |
   | Black Friday | dia seguinte à 4ª quinta de novembro | só por escolha, nunca automática |

6. **Validação no servidor** (o cliente não é confiável): só chaves conhecidas e só ids do catálogo, sem URL nem CSS
   livre; configuração com até 2048 bytes e `v = 1`; tinta × container ≥ 4,5:1, com sugestão da tinta que passa.
   Todos os problemas voltam juntos (`422` com `errors`). A forma canônica, com os padrões preenchidos, é o que vai
   para `post.background`.
7. **Alerta sanitário não tem moldura** (`422`): a faixa de alerta precisa ser reconhecida na hora. A V3 também
   recusa (`CHECK`).
8. **Menor de 16** usa só artes do catálogo: arte própria → `403` (ECA Digital). Para os demais, arte própria
   ainda volta `422` até existir a moderação.
9. **Direção recomendada** (`GET /api/v1/backgrounds/recommendations?category=&feed=&media=`): padrão + material que
   combinam com o tipo de conta (Chef → Forno a lenha + Inox; Estabelecimento → Azulejo + Papel manteiga…) e a
   cartela do dia. Com foto ou vídeo do prato, sugere sem material, para o prato ficar em destaque. Alerta → nada.
10. **Leitura para o card:** `BackgroundService.resolve` devolve o tipo de arte (`COLOR`, `GRADIENT`, `SEASONAL`,
    `AURA`, `LAYOUT`, `NONE`), o id, a paleta, o CSS e o container com a tinta já calculada; no modo automático, a
    cartela do dia.

## Diagramas

[Casos de uso](casos-de-uso.md) · [Atividades](atividades.md) · [Classes](classes.md) · [Componentes](componentes.md) ·
[Sequência](sequencia.md) · [Estados](estados.md)

## Rastreabilidade

| Camada | Artefato | Situação |
|---|---|---|
| Catálogo | `BackgroundCatalog` (versão por SHA-256 do conteúdo), `SeasonalCalendar` (Páscoa por Meeus/Jones/Butcher) | ✅ |
| Regras | `BackgroundValidator` (forma canônica, uma arte por vez, Cardápio, alerta, menor de 16), `Contrast` (WCAG 2.x, mesmo cálculo de `scripts/wcag_contrast.py`) | ✅ |
| Leitura | `BackgroundService.resolve` e `recommend` | ✅ |
| API | `BackgroundController`: `GET /api/v1/backgrounds/catalog` (ETag, `304`) e `/recommendations` | ✅ |
| Post | coluna `post.background` (V3, até 2048 bytes, nula em alerta); validação na criação e na edição e `background` resolvido na view do post ([RF9](../TAXONOMIA_FRIENDLY_EATS.md#13-mapa-para-os-rfs)) | 🟡 em integração |
| Testes | `BackgroundRulesTest` (cartelas e prioridades, Páscoa e Carnaval, Black Friday fora do automático, forma canônica, chaves e ids desconhecidos, uma arte por vez, Cardápio, alerta, menor de 16, contraste de todas as skins e cores de container, leitura e recomendação) | ✅ |
| Telas | estúdio com as abas Cor, Padrão e material, Layout e Container; prévia ao vivo | ⏳ |
| Arte própria | envio ou IA, com moderação e checagem de flashes (RF13) | ⏳ |
