# Pesquisa de requisitos 01 · Comunidade, social e descoberta

Pesquisa de requisitos candidatos (RC) para o Friendly Eats no eixo **comunidade, social e descoberta**. Ela não
altera o código nem os RFs. Cada RC é uma proposta para virar RF (ou extensão de RF) depois de revisão.

**Data da pesquisa:** 25/10/2026. **Método:** buscas na web e leitura de páginas oficiais (centrais de ajuda, blogs das
empresas) e de imprensa. Quando a fonte é de terceiros, antiga ou não confirmou o detalhe, o texto diz
**(não verificado)** ou indica o ano da fonte.

**O que já existe e não é reproposto aqui:** RF1 a RF16 (cadastro com 7 tipos de conta, perfil, consentimentos,
login, exclusão, estabelecimento, classificação, estrelas, posts com 7 tipos, busca com raio, interações, moderação,
feeds, perfil público, moldura) e os pendentes já anotados (seguir perfis, "meus salvos", notificações, resposta do
dono, check-in, lista/guia). Os RCs abaixo que tocam esses pendentes descrevem **só a extensão nova** e dizem
qual é o recorte.

**Restrições do produto que valem para todos os RCs** (do código e da taxonomia atuais):

- Consentimentos do RF3 nascem desligados. `PERSONALIZED_FEED` e `LOCATION_TRACKING` ficam **travados para menor de
  16** (ECA Digital); `SENSITIVE_PROFILE_DATA` (estilos alimentares que revelam saúde ou religião) fica travado
  para menor de 18 sem o responsável (LGPD Art. 11 e 14).
- `COMENSAL` a partir de 13 anos; todos os outros tipos de conta exigem 18+. Conteúdo `AGE_18` não aparece para
  menor nem para visitante.
- O selo `category_verification` diz só "é quem diz ser": nunca é pago e nunca indica qualidade.
- Regras contra avaliação falsa da taxonomia (seção 10.7): `RULE_NO_INSIDER`, `RULE_NO_SENTIMENT_INCENTIVE`,
  `RULE_INCENTIVE_DISCLOSED`, `RULE_NO_AI_FAKE`, `RULE_INFLUENCER`.
- ECA Digital (Lei 15.211/2025): em vigor desde 17/03/2026, com seis meses de adaptação segundo a imprensa; exige
  conta de menor de 16 vinculada a um responsável, ferramentas de supervisão, privacidade mais restritiva por
  padrão e veda perfilamento para publicidade a crianças e adolescentes
  ([Xataka](https://www.xataka.com.br/diversos/adeus-as-contas-secretas-redes-sociais-serao-obrigadas-a-vincular-perfil-menores-16-anos-ao-celular-dos-pais-gracas-ao-eca-digital),
  [Grupo GEN](https://blog.grupogen.com.br/juridico/areas-de-interesse/estatuto-da-crianca-e-do-adolescente/eca-digital-lei-15211-2025-estatuto-crianca-adolescente/),
  [Demarest](https://www.demarest.com.br/protecao-de-criancas-e-adolescentes-no-ambiente-digital/)). A vedação a
  "recursos que estimulam a permanência" e a "notificações de urgência fabricadas" foi citada pela imprensa
  ([Jornal de Brasília/Folha](https://jornaldebrasilia.com.br/noticias/brasil/governo-lula-lanca-eca-digital-para-ampliar-protecao-as-criancas-na-internet/)),
  mas o texto literal do artigo **não foi verificado**. Há ainda o PL 5.271/2026, que proporia proibir conta de
  menor de 16 ([Congresso em Foco](https://www.congressoemfoco.com.br/noticia/123095/projeto-proibe-conta-independente-nas-redes-para-menores-de-16));
  situação **não verificada**. Conferir o texto no Planalto antes de transformar em RF.

---

### RC-COM-01 · Seguir lugares e feed "Seguindo" em ordem cronológica

**Ator(es):** Comensal, Criador, Chef, Comunidade (quem segue); Estabelecimento e Varejo & Marca (quem é seguido como
lugar); Visitante (só vê contadores públicos).

**Descrição:** Estende o "seguir perfis" já pendente com duas coisas novas: seguir um **estabelecimento** (o lugar,
não só a conta dona), escolhendo o que receber (prato do dia, promoção, evento, cardápio novo), e um feed
**Seguindo** em ordem cronológica, separado do feed "em alta" e do "para você". Conta com perfil privado aprova
pedidos de seguir. Silenciar alguém tira os posts do feed sem avisar a pessoa.

**Critérios de aceite:**
1. Na página do estabelecimento (RF11), "Seguir" abre as opções `PRATO_DO_DIA`, `PROMOCAO`, `EVENTO` e "tudo"; dá
   para mudar ou parar de seguir a qualquer momento, e a escolha vale só para aquele lugar.
2. O feed Seguindo lista só posts de perfis e lugares seguidos, do mais novo para o mais antigo, sem reordenar
   por engajamento e sem depender de `PERSONALIZED_FEED`.
3. Seguir um perfil com visibilidade privada cria um pedido pendente; o dono aceita ou recusa, e quem pediu só
   vê "pedido enviado".
4. Silenciar e deixar de seguir são ações distintas; silenciar não notifica a pessoa silenciada e é reversível.
5. Promoção de lugar seguido só chega para quem ligou `MARKETING` (RF3); sem esse consentimento, a promoção aparece
   no feed Seguindo mas não gera notificação.
6. Bloquear remove o vínculo de seguir nos dois sentidos e impede um novo pedido.

**Regras e riscos:**
- LGPD: a lista de quem a pessoa segue revela hábitos (bares, lugares religiosos como restaurante kosher ou
  halal); padrão "só eu vejo quem eu sigo" para menor de 18, configurável para adulto.
- ECA Digital: menor de 16 nasce com perfil privado e só aceita seguidores aprovados; o responsável vinculado vê a
  lista de quem segue o menor.
- Abuso: limite de pedidos de seguir por hora contra perseguição e spam de "seguir para ser seguido".
- Acessibilidade: o botão Seguir tem rótulo de estado ("Seguindo", "Pedido enviado"), não só ícone ou cor.

**Referências de mercado:**
- Untappd: seguir um *venue* verificado para receber alertas de cervejas novas e eventos, com escolha "todos",
  "só cervejas" ou "só eventos" ([ajuda Untappd](https://help.untappd.com/hc/en-us/articles/360033786032-How-Do-I-Subscribe-to-a-Verified-Venue-on-Untappd),
  [notificações](https://help.untappd.com/hc/en-us/articles/360034388971-Manage-Notifications-for-Friend-Activity-Beers-Venues-App-Updates-Shop-Promotions)).
- Beli: feed com os lugares que os amigos experimentaram por último ([App Store](https://apps.apple.com/us/app/-/id1478375386)).

**Prioridade:** Must, porque é a base de todo o eixo social e o feed "em alta" sozinho não cria vínculo.

**Depende de:** RF2, RF3, RF11, RF14, RF15 e o "seguir perfis" pendente do RF12.

**Esforço:** M

---

### RC-COM-02 · Listas colaborativas com coeditores e votação

**Ator(es):** Comensal, Criador, Chef, Comunidade (dono e coeditores); qualquer conta (seguidor da lista); Visitante
(lê listas públicas).

**Descrição:** Estende a lista/guia já pendente com **colaboração**: o dono convida coeditores, que adicionam lugares
e notas, e qualquer membro reage a cada item ("quero ir", "não curto"). Listas têm três visibilidades (privada,
por link, pública) e podem ser **seguidas**: quem segue vê as mudanças. As listas de sistema "Quero ir" e "Já fui"
ficam sempre privadas por padrão.

**Critérios de aceite:**
1. O dono convida coeditores por perfil ou por link de convite com validade; o convidado aceita antes de editar e
   pode sair a qualquer momento.
2. Cada item mostra quem o adicionou, a nota opcional e a contagem de reações por tipo.
3. Visibilidade `PRIVADA`, `LINK` ou `PUBLICA`; mudar para pública pede confirmação e avisa os coeditores.
4. Seguir uma lista pública coloca as mudanças dela no feed Seguindo e não exige seguir o dono.
5. Lugar removido ou fechado (RF6) continua na lista com o rótulo "Fechado", sem link quebrado.
6. A ordem dos itens pode ser mudada por botões "subir" e "descer", além de arrastar.

**Regras e riscos:**
- LGPD: "Já fui" é histórico de presença; nunca vira pública sem ação explícita. Listas de menor de 16 não podem ser
  `PUBLICA`.
- Abuso: lista pública é conteúdo e entra na moderação (RF13); título e notas passam pelos mesmos filtros de
  comentário. Coeditor removido perde acesso na hora.
- Publicidade: lista pública de Criador com lugar pago precisa do chip Publicidade (`RULE_INFLUENCER`).
- Acessibilidade: arrastar precisa de alternativa sem arrasto (WCAG 2.2, critério 2.5.7).

**Referências de mercado:**
- Google Maps: listas compartilhadas em que amigos adicionam lugares e votam com emoji (2023)
  ([TechCrunch](https://techcrunch.com/2023/11/15/google-maps-gets-more-social-feature-help-you-collaborate-with-friends));
  listas seguíveis, com as listas padrão "Favoritos" e "Quero ir" (2017) ([VentureBeat](https://venturebeat.com/mobile/google-maps-now-lets-you-create-share-and-follow-lists-of-places)).
- Tripadvisor Trips: listas por destino, privadas ou públicas; convite por link para coeditar (fonte de design,
  **não verificado**) ([screensdesign](https://screensdesign.com/showcase/tripadvisor-plan-book-trips)).
- Guia Michelin: o app permite criar e compartilhar coleções e marcar lugares favoritos
  ([Michelin, 12/2025](https://guide.michelin.com/es/es/articulo/news-and-views/como-usar-la-guia-michelin-funciones-para-restaurantes-y-viajes)).
- Yelp Collections: coleções pessoais com notas, compartilháveis por link; edição por terceiros **não verificada**
  ([Marketing Dive](https://www.marketingdive.com/news/yelp-uses-ai-to-complement-consumer-curated-content-in-collections/524365/)).
- Letterboxd: listas ranqueadas ou não, com comentário por item e visibilidade privada, por link ou pública
  ([PopSci](https://www.popsci.com/diy/how-to-use-letterboxd/), [exemplo de lista](https://letterboxd.com/desynchronising/list/2026-ranked/)).

**Prioridade:** Should, porque a lista simples já está pendente; a colaboração é o diferencial, mas pode vir depois.

**Depende de:** RF6, RF11, RF12 (lista/guia pendente), RF13, RF15; RC-COM-01 para seguir listas.

**Esforço:** M

---

### RC-COM-03 · Ranking pessoal por comparação (estilo Beli)

**Ator(es):** Comensal, Criador, Chef.

**Descrição:** Depois de marcar um lugar como "Já fui", a pessoa diz se gostou, achou ok ou não gostou e então
compara o lugar novo com outros da própria lista, um par por vez, até o app achar a posição dele. O resultado é um
**ranking pessoal** com nota relativa de 0 a 10, separado das estrelas públicas do RF8. A comparação fica restrita
a lugares do mesmo grupo (por exemplo, padaria com padaria), para evitar comparar coisas incomparáveis.

**Critérios de aceite:**
1. Primeiro passo com três faixas ("Gostei", "Foi ok", "Não gostei"); a comparação só acontece dentro da faixa.
2. Cada comparação mostra dois lugares do mesmo grupo de tipo (taxonomia, seção 7) e aceita "não sei comparar",
   que pula o par.
3. A posição sai por busca binária na faixa (no máximo cerca de log₂(n) perguntas); a pessoa pode mover o item
   manualmente depois.
4. A nota de 0 a 10 é derivada da posição e da faixa e **não entra** na nota agregada pública do RF8.
5. O ranking é privado por padrão; publicar o "meu top 10" é uma ação explícita, por grupo de tipo.

**Regras e riscos:**
- Integridade: a nota pessoal não pode ser vendida nem usada como selo pelo estabelecimento; o lugar não vê quem
  o colocou em que posição.
- ECA Digital: para menor de 16, sem ranking público e sem placar entre amigos.
- Acessibilidade: o par de comparação é um grupo de dois botões com nome do lugar no rótulo; nada de deslizar como
  única forma de escolher.
- Risco de produto: críticos dizem que comparar lugares muito diferentes distorce o resultado; o filtro por grupo
  de tipo responde a isso.

**Referências de mercado:**
- Beli: três faixas iniciais e comparações par a par até dar uma nota de 0 a 10; recomendações começam com cerca
  de 15 lugares ranqueados ([Spoon University](https://spoonuniversity.com/school/brown/beli-the-new-app-personalizing-restaurant-reviews-and-recommendations/),
  [Pratt IXD](https://ixd.prattsi.org/2022/02/design-critique-beli-ios-app/)). A crítica de design sugere comparar
  dentro da mesma culinária ou faixa de preço (mesma fonte Pratt).
- Beli: mais de 75 milhões de avaliações em cerca de 30 mil cidades (09/2025), cerca de 80% dos usuários com menos
  de 35 anos, segundo os fundadores ([Wikipedia](https://en.wikipedia.org/wiki/Beli_(app))).

**Prioridade:** Should, porque é o recurso que mais diferencia o Beli entre jovens e cabe sem mexer no RF8.

**Depende de:** RF6, RF7, RF8; listas de sistema "Já fui" (RC-COM-02).

**Esforço:** M

---

### RC-COM-04 · Diário de visitas com acompanhantes

**Ator(es):** Comensal, Criador, Chef (autor); pessoas marcadas como acompanhantes.

**Descrição:** Um **diário** em formato de calendário: cada visita registra data, lugar, modo (`DINE_IN`, `TAKEOUT`,
`DELIVERY`), pratos, foto e nota pessoal, e pode existir sem avaliação pública. Revisitas ficam como entradas novas
do mesmo lugar. A pessoa marca acompanhantes, que precisam aceitar para aparecer. No fim do ano há uma
retrospectiva gerada só a partir do diário.

**Critérios de aceite:**
1. Uma entrada do diário exige data e lugar; o resto é opcional; a visibilidade padrão é "só eu".
2. "Revisita" aparece quando já existe entrada do mesmo lugar, e o contador de visitas fica no perfil privado.
3. Marcar um acompanhante gera um pedido; sem aceite, o nome não aparece para ninguém além do autor.
4. Transformar uma entrada em avaliação (RF8) ou post (RF9) aproveita os dados, sem duplicar o registro.
5. A retrospectiva anual mostra totais, culinárias e lugares mais visitados e só é compartilhada se a pessoa tocar
   em "compartilhar".
6. Exportar o diário em CSV ou JSON (direito de portabilidade, LGPD Art. 18, V).

**Regras e riscos:**
- LGPD: diário é registro de presença e rotina; a data exata nunca aparece em público, só mês e ano, se a pessoa
  publicar. Marcar um terceiro trata dado dele: daí o aceite.
- ECA Digital: menor de 16 não pode ser marcado por conta que ele não segue; o responsável vinculado pode ver as
  marcações.
- Abuso: limite de marcações por entrada; bloqueado não marca.
- Acessibilidade: o calendário tem visão em lista equivalente.

**Referências de mercado:**
- Letterboxd: o diário mostra, mês a mês, o que foi visto e revisto ([Digital Citizen](https://www.digitalcitizen.life/letterboxd-how-to-find-and-review-movies-online/)).
- Swarm: histórico de check-ins pesquisável, com categorias, companhias frequentes e mapa por país
  ([TechCrunch 2016](https://techcrunch.com/2016/03/24/swarm-launches-new-profile-pages-with-front-and-center-history-search/),
  [VentureBeat](https://venturebeat.com/mobile/foursquares-swarm-now-lets-you-make-sense-of-where-youve-been)).
- Beli: marcar acompanhantes no lugar ([Wikipedia](https://en.wikipedia.org/wiki/Beli_(app))).
- iFood: Retrospectiva anual em formato de stories, com pratos e top 5 restaurantes, compartilhável
  ([Olhar Digital, 12/2025](https://olhardigital.com.br/2025/12/02/pro/retrospectiva-ifood-quantos-pedidos-voce-fez-este-ano-confira-como-acessar-a-sua/)).

**Prioridade:** Should, porque dá motivo para voltar ao app sem depender de audiência e alimenta o ranking e as
recomendações.

**Depende de:** RF6, RF8, RF9; "check-in" pendente; RC-COM-02 ("Já fui").

**Esforço:** M

---

### RC-COM-05 · Visita comprovada ("esteve lá") sem guardar a localização

**Ator(es):** Comensal, Criador, Chef, Comunidade (quem comprova); Estabelecimento (opcionalmente exibe um código);
Moderador.

**Descrição:** Estende o check-in pendente com uma **prova de visita**: no momento do check-in o app confere, uma vez,
se o aparelho está dentro de um raio do lugar, ou a pessoa lê um QR rotativo exibido pelo estabelecimento. A prova
gera o selo "Visita comprovada" na entrada do diário e na avaliação, sem guardar a coordenada. A avaliação sem prova
continua valendo.

**Critérios de aceite:**
1. A conferência usa a localização do momento, compara com o raio do lugar (ex.: 150 m, ajustável por tipo) e
   descarta a coordenada; só fica gravado `verified_at` e o método (`GEO` ou `QR`).
2. Funciona sem o consentimento `LOCATION_TRACKING`, porque é pontual (o mesmo princípio da busca por raio do RF10);
   a tela explica isso antes de pedir a permissão do sistema.
3. O QR do estabelecimento muda a cada poucos minutos e vale uma vez por conta por dia.
4. A avaliação (RF8) mostra "Visita comprovada em mês/ano" e o número de visitas comprovadas, se a pessoa permitir.
5. Comprovar visita não dá peso extra fixo à nota; o peso, se houver, segue a fórmula do RF8 e é documentado.
6. Sinais de fraude (GPS simulado, muitas visitas em pouco tempo e longe entre si) mandam a prova para revisão do
   RF13.

**Regras e riscos:**
- LGPD: dado de localização mínimo e efêmero; a finalidade fica escrita na tela.
- ECA Digital: menor de 16 pode comprovar visita (dado não é guardado), mas o selo não aparece em público.
- Abuso: o estabelecimento não pode condicionar o QR a nota positiva (`RULE_NO_SENTIMENT_INCENTIVE`); QR
  compartilhado em grupo é detectado pela repetição.
- Acessibilidade: o QR tem alternativa por código curto digitável.

**Referências de mercado:**
- Yelp (2010): o número de check-ins aparece junto da avaliação como sinal de credibilidade e o frequentador mais
  assíduo ganha destaque ([VentureBeat](https://venturebeat.com/technology/yelp-iphone-check-ins),
  [Adweek](https://www.adweek.com/lostremote/yelp-adds-check-ins-on-mobile-app/)). Estado atual **não verificado**.
- Foursquare (2010): check-in em qualquer lugar, mas pontos e selos só com localização conferida pelo GPS
  ([TechCrunch](https://techcrunch.com/2010/04/07/foursquare-starts-to-enforce-the-rules-cracks-down-on-fake-check-ins)).
- Untappd: check-in a mais de 60 milhas do lugar não ganha selo ([ajuda Untappd](https://help.untappd.com/hc/en-us/articles/360037071292-How-can-I-check-in-to-venues-that-are-greater-than-60-miles-away)).

**Prioridade:** Should, porque reforça a confiança das avaliações (`RULE_NO_AI_FAKE`) com custo baixo de privacidade.

**Depende de:** RF3, RF6, RF8, RF13; check-in pendente; RC-COM-04.

**Esforço:** M

---

### RC-COM-06 · Decidir onde comer em grupo

**Ator(es):** Comensal, Criador, Chef (organizador e participantes); Visitante convidado por link (vota sem conta,
com limitações).

**Descrição:** O organizador cria uma **rodada de escolha** com data, região e até 10 opções (de uma lista, da busca ou
sugeridas pelos participantes). Cada pessoa vota "topo", "tanto faz" ou "não dá" em cada opção; a rodada fecha num
prazo e mostra o vencedor. Um filtro opcional cruza as opções com restrições declaradas pelos participantes, sem
revelar quem declarou o quê.

**Critérios de aceite:**
1. A rodada tem link de convite com validade; participantes com conta votam pelo perfil, e visitante vota com um
   apelido, sem coleta de e-mail.
2. Cada opção mostra os votos agregados por tipo; o organizador escolhe se os nomes ficam visíveis.
3. Prazo de encerramento obrigatório; ao fechar, o vencedor é o de mais "topo" com menos "não dá", e o empate é
   decidido pelo organizador.
4. Restrição alimentar (ex.: "sem glúten") é informada só para aquela rodada, mostrada como "1 pessoa precisa de
   opção sem glúten" e apagada quando a rodada fecha.
5. Ao fechar, "Criar plano" leva para o convite com RSVP (RC-COM-07) ou, quando houver, para o parceiro de reserva.
6. Votar também funciona por botões; deslizar é atalho opcional.

**Regras e riscos:**
- LGPD Art. 11: restrição alimentar pode revelar saúde ou religião; aqui ela é efêmera, anonimizada no grupo e
  apagada no fim, com finalidade explícita. Não vale como `SENSITIVE_PROFILE_DATA` salvo no perfil.
- ECA Digital: menor de 16 só entra em rodada criada por quem ele segue e não vota como visitante.
- Abuso: número máximo de participantes; links expiram.
- Acessibilidade: placar com texto e números, não só barras coloridas.

**Referências de mercado:**
- Google Maps group planning (2018): lista curta compartilhada, votação com polegar, amigos adicionam e removem
  opções ([TechCrunch](https://techcrunch.com/2018/09/26/google-launches-its-group-planning-feature-for-maps/amp/),
  [The Takeout](https://thetakeout.com/google-maps-group-planning-restaurants-vote-1829389428)).
- Apps de deslizar em grupo (DinnerWhere, Tonight's Bite, Tenders): sessão por link ou código, sim/não/talvez e
  aviso quando todos concordam (descrições de loja, **não verificado**)
  ([DinnerWhere](https://apps.apple.com/us/app/dinnerwhere/id6760210873), [Tenders](https://apps.apple.com/us/app/-/id1606224787)).

**Prioridade:** Could, porque é muito pedido em grupos de amigos, mas depende das listas e da busca estarem prontas.

**Depende de:** RF7, RF10, RF11; RC-COM-02; RC-COM-07.

**Esforço:** M

---

### RC-COM-07 · Convite com RSVP e lista de espera (refeição e evento)

**Ator(es):** Comensal, Criador, Chef (refeição entre amigos); Estabelecimento, Varejo & Marca, Comunidade, Chef
(evento, post `EVENTO` do RF9); convidados.

**Descrição:** Acrescenta **RSVP** a dois casos: o plano de refeição entre amigos (data, lugar, convidados) e o post
`EVENTO` já existente. Convidados respondem "vou", "talvez" ou "não vou", e eventos com lotação abrem lista de
espera com promoção automática. Cada convidado pode deixar uma observação para o organizador (ex.: alergia), visível
só para ele.

**Critérios de aceite:**
1. O evento ganha `capacity` opcional; ao lotar, novos "vou" entram na lista de espera, e o primeiro dela sobe
   quando alguém cancela, com aviso.
2. RSVP com acompanhantes só sobe da espera se houver vaga para o grupo todo.
3. A observação do convidado é privada entre ele e o organizador e some 7 dias depois do evento.
4. O organizador vê a lista de presença, pode remover alguém e pode marcar comparecimento.
5. Evento pago fica fora do RSVP automático com lista de espera (pagamento é de outro RF); o post só aponta para o
   link de venda.
6. Evento `AGE_18` não aceita RSVP de menor e não aparece para ele.

**Regras e riscos:**
- LGPD: alergia na observação é dado de saúde; o campo diz "visível só para quem organiza", e o dado é apagado no
  prazo. Lista de presença pública só com opção do organizador e consentimento de cada um.
- ECA Digital: menor de 16 só recebe convite de contas que ele segue; evento de conta comercial para menor não pode
  ter publicidade dirigida.
- Abuso: limite de convites por dia; denúncia de evento falso cai no RF13.
- Acessibilidade: data e hora por extenso no rótulo, exportação `.ics` para o calendário do aparelho.

**Referências de mercado:**
- Resy "Invite Your Party": link da reserva por mensagem, convidado entra com conta, vê os detalhes, pode deixar
  nota de alergia, e o anfitrião vê quem confirmou ([blog Resy](https://blog.resy.com/?p=20284),
  [ajuda Resy](https://helpdesk.resy.com/guests-can-invite-fellow-diners-to-their-reservation-party-SJmPvXUO)).
- OpenTable (2017): "Add Guests" no iMessage com aceitar ou recusar ([press OpenTable](https://press.opentable.co.uk/opentable-unveils-new-features-optimized-for-ios-11/)).
  Estado atual **não verificado**.
- Meetup: lista de espera automática quando há limite, promoção por ordem e regra de vaga para o grupo inteiro;
  evento com taxa obrigatória não usa lista de espera ([ajuda Meetup](https://help.meetup.com/hc/en-us/articles/41229648295181-How-to-Join-an-Event-Waitlist-on-Meetup),
  [gerenciar participantes](https://help.meetup.com/hc/en-us/articles/9389668230541-Manage-attendees-and-track-attendance-for-your-Meetup-event)).
- Yelp Elite events: RSVP não é confirmação, e presença passada pesa na seleção ([Isthmus](https://isthmus.com/events/yelp-carb-comeback-eloura/)).

**Prioridade:** Should, porque o post `EVENTO` já existe e, sem RSVP, não mede interesse nem lotação.

**Depende de:** RF9 (`EVENTO`), RF11, RF14; RC-COM-01.

**Esforço:** M

---

### RC-COM-08 · Grupos e confrarias da conta Comunidade

**Ator(es):** Comunidade (dona do grupo); administradores e membros (contas pessoais); Moderador.

**Descrição:** A conta `COMUNIDADE` (confraria, clube de vinho, grupo de cozinha, coletivo de feira) ganha **membros**
com papéis (administrador, membro), um mural interno, listas coletivas (RC-COM-02) e eventos com RSVP
(RC-COM-07). O grupo pode ser aberto, com aprovação ou só por convite. Regras do grupo ficam no topo e cada
administrador modera o mural.

**Critérios de aceite:**
1. Três modos de entrada: `ABERTO`, `APROVACAO` (com até 3 perguntas) e `CONVITE`.
2. Papéis `ADMIN` e `MEMBRO`; o administrador remove post do mural, remove membro e fixa as regras.
3. O post no mural do grupo herda a visibilidade do grupo; post de grupo fechado não aparece em busca nem em
   "em alta".
4. A avaliação "em nome do grupo" continua com peso igual (taxonomia 2.1); a página do grupo lista as avaliações
   que ele publicou.
5. Sair do grupo é imediato, e os posts antigos ficam ou somem, à escolha de quem sai.
6. Denúncia dentro do grupo vai primeiro para os administradores e, se ignorada ou grave, para o RF13.

**Regras e riscos:**
- LGPD: ser membro de grupo "kosher", "vegano por religião" ou de dieta para doença revela dado sensível; a lista de
  membros é privada por padrão e nunca aparece no perfil público da pessoa.
- ECA Digital: menor de 16 entra só em grupo aberto, com aviso ao responsável vinculado; grupo cujo tema é bebida
  alcoólica é `AGE_18`.
- Abuso: grupo usado para combinar avaliação em massa ou ataque (`RULE_REVIEW_BOMBING`) é sinal de moderação.
- Acessibilidade: regras e perguntas de entrada em texto, sem imagem obrigatória.

**Referências de mercado:**
- Meetup: grupos com organizadores, eventos e aprovação de participantes ([ajuda Meetup](https://help.meetup.com/hc/en-us/articles/9389668230541-Manage-attendees-and-track-attendance-for-your-Meetup-event)).
  Aprovação de membros por perguntas no Meetup **não verificada**.
- Yelp: o time de comunidade organiza eventos locais com parceiros ([vaga de Community Manager](https://www.netinfluencer.com/jobs/community-manager-tucson-at-yelp/)).

**Prioridade:** Could, porque a taxonomia já reserva "membros, eventos e listas coletivas" para `COMUNIDADE`, mas
moderação descentralizada exige RF13 maduro.

**Depende de:** RF1 (`COMUNIDADE`), RF9, RF13, RF15; RC-COM-02; RC-COM-07.

**Esforço:** G

---

### RC-COM-09 · Preferências de notificação, horário de silêncio e resumo semanal

**Ator(es):** todas as contas; responsável vinculado (para menor de 16).

**Descrição:** Estende as notificações já pendentes com uma **central de preferências** por categoria (social, lugares
seguidos, eventos, alertas sanitários, segurança da conta) e por canal (push, e-mail, no app), um **horário de
silêncio** e um **resumo** diário ou semanal que junta o que não é urgente. Alerta sanitário da região e eventos de
segurança da conta não entram no silêncio.

**Critérios de aceite:**
1. Cada categoria tem chave por canal; um botão desliga todos os e-mails não obrigatórios.
2. Horário de silêncio configurável (padrão 22h a 7h em `America/Sao_Paulo`); o que chegar nesse período vai para o
   resumo.
3. Resumo semanal com curtidas e comentários agrupados ("12 pessoas curtiram seu post"), novos posts de lugares
   seguidos e eventos da semana; dá para trocar por diário ou desligar.
4. `ALERTA_SANITARIO` da região e avisos de login ou 2FA (RF4) são sempre entregues, com a razão explicada na tela.
5. Nenhuma notificação usa contagem regressiva falsa ou urgência inventada ("só hoje!" sem vigência real no post).
6. Toda notificação de promoção exige `MARKETING` ligado (RF3).

**Regras e riscos:**
- ECA Digital: menor de 16 nasce com silêncio noturno ligado, sem push de curtida e sem resumo de "você está
  perdendo"; o responsável vinculado vê e ajusta.
- LGPD: e-mail de resumo é tratamento por consentimento ou legítimo interesse, documentado; descadastrar é de um
  clique.
- Abuso: limite diário de push por remetente (lugar seguido não manda 10 promoções no dia).
- Acessibilidade: notificação com texto completo, sem depender de ícone ou cor.

**Referências de mercado:**
- Instagram Quiet Mode (2023): silencia notificações num horário escolhido, avisa no status, responde DM
  automaticamente e entrega um resumo ao fim; o app sugere o modo para adolescentes de madrugada
  ([Social Media Today](https://www.socialmediatoday.com/news/Instagram-Adds-Quiet-Mode-to-Help-Users-Take-Breaks/640780/)).
- Untappd: alertas por lugar, por cerveja e por tipo de atividade ([ajuda Untappd](https://help.untappd.com/hc/en-us/articles/360034388971-Manage-Notifications-for-Friend-Activity-Beers-Venues-App-Updates-Shop-Promotions)).
- Yelp: chave por tipo de e-mail e chave geral no app ([Adweek](https://www.adweek.com/digital/yelp-heres-how-to-manage-your-email-notifications-settings/)).
  Resumo (digest) do Yelp **não verificado**.
- Swarm "Weekly Swarm": resumo semanal de onde os amigos foram ([The Points Guy](https://thepointsguy.com/2017/08/swarm-foursquare-travel-app-review)).
- Estudo de app de dieta (cerca de 61 mil usuários): ativar notificações esteve entre os preditores de retenção em
  7 e 14 dias; não testou recursos sociais ([JMIR preprint](https://preprints.jmir.org/preprint/97647)).

**Prioridade:** Must, porque notificação sem controle fino gera desinstalação e o ECA Digital pede padrões
protetivos para menor.

**Depende de:** RF3, RF4, RF9 (`ALERTA_SANITARIO`); notificações pendentes; RC-COM-01.

**Esforço:** M

---

### RC-COM-10 · Selos de contribuição e níveis, com regras anti-abuso

**Ator(es):** Comensal, Criador, Chef, Comunidade (quem ganha); Moderador.

**Descrição:** Um sistema de **níveis** por pontos de contribuição útil (avaliação com texto, foto com texto
alternativo, lugar novo aprovado, correção de dados aceita) e **selos** temáticos ("Explorador de feiras", "10
culinárias do Norte"). Os pontos só contam depois que o conteúdo passa pela moderação e são retirados se ele for
removido. Não há ranking público entre pessoas nem perda por "quebrar sequência".

**Critérios de aceite:**
1. Tabela de pontos pública e versionada; avaliação curta vale menos que avaliação com texto e foto acessível.
2. Pontos entram como pendentes e viram efetivos quando o conteúdo fica publicado por 72 horas sem remoção; conteúdo
   removido pelo RF13 tira os pontos.
3. Avaliar um lugar ligado a si (`RULE_NO_INSIDER`) ou com benefício recebido (`RULE_INCENTIVE_DISCLOSED`) não gera
   pontos.
4. Selos temáticos usam a taxonomia (culinária, formato, região); cada selo diz o critério e a data em que foi
   ganho.
5. Nível e selos aparecem no perfil público (RF15) com texto, não só ícone, e podem ser ocultados pela pessoa.
6. Sequências ("streaks"), se existirem, são só informativas: nenhuma perde pontos nem manda push de "não perca sua
   sequência".

**Regras e riscos:**
- ECA Digital: para menor de 16, sem sequência, sem placar e sem recompensa variável ou surpresa; só selos de
  descoberta.
- Abuso: limite diário de pontos, detecção de texto duplicado entre lugares e de fotos repetidas; quem fraudar perde
  o nível. Pontos nunca valem dinheiro, desconto ou prioridade de exibição.
- Integridade: o selo não pode ser confundido com o selo de verificação (`badge-check`) nem com qualidade.
- Acessibilidade: o selo tem nome e critério legíveis por leitor de tela; a forma não depende de cor (Design System).

**Referências de mercado:**
- Google Local Guides: 10 pontos por avaliação (+10 acima de 200 caracteres), 5 por foto, 15 por lugar novo; níveis
  1 a 10 (0 a 100 mil pontos); selo visível a partir do nível 4; pontos podem ser retirados por violação de política
  ([ajuda Google](https://support.google.com/maps/answer/6225851?hl=en)). Quem duplica avaliações entre lugares sai
  do programa ([diretrizes](https://support.google.com/local-guides/answer/7358351)).
- Google: remoções por engano de avaliações legítimas de Local Guides, revertidas depois, mostram o custo do
  combate automatizado ([Search Engine Land](https://searchengineland.com/google-maps-removed-policy-abiding-reviews-from-local-guides-392555)).
- Swarm: off-the-grid (check-in privado) não conta para *mayorship* e rende menos moedas
  ([ajuda Foursquare](https://support.foursquare.com/hc/en-us/articles/14883960538396-Can-Others-See-Me-When-I-Check-in)).
- Beli: placares por cidade e sequências, com convites que ajudam a subir no placar
  ([Wikipedia](https://en.wikipedia.org/wiki/Beli_(app)), [Today](https://www.today.com/today/amp/rcna217748)).
  Usado aqui como contraexemplo para menor.
- Tripadvisor: pontos sem valor monetário; selos por tipo de viajante ([Tripadvisor](https://www.tripadvisor.com/TripCollectiveFAQ)).

**Prioridade:** Should, porque incentiva conteúdo útil (fotos acessíveis, lugares novos), mas só depois de RF13.

**Depende de:** RF6, RF7, RF8, RF9, RF13, RF15.

**Esforço:** M

---

### RC-COM-11 · Desafios culinários e roteiros temáticos

**Ator(es):** Oficial e equipe do Friendly Eats (criam desafios da plataforma); Comunidade, Criador, Chef (criam
desafios abertos); qualquer conta que participa.

**Descrição:** **Desafios** com prazo e tema: um roteiro ("Pastel de feira em 5 bairros"), uma receita ("Moqueca do seu
jeito") ou uma descoberta ("Uma culinária nova por mês"). Participar é postar ou registrar visitas que cumprem o
critério; o fim do desafio gera um selo de participação e uma galeria. Sem prêmio em dinheiro e sem sorteio.

**Critérios de aceite:**
1. O desafio tem título, critério verificável (taxonomia, lugares de uma lista, tipo de post), início, fim e quem
   criou.
2. Participar é opcional e aparece como "x de y concluídos"; não há placar de quem terminou primeiro.
3. A galeria do desafio só mostra posts publicados e moderados; o dono do desafio pode tirar um post da galeria,
   não do perfil do autor.
4. Desafio de estabelecimento ou marca é publicidade: chip Publicidade e nenhuma exigência de nota positiva
   (`RULE_NO_SENTIMENT_INCENTIVE`).
5. Desafio com bebida alcoólica é `AGE_18`.

**Regras e riscos:**
- ECA Digital: sem recompensa surpresa nem caixa de recompensa; prazos sem contagem regressiva por push para menor.
- Saúde: desafios de quantidade ("coma 2 kg") ou de jejum são proibidos nas regras de conteúdo.
- Abuso: post que não cumpre o critério sai da galeria; desafio de marca não pode coletar contato dos participantes.
- Acessibilidade: o progresso tem texto ("3 de 5"), além da barra.

**Referências de mercado:**
- Swarm: adesivos e desafios para dar motivo de check-in ([VentureBeat](https://venturebeat.com/social/foursquares-swarm-attempts-to-bring-back-the-fun-with-stickers-and-challenges)).
- Beli: "Dining Hall of Fame" entre 187 universidades ([Wikipedia](https://en.wikipedia.org/wiki/Beli_(app))).
- Veja Comer & Beber: eleições temáticas por cidade (ex.: melhor baguncinha de Cuiabá), com júri local
  ([Olhar Direto](https://olhardireto.com.br/conceito/noticias/veja-comer-beber-vai-eleger-o-melhor-baguncinha-de-cuiaba)).
  Voto do leitor no Comer & Beber **não verificado**.

**Prioridade:** Could, porque é bom para sazonalidade e campanhas, mas depende de selos, listas e moderação.

**Depende de:** RF7, RF9, RF13; RC-COM-02; RC-COM-10.

**Esforço:** M

---

### RC-COM-12 · "Eu fiz": foto de quem cozinhou a receita

**Ator(es):** qualquer conta ativa (quem cozinhou); autor da receita (post `RECEITA` do RF9).

**Descrição:** Na receita, o botão "Eu fiz" publica uma **foto com comentário** de quem reproduziu o prato, ligada à
receita original. As fotos aparecem numa faixa na página da receita e notificam o autor. É o equivalente social da
avaliação para receitas, sem estrelas.

**Critérios de aceite:**
1. "Eu fiz" exige foto com texto alternativo e aceita comentário e substituições ("troquei leite por leite de
   coco").
2. A receita mostra o total de "Eu fiz" e a faixa das mais recentes; o autor recebe notificação agrupada.
3. O autor da receita pode ocultar um "Eu fiz" da página dele, mas não apagá-lo do perfil de quem postou.
4. "Eu fiz" herda alergênicos da receita e permite marcar o que mudou.
5. O "Eu fiz" pode ser denunciado como qualquer post (RF13).

**Regras e riscos:**
- LGPD: substituições podem revelar dieta por saúde; o texto é livre e público, então a tela avisa antes de
  publicar.
- ECA Digital: menor de 16 pode postar, com foto que não mostre rosto de terceiros sem permissão (aviso na tela).
- Abuso: limite de um "Eu fiz" por receita por dia por conta; receita copiada de outro autor cai na política de
  direitos autorais.
- Acessibilidade: texto alternativo obrigatório, como no `FOTO_PRATO`.

**Referências de mercado:**
- Cookpad "cooksnap": foto de quem cozinhou, enviada ao autor e exibida na página da receita
  ([Cookpad, diretrizes](https://cookpad.com/us/community)).

**Prioridade:** Should, porque é barato (reaproveita o post com foto) e dá retorno direto a quem publica receitas.

**Depende de:** RF9 (`RECEITA`, `FOTO_PRATO`), RF12, RF13.

**Esforço:** P

---

### RC-COM-13 · Programa anual de Embaixadores locais

**Ator(es):** Comensal, Criador, Chef (candidatos); Moderador e equipe de comunidade (seleção); Estabelecimento
(anfitrião de eventos, sem influência na seleção).

**Descrição:** Programa **anual**, por cidade, que reconhece quem contribui bem (avaliações úteis, fotos acessíveis,
lugares cadastrados, respeito às regras). A pessoa se indica ou é indicada; a equipe seleciona com critério público.
Embaixadores ganham selo do ano e convites para eventos da comunidade, nunca prioridade na nota.

**Critérios de aceite:**
1. Requisitos públicos: 18+, nome e foto reais no perfil, histórico sem remoção grave nos últimos 12 meses e
   nenhuma relação comercial com estabelecimento da cidade (declaração assinada).
2. Indicação própria ou por terceiros; a decisão diz o motivo em caso de recusa.
3. O selo "Embaixador 2027 · Cidade" vale até 31/12 e precisa de nova seleção no ano seguinte.
4. Avaliação de embaixador tem o mesmo peso que as outras no RF8.
5. Benefício recebido em evento de estabelecimento é declarado na avaliação daquele lugar
   (`RULE_INCENTIVE_DISCLOSED`) ou impede avaliar por 90 dias.

**Regras e riscos:**
- ECA Digital: só 18+ (evita exposição e eventos com álcool).
- Integridade: conflito de interesse exclui; embaixador que aceitar pagamento para avaliar perde o selo.
- LGPD: nome real é exigido só para quem se candidata e só no programa.
- Acessibilidade: eventos indicam acessibilidade do local (faceta 8.4 da taxonomia).

**Referências de mercado:**
- Yelp Elite: perfil com nome e foto reais, avaliações cuidadosas, indicação própria ou de terceiros, selo como
  "marca de confiança" e eventos exclusivos; status anual ([Yelp Elite](https://www.yelp.com/elite));
  idade mínima de consumo de álcool e regras de conflito de interesse (fonte de terceiros) ([ABC4](https://www.abc4.com/community/what-is-the-yelp-elite-squad-and-do-i-have-what-it-takes-to-join/amp)).
  Número mínimo de avaliações **não verificado** (fontes divergem entre 20 e 40 por ano).

**Prioridade:** Could, porque funciona como alavanca de comunidade em cidades com massa crítica, não no lançamento.

**Depende de:** RF8, RF13, RF15; RC-COM-07; RC-COM-10.

**Esforço:** M (com operação humana contínua)

---

### RC-COM-14 · Recomendações "Para você" com compatibilidade de gosto explicada

**Ator(es):** Comensal, Criador, Chef (adultos com `PERSONALIZED_FEED` ligado).

**Descrição:** Um feed e uma seção de busca **Para você** que estimam a chance de a pessoa gostar de um lugar ou post,
a partir das próprias avaliações, do ranking pessoal (RC-COM-03), das listas e de pessoas com gosto parecido. Cada
recomendação diz **por quê** ("você ranqueou bem 3 lugares de comida nordestina"). Também mostra a compatibilidade de
gosto com perfis seguidos.

**Critérios de aceite:**
1. Só funciona com `PERSONALIZED_FEED` ligado; desligado, o feed volta ao cronológico e "em alta", e os dados de
   modelo daquela pessoa são apagados em até 30 dias.
2. A previsão só aparece depois de um mínimo de lugares avaliados ou ranqueados (ex.: 10); antes, a tela explica
   quanto falta.
3. Cada item mostra um motivo legível e o botão "não me mostre isso", que ajusta o modelo.
4. Compatibilidade com outro perfil (0 a 100%) só aparece se os dois tiverem o consentimento ligado e perfil
   visível um para o outro.
5. Estilos alimentares sensíveis só entram no cálculo com `SENSITIVE_PROFILE_DATA`; sem ele, o modelo não os infere.
6. Post com chip Publicidade ou Patrocinado nunca é "recomendado pelo seu gosto" sem o rótulo.

**Regras e riscos:**
- ECA Digital e RF3: travado para menor de 16; nenhum perfilamento de menor para publicidade.
- LGPD Art. 20: direito de revisão de decisão automatizada; o motivo e o "não me mostre" atendem em parte, e a
  política documenta o resto.
- Inferência sensível: não inferir religião ou saúde a partir de lugares (ex.: frequentar restaurante kosher).
- Acessibilidade: a porcentagem tem texto e faixa ("alta compatibilidade"), não só cor.

**Referências de mercado:**
- Vivino "Match for You": porcentagem de chance de gostar, ativa com 5 vinhos avaliados, faixas 70–100, 40–70 e 0–40
  ([Vivino](https://www.vivino.com/wine-news/introducing-match-for-you)).
- Beli: nota prevista para cada lugar e "match score" de gosto com cada amigo
  ([App Store](https://apps.apple.com/us/app/-/id1478375386), [Startup Signals](https://startupsignals.substack.com/p/beli-your-food-diary-and-dining-advisor)).
- Relatório de fornecedor (InAppStory, não revisado por pares): mensagem personalizada ligada a +6% de retenção em
  30 dias em apps de foodtech ([The Packer](https://www.thepacker.com/news/packer-tech/sophistication-needed-foodtech-apps-report-says)).

**Prioridade:** Should, porque já existe o consentimento `PERSONALIZED_FEED` sem consumidor, e o ranking e o diário
geram o sinal.

**Depende de:** RF3 (`PERSONALIZED_FEED`, `SENSITIVE_PROFILE_DATA`), RF8, RF10, RF14; RC-COM-03; RC-COM-04.

**Esforço:** G

---

### RC-COM-15 · Mapa de descoberta com camadas sociais, sem localização de pessoas

**Ator(es):** qualquer conta; Visitante (camadas públicas).

**Descrição:** Um **mapa** dos lugares com camadas: resultados da busca (RF10), "minhas listas", "quero ir", "lugares
que quem eu sigo avaliou" e eventos da semana. O mapa mostra **lugares**, nunca onde as pessoas estão. "Perto de
mim" usa a localização do momento; com `LOCATION_TRACKING`, lembra a última posição.

**Critérios de aceite:**
1. Camadas ligáveis e desligáveis, com legenda que usa forma e ícone além da cor.
2. A camada "quem eu sigo avaliou" mostra o lugar e a contagem, e o nome de quem avaliou só se o perfil dele
   permitir.
3. Nenhuma camada mostra pessoas, check-ins em tempo real ou "quem está aqui agora".
4. Cozinha caseira aparece só pelo bairro (`ADDR_PUBLIC_BAIRRO`), com área aproximada e não com ponto.
5. Toda informação do mapa tem visão em lista equivalente, com distância e filtros.
6. Sem `LOCATION_TRACKING`, o mapa abre no centro da cidade do perfil e pede a localização só ao tocar "perto de mim".

**Regras e riscos:**
- LGPD: localização pontual não é guardada; a camada social não expõe rotina (data de visita só mês e ano).
- ECA Digital: menor de 16 não aparece em nenhuma camada social de terceiros.
- Abuso: impedir triangulação (ex.: não mostrar "fulano avaliou há 10 minutos" no mapa).
- Acessibilidade: mapa é difícil para leitor de tela; a lista equivalente é obrigatória.

**Referências de mercado:**
- Instagram Map (08/2025): reação de usuários e de senadores dos EUA à percepção de que a localização era
  compartilhada; a Meta explicou que eram posts com local e reforçou o indicador de compartilhamento
  ([NBC News](https://www.nbcnews.com/tech/tech-news/instagram-maps-feature-raises-privacy-concerns-some-users-rcna223706)).
  Lição: mapa social precisa deixar óbvio o que é público.
- TikTok Nearby: feed local opcional para 18+ na Europa, por local do post, tema (incluindo comida) e recência
  ([eMarketer](https://www.emarketer.com/content/tiktok-introduces-nearby-feed-boosting-local-discovery-organic-reach)).
- Strava: zonas de privacidade que escondem início e fim do trajeto; estudo da KU Leuven mostrou vazamento mesmo
  assim ([Bikerumor](https://bikerumor.com/2021/08/12/strava-privacy-zones-expand-with-new-edit-map-visibility-functions/),
  [Dark Reading](https://www.darkreading.com/application-security/popular-fitness-apps-leak-location-data-even-when-users-set-privacy-zones)).

**Prioridade:** Should, porque o RF10 já faz busca por raio, e o mapa é o formato natural para descoberta local.

**Depende de:** RF3 (`LOCATION_TRACKING`), RF6, RF10, RF11; RC-COM-01; RC-COM-02.

**Esforço:** M

---

### RC-COM-16 · Cartão de prévia (Open Graph) para post, perfil, lugar e lista

**Ator(es):** qualquer conta que compartilha; quem recebe o link (WhatsApp, Telegram, X, e-mail).

**Descrição:** Os links públicos do Friendly Eats (`/p/{id}`, perfil, estabelecimento, lista pública) geram um
**cartão de prévia** com título, descrição e imagem 1200 × 630 com a moldura do RF16. Conteúdo privado, 18+ ou
removido gera um cartão genérico, sem foto nem texto.

**Critérios de aceite:**
1. Cada página pública tem `og:title`, `og:description`, `og:image`, `og:url`, `og:type` e `twitter:card`.
2. A imagem tem 1200 × 630 (proporção 1,91:1) e no máximo 300 KB, para o WhatsApp mostrar a prévia.
3. Post `AGE_18`, perfil privado, lista privada ou por link, e conteúdo `REMOVED` geram o cartão genérico "Conteúdo
   no Friendly Eats".
4. A prévia de estabelecimento mostra nome, culinária, bairro e nota agregada só se houver avaliações suficientes
   (regra do RF8).
5. A imagem tem `og:image:alt` com o texto alternativo do post.
6. O link não carrega quem compartilhou (como o `ShareChannel` atual).

**Regras e riscos:**
- LGPD: a prévia é pública; menor de 16 nunca tem foto ou nome na prévia.
- ECA Digital: post de menor gera cartão genérico.
- Abuso: cache da prévia invalidado quando o post é removido (`REMOVED`), para não circular depois da moderação.
- Acessibilidade: `og:image:alt` obrigatório.

**Referências de mercado:**
- Wix (com base nas recomendações do Facebook): 1200 × 630, 1,91:1, mínimo 200 × 200; WhatsApp só mostra a prévia
  abaixo de cerca de 300 KB ([Wix](https://support.wix.com/en/article/wix-editor-recommended-ogimage-size)).
  Outras fontes dizem 600 KB; o limite oficial do WhatsApp **não foi verificado**.
- Untappd: ao tirar as DMs, manteve o compartilhamento de check-ins para apps externos
  ([blog Untappd](https://untappd.com/blog/saying-goodbye-to-direct-messages-on-untappd/1880)).

**Prioridade:** Must, porque o WhatsApp é o principal canal de compartilhamento no Brasil, e o RF12 já registra o
canal `WHATSAPP` sem uma prévia que convença a abrir o link.

**Depende de:** RF9, RF11, RF12 (`ShareChannel`), RF15, RF16.

**Esforço:** P

---

### RC-COM-17 · Convite e indicação sem recompensa financeira

**Ator(es):** qualquer conta adulta (quem convida); pessoa convidada (cadastro pelo RF1).

**Descrição:** Cada conta tem um **link de convite** pessoal. Quem entra por ele já segue quem convidou (se aceitar) e
vê os lugares e listas públicas dessa pessoa no primeiro uso. O reconhecimento a quem convida é simbólico (selo
"Trouxe amigos"), sem dinheiro, cupom nem pontos que valham algo.

**Critérios de aceite:**
1. Link de convite único, revogável, com prévia Open Graph (RC-COM-16).
2. No cadastro por convite, "seguir quem convidou" vem sugerido, mas desmarcável.
3. O app não lê a agenda de contatos; para achar amigos, a pessoa compartilha o link por conta própria.
4. Contas criadas por convite com sinais de fraude (mesmo aparelho, padrão de e-mail, cadastro em série) não contam
   para o selo e vão para revisão.
5. Indicação nunca dá peso de avaliação, pontos do RC-COM-10 ou posição em placar.

**Regras e riscos:**
- ECA Digital: menor de 16 não gera link de convite e não é alvo de campanha de indicação.
- LGPD: sem upload de agenda (dado de terceiros sem base legal); o link não guarda quem clicou, só quem se
  cadastrou.
- Abuso: recompensa financeira atrai contas falsas; a escolha por reconhecimento simbólico reduz isso.
- Acessibilidade: botão "copiar link" com confirmação em texto.

**Referências de mercado:**
- Yummly (estudo de caso publicado pelo Google): convites dentro do app geraram cerca de 60% mais instalações que
  outros canais de compartilhamento ([Android Developers Blog](https://android-developers.googleblog.com/2016/01/how-fabulous-and-yummly-grew-with-app.html)).
- Beli: convidar libera funções e ajuda a subir no placar ([Wikipedia](https://en.wikipedia.org/wiki/Beli_(app))).
- TheFork Yums: programa de pontos por reserva, com contas suspensas por suposto uso indevido segundo queixas em
  Portugal ([Portal da Queixa](https://portaldaqueixa.com/brands/thefork/complaints/thefork-conta-suspensa-indevidamente-155199126),
  [Echo Boomer](https://echoboomer.pt/programa-yums-thefork-novo-valor-minimo-consumo/)). Mostra o custo de
  operar recompensa com valor.
- Programas de lista de espera: placares de indicação atraem cadastros falsos ([LaunchList](https://blog.getlaunchlist.com/blog/waitlist-referral-program-guide)).

**Prioridade:** Should, porque é o canal de aquisição mais barato, e começar sem recompensa evita fraude.

**Depende de:** RF1, RF3, RF13; RC-COM-01; RC-COM-16.

**Esforço:** P

---

### RC-COM-18 · Criadores: parceria paga rotulada, coautoria e mídia kit

**Ator(es):** Criador e Chef (quem publica); Estabelecimento e Varejo & Marca (parceiro); Moderador.

**Descrição:** O chip Publicidade já existe; este RC acrescenta o **vínculo verificável com o parceiro**: o post de
Criador pode marcar "Parceria paga com @marca", que o parceiro confirma, e o post aparece nos dois perfis
(coautoria). O Criador também ganha uma página de **mídia kit** opcional com dados agregados e verificados pela
plataforma (seguidores, alcance médio, culinárias mais publicadas) para negociar com marcas.

**Critérios de aceite:**
1. "Parceria paga com" exige um parceiro verificado (`VERIFIED`); o rótulo aparece acima da foto, na primeira
   visualização, sem precisar abrir "mais".
2. O parceiro aceita ou recusa a marcação; recusada, o post continua com o chip Publicidade e sem o nome do parceiro.
3. Coautoria: o post aceito aparece nos dois perfis, com interações somadas.
4. Avaliação (RF8) de lugar com parceria ativa nos últimos 12 meses exige a marcação `RULE_INFLUENCER` ou é
   bloqueada.
5. O mídia kit mostra só métricas agregadas que a plataforma calcula (sem número digitado pelo Criador) e é privado
   por padrão, compartilhável por link.
6. Hashtags em inglês (#ad) não substituem o rótulo; o texto padrão é "Publicidade".

**Regras e riscos:**
- CONAR (guia de influenciadores, atualização 2026): identificação explícita, visível sem clicar em "mais", com
  ferramenta da plataforma ou "#publi"/"#publicidade"; permuta e comissão também contam; termos em inglês pedem
  cautela ([Propmark](https://propmark.com.br/digital/conar-atualiza-guia-de-publicidade-com-influenciadores-e-inclui-regras-sobre-ia/),
  [Migalhas](https://www.migalhas.com.br/depeso/456124/guia-conar-2026-o-que-todo-influenciador-precisa-saber-agora)).
  Data de vigência exata **não verificada** (fontes divergem). CDC art. 36 (identificação da publicidade).
- ECA Digital: Criador é 18+; post de parceria não é direcionado a menor por perfilamento.
- Abuso: parceria não declarada denunciada e confirmada remove o post e pode retirar o selo (RF13).
- Acessibilidade: o rótulo é texto, não só ícone.

**Referências de mercado:**
- Instagram: rótulo "Parceria paga" para contas profissionais; a marca pode exigir aprovação dos criadores
  ([ajuda Instagram](https://help.instagram.com/ipad-app/1123581461537025), [Later](https://later.com/blog/instagram-branded-content)).
- Instagram Collab: coautor convidado aceita e o post aparece nos dois perfis
  ([HubSpot](https://blog.hubspot.com/marketing/instagram-collab-post)). Limite de coautores **não verificado**
  (fontes divergem).
- TikTok One (ex-Creator Marketplace): marcas descobrem criadores, briefings, métricas de público; requisitos de
  18+ e 10 mil seguidores segundo terceiros, **não verificado** ([HireInfluence](https://hireinfluence.com/blog/what-is-tiktok-creator-marketplace-a-brands-guide/),
  [Mega Digital](https://megadigital.ai/en/blog/tiktok-creator-marketplace/)).
- Verificação do Instagram: autêntico, único, completo e notável; mídia paga não conta para notabilidade
  ([ajuda Instagram](https://help.Instagram.com/312685272613322)). O Friendly Eats já tem o selo "é quem diz ser" e não
  precisa de selo de notoriedade (ver ideias descartadas).

**Prioridade:** Should, porque a regra `RULE_INFLUENCER` e o CONAR já exigem identificação, e o vínculo verificável
torna isso fiscalizável.

**Depende de:** RF1 (`CRIADOR`, `CHEF`, selo), RF8, RF9 (chip Publicidade), RF13, RF15.

**Esforço:** M

---

## Tabela-resumo

| ID | Título | Prioridade | Esforço | Depende de |
|---|---|---|---|---|
| RC-COM-01 | Seguir lugares e feed "Seguindo" cronológico | Must | M | RF2, RF3, RF11, RF12, RF14, RF15 |
| RC-COM-02 | Listas colaborativas com coeditores e votação | Should | M | RF6, RF11, RF12, RF13, RF15 |
| RC-COM-03 | Ranking pessoal por comparação (estilo Beli) | Should | M | RF6, RF7, RF8 |
| RC-COM-04 | Diário de visitas com acompanhantes | Should | M | RF6, RF8, RF9 |
| RC-COM-05 | Visita comprovada sem guardar a localização | Should | M | RF3, RF6, RF8, RF13 |
| RC-COM-06 | Decidir onde comer em grupo | Could | M | RF7, RF10, RF11 |
| RC-COM-07 | Convite com RSVP e lista de espera | Should | M | RF9, RF11, RF14 |
| RC-COM-08 | Grupos e confrarias da conta Comunidade | Could | G | RF1, RF9, RF13, RF15 |
| RC-COM-09 | Preferências de notificação, silêncio e resumo | Must | M | RF3, RF4, RF9 |
| RC-COM-10 | Selos e níveis com anti-abuso | Should | M | RF6, RF7, RF8, RF9, RF13, RF15 |
| RC-COM-11 | Desafios culinários e roteiros temáticos | Could | M | RF7, RF9, RF13 |
| RC-COM-12 | "Eu fiz": foto de quem cozinhou a receita | Should | P | RF9, RF12, RF13 |
| RC-COM-13 | Programa anual de Embaixadores locais | Could | M | RF8, RF13, RF15 |
| RC-COM-14 | Recomendações "Para você" com motivo explicado | Should | G | RF3, RF8, RF10, RF14 |
| RC-COM-15 | Mapa com camadas sociais, sem localizar pessoas | Should | M | RF3, RF6, RF10, RF11 |
| RC-COM-16 | Cartão de prévia Open Graph | Must | P | RF9, RF11, RF12, RF15, RF16 |
| RC-COM-17 | Convite e indicação sem recompensa financeira | Should | P | RF1, RF3, RF13 |
| RC-COM-18 | Parceria paga rotulada, coautoria e mídia kit | Should | M | RF1, RF8, RF9, RF13, RF15 |

Ordem sugerida: RC-COM-16 e RC-COM-01 (base de distribuição e de vínculo), RC-COM-09 (controle antes de crescer
o volume de notificações), depois RC-COM-02/03/04/05 (o núcleo "lista, ranking, diário, prova"), RC-COM-12, RC-COM-17,
RC-COM-18, RC-COM-14 e RC-COM-15; os Could por último.

## Ideias descartadas e por quê

| Ideia | Por que ficou fora |
|---|---|
| **Mensagens diretas (DM) entre pessoas** | Abre um canal privado que a moderação não vê, o pior cenário para menor de 16 (ECA Digital pede supervisão e controle de contatos) e para assédio. O Untappd desligou as DMs em 02/03/2026 citando queda de uso e aumento de spam, e manteve o compartilhamento para apps externos ([blog Untappd](https://untappd.com/blog/saying-goodbye-to-direct-messages-on-untappd/1880)). No Brasil, a conversa já acontece no WhatsApp: o produto ganha mais com a prévia Open Graph (RC-COM-16) e com conversas de escopo fechado (comentários do convite RC-COM-07 e do grupo RC-COM-08) do que com um chat próprio. Reavaliar só com moderação madura e só entre adultos que se seguem mutuamente. |
| **Localização ao vivo de amigos / "quem está aqui agora"** | A polêmica do Instagram Map (2025) mostrou como é fácil o usuário achar que está sendo rastreado ([NBC News](https://www.nbcnews.com/tech/tech-news/instagram-maps-feature-raises-privacy-concerns-some-users-rcna223706)), e o Strava mostrou que até zonas de privacidade vazam ([Dark Reading](https://www.darkreading.com/application-security/popular-fitness-apps-leak-location-data-even-when-users-set-privacy-zones)). Rotina de lugares é dado de alto risco (perseguição) e o ganho para descoberta de comida é pequeno. |
| **"Prefeito" (mayorship) público por lugar** | Expõe quem frequenta mais um lugar, ou seja, rotina e endereço provável; incentiva check-in falso. O Swarm só mantém com restrições e sem contar check-ins privados ([ajuda Foursquare](https://support.foursquare.com/hc/en-us/articles/14883960538396-Can-Others-See-Me-When-I-Check-in)). |
| **Placar público entre pessoas (quem avaliou mais lugares)** | Estimula volume em vez de qualidade e avaliação de lugar não visitado; para menor de 16, vai contra a vedação a recursos que estimulam permanência relatada para o ECA Digital. O RC-COM-10 fica com níveis individuais, sem comparação. |
| **Sequência (streak) com perda e push "não perca"** | Mecanismo de urgência artificial; o Beli usa ([Wikipedia](https://en.wikipedia.org/wiki/Beli_(app))), mas aqui seria problema regulatório para menor e de bem-estar para todos. Fica só informativa (RC-COM-10, critério 6). |
| **Pontos trocáveis por desconto (tipo Yums)** | Transforma contribuição em moeda e atrai fraude; queixas sobre suspensão de contas no programa do TheFork em Portugal mostram o custo operacional ([Portal da Queixa](https://portaldaqueixa.com/brands/thefork/complaints/thefork-conta-suspensa-indevidamente-155199126)). Fidelidade de estabelecimento pode ser outro eixo (varejo), não comunidade. |
| **Recompensa financeira por indicação** | Mesma razão: contas falsas e indicação em série; o RC-COM-17 usa reconhecimento simbólico. |
| **Selo de notoriedade ("criador famoso") ou selo pago** | O selo do Friendly Eats diz só "é quem diz ser" e nunca é pago (taxonomia 2.1). Notoriedade é subjetiva e vira moeda de troca; o mídia kit (RC-COM-18) mostra números verificados em vez de um selo. |
| **Leitura da agenda de contatos para "encontrar amigos"** | Trata dado de terceiros sem base legal clara (LGPD) e é vetor de exposição de menor. O link de convite resolve o caso de uso. |
| **Votação pública "melhor do ano" pelos usuários** | Alvo fácil de campanha coordenada (`RULE_REVIEW_BOMBING`) e de compra de votos; prêmios brasileiros conhecidos, como o Comer & Beber, usam júri local ([Olhar Direto](https://olhardireto.com.br/conceito/noticias/veja-comer-beber-vai-eleger-o-melhor-baguncinha-de-cuiaba)). Os desafios (RC-COM-11) dão o lado lúdico sem um "vencedor" manipulável. |
| **Deslizar estilo Tinder como única forma de escolher em grupo** | Inacessível para leitor de tela e para quem tem dificuldade motora (WCAG 2.2, 2.5.1 e 2.5.7). No RC-COM-06, deslizar é só atalho. |

## Lacunas da pesquisa

- **Kekanto:** só achei a história de 2010 a 2013 (rede social de avaliações, 6 milhões de visitantes únicos por mês em
  2013, receita por publicidade) ([Startupi](https://startupi.com.br/kekanto-remodela-perfil-do-usuario-e-da-enfase-a-lugares-frequentados/),
  [Guia da Semana](https://www.guiadasemana.com.br/restaurantes/noticia/rede-social-vira-febre-entre-restaurantes));
  quando e por que encerrou **não foi verificado**.
- **Guia Quatro Rodas:** nada atual sobre a seção de restaurantes; **não verificado**. O Guia Michelin no Brasil
  publica só em site e app desde a volta em 2023 ([Panrotas](https://www.panrotas.com.br/destinos/comer-e-beber/2023/08/guia-michelin-volta-ao-brasil-e-relanca-selecao-de-restaurantes_199237.html)).
- **iFood:** só confirmei a Retrospectiva anual; listas e recursos sociais atuais **não verificados**.
- **TheFork BR:** o Yums em São Paulo aparece numa matéria sem data (150 Yums por reserva no app); regras atuais no
  Brasil **não verificadas**.
- **The Infatuation e Tastemade:** The Infatuation tem a "Hit List" para salvar lugares a partir de reviews e guias
  ([App Store](https://apps.apple.com/app/id465685575)); listas múltiplas **não verificadas**. Tastemade não foi
  pesquisado a fundo.
- **Estudos de retenção:** há pouca evidência revisada por pares sobre recursos sociais em apps de comida; os números
  citados vêm de estudos vizinhos (dieta, fitness) ou de fornecedores.
- **Texto legal:** o ECA Digital e o guia do CONAR foram lidos por fontes secundárias; conferir os textos oficiais
  antes de virar critério de aceite.
