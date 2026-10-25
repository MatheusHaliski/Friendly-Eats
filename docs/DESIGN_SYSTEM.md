# Design System do Friendly Eats

Regras visuais e de acessibilidade das telas do Friendly Eats. A gramática é a mesma da Orange Social, para que o
ecossistema Velion se leia igual nos dois apps:

- a **bolinha** diz quem publica (tipo de conta);
- a **borda** diz o tipo de conteúdo;
- o **chip** diz a classificação (culinária, 18+, publicidade).

Os códigos e as regras de cada categoria ficam em [`TAXONOMIA_FRIENDLY_EATS.md`](TAXONOMIA_FRIENDLY_EATS.md). As cores
vêm da pesquisa visual de 25/10/2026: cada matiz foi ajustado em OKLCH para maximizar a distância entre pares na visão
normal e nas simulações protan, deutan e tritan (Machado et al., 2009), com a restrição de contraste de cada tema.

**Legenda.** Ainda não há telas: esta página é a especificação. ⏳ marca o que depende de um RF planejado (RF9 a RF13)
ou de uma decisão pendente.

## 1. Princípios

| Princípio | Regra | Referência |
|---|---|---|
| A cor nunca é o único sinal | toda categoria tem **cor + glifo ou ícone + rótulo**. Com 7 ou 8 categorias, alguns pares ficam próximos sob daltonismo, por isso glifo e rótulo são obrigatórios | WCAG 1.4.1 |
| Contraste de texto | **4,5:1** ou mais contra o fundo do tema | WCAG 1.4.3 |
| Contraste de ícone e contorno | **3:1** ou mais (estrelas, contorno de chip, anel, borda de estado) | WCAG 1.4.11 |
| Alvo de toque | pelo menos **24×24 px** (rádio de estrelas, chips clicáveis, botões de ícone) | WCAG 2.5.8 |
| Um ícone, um significado | um ícone Lucide nunca representa duas coisas. Os ícones reservados estão na tabela abaixo | — |
| Sem escala vermelho → verde | nem nota nem preço usam vermelho e verde como escala; o vermelho fica só no alerta sanitário | — |

**Ícones reservados** (Lucide, versão fixada **0.460**):

| Ícone | Significado único | Não usar para |
|---|---|---|
| `user-round`, `camera`, `chef-hat`, `store`, `shopping-basket`, `users`, `landmark` | tipos de conta (seção 3) | conteúdo ou culinária. O post de foto ou vídeo usa `clapperboard`, e não `camera`, que é da conta Criador |
| `badge-check` | selo `VERIFIED` | qualidade, destaque ou "recomendado" |
| `cooking-pot`, `wine`, `soup`, `beef`, `bean`, `pizza`, `coffee`, `vegan` | grupos do chip de culinária (seção 4) | estilos alimentares ou formatos. Enquanto Américas usar `beef`, a Brasileira não pode usar a alternativa `beef` |
| `star` | nota | favoritos ou destaque |
| `triangle-alert`, `shield-alert` | alerta sanitário e fonte oficial (seção 7) | erro de formulário |
| `flame` | reservado para "picante" ou "em alta" (decisão pendente) | Américas |

Não há ícone de sushi, hashi, taco ou kebab no Lucide, e `hamburger`, `shrimp`, `avocado`, `cupcake` e `groceries` não
existem na 0.460.

### Logo oficial

Xícara amarela fumegante em relevo de massinha, sobre um disco com faixas vermelha, verde e azul e borda branca.

| Arquivo | Uso |
|---|---|
| `brand/logo/logo-friendly-eats-original.jpg` | original recebido (1200 × 896, fundo xadrez de "transparência" falsa) |
| `brand/logo/logo-friendly-eats.png` | mestre recortado: círculo de 696 px com fundo transparente e borda branca com contorno `#E2D6C8` |
| `brand/logo/logo-friendly-eats-512.png` | 512 px para lojas, redes e apresentações |
| `frontend/apps/web/public/brand/logo-{96,192,512}.webp` | cabeçalho do app web (40 px na tela, 96 px para telas 2×) e usos maiores |
| `frontend/apps/web/app/icon.png` · `app/apple-icon.png` | favicon (64 px) e ícone da tela inicial do iOS (180 px, sobre o carvão `#17120D`) |

- No cabeçalho, o logo é **decorativo** (`alt=""`) porque o nome "Friendly Eats" vem escrito ao lado. Sozinho (sem
  o nome), use `alt="Friendly Eats"`.
- A borda branca garante o recorte no tema escuro; no claro, o contorno `#E2D6C8` fecha o círculo. Tamanho mínimo:
  16 px (favicon). Não distorcer, não recolorir, não pôr texto sobre o disco.
- As cores do logo são da ilustração, **não** são tokens: o vermelho do disco não vira cor de interface (o vermelho
  da interface é só do alerta sanitário, seção 7) e o amarelo não substitui a estrela (seção 6).

## 2. Tema

| Token | Escuro | Claro |
|---|---|---|
| Fundo | `#17120D` (carvão quente) | `#FFFFFF` |
| Superfície rebaixada (`surface-sunken`) | `#2A2119` (texto 13,81:1) | `#F2ECE5` (texto 15,86:1) |
| Marca (`brand`, o "Eats" do logo e links de destaque) | `#FF9F5A` (9,18:1) | `#A34A00` (5,94:1) |
| Erro de formulário (`feedback.error`) | `#FFB86B` (10,92:1) | `#8A3C00` (7,70:1) |
| Sucesso (`feedback.success`) | `#6FE699` (11,92:1) | `#08693A` (6,79:1) |

- **Erro de formulário não é vermelho:** o vermelho fica reservado ao alerta sanitário (seção 7). O erro usa o tom
  âmbar/cobre acima **com o ícone `circle-alert` e o texto do erro ao lado do campo**: a cor nunca é o único sinal.
- Os tokens vivem em DTCG (`frontend/packages/tokens/tokens/*.json`) e o Style Dictionary 5 gera `tokens.css`
  (claro padrão, escuro por `prefers-color-scheme` e por `data-theme`) e `tokens.ts`. O teste
  `frontend/packages/tokens/test/contraste.test.ts` confere 4,5:1 para texto e 3:1 para ícone e contorno.

Toda cor desta página foi medida contra o fundo do seu tema. Uma cor do tema escuro nunca é usada no claro, e vice-versa.

## 3. Tipos de conta (bolinha)

| Código | Rótulo | Glifo | Ícone Lucide (rótulos e menus) | Escuro | vs `#17120D` | Claro | vs branco |
|---|---|---|---|---|---|---|---|
| `COMENSAL` | Comensal | ◇ losango | `user-round` | `#48A8FF` | 7,37 | `#05558F` | 7,77 |
| `CRIADOR` | Criador | ✦ estrela | `camera` | `#F7C638` | 11,60 | `#876A06` | 5,13 |
| `CHEF` | Chef | ⬠ pentágono | `chef-hat` | `#D6C5FF` | 11,77 | `#7D5BBA` | 5,15 |
| `ESTABELECIMENTO` | Estabelecimento | ▭ retângulo | `store` | `#E88F12` | 7,41 | `#7F4D06` | 7,08 |
| `VAREJO` | Varejo & Marca | △ triângulo | `shopping-basket` | `#EE559F` | 5,70 | `#96075A` | 8,49 |
| `COMUNIDADE` | Comunidade | ⬢ hexágono | `users` | `#08DABD` | 10,43 | `#0B8170` | 4,79 |
| `OFICIAL` | Oficial | ◎ círculo duplo | `landmark` | `#0C0A07` + **anel `#D6D0C8`** | 1,06 sem anel; anel 12,15 | `#0C0A07` | 19,77 |

- **Anel obrigatório da conta Oficial.** No tema escuro, o preto `#0C0A07` dá só 1,06:1 contra o fundo. A bolinha
  Oficial **sempre** leva um anel de 2 px `#D6D0C8` (12,15:1). Sem o anel, a bolinha não aparece.
- **Glifos opostos nos pares próximos.** Sob daltonismo, os pares mais próximos são Criador × Estabelecimento
  (estrela × retângulo) e Chef × Comunidade (pentágono × hexágono). Os glifos são opostos de propósito.
- Os glifos seguem a semântica da Orange Social (pessoa, mídia, instituição, marketing, comunidade, especial). O
  pentágono do Chef é novo.
- **Selo `badge-check`.** A conta com `category_verification = VERIFIED` mostra o ícone `badge-check` ao lado do nome,
  com o rótulo acessível "Conta verificada". O selo **não** é uma cor nova e não muda a bolinha. Com `PENDING`, nada
  aparece no card público. ⏳ No próprio perfil, o dono vê "Verificação em andamento".

## 4. Chips de culinária

Chip-pílula com **pictograma Lucide + rótulo**, nunca só a cor. O grupo é derivado da classificação do estabelecimento
(regra na [taxonomia, seção 12](TAXONOMIA_FRIENDLY_EATS.md#12-chip-de-culinária)). Os glifos geométricos sólidos ficam
reservados à bolinha, por isso a culinária usa pictograma. No mapa, o pin usa o mesmo pictograma em 14 a 16 px.

| Código | Rótulo | Lucide (0.460) | Alternativa | Escuro | vs `#17120D` | Claro | vs branco |
|---|---|---|---|---|---|---|---|
| `BRASILEIRA` | Brasileira | `cooking-pot` | `beef` | `#DDAE14` | 8,98 | `#6B5300` | 7,34 |
| `EUROPEIA` | Europeia | `wine` | `ham` | `#8DBAFF` | 9,39 | `#0B57B8` | 6,84 |
| `ASIATICA` | Asiática | `soup` | `fish` | `#FF8C7E` | 8,24 | `#B3141B` | 6,92 |
| `AMERICAS` | Américas | `beef` | `flame` | `#12AAAA` | 6,52 | `#017C7C` | 5,03 |
| `ORIENTE_MEDIO_AFRICA` | Oriente Médio & África | `bean` | `nut` | `#AC8CE6` | 6,78 | `#5E3E92` | 8,14 |
| `LANCHES_PIZZA` | Lanches & pizza | `pizza` | `sandwich` (`hamburger` só na 1.x) | `#E07C0A` | 6,25 | `#A65A06` | 5,14 |
| `DOCES_CAFE` | Doces & café | `coffee` | `croissant`, `cake-slice` | `#FFC1DB` | 12,32 | `#8E1A5D` | 8,55 |
| `VEGETAL` | Vegetal | `vegan` | `leafy-green`, `salad` | `#6FE699` | 11,92 | `#08693A` | 6,79 |

As alternativas só valem se não quebrarem "um ícone, um significado": `beef` não serve para a Brasileira enquanto for
o ícone das Américas, e `flame` está reservado (seção 1).

Pares mais próximos (ΔOKLab × 100; abaixo de ~5, os tons ficam difíceis de separar lado a lado):

| Tema | Normal | Protan | Deutan | Tritan |
|---|---|---|---|---|
| Escuro | Asiática × Lanches 11,3 | Asiática × Américas 6,3 | Asiática × Vegetal 6,6 | Asiática × Lanches 5,4 |
| Claro | Américas × Vegetal 10,2 | Lanches × Vegetal 4,6 | Brasileira × Asiática 4,4 | Europeia × Vegetal 5,5 |

Por isso o pictograma e o rótulo são obrigatórios em todo chip.

**Estilos alimentares:** sempre com rótulo textual ("Opções veganas", "100% vegetariano"). Sem glúten usa
`wheat-off`, e sem lactose usa `milk-off`, sempre com o aviso "Informação declarada pelo estabelecimento; confirme
sobre contaminação cruzada". O `vegan` é do chip Vegetal, então o estilo vegano com nível `OFERECE` aparece só em texto.

## 5. Preço

**Glifo:** 1 a 4 **cifrões cheios**, e o restante até 4 **vazados** (só contorno). A contagem é o sinal principal. A
cor é sequencial de um só matiz (areia → cobre) e só reforça.

**Rótulo sempre visível:** nível, faixa em reais e origem. Exemplo: "$$ · Moderado · R$ 40 a R$ 80 por pessoa ·
informado pelo estabelecimento" (ou "segundo 23 avaliações").

| Código | Glifo | Rótulo | Faixa por pessoa | Escuro | vs `#17120D` | Claro | vs branco |
|---|---|---|---|---|---|---|---|
| `PRICE_1` | `$` | Econômico | até R$ 40 | `#E9DCC6` | 13,75 | `#7D7360` | 4,67 |
| `PRICE_2` | `$$` | Moderado | R$ 40 a R$ 80 | `#E6C08A` | 10,87 | `#86622A` | 5,53 |
| `PRICE_3` | `$$$` | Caro | R$ 80 a R$ 160 | `#E3A15A` | 8,43 | `#99500F` | 5,98 |
| `PRICE_4` | `$$$$` | Muito caro | acima de R$ 160 | `#E0823A` | 6,58 | `#6A2C00` | 10,64 |
| `PRICE_UNKNOWN` | — | Preço não informado | — | cor do texto secundário | ≥ 4,5 | cor do texto secundário | ≥ 4,5 |
| `PRICE_NA` | — | não é exibido | — | — | — | — | — |

- Os limites são **40 / 80 / 160**, os da taxonomia (seção 9). A pesquisa visual usou 150 no último degrau, mas essa
  escala foi substituída.
- No tema claro, `$$` × `$$$` ficam próximos em deutan (2,0). Quem diferencia é a contagem de cifrões e o rótulo.
- Sem preço (`PRICE_UNKNOWN`), não aparecem cifrões vazados: um "$$$$" todo vazado pareceria um nível.
- **Leitor de tela.** O "$" é lido como "dólar", e "$$" vira "dólar dólar". Os cifrões ficam com
  `aria-hidden="true"`, e o conjunto recebe o rótulo por extenso:
  `aria-label="Faixa de preço: moderado, R$ 40 a R$ 80 por pessoa, informado pelo estabelecimento"` (ou ", segundo 23
  avaliações").
  ⏳ Avaliar se o "$" deve virar um ícone de moeda neutro, porque ele também sugere dólar para quem enxerga.

## 6. Estrelas

| Elemento | Escuro | vs `#17120D` | Claro | vs branco | Regra |
|---|---|---|---|---|---|
| Estrela cheia e meia estrela | `#F7C948` | 11,87 | `#B58600` | 3,29 | ícone (≥ 3:1). O `#B58600` **nunca** é usado como cor de texto |
| Estrela vazia (só contorno) | `#8C847A` | 5,05 | `#8A8178` | 3,82 | a forma diferencia, não só a cor |
| Média ("4,3") e contagem ("128 avaliações") | cor do texto | ≥ 4,5 | cor do texto | ≥ 4,5 | sempre ao lado das estrelas |

- **Meia estrela pela forma:** metade esquerda preenchida e contorno inteiro (`star-half` sobre o contorno de `star`),
  nunca só um tom mais claro. As estrelas da média são arredondadas para meia estrela apenas como apoio visual. O
  sinal principal é o número.
- A estrela **não** muda de cor conforme a nota.
- **Número e contagem ao lado:** as estrelas seguidas de "4,3 · 128 avaliações". A média usa uma casa decimal e vírgula.
- **Rótulo acessível:** as estrelas ficam `aria-hidden="true"`, e o conjunto recebe
  `aria-label="Nota 4,3 de 5, com base em 128 avaliações"`.
- **Estados da nota agregada** (taxonomia, seção 10.2):

  | Estado | O que aparece |
  |---|---|
  | `RATING_NONE` | o texto "Sem avaliações ainda", sem estrelas. Nunca mostrar 5 estrelas vazias sem texto, que pareceriam nota 0 |
  | `RATING_FEW` | o texto "3 avaliações, ainda sem nota média", sem estrelas e sem média |
  | `RATED` | estrelas, média, contagem e, na página do estabelecimento, o histograma de 1 a 5 |

- **Entrada da nota:** grupo de rádio (`role="radiogroup"`) com **5 opções rotuladas**: "1 – Péssimo", "2 – Ruim",
  "3 – Regular", "4 – Bom" e "5 – Excelente". A nota é inteira e não há meia estrela na entrada. Funciona com as
  setas do teclado, e cada estrela tem alvo de pelo menos 24×24 px. O rótulo da opção escolhida aparece em texto ao
  lado ("4 – Bom").
- As dimensões (comida, atendimento, ambiente, custo-benefício, entrega) usam o mesmo componente, menor, e só aparecem
  conforme o `visit_mode`.

## 7. Alertas e estados especiais

| Estado | Sinal visual | Cores | Ícone e rótulo |
|---|---|---|---|
| ⏳ **Alerta sanitário / recall** (só conta `OFICIAL` com selo) | faixa hazard (listras diagonais) + borda vermelha | faixa `#F7C948` × `#17120D` (11,87:1) nos dois temas; borda `#FF5A4F` no escuro (6,05:1) e `#C01F1F` no claro (6,07:1; texto branco sobre ela: 6,07:1) | `triangle-alert` (com `shield-alert` para a fonte oficial) + "Alerta sanitário · Fonte: Anvisa". É o único uso de vermelho |
| ⏳ Prato do dia ou promoção encerrados | a borda vira tracejado cinza; sai do feed | cinza das estrelas vazias: `#8C847A` no escuro (5,05:1), `#8A8178` no claro (3,82:1) | rótulo "Encerrado" |
| ⏳ Chip de classificação | pílula com contorno | contorno ≥ 3:1 e texto ≥ 4,5:1 | "Livre", "18+" (bebida alcoólica como tema ou atração), "Publicidade" (publi, permuta ou convite), "Patrocinado" (impulsionado). Publicidade e Patrocinado podem coexistir com Livre ou 18+ |
| Endereço só com bairro (`ADDR_PUBLIC_BAIRRO`) | o mapa mostra uma área, e não um pino exato | — | "Endereço aproximado: bairro e cidade" |
| Informação declarada | texto auxiliar abaixo do atributo | cor do texto secundário (≥ 4,5:1) | "Informação declarada pelo estabelecimento" (estilos, alergênicos, acessibilidade) |

- O alerta sanitário nunca é sensacionalista e encerra quando a fonte oficial encerra. A suspeita de um usuário vira
  relato à moderação, nunca alerta.
- ⏳ O alerta ativo da região fica fixado no topo do feed enquanto valer.

## 8. Como conferir

```bash
python3 scripts/wcag_contrast.py                       # tabela dos tipos de conta, nos dois temas
python3 scripts/wcag_contrast.py "#DDAE14" "#17120D"   # qualquer par de cores
cd frontend && npm test -w @friendly-eats/tokens       # contraste de todos os tokens do app web
```

Por enquanto, a tabela do script cobre **só os tipos de conta** (e o anel da conta Oficial). As cores de chip, preço,
estrelas e alerta desta página foram conferidas par a par com o segundo comando. Ao mudar qualquer cor, confira o par
nos dois temas e atualize esta página no mesmo commit. As simulações de daltonismo não substituem testes com pessoas
daltônicas em protótipo real.

## Referências

- WCAG 2.2, critérios 1.4.1, 1.4.3, 1.4.11 e 2.5.8 — https://www.w3.org/TR/WCAG22/
- Lucide 0.460 (lista de ícones) — https://cdn.jsdelivr.net/npm/lucide-static@0.460.0/tags.json
- OKLab — https://bottosson.github.io/posts/oklab/
- Simulação de daltonismo (Machado, Oliveira e Fernandes, 2009) — https://www.inf.ufrgs.br/~oliveira/pubs_files/CVD_Simulation/CVD_Simulation.html
- Orange Social, `docs/DESIGN_SYSTEM.md` (gramática de bolinha, borda e chip)
