# Pesquisa de requisitos · Cozinha de casa, receitas, conteúdo e uso responsável de IA

Friendly Eats · eixo 4 · pesquisa feita em 25/10/2026 (WebSearch/WebFetch). São requisitos **candidatos** (RC), não RFs:
cada um precisa de triagem antes de virar `docs/RFn/`.

**Ponto de partida (não repetido aqui):** post `RECEITA` do RF9 (`recipe{ingredients[], steps[], servings, prepMinutes}` em
texto simples), alergênicos do RF9 (RDC 727/2022, "contém" e "pode conter", com aviso de contaminação cruzada), estilos
alimentares, culinária (56) e formato (33), `ageRating` 18+ automático para formato alcoólico (RF9), preferências
sensíveis com consentimento (RF2/RF3), moldura (RF16), contas Chef e Criador. Vídeo ainda não existe (pendência do RF9).
Moderação (RF13) e página do estabelecimento (RF11) estão planejadas.

**Convenções deste documento**

- "Não verificado" = não consegui confirmar em fonte primária nesta pesquisa (página fora do ar, sem resultado ou só
  fonte secundária). Planalto, BVS/MS e o PDF da Anvisa estavam fora do ar (503/403) durante a pesquisa; a Lei 9.610 e a
  IN 75/2020 foram conferidas em cópias (normaslegais.com.br e PDF da Visa de Jundiaí).
- Prioridade MoSCoW: **M**ust, **S**hould, **C**ould, **W**on't (agora). Esforço: **P** (até 1 semana de 1 dev), **M**
  (2 a 4 semanas), **G** (mais de 1 mês ou depende de terceiros/licença).

## Achados que mudam premissas

1. **A Lei 14.016/2020 foi revogada.** A Lei 15.224, de 30/09/2025, instituiu a Política Nacional de Combate à Perda e ao
   Desperdício de Alimentos (PNCPDA), criou o Selo Doador de Alimentos e revogou a 14.016 (art. 19). Doação de alimentos
   preparados é permitida (art. 14), doador e intermediário só respondem civilmente com dolo (art. 16), a doação não é
   relação de consumo (art. 17) e a lei prevê microcoleta por "aplicativos, sítios na internet" (art. 4º, VIII). O art. 18
   foi vetado. Fonte: [Câmara, publicação original](https://www2.camara.leg.br/legin/fed/lei/2025/lei-15224-30-setembro-2025-798048-publicacaooriginal-176570-pl.html).
2. **A TBCA não pode ser embutida num produto comercial sem autorização.** A página da TBCA 7.3 (2025) diz: "Não é
   permitida a reprodução total ou parcial do material", "Não é permitida a comercialização" e, para fins comerciais,
   manda contatar os coordenadores. A citação é dispensada quando os dados servem para calcular informação nutricional.
   Não há API pública. Fonte: [tbca.net.br](https://www.tbca.net.br/). A **TACO 4ª ed. (NEPA/UNICAMP, 2011)** permite
   "reprodução total ou parcial do material, desde que seja citada a fonte", mas é de 2011
   ([PDF hospedado na USP](https://edisciplinas.usp.br/mod/resource/view.php?id=2301180); site oficial do NEPA não conferido).
3. **A xícara da Anvisa tem 200 ml, não 240 ml.** IN 75/2020, Anexo VII: xícara de chá 200 ml, copo 200 ml, colher de sopa
   10 ml, colher de chá 5 ml, prato raso 22 cm, prato fundo 250 ml (conferido no PDF). O uso de 240 ml em receitas
   brasileiras é comum, mas **não verificado** como padrão. Converter "xícara" sem dizer qual é, é fonte de erro.
4. **A LGPD não obriga revisão humana** de decisão automatizada: o § 3º do art. 20, que exigia revisão "por pessoa natural",
   foi vetado e o veto foi mantido em 02/10/2019 ([Serpro](https://serpro.gov.br/lgpd/noticias/2019/congresso-aprecia-veto-ao-artigo-20-da-lgpd),
   [Teletime](https://teletime.com.br/02/10/2019/congresso-mantem-veto-e-lgpd-fica-sem-revisao-humana-de-decisoes-automatizadas/)).
   O direito de pedir revisão continua no caput. Este documento propõe revisão humana mesmo assim (RC-REC-18).
5. **O PL 2338/2023 (marco da IA) ainda não é lei.** Aprovado no Senado em 10/12/2024, está na Câmara desde 17/03/2025, em
   comissão especial (relator Aguinaldo Ribeiro). Em 24/08/2026 o relator disse que a votação fica para depois das
   eleições de outubro de 2026 ([LCF monitor](https://monitor.lcfconsulting.com.br/proposicoes/pl-2338-2023/),
   [Desinformante](https://desinformante.com.br/votacao-do-marco-da-ia-fica-para-2026-em-meio-a-impasses-politicos-e-criticas-ao-texto)).
   Na versão publicada pela Câmara, o art. 19 exige um identificador em conteúdo sintético
   ([inteiro teor](https://www.camara.leg.br/proposicoesWeb/prop_mostrarintegra?codteor=2868197&filename=PL+2338%2F2023));
   a numeração final **não foi verificada** contra o autógrafo do Senado.
6. **O Yummly fechou** em dezembro de 2024, depois que a Whirlpool demitiu a equipe; usuários relataram dificuldade para
   exportar receitas ([Wikipedia](https://en.wikipedia.org/wiki/Yummly), [Plan to Eat](https://www.plantoeat.com/blog/2024/12/yummly-is-closing-discover-the-best-meal-planning-alternative)).
   Lição: o caderno de receitas da pessoa precisa ser exportável (LGPD art. 18, V).

---

## Requisitos candidatos

### RC-REC-01 · Ingredientes estruturados, escala de porções e conversão de medidas

**Ator(es):** autor da receita (qualquer conta ativa, Chef e Criador em destaque); quem lê a receita.

**Descrição:** cada ingrediente da receita passa a ter quantidade, unidade, item e observação ("2 xícaras (240 ml) de
farinha de trigo, peneirada"), em vez de só texto. Com isso a pessoa muda o número de porções e vê as quantidades
recalculadas, e alterna entre medida caseira e métrica (g/ml). O texto livre continua aceito para o que não se mede
("sal a gosto"). É a base de lista de compras, nutrição, substituições e marcação schema.org.

**Critérios de aceite:**
1. O autor escolhe a unidade numa lista fechada (g, kg, ml, l, unidade, xícara, copo, colher de sopa, colher de chá,
   pitada, "a gosto") e, para xícara e copo, declara o volume de referência (200 ml da IN 75/2020 ou 240 ml). A página
   sempre mostra o volume entre parênteses.
2. Mudar as porções recalcula as quantidades numéricas com frações legíveis (½, ⅓, ¼) e não mexe em "a gosto",
   "pitada", tempo de forno nem temperatura. Um aviso fixo diz que tempo, forma e fermento podem não escalar em linha reta.
3. A conversão volume → massa só aparece quando há densidade conhecida do ingrediente na base de referência; sem ela,
   mostra só o volume.
4. Receita antiga (texto simples do RF9) continua válida; o autor pode estruturá-la depois, e a API aceita os dois formatos.
5. A escala é feita no cliente e não altera o post; o valor escolhido fica só na sessão de quem lê.

**Regras e riscos:** a conversão é aproximada: nunca usar a escala para dosagem de fermento em conserva, nem para
receitas para bebês. Alergênicos não mudam com a escala. A tabela de densidades precisa de fonte com licença (ver
RC-REC-06: a TBCA tem medidas caseiras, mas proíbe reprodução sem autorização).

**Referências de mercado:**
- Paprika: "Scale ingredients to your desired serving size, and convert between measurements" ([App Store](https://apps.apple.com/us/app/-/id1303222868)).
- Tastemade Cooking: "scale serving sizes, and switch instantly between metric and imperial" ([App Store](https://apps.apple.com/us/app/-/id6736664843)).
- Tudo Gostoso mostra "Ingredientes (8 porções)" sem ajuste de porções ([receita de bolo de cenoura](https://www.tudogostoso.com.br/receita/23-bolo-de-cenoura.html)): lacuna no mercado brasileiro.
- IN 75/2020, Anexo VII (capacidades dos utensílios), conferida no [PDF da Visa de Jundiaí](https://visa.jundiai.sp.gov.br/wp-content/uploads/2024/08/IN_75_2020-requisitos-para-rotulagem-nutricional.pdf).

**Prioridade:** **M** — sem ingrediente estruturado não há lista de compras, nutrição, substituição nem rich result útil.

**Depende de:** RF9.

**Esforço:** M.

---

### RC-REC-02 · Modo cozinhar (tela acesa, passo a passo e timers)

**Ator(es):** quem cozinha a receita (web e mobile).

**Descrição:** uma visão de cozinha que mantém a tela acesa, mostra um passo por vez em letra grande, deixa riscar
ingredientes já separados e cria timers a partir dos tempos escritos nos passos ("asse por 40 minutos"). Funciona
offline depois de aberta. Comando de voz ("próximo", "repetir") é opcional e fica atrás de um interruptor.

**Critérios de aceite:**
1. Ao entrar no modo, o app pede o bloqueio de tela (Screen Wake Lock API na web) e o libera ao sair; se o navegador
   recusar, mostra "Sua tela pode apagar" sem quebrar o fluxo.
2. Tempos nos passos ("10 min", "1 hora e meia") viram botões de timer; vários timers rodam ao mesmo tempo, com rótulo do
   passo, alerta sonoro **e** visual/vibração (a cor nunca é o único sinal).
3. Navegação por botões grandes (alvo de toque ≥ 44 px), teclado e leitor de tela; o passo atual é anunciado
   (`aria-live`).
4. Os ingredientes riscados e o passo atual sobrevivem a recarregar a página (estado local, sem ir ao servidor).
5. Comando de voz só é ativado por ação explícita, processa o áudio no dispositivo quando possível e mostra um indicador
   permanente de microfone aberto.

**Regras e riscos:** microfone é dado pessoal: sem gravação, sem envio de áudio ao servidor sem aviso (LGPD art. 6º, III).
No iOS, a Wake Lock funciona no Safari desde o 16.4, mas houve relatos (2023) de falha em web app instalado na tela
inicial; a correção **não foi verificada** — testar no app mobile nativo e no PWA. Suporte a reconhecimento de voz no
navegador: **não verificado** nesta pesquisa.

**Referências de mercado:**
- Paprika: "Keep the screen on while cooking, cross off ingredients, and highlight your current step"; "Cook times are automatically detected in your directions" ([App Store](https://apps.apple.com/us/app/-/id1303222868)).
- NYT Cooking: "Follow recipes easily on a screen that won't go dark" ([App Store](https://apps.apple.com/BR/app/id911422904)).
- Tastemade Cooking: "step-by-step cooking mode" ([App Store](https://apps.apple.com/us/app/-/id6736664843)).
- Suporte da Wake Lock: [caniuse](https://caniuse.com/wake-lock), [web.dev](https://web.dev/blog/screen-wake-lock-supported-in-all-browsers), [WebKit bug 254545](https://bugs.webkit.org/show_bug.cgi?id=254545).
- Google já orientava `recipeIngredient` e `recipeInstructions` para receitas guiadas por voz no Assistente (2018) ([blog do Google](https://developers.google.cn/search/blog/2018/05/send-your-recipes-to-google-assistant?hl=en)).

**Prioridade:** **S** — é o recurso mais citado nos apps de receita e tem custo baixo depois do RC-REC-01.

**Depende de:** RF9, RC-REC-01 (timers e riscar ingredientes ficam melhores com passos e ingredientes estruturados).

**Esforço:** M (voz: +M, opcional).

---

### RC-REC-03 · Página pública de receita com schema.org/Recipe e Rich Pins

**Ator(es):** autor da receita; buscadores e Pinterest (secundários); visitante sem conta.

**Descrição:** receitas públicas e `LIVRE` ganham JSON-LD `schema.org/Recipe` na página do post, para aparecer como
resultado rico no Google e virar Rich Pin de receita no Pinterest. A marcação sai dos campos estruturados (RC-REC-01) e
nunca inventa dado ausente.

**Critérios de aceite:**
1. Sempre emite `name` e `image` (obrigatórios para o resultado rico do Google); emite `recipeIngredient`,
   `recipeInstructions` como `HowToStep`, `recipeYield`, `prepTime`/`cookTime` (os dois juntos) ou `totalTime`,
   `recipeCuisine`, `recipeCategory`, `author`, `datePublished` e `keywords` quando existirem.
2. `nutrition.calories` só sai com `recipeYield` em porções (regra do Google) e só quando o RC-REC-06 tiver calculado.
3. `suitableForDiet` só usa os valores de `RestrictedDiet` que batem exatamente com o estilo declarado (ex.: `VeganDiet`,
   `VegetarianDiet`, `GlutenFreeDiet`, `HalalDiet`, `KosherDiet`, `LowLactoseDiet`); estilo sem equivalente fica de fora.
4. `aggregateRating` só aparece se houver avaliações reais da receita (RC-REC-04), seguindo as regras de review snippet.
5. Post 18+, privado, removido ou de autor menor de idade não emite marcação nem Open Graph de receita.
6. Remix (RC-REC-05) emite `isBasedOn` apontando para a receita original.

**Regras e riscos:** marcação não pode afirmar o que a página não mostra (risco de ação manual do Google, **não
verificado** aqui). Alergênicos não têm propriedade no schema.org/Recipe: não "traduzir" "sem glúten" do autor em
garantia. Indexar receita de menor de 18 expõe o menor fora da plataforma (ECA Digital).

**Referências de mercado:**
- Google Search Central, dados estruturados de receita: obrigatórios `image` e `name`; "If `nutrition.calories` is defined, `recipeYield` must be defined"; `cookTime` e `prepTime` juntos ([documentação](https://developers.google.com/search/docs/appearance/structured-data/recipe)).
- schema.org: [RestrictedDiet](https://schema.org/RestrictedDiet), [isBasedOn](https://schema.org/isBasedOn).
- Pinterest: tipos de Rich Pin Article, Product e Recipe; Schema.org e Open Graph são os formatos comuns; prioridade Product > Recipe > Article ([docs](https://developers.pinterest.com/docs/rich-pins/)).

**Prioridade:** **S** — aquisição orgânica barata; depende de dados estruturados.

**Depende de:** RF9, RF14 (página do post), RC-REC-01.

**Esforço:** P.

---

### RC-REC-04 · "Fiz essa receita" (foto de quem fez) e notas pessoais

**Ator(es):** quem cozinhou a receita; autor (recebe o retorno); leitores.

**Descrição:** quem fez a receita publica uma foto e um comentário ligados a ela ("fiz e deu certo, usei menos açúcar"),
que aparecem na página da receita e notificam o autor. Opcionalmente dá estrelas **à receita** (separado da nota de
estabelecimento do RF8). Cada pessoa também guarda uma nota privada na receita, visível só para ela.

**Critérios de aceite:**
1. "Fiz essa receita" exige foto com `altText` (mesma limpeza de EXIF/GPS do RF9) e texto de até 500 caracteres; aparece
   numa galeria na receita e no perfil de quem fez.
2. Estrela de receita é 1 a 5, uma por pessoa por receita, editável; a média mostra o número de avaliações e só aparece
   a partir de um mínimo configurável (ex.: 3).
3. O autor não avalia a própria receita (mesma regra de conflito do RF8, `RULE_NO_INSIDER`).
4. A nota privada não aparece na API pública, não entra em busca nem em IA, e sai na exportação de dados da pessoa.
5. A pessoa exporta o próprio caderno (receitas salvas, notas, "fiz essa") em arquivo legível (JSON e PDF/HTML).

**Regras e riscos:** foto com pessoas ou animais: seguir a regra do Cookpad (só o prato) para reduzir dado pessoal de
terceiros. Estrela de receita ≠ estrela de lugar: não misturar nos agregados do RF8. Denúncia e remoção pelo RF13.

**Referências de mercado:**
- Cookpad, Cooksnaps: foto enviada ao autor quando você faz a receita; aparecem na página da receita; pede fotos sem pessoas ou animais ([diretrizes da comunidade](https://cookpad.com/us/community)).
- NYT Cooking: notas privadas "que só você vê", na web e no app ([central de ajuda](https://thenewyorktimeshelpcenter.helpjuice.com/360011047512-New-York-Times-Cooking/19627732008468-Public-and-Private-Cooking-Notes)).
- Tudo Gostoso: "4.7 / 5 (16.513 avaliações)", "Avalie essa receita", comentários; sem fotos de quem fez ([página de receita](https://www.tudogostoso.com.br/receita/23-bolo-de-cenoura.html)).
- Allrecipes "I Made It": **não verificado** (site bloqueou a leitura e a busca não trouxe fonte).
- Fechamento do Yummly e a dificuldade de exportar ([Plan to Eat](https://www.plantoeat.com/blog/2024/12/yummly-is-closing-discover-the-best-meal-planning-alternative)).

**Prioridade:** **S** — prova social específica de receita e motor de engajamento da cozinha de casa.

**Depende de:** RF9, RF12 (salvar, comentar), RF8 (regra de conflito), RF13 (denúncia), RF5 (exportação/eliminação).

**Esforço:** M.

---

### RC-REC-05 · Remix ("minha versão de") com crédito ao autor original

**Ator(es):** autor do remix; autor original; leitores.

**Descrição:** a partir de uma receita, a pessoa cria "a minha versão", que nasce ligada à original: o crédito ("versão
de X, a partir de *Bolo de fubá da Vó*") é automático e não pode ser removido. A original mostra "N versões". O remix
copia a lista de ingredientes e a estrutura, mas **não** copia a foto, e o modo de preparo vem em branco ou como rascunho
que o autor precisa reescrever.

**Critérios de aceite:**
1. O remix guarda `basedOnPostId` e mostra o crédito com link; se a original for removida, o crédito vira "receita
   original indisponível" sem apagar o nome do autor (direito moral, Lei 9.610 art. 24, II).
2. O remix não herda a foto da original; publicar exige foto própria (ou nenhuma).
3. O texto do modo de preparo copiado da original fica marcado como rascunho; a publicação é recusada (`422`) se ele for
   idêntico ao original acima de um limiar de similaridade configurável.
4. O autor original pode desligar novos remixes nas suas receitas (como o Stitch do TikTok); remixes existentes ficam.
5. Uma publicação nova de receita que copia texto ou foto de outra (hash perceptual, já pendente no RF9) entra em
   revisão (RF13) com aviso ao autor original.
6. Remixes emitem `isBasedOn` (RC-REC-03).

**Regras e riscos:** direito brasileiro (Lei 9.610/98): textos e fotografias são obras protegidas (art. 7º, I e VII);
"ideias, procedimentos normativos, sistemas, métodos" não são (art. 8º, I). Leitura deste documento: a lista de
ingredientes e o método em si não são protegidos, mas o texto autoral do modo de preparo, a foto e o vídeo são.
**Não verifiquei jurisprudência** brasileira específica sobre receitas; pedir revisão jurídica. Base de dados por
"seleção, organização ou disposição" pode ser protegida (art. 7º, XIII): não importar coleções inteiras de terceiros.

**Referências de mercado:**
- Cookpad: citar a fonte usada; cópia integral da receita de outra pessoa só com permissão ([diretrizes](https://cookpad.com/us/community)).
- TikTok Stitch: até 5 s do original, crédito e link automáticos; o criador escolhe quem pode fazer Stitch ([guia de terceiros](https://www.shopify.com/blog/how-to-stitch-on-tiktok); central de ajuda do TikTok **não verificada**).
- Lei 9.610/98, arts. 7º, 8º, 24 e 46 ([texto em normaslegais.com.br](https://www.normaslegais.com.br/legislacao/trabalhista/lei9610_1998.htm)).
- Doutrina: [UFRN, originalidade na produção gastronômica](https://repositorio.ufrn.br/server/api/core/bitstreams/6aed48ac-1e4c-4a87-b6c8-7e5a8ba0ad1e/content); [Conjur, proteção de receitas](https://www.conjur.com.br/?p=448898).

**Prioridade:** **M** — reduz o risco jurídico de cópia (que vai acontecer de qualquer forma) e transforma cópia em crédito.

**Depende de:** RF9, RF15 (perfil público, crédito), RF13 (revisão de cópia).

**Esforço:** M.

---

### RC-REC-06 · Painel do prato: nutrição estimada e grau de processamento

**Ator(es):** quem lê a receita; autor (confirma o mapeamento dos ingredientes); Chef e Criador.

**Descrição:** cada ingrediente estruturado (RC-REC-01) é ligado a um alimento de uma tabela de composição; a receita
mostra uma **estimativa** por porção (energia, carboidratos, proteínas, gorduras totais e saturadas, fibras, sódio) e a
proporção de ingredientes por grupo do Guia Alimentar (in natura/minimamente processados, ingredientes culinários,
processados, ultraprocessados). Nada é calculado sem o autor confirmar o mapeamento dos ingredientes.

**Critérios de aceite:**
1. O painel diz "Estimativa calculada a partir de [fonte e versão]; não é rótulo nutricional" e mostra a fonte com a
   citação exigida pela licença.
2. Ingrediente sem correspondência fica "não calculado" e o painel mostra a cobertura (ex.: "8 de 10 ingredientes"); abaixo
   de um mínimo (ex.: 80% da massa), o painel não aparece.
3. %VD, se mostrado, usa os VDR da IN 75/2020, Anexo II (2.000 kcal, 2.000 mg de sódio etc.) e diz isso.
4. O grau de processamento é mostrado por ingrediente e no total, em texto e glifo (sem semáforo só de cor), sem rótulo
   de "saudável"/"não saudável".
5. Nenhum post usa o desenho da rotulagem frontal da Anvisa (lupa "ALTO EM").

**Regras e riscos:**
- **Licença:** TBCA 7.3 proíbe reprodução e comercialização sem autorização (contato: tbca.contato@usp.br) — exige
  acordo antes do uso. TACO 4ª ed. permite reprodução com citação, mas é de 2011. Sem acordo com a TBCA, começar pela TACO.
- **Rotulagem:** a RDC 429/2020 vale para alimentos embalados na ausência do consumidor (art. 2º, segundo
  [legisweb](https://www.legisweb.com.br/legislacao/?id=479492)); receita de post não é rótulo, mas não pode parecer um.
- **Saúde:** sem metas pessoais nem "dieta" (dado de saúde, LGPD art. 11); valores para gestantes, crianças e doenças
  ficam fora.
- **Guia Alimentar:** o Ministério da Agricultura pediu em 2026 a revisão do Guia para retirar as recomendações contra
  ultraprocessados ([ofício via Congresso em Foco](https://static.congressoemfoco.com.br/2026/03/17/attachment/g-drive/1lbt0hqn3uii8ciiymgxnnqv9z5gntpxb_oficio-mapa-guia-alimentar.pdf),
  [CNN Brasil](https://www.cnnbrasil.com.br/saude/agricultura-pede-retirada-de-alerta-sobre-ultraprocessados-em-guia-alimentar/));
  desfecho **não verificado**. Mostrar o grupo como informação, citando o Guia, sem juízo próprio.
- Licença de reprodução do Guia Alimentar (BVS fora do ar): **não verificada**.

**Referências de mercado:**
- Tastemade Cooking: "view detailed nutrition info" ([App Store](https://apps.apple.com/us/app/-/id6736664843)).
- Samsung Food: análise nutricional dos ingredientes ([Android Central](https://www.androidcentral.com/apps-software/samsung-food-app-launch)).
- Mealime: nutrição só no plano Pro ([iPhone Life](https://iphonelife.com/content/mealime-best-recipe-app-busy-health-conscious-people)).
- Receitas Nestlé e Tudo Gostoso não mostram informação nutricional na página da receita ([Nestlé](https://www.receitasnestle.com.br/), [Tudo Gostoso](https://www.tudogostoso.com.br/receita/23-bolo-de-cenoura.html)).
- TBCA ([site](https://www.tbca.net.br/), [Jornal da USP](https://jornal.usp.br/?p=268561)); Guia Alimentar e regra de ouro ([CFN](https://cfn.org.br/guia-alimentar-para-a-populacao-brasileira-e-a-principal-referencia-para-escolhas-alimentares-no-pais-2/), [Idec](https://idec.org.br/dicas-e-direitos/guia-alimentar-alimentacao-saudavel)).

**Prioridade:** **C** — valor alto, mas travado por licença de dados e com risco regulatório/político.

**Depende de:** RF9, RC-REC-01.

**Esforço:** G.

---

### RC-REC-07 · "Tá na época": sazonalidade de frutas e hortaliças

**Ator(es):** quem procura o que cozinhar; autor (marca ingrediente da estação); Varejo (destaca o que tem).

**Descrição:** um calendário mensal de frutas, legumes e verduras da estação alimenta um filtro "da estação" nas receitas,
um selo discreto no ingrediente e uma vitrine mensal no feed. Ajuda a economizar e reduz desperdício.

**Critérios de aceite:**
1. A tabela tem produto, meses de oferta alta/média/baixa e fonte com data; é editável só por conta interna.
2. Receita com ingrediente principal da estação ganha o filtro "da estação" no mês corrente (fuso `America/Sao_Paulo`).
3. O selo aparece em texto ("da estação") além do glifo; não é sinal só de cor.
4. A página mostra que a sazonalidade é do entreposto de referência (ex.: São Paulo) e pode variar por região.

**Regras e riscos:** a CEAGESP tem uma Tabela de Sazonalidade feita pela SEDES (contato sedes@ceagesp.gov.br), mas não
achei a tabela publicada nem a licença: **obter autorização**. Sazonalidade é regional; não prometer preço.

**Referências de mercado:**
- Panelinha: seção "Tá na época" com ingredientes da estação e dicas para "economizar na feira" ([panelinha.com.br](https://www.panelinha.com.br/)).
- CEAGESP: Tabela de Sazonalidade da SEDES citada na carta de serviços ([PDF](https://ceagesp.gov.br/wp-content/uploads/2020/08/APROVADA-carta-de-servi%C3%A7o-ao-cidad%C3%A3o-em-RD-10-de-26.02.2021.pdf)).

**Prioridade:** **C** — barato e muito brasileiro, mas depende de licença da tabela.

**Depende de:** RF9, RF14, RC-REC-01.

**Esforço:** P (sem contar o acordo de dados).

---

### RC-REC-08 · Lista de compras da receita e "comprar no varejo"

**Ator(es):** quem vai cozinhar; conta **Varejo** verificada (mercado, empório, hortifrúti); Estabelecimento (secundário).

**Descrição:** a pessoa manda os ingredientes de uma ou mais receitas para uma lista de compras que soma quantidades,
agrupa por seção do mercado e deixa marcar "já tenho". A partir da lista, pode ver contas Varejo próximas que declararam
ter os itens, com o preço declarado e a data, e abrir o canal de compra da loja.

**Critérios de aceite:**
1. Ingredientes iguais de receitas diferentes são somados quando a unidade é compatível; senão aparecem em linhas
   separadas.
2. A lista é privada, funciona offline e pode ser compartilhada por link com quem a pessoa escolher.
3. Preço só aparece se veio do Varejo (declarado, com data e "preço informado pela loja"), nunca estimado pela
   plataforma; preço com mais de N dias some.
4. Lojas sugeridas são ordenadas por distância e cobertura dos itens (ex.: só lojas com ≥ 40% dos itens), nunca por
   pagamento sem o chip **Publicidade**.
5. A localização usada é a aproximada que a pessoa autorizou (RF3/RF10), não o GPS contínuo.

**Regras e riscos:** CDC art. 31 (informação correta de preço) e art. 36 (publicidade identificada). Varejo não vê a lista
da pessoa nem quem abriu a loja sem consentimento (LGPD). Integração de carrinho com redes grandes: não achei API pública
brasileira; começar com link para o canal da loja.

**Referências de mercado:**
- Tudo Gostoso: botão "Compre os ingredientes no iFood" na receita ([página](https://www.tudogostoso.com.br/receita/23-bolo-de-cenoura.html)).
- DeliRec + Mambo (2023): "Comprar Ingredientes" na receita, com itens removíveis do carrinho ([Mercado & Consumo](https://mercadoeconsumo.com.br/14/03/2023/ecommerce/delirec-fecha-parceria-com-mambo-supermercado-para-compra-de-ingredientes-direto-pelo-app/)).
- Instacart Developer Platform: `POST /idp/v1/products/recipe` gera página de receita comprável; mostra lojas com ≥ 40% dos ingredientes; a pessoa monta o carrinho ([docs](https://docs.instacart.com/developer_platform_api/guide/tutorials/create_a_recipe_page)).
- Tastemade Cooking: lista que soma ingredientes entre receitas e agrupa por corredor ([App Store](https://apps.apple.com/us/app/-/id6736664843)); Mealime: lista por seção e "remover o que já tenho" ([foodandnutrition.org](https://foodandnutrition.org/july-august-2016/mealime-version-2-0/)).
- NYT Cooking: lista de compras dentro do Recipe Box ([ajuda](https://thenewyorktimeshelpcenter.helpjuice.com/360011047512-New-York-Times-Cooking/19698276311700-Cooking-Recipe-Box)).

**Prioridade:** **S** — liga o eixo de receitas às contas Varejo (modelo de receita do produto).

**Depende de:** RF6 (Varejo e local), RF9, RF10 (raio), RF3 (consentimento de localização), RC-REC-01.

**Esforço:** G.

---

### RC-REC-09 · Aproveitamento integral e ponte para doação de excedentes

**Ator(es):** autor de receita; quem cozinha; Estabelecimento e Varejo verificados (doadores); Comunidade ou Oficial
verificadas como instituição receptora/banco de alimentos.

**Descrição:** receitas ganham a marcação "aproveitamento" (talos, cascas, sobras) e uma coleção editorial. Separadamente,
Estabelecimentos e Varejo verificados publicam excedentes próprios para doação e instituições receptoras verificadas os
reservam, no espírito da microcoleta prevista na Lei 15.224/2025.

**Critérios de aceite:**
1. "Aproveitamento" é um marcador de receita filtrável e não substitui culinária nem formato.
2. Oferta de doação: só de conta `ESTABELECIMENTO`/`VAREJO` verificada, com item, quantidade, validade, retirada até e
   endereço; só instituição receptora verificada vê e reserva.
3. A oferta mostra os alergênicos declarados (RF9) e o aviso de contaminação cruzada.
4. A plataforma registra entrega e confirmação, mas não intermedeia transporte nem garante qualidade.
5. Pessoa física **não** oferta comida preparada pelo app nesta fase.

**Regras e riscos:** Lei 15.224/2025: doação de preparados é permitida desde que segura (art. 14); bancos, instituições e
estabelecimentos que doam direto precisam de profissional habilitado que ateste a qualidade (art. 14, § 1º); dolo como
critério de responsabilidade (art. 16). Se a plataforma é "intermediário" no sentido da lei: **não verificado** (pedir
parecer). Comida caseira entre pessoas físicas tem risco sanitário alto: excluída.

**Referências de mercado:**
- Lei 15.224/2025 ([Câmara](https://www2.camara.leg.br/legin/fed/lei/2025/lei-15224-30-setembro-2025-798048-publicacaooriginal-176570-pl.html)); revogação da 14.016 anotada no [Planalto](https://planalto.gov.br/ccivil_03/_Ato2019-2022/2020/Lei/L14016.htm).
- Panelinha: posts sobre reaproveitamento de alimentos ([panelinha.com.br](https://www.panelinha.com.br/)).
- App de doação por excedente (ex.: Too Good To Go): **não pesquisado** nesta rodada.

**Prioridade:** **C** — alinhado à lei nova e à marca, mas fora do núcleo de receitas e com risco jurídico a avaliar.

**Depende de:** RF6, RF9, RF13.

**Esforço:** M (marcador: P; doação: M).

---

### RC-REC-10 · Vídeo de receita curto, com legendas e capítulos por passo

**Ator(es):** autor (principalmente Chef e Criador); quem assiste; pessoas surdas ou com baixa visão.

**Descrição:** a receita pode ter um vídeo vertical curto. Cada passo da receita pode apontar para um trecho do vídeo
(capítulo), e o modo cozinhar (RC-REC-02) toca o trecho do passo atual. Legenda é obrigatória para publicar; a
transcrição fica disponível em texto.

**Critérios de aceite:**
1. Limite inicial de duração e tamanho configurável (ex.: até 3 min e 200 MB); o servidor remove metadados (GPS, aparelho)
   como já faz com foto.
2. Publicar exige legenda: gerada automaticamente como rascunho e **revisada pelo autor**, ou enviada em WebVTT.
3. Capítulos ligados a `steps[]`; na marcação schema.org saem como `Clip` no `HowToStep` (RC-REC-03).
4. O player não começa com som, tem controles por teclado, legenda ligável e velocidade ajustável.
5. Vídeo de formato alcoólico herda o 18+ do RF9.
6. A transcrição vale como alternativa textual quando o vídeo tem informação só visual (WCAG 1.2.3), e os passos
   escritos continuam obrigatórios.

**Regras e riscos:** WCAG 2.2: legendas em mídia gravada (1.2.2, nível A) e audiodescrição (1.2.5, AA) — a
audiodescrição completa fica fora; os passos escritos cobrem parte. Música de fundo com direitos: o autor declara que tem
licença; denúncia via RF13. Custo de transcodificação e armazenamento S3.

**Referências de mercado:**
- TikTok: 10 min gravando no app e até 60 min enviando (o limite de 60 min foi teste em 2024; situação atual **não verificada**) ([Dexerto](https://www.dexerto.com/entertainment/how-long-can-a-tiktok-be-1554929/), [Search Engine Land](https://searchengineland.com/tiktok-testing-60-minute-video-uploads-440588)); legendas automáticas editáveis antes de postar ([Business Insider](https://www.businessinsider.nl/how-to-use-tiktoks-auto-caption-feature-to-enable-captions-on-your-own-videos-or-hide-captions-from-videos-youre-viewing)).
- Instagram Reels: 3 min desde janeiro de 2025; 20 min **não verificado** ([Metricool](https://metricool.com/instagram-reels-length)).
- Tudo Gostoso tem vídeo na receita ("Assistir: Bolo de cenoura"); Tastemade, "step-by-step videos" ([Product Hunt](https://www.producthunt.com/@tom_bash)).
- Google: `video` recomendado em `Recipe`; `Clip` no `HowToStep` ([documentação](https://developers.google.com/search/docs/appearance/structured-data/recipe)).
- WCAG 2.2 ([W3C Understanding](https://www.w3.org/WAI/WCAG22/Understanding/)).

**Prioridade:** **S** — vídeo é o formato dominante de receita, e o RF9 já o lista como pendência.

**Depende de:** RF9 (pendência de vídeo), RF13, RC-REC-02, RC-REC-03.

**Esforço:** G.

---

### RC-REC-11 · Cookalong ao vivo

**Ator(es):** Chef, Criador ou Comunidade (anfitrião); participantes; moderador.

**Descrição:** um `EVENTO` do RF9 pode virar uma sessão ao vivo de "cozinhe junto": a lista de ingredientes sai com
antecedência (RC-REC-08), o anfitrião transmite e os participantes acompanham pelo modo cozinhar, com chat moderado.

**Critérios de aceite:**
1. A sessão está ligada a um `EVENTO` com receita; os inscritos recebem a lista de compras D-2.
2. Legenda ao vivo ligada por padrão (WCAG 1.2.4, nível AA).
3. Chat com filtro, modo lento e botão de denúncia; o anfitrião e o moderador podem silenciar.
4. Sessão com bebida alcoólica é 18+ e só entra quem tem idade verificada.
5. Gravação só com aviso prévio aos participantes; quem aparece em vídeo consente.

**Regras e riscos:** o CONAR abriu representação contra lives com consumo de bebida "sem restrição de acesso para menores
de 18 anos" (caso Gusttavo Lima/Ambev, 2020; desfecho **não verificado**). ECA Digital (Lei 15.211/2025, vigente desde
17/03/2026). Moderação em tempo real é cara.

**Referências de mercado:**
- Food Network Kitchen: até 25 aulas ao vivo por semana no lançamento (2019); o app saiu das lojas em 28/03/2024 e parou em
  01/05/2024 ([ajuda da Food Network](https://help.foodnetwork.com/hc/en-us/articles/22105246548247-What-is-happening-to-the-Food-Network-Kitchen-app), [TechCrunch](https://techcrunch.com/?p=1900861)).
- Caso CONAR das lives ([Metrópoles](https://www.metropoles.com/entretenimento/musica/publicidade-de-cerveja-em-lives-de-gusttavo-lima-e-alvo-do-conar)).

**Prioridade:** **W** (agora) — o maior serviço do gênero fechou, o custo de moderação é alto e há risco com menores.
Rever depois de RC-REC-10.

**Depende de:** RF9 (`EVENTO`), RF13, RF1/RF4 (idade), RC-REC-02, RC-REC-08, RC-REC-10.

**Esforço:** G.

---

### RC-REC-12 · Harmonização com bebida (com e sem álcool)

**Ator(es):** autor da receita (Chef e Criador em especial); leitores adultos e menores; Estabelecimento (sommelier).

**Descrição:** a receita pode sugerir bebidas para acompanhar, em dois blocos: "sem álcool" (sucos, chás, cafés,
fermentados sem álcool) e "com álcool" (vinho, cerveja, destilados). O bloco com álcool é 18+ e some para menores e para
visitantes sem idade verificada; a receita continua `LIVRE`.

**Critérios de aceite:**
1. A harmonização é uma lista de até 3 itens por bloco, com tipo, estilo (ex.: "branco seco", "IPA") e motivo curto; sem
   marca, a não ser em post com o chip **Publicidade**.
2. O bloco com álcool não é renderizado nem vai na API para menores de 18 ou visitantes sem idade verificada; a receita
   não vira 18+ por causa dele.
3. Menor de 18 não preenche o bloco com álcool (`422`).
4. O texto não associa álcool a desempenho, saúde, direção ou esporte (validação por lista de termos + RF13).
5. Receita com álcool **como ingrediente** (ex.: risoto com vinho) mostra o aviso de que o álcool nem sempre evapora
   (redação a validar).

**Regras e riscos:** Lei 9.294/96 define bebida alcoólica, para fins de propaganda, como acima de 13 °GL; cerveja e a
maioria dos vinhos ficam sob a autorregulação do CONAR (Anexo P, "não ter crianças e adolescentes como alvo"). ECA Digital
veda autodeclaração para conteúdo proibido a menores (fonte secundária) e caberá à ANPD definir os métodos de verificação.
ECA art. 81, II e art. 243 (venda a menores): **não verificado** nesta pesquisa.

**Referências de mercado:**
- Vivino: busca "por uva, região, estilo ou prato" e harmonizações ao ler o rótulo ([App Store](https://apps.apple.com/app/414461255)).
- Lei 9.294/96 e CONAR ([UNIRIO](https://www.unirio.br/cead/ccbs/nutricao/ppgan_pt/acoes-ppgan/alimentacao-e-saude/palestras/2015/bebidas-alcoolicas-suas-classificacoes-e-sua-publicidade), [FAPESP](https://agencia.fapesp.br/index.php/publicidade-de-cerveja-viola-normas-de-protecao-a-criancas-e-adolescentes-diz-estudo/21729)).
- ECA Digital ([Olhar Digital](https://olhardigital.com.br/2026/03/17/internet-e-redes-sociais/eca-digital-entra-em-vigor-nesta-terca-saiba-o-que-muda-na-internet-brasileira/), [Machado Meyer](https://www.machadomeyer.com.br/images/ebooks/Factsheet_ECA_Digital.pdf)).

**Prioridade:** **C** — agrega valor para Chef e Criador, mas exige verificação de idade robusta antes.

**Depende de:** RF1/RF4 (idade), RF9 (`ageRating`), RF13.

**Esforço:** P.

---

### RC-REC-13 · Substituições de ingredientes (alergia, estilo e despensa)

**Ator(es):** autor (declara substituições testadas); quem cozinha (escolhe); pessoa com alergia ou restrição.

**Descrição:** o autor indica, por ingrediente, substituições que ele testou ("leite → bebida de aveia"). Quem lê escolhe
uma substituição e vê a receita ajustada, com os alergênicos **recalculados como sugestão** e um aviso fixo. A
plataforma nunca afirma que a versão substituída é segura para quem tem alergia.

**Critérios de aceite:**
1. Substituição é declarada pelo autor (ingrediente → alternativo, proporção, observação); só o autor publica.
2. Ao escolher uma substituição, os alergênicos do RF9 são recalculados: alergênico removido vira "pode conter — confira
   os rótulos", nunca "não contém".
3. O aviso "Confira sempre o rótulo de cada produto. Esta receita não é garantia para pessoas com alergia" aparece em
   toda versão substituída, em texto, sem ser escondível.
4. Filtros de busca por alergênico tratam a versão substituída como "pode conter".
5. A escolha de substituição não é salva no perfil nem usada em recomendação sem o consentimento do RF2/RF3 (estilo e
   alergia são dados sensíveis).

**Regras e riscos:** contaminação cruzada depende do produto comprado, não da receita. Aviso "pode conter" é voluntário e
incompleto no rótulo: críticas a apps que ignoram "may contain" mostram o risco de passar falsa segurança.
LGPD art. 11 (dado de saúde).

**Referências de mercado:**
- Samsung Food: "Personalize" adapta receitas para sem glúten, pescetariano e sem lactose, com "ingredient alternatives"; não diz se trata contaminação cruzada ([DT Next](https://www.dtnext.in/lifestyle/technology/samsungs-ai-powered-smart-fridge-to-create-recipes-as-per-your-diet-757297), [Samsung Newsroom](https://news.samsung.com/global/meet-samsung-food-at-ifa-2023-the-ultimate-personalized-cooking-assistant)).
- Mealime: filtro de alergênicos e o aviso "For severe allergies please double-check everything" (blog de terceiros) ([Explore Big Sky](https://www.explorebigsky.com/from-jackie-with-love-healthy-meal-planning-made-easy)).
- Críticas a apps que ignoram "pode conter" ([SnackSafely sobre Sifter/FARE](https://snacksafely.com/2022/12/advisory-do-not-use-fares-sifter-shopping-app-if-allergen-cross-contact-concerns-you/), [sobre Fig](https://snacksafely.com/2023/09/advisory-dont-use-the-fig-scanner-app-if-the-potential-for-allergen-cross-contact-concerns-you/)).
- Receitas Nestlé: categorias "Receitas sem Lactose", "Receitas Sem Glúten" ([site](https://www.receitasnestle.com.br/)).

**Prioridade:** **S** — tema central para alérgicos e restrições, com regra conservadora.

**Depende de:** RF9 (alergênicos), RF2/RF3 (consentimento), RC-REC-01.

**Esforço:** M.

---

### RC-REC-14 · IA: classificação sugerida e texto alternativo a partir da foto

**Ator(es):** autor do post; serviço de visão computacional (secundário); pessoas com deficiência visual (beneficiárias).

**Descrição:** ao enviar a foto de um prato ou receita, a IA sugere culinária, formato e um rascunho de texto alternativo.
Tudo chega como sugestão pré-preenchida e destacada; o autor confirma, edita ou descarta. Nada é publicado sem ação
humana.

**Critérios de aceite:**
1. As sugestões aparecem como chips "sugerido" e só entram no post quando o autor confirma cada uma; o padrão é "não
   aplicado".
2. A sugestão só aparece acima de um limiar de confiança configurável; abaixo dele, nada é sugerido.
3. O texto alternativo sugerido usa linguagem de incerteza ("parece ser…"), tem até 250 caracteres, e o autor precisa
   tocar no campo para aceitar; fica registrado se foi editado.
4. A IA não sugere alergênicos nem estilo alimentar a partir da foto.
5. A foto enviada ao serviço é a versão já sem EXIF; o fornecedor não pode treinar com ela (cláusula contratual).
6. Métrica: taxa de aceitação e de edição por campo, para medir a qualidade.

**Regras e riscos:** acurácia baixa e variável: um estudo com 185 fotos e 7 plataformas comerciais achou acerto top-1 de 9%
(Google Vision API) a 63% (Calorie Mama), e nenhuma estimava a quantidade (resumo da busca; texto completo **não
verificado**). Culinárias brasileiras regionais tendem a estar sub-representadas (**não verificado**). LGPD art. 6º, III
(minimização) e contrato com operador (art. 39).

**Referências de mercado:**
- Samsung Food Vision AI: reconhece pratos e ingredientes pela câmera ([Samsung Newsroom DE](https://news.samsung.com/de/die-samsung-food-app-ein-ultimativ-personalisierbarer-kuchenhelfer), [Silicon Republic](https://www.siliconrepublic.com/?p=1046289)).
- Meta, texto alternativo automático: frases com ressalva ("may be…"); texto do autor tem precedência ([TechCrunch](https://techcrunch.com/2021/01/19/facebook-and-instagrams-ai-generated-image-captions-now-offer-far-more-details/), [Social Media Today](https://www.socialmediatoday.com/social-networks/facebook-automated-captions-improve-accessibility-provide-additional-insights)).
- Estudo de acurácia ([JMIR Formative Research, 2020](https://formative.jmir.org/2020/12/e15602/)).

**Prioridade:** **S** — reduz atrito de publicação e melhora acessibilidade, com risco controlado pela confirmação.

**Depende de:** RF9 (foto, `altText`, taxonomia), RF16 (a moldura não entra na análise), RC-REC-18.

**Esforço:** M.

---

### RC-REC-15 · IA: alergênicos detectados no texto da receita (só sugestão)

**Ator(es):** autor da receita; leitores com alergia (beneficiários indiretos).

**Descrição:** ao salvar uma receita, um verificador (dicionário de ingredientes + modelo) lê ingredientes e passos e
aponta alergênicos que o autor **não marcou** ("farinha de trigo → glúten e trigo?"). O autor decide. O objetivo é reduzir
omissões, nunca declarar ausência.

**Critérios de aceite:**
1. O verificador só **acrescenta** sugestões; nunca remove um alergênico marcado pelo autor nem sugere "não contém".
2. Cada sugestão mostra o trecho do texto que a gerou e os botões "marcar como contém", "marcar como pode conter" e
   "não se aplica".
3. "Não se aplica" pede confirmação explícita e fica registrado (quem, quando, versão do modelo) para auditoria.
4. O leitor vê se o alergênico foi marcado pelo autor ou aceito a partir de sugestão; nunca vê sugestão não confirmada.
5. Aviso fixo: "Alergênicos informados pelo autor. Confira os rótulos. Pode haver contaminação cruzada."
6. Versão do dicionário/modelo e taxa de falsos negativos medidos num conjunto de teste em pt-BR antes de ligar.

**Regras e riscos:** usar os alergênicos do RF9 (RDC 727/2022) como vocabulário. Ingredientes compostos ("molho
inglês", "caldo em tablete") variam por marca: sugerir "pode conter". Responsabilidade: a declaração continua do autor;
a plataforma não certifica. Evitar a sensação de garantia criticada em apps de alergia.

**Referências de mercado:**
- App de receitas que trata ingrediente incerto como "may contain" (texto de marketing, **não verificado**) e recomendação de modelar contaminação cruzada como atributo separado ([resultado de busca](https://www.similarweb.com/app/apple/6798419343/)).
- Críticas a falsa segurança ([SnackSafely](https://snacksafely.com/2022/12/advisory-do-not-use-fares-sifter-shopping-app-if-allergen-cross-contact-concerns-you/)).
- Não achei app de receita grande que faça detecção de alergênicos no texto: **não verificado** no mercado.

**Prioridade:** **S** — reduz omissão de alergênico, que é o maior risco de saúde do conteúdo de receitas.

**Depende de:** RF9 (alergênicos), RC-REC-01, RC-REC-18.

**Esforço:** M.

---

### RC-REC-16 · IA: busca de receitas em linguagem natural e "o que tem em casa"

**Ator(es):** quem procura receita (com ou sem conta).

**Descrição:** a pessoa escreve "jantar rápido sem lactose com abobrinha e frango" ou lista o que tem na geladeira, e a
busca converte o pedido em filtros explícitos (ingredientes, tempo, estilo, alergênicos, culinária) e mostra esses
filtros como chips editáveis. A IA interpreta o pedido; a ordenação e os resultados vêm do índice de receitas da
plataforma, nunca de texto gerado.

**Critérios de aceite:**
1. A consulta vira filtros visíveis ("sem lactose", "≤ 30 min", "abobrinha", "frango"); a pessoa remove ou edita cada um.
2. Os resultados são só receitas publicadas; a IA não gera receitas, quantidades nem passos.
3. Filtro de alergênico exclui receitas que "contêm" e marca as que "podem conter"; nunca diz "segura".
4. A consulta não vira dado de perfil; estilo e alergia citados na busca não são salvos sem o consentimento do RF2/RF3.
5. Sem IA disponível, a busca cai para palavra-chave com os mesmos filtros.

**Regras e riscos:** consulta pode conter dado sensível ("sou celíaco"): não registrar em log com identificação (LGPD art.
11; minimização). Alucinação de filtro: sempre mostrar o que foi entendido.

**Referências de mercado:**
- Receitas Nestlé: "O que tem em casa?" / "Busca por ingredientes que tenho em casa" ([site](https://www.receitasnestle.com.br/)).
- Cookpad: projeto com a Universidade de Liverpool de NLP para buscar por ingrediente e nível de habilidade ([Liverpool](https://liverpool.ac.uk/collaborate/consultancy/cookpad)).
- Panelinha: filtros por categoria e tempo ([site](https://www.panelinha.com.br/)).
- Busca em linguagem natural lançada por NYT Cooking ou Allrecipes: **não verificado** (não achei anúncio).

**Prioridade:** **C** — melhora a descoberta, mas a busca por filtros (RF10 estendida a receitas) vem antes.

**Depende de:** RF10 (padrão de filtros), RF9, RC-REC-01, RC-REC-18.

**Esforço:** G.

---

### RC-REC-17 · IA: resumo de avaliações e de "Fiz essa receita", com transparência

**Ator(es):** leitor da receita ou da página do estabelecimento; autor/dono (vê o resumo); moderador.

**Descrição:** com volume suficiente, um parágrafo curto resume o que as pessoas disseram ("muitos reduziram o açúcar;
alguns acharam o tempo de forno curto"), com atributos tocáveis que filtram os comentários de origem. O resumo é
identificado como gerado por IA e liga para os textos que o originaram.

**Critérios de aceite:**
1. Só aparece com um mínimo de N comentários (ex.: 20) e usa só conteúdo publicado e não removido (RF13).
2. Rótulo fixo: "Resumo gerado por IA a partir de N comentários" e link "ver comentários"; cada atributo filtra os
   comentários que o mencionam.
3. O resumo mantém pontos positivos e negativos na proporção dos comentários; não reescreve a nota em estrelas.
4. O autor ou dono pode contestar o resumo (vai para revisão humana); não pode editá-lo nem escondê-lo.
5. O resumo é recalculado quando entram ou saem comentários, com data da última atualização.
6. Avaliações sob suspeita (`UNDER_REVIEW`) ficam fora.

**Regras e riscos:** resumo errado sobre estabelecimento afeta reputação e pode ser lido como decisão automatizada com
efeito sobre o dono (LGPD art. 20): prever pedido de revisão. CDC: não pode virar publicidade disfarçada.

**Referências de mercado:**
- Amazon: "Customers say", com "AI-generated from the text of customer reviews", atributos tocáveis e só avaliações de compra verificada ([About Amazon](https://www.aboutamazon.com/news/amazon-ai/amazon-ai-generated-review-highlights)).
- Google Maps: resumos de avaliações com Gemini (fim de 2024); rótulo "Curated with Gemini" visto em sugestões, não confirmado nos resumos ([9to5Google](https://9to5google.com/?p=647821), [Silicon UK](https://www.silicon.co.uk/cloud/ai/google-maps-ai-587188)).

**Prioridade:** **C** — útil só com volume de comentários; vem depois de RC-REC-04 e do RF13.

**Depende de:** RF8, RF11 (página do estabelecimento), RF12, RF13, RC-REC-04, RC-REC-18.

**Esforço:** M.

---

### RC-REC-18 · Governança de IA: rótulo "gerado por IA", moderação assistida e revisão de decisões

**Ator(es):** qualquer autor; moderador; titular de dados afetado; encarregado (DPO).

**Descrição:** regra única para todo uso de IA no Friendly Eats. (a) Conteúdo **gerado** ou muito alterado por IA leva o
rótulo "Gerado por IA", declarado pelo autor e também acionado por metadados de procedência (C2PA/IPTC) quando
presentes. (b) A moderação usa classificadores só para **priorizar** a fila do RF13; remoção e suspensão são decididas por
pessoa. (c) Toda decisão automatizada que afeta alguém (ocultar post, rebaixar no feed, resumo contestado) pode ter
revisão pedida, com explicação dos critérios.

**Critérios de aceite:**
1. Campo "conteúdo gerado por IA" no post; foto com manifesto C2PA ou IPTC indicando IA chega pré-marcada, e o autor só
   desmarca com justificativa (vai para amostragem do moderador).
2. O rótulo aparece em texto ("Gerado por IA") e glifo, com toque que explica o que significa; vale para foto, vídeo e
   texto.
3. Receita inteira gerada por IA exige o rótulo e a confirmação do autor de que a testou; sem confirmação, não publica.
4. Classificadores de moderação só ordenam a fila e ocultam provisoriamente conteúdo de risco grave (ex.: nudez, ódio);
   toda ocultação tem revisão humana em até X horas e notifica o autor com o motivo.
5. "Pedir revisão" disponível em toda decisão automatizada, com registro (modelo, versão, entrada, saída, data), resposta
   por pessoa e prazo publicado.
6. Página pública "Como usamos IA" lista cada recurso, fornecedor (categoria), dados usados e como desligar.

**Regras e riscos:** LGPD art. 20 (revisão; a revisão por pessoa natural foi vetada, mas adotamos por política). PL
2338/2023: ainda em tramitação; o texto da Câmara prevê identificador em conteúdo sintético (art. 19) — preparar sem
depender dele. ECA Digital: perfis de menores com padrão mais restrito. Detecção automática de IA falha muito: a Meta
admite que não detecta tudo; o rótulo é declaração do autor + sinal técnico, nunca prova.

**Referências de mercado:**
- Meta: "Made with AI" virou "AI Info"; usa C2PA e IPTC; admite limites de detecção ([Engadget](https://www.engadget.com/meta-plans-to-ramp-up-labeling-of-ai-generated-images-across-its-platforms-160234038.html), [Jang](https://jang.com.pk/en/15206-meta-replaces-made-with-ai-label-with-ai-info-amid-user-criticism-news)).
- TikTok: exige rótulo em conteúdo realista gerado por IA e lê Content Credentials (C2PA) ([ForkLog](https://forklog.com/en/tiktok-introduces-tool-to-limit-ai-content-in-feeds/)).
- LGPD art. 20 e o veto ([Serpro](https://serpro.gov.br/lgpd/noticias/2019/congresso-aprecia-veto-ao-artigo-20-da-lgpd)); PL 2338/2023 ([Senado](https://www25.senado.leg.br/web/atividade/materias/-/materia/157233), [inteiro teor na Câmara](https://www.camara.leg.br/proposicoesWeb/prop_mostrarintegra?codteor=2868197&filename=PL+2338%2F2023)).

**Prioridade:** **M** — pré-requisito de qualquer recurso de IA (RC-REC-14 a 17) e da moderação em escala.

**Depende de:** RF3 (transparência e consentimentos), RF9, RF13, RF14 (feed), RF1 (idade).

**Esforço:** G.

---

## Tabela-resumo

| ID | Título | Prioridade | Esforço | Depende de |
|---|---|---|---|---|
| RC-REC-01 | Ingredientes estruturados, escala de porções e conversão de medidas | M | M | RF9 |
| RC-REC-02 | Modo cozinhar (tela acesa, passo a passo e timers) | S | M | RF9, RC-REC-01 |
| RC-REC-03 | Página pública com schema.org/Recipe e Rich Pins | S | P | RF9, RF14, RC-REC-01 |
| RC-REC-04 | "Fiz essa receita" e notas pessoais | S | M | RF9, RF12, RF8, RF13, RF5 |
| RC-REC-05 | Remix com crédito ao autor original | M | M | RF9, RF15, RF13 |
| RC-REC-06 | Painel do prato: nutrição estimada e grau de processamento | C | G | RF9, RC-REC-01 |
| RC-REC-07 | "Tá na época": sazonalidade | C | P | RF9, RF14, RC-REC-01 |
| RC-REC-08 | Lista de compras e "comprar no varejo" | S | G | RF6, RF9, RF10, RF3, RC-REC-01 |
| RC-REC-09 | Aproveitamento integral e ponte para doação | C | M | RF6, RF9, RF13 |
| RC-REC-10 | Vídeo de receita com legendas e capítulos | S | G | RF9, RF13, RC-REC-02, RC-REC-03 |
| RC-REC-11 | Cookalong ao vivo | W | G | RF9, RF13, RF1/RF4, RC-REC-02, 08, 10 |
| RC-REC-12 | Harmonização com bebida (com e sem álcool) | C | P | RF1/RF4, RF9, RF13 |
| RC-REC-13 | Substituições de ingredientes | S | M | RF9, RF2/RF3, RC-REC-01 |
| RC-REC-14 | IA: classificação e texto alternativo pela foto | S | M | RF9, RF16, RC-REC-18 |
| RC-REC-15 | IA: alergênicos detectados no texto (sugestão) | S | M | RF9, RC-REC-01, RC-REC-18 |
| RC-REC-16 | IA: busca em linguagem natural e "o que tem em casa" | C | G | RF10, RF9, RC-REC-01, RC-REC-18 |
| RC-REC-17 | IA: resumo de avaliações com transparência | C | M | RF8, RF11, RF12, RF13, RC-REC-04, RC-REC-18 |
| RC-REC-18 | Governança de IA: rótulo, moderação assistida e revisão | M | G | RF3, RF9, RF13, RF14, RF1 |

Ordem sugerida: RC-REC-01 → 05 → 18 → 02/03/13 → 15/14 → 04 → 08 → 10 → o resto.

## Ideias descartadas e por quê

| Ideia | Por que ficou de fora |
|---|---|
| **Gerar receitas inteiras por IA** (pelo app, sem autor) | Quantidades e tempos inventados podem causar risco de saúde (fermentação, conservas, alergênicos) e não há quem responda pela receita. Receita feita com IA e testada por uma pessoa entra pelo RC-REC-18 com rótulo. |
| **Calorias ou porção estimadas pela foto** | Nenhuma das 7 plataformas do estudo da JMIR (2020) estimava quantidade, e o acerto top-1 ia de 9% a 63%. Um número errado sobre saúde induz a erro. |
| **Selo "seguro para alérgicos" / "livre de"** em receita | A receita não controla contaminação cruzada nem a formulação de cada marca. Contraria a regra do RF9 e as críticas a apps que passam falsa segurança. |
| **Importar receita de qualquer site ou vídeo copiando texto e foto** (como o Samsung Food) | Texto e foto são protegidos (Lei 9.610, art. 7º, I e VII), e a coleção pode ser protegida como base de dados (art. 7º, XIII). Alternativa possível: salvar o link com notas privadas (RC-REC-04). |
| **Plano alimentar com metas de saúde** (perfil de saúde do Samsung Food+) | Dado de saúde (LGPD art. 11) e risco de exercício de atividade privativa de nutricionista (Lei 8.234/1991, **não verificado**). O Nestlé usa nutricionista humano ("Chat com Nutricionista"). |
| **Rotulagem frontal "ALTO EM" nas receitas** | A RDC 429/2020 e a IN 75/2020 tratam de alimento embalado. Imitar a lupa num post confunde a pessoa e sugere chancela da Anvisa. |
| **Comparador de "menor preço" com raspagem de sites de mercado** | Preço desatualizado ou errado é problema de CDC (art. 31), há risco contratual de raspagem e conflito com as contas Varejo. O RC-REC-08 usa só o preço declarado pela loja, com data. |
| **Recomendação pelo estilo alimentar ou pela alergia inferidos do comportamento** | Inferir dado sensível sem consentimento viola a LGPD (art. 11). Só a preferência declarada e consentida do RF2/RF3. |
| **Desafios ou trends com bebida alcoólica** | Engajamento viral alcança menores. Vai contra o CONAR (Anexo P) e o ECA Digital. |
| **Doação de comida caseira entre pessoas físicas** | Risco sanitário sem profissional habilitado (Lei 15.224, art. 14, § 1º, para quem doa direto) e dúvida sobre o papel da plataforma como intermediária. O RC-REC-09 limita a doação a organizações verificadas. |
| **Integração com eletrodomésticos inteligentes** (termômetro, forno) | Fora do eixo, e há o risco de depender de terceiros: donos do termômetro do Yummly ficaram sem app quando o serviço fechou. |
| **Detecção automática de IA como prova para rotular ou punir** | A detecção falha muito (a própria Meta admite), e um falso positivo pune quem é inocente. Fica só como sinal no RC-REC-18. |
| **Cookalong ao vivo agora** | Documentado como RC-REC-11 com prioridade W: o maior serviço do gênero (Food Network Kitchen) fechou em 2024, e o custo de moderação ao vivo e o risco com menores são altos. |

## Lacunas desta pesquisa (não verificadas)

- Allrecipes ("I Made It", ajuste de porções): o site bloqueou a leitura e a busca não trouxe fonte oficial.
- Panelinha: não há app oficial com lista de compras ou cardápio confirmado; só os recursos do site citados acima.
- Tabela de Sazonalidade da CEAGESP: a existência está confirmada, mas o conteúdo e a licença não.
- Licença de reprodução do Guia Alimentar e o desfecho do pedido de revisão do Mapa (2026).
- ECA arts. 81 e 243 e o texto do Anexo P do CONAR (texto vigente).
- Numeração final do PL 2338/2023 (o art. 19 vem da versão publicada pela Câmara).
- Legendas automáticas do Instagram e limite de 20 min dos Reels; limite de 60 min do TikTok para todos.
- Wake Lock em web app instalado no iOS depois de 2023; reconhecimento de voz no navegador.
