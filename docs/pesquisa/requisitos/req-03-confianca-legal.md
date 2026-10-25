# Friendly Eats · Pesquisa 03 · Confiança e segurança, integridade das avaliações e conformidade legal no Brasil

> Pesquisa de requisitos candidatos (RC), feita em 25/10/2026. **Não é parecer jurídico.** Os pontos marcados com ⚖️
> precisam ser validados por um advogado antes de virarem RF. "**Não verificado**" quer dizer que não consegui
> confirmar o ponto numa fonte primária. Na reta final, o limite de buscas da sessão acabou, então alguns itens ficaram
> sem a segunda fonte, e isso está indicado em cada caso.

## Fontes primárias lidas na íntegra

Li o texto oficial do Planalto, baixado em 25/10/2026, destas normas:

- [Lei 15.211/2025 (ECA Digital)](https://www.planalto.gov.br/ccivil_03/_ato2023-2026/2025/lei/l15211.htm)
- [Decreto 12.880/2026](https://www.planalto.gov.br/ccivil_03/_ato2023-2026/2026/decreto/d12880.htm), que regulamenta o ECA Digital, de 18/03/2026
- [LGPD](https://www.planalto.gov.br/ccivil_03/_ato2015-2018/2018/lei/l13709compilado.htm)
- [Marco Civil da Internet](https://www.planalto.gov.br/ccivil_03/_ato2011-2014/2014/lei/l12965.htm)
- [Decreto 8.771/2016](https://www.planalto.gov.br/ccivil_03/_ato2015-2018/2016/decreto/d8771.htm)
- [CDC](https://www.planalto.gov.br/ccivil_03/leis/l8078compilado.htm)
- [Lei 14.811/2024](https://www.planalto.gov.br/ccivil_03/_ato2023-2026/2024/lei/l14811.htm)
- [LBI (Lei 13.146/2015)](https://www.planalto.gov.br/ccivil_03/_ato2015-2018/2015/lei/l13146.htm)
- a [NT 2025.001 da NF-e/NFC-e (QR Code v3)](https://inventti.com.br/wp-content/uploads/2025/09/NT2025.001_v1.02-NFCe_qrCode_v3.pdf)

O restante vem de fontes secundárias, indicadas em cada item.

## Fatos-chave confirmados

1. **STF, Temas 987 e 533, julgados em 26 e 27/06/2025.** O art. 19 do Marco Civil é **parcialmente inconstitucional**,
   com efeitos só daqui para frente (item 14). A tese ficou assim:
   - **Regra geral (item 3):** para crime ou ato ilícito vale o regime do **art. 21**. O provedor responde se, depois
     de **notificado de forma extrajudicial**, não remover o conteúdo. A regra vale também para contas denunciadas como
     inautênticas.
   - **Crimes contra a honra (item 3.1):** continua valendo o art. 19, ou seja, é preciso ordem judicial para haver
     indenização. Mesmo assim, a plataforma **pode** remover o conteúdo por notificação. Réplicas de uma ofensa já
     reconhecida em juízo saem sem nova decisão (item 3.2).
   - **Presunção de responsabilidade (item 4):** vale para **anúncios e impulsionamentos pagos** e para **redes
     artificiais (robôs)**.
   - **Dever de cuidado (item 5):** cobre um rol de crimes graves, e a plataforma responde quando há **falha
     sistêmica**.
   - **Marketplaces (item 7):** respondem pelo CDC.
   - **Deveres de autorregulação (itens 8 a 11):** um **sistema de notificações**, o **devido processo**, **relatórios
     anuais de transparência** (sobre notificações extrajudiciais, anúncios e impulsionamentos), **canais de
     atendimento** para usuários e não usuários, regras públicas revistas periodicamente, e sede e representante no
     Brasil.

   Fonte: resumo da tese em [tesesesumulas.com.br](https://tesesesumulas.com.br/tema/transparencia) e
   [Teletime](https://teletime.com.br/26/06/2025/art-19-stf-concilia-votos-e-plataformas-terao-responsabilizacao-parcial/).
   ⚖️ Os **embargos** foram julgados em jun/2026, com proposta de prazo de 60 dias para adequação. O resultado final
   está **não verificado**.

2. **O ECA Digital está em vigor desde 17/03/2026** (art. 41-A, pela MP 1.319/2025) e se aplica a serviço "de acesso
   provável" por menores. Uma rede social de interação em larga escala se enquadra pelo art. 1º, parágrafo único, III.
   Os pontos que importam aqui:
   - **art. 9º, §1º:** verificação de idade **a cada acesso**, **vedada a autodeclaração**, para conteúdo impróprio a
     menores de 18;
   - **art. 24:** conta de menor **de até 16 anos vinculada** à de um responsável;
   - **arts. 17 e 18:** supervisão parental;
   - **arts. 28 a 30:** mecanismo de notificação, retirada **independente de ordem judicial** e direito de contestação;
   - **art. 31:** relatório **semestral**, só para quem tem mais de 1 milhão de usuários menores;
   - **arts. 32 e 33:** uso abusivo de denúncias;
   - **art. 35:** multa de até 10% do faturamento ou R$ 50 milhões por infração.

   O **Decreto 12.880/2026** acrescenta:
   - **art. 15, §1º, II:** bebida alcoólica é conteúdo **proibido** a menores;
   - **art. 19:** a rede social que mostra esse conteúdo cria uma **versão sem ele** ou adota **verificação de idade
     efetiva, vedada a autodeclaração**;
   - **art. 24, §3º:** é proibido guardar a imagem do documento usado na verificação;
   - **art. 27:** o usuário pode contestar a idade aferida;
   - **arts. 41 a 44:** canal de denúncia, retirada **imediata** e entidades habilitadas pela ANPD;
   - **art. 47:** avaliação de impacto à segurança da criança, com **resumo público**;
   - **art. 49:** a ANPD define as **etapas de implementação** da aferição de idade.

3. **Marco Civil, art. 15:** o provedor de aplicação com fins econômicos guarda os **registros de acesso** (data, hora
   e IP; art. 5º, VIII) por **6 meses**, sob sigilo. A entrega depende de **ordem judicial** (arts. 10, §1º, e 22). O
   **Decreto 8.771/2016, art. 13,** exige:
   - controle de acesso;
   - autenticação dupla;
   - **inventário de quem consultou os registros**;
   - cifragem.

4. **Incidente de segurança:** a LGPD, art. 48, junto com a Resolução CD/ANPD 15/2024, define:
   - comunicação à ANPD e aos titulares em **3 dias úteis**, contados de quando se sabe que o incidente atingiu dados
     pessoais;
   - complemento em até 20 dias úteis;
   - **registro de todo incidente por 5 anos**.

   Fontes: [Mattos Filho](https://www.mattosfilho.com.br/unico/regulamento-incidente-seguranca/) e
   [Cescon Barrieu](https://cesconbarrieu.com.br/anpd-aprova-o-regulamento-de-comunicacao-de-incidente-de-seguranca/).
   São secundárias; o texto da resolução **não foi lido** por mim.

5. **Lei 14.811/2024** criou o art. 146-A do Código Penal: intimidação sistemática. Na forma **virtual
   (cyberbullying)**, a pena é de 2 a 4 anos de reclusão. A lei mira escolas e o poder público, mas o **ECA Digital,
   art. 6º, §2º,** obriga o produto a ter **políticas contra intimidação sistemática virtual, com apoio às vítimas**.

## O que vi no repositório (só leitura)

- **Idade:** é **autodeclarada** (`dateOfBirth` no cadastro). O 18+ dos posts de bebida (RF9) e as travas de menor
  (RF1 e RF3) dependem dessa data. O RF1 só pede o **e-mail** do responsável, e não há conta vinculada.
- **IP:** o `ClientInfo` guarda o IP **só como HMAC**. Não encontrei tabela de registros de acesso no `schema.sql`.
  Os Termos (§10) prometem guardar "data, hora e IP" por 6 meses. É preciso confirmar se existe log de infraestrutura
  que cumpra isso.
- **Termos de Uso:** o encarregado é `{{ENCARREGADO_NOME}}`, sem preenchimento. O §11 cita o "art. 19 do Marco Civil"
  como regra, o que ficou desatualizado depois do STF. O §8 promete recurso "sempre que possível".
- **Pendências declaradas:** "Exportar dados" (Art. 18, V) é pendência do RF5. Denúncia e moderação são pendências do
  RF9 e do RF13.

---

## Requisitos candidatos

### RC-SEG-01 · Denunciar conteúdo e conta, com fila priorizada e prazos

- **Ator(es):** qualquer pessoa, logada ou não (STF item 9: "usuários e não usuários"); moderador; vítima ou
  responsável; Ministério Público, polícia e entidades habilitadas pela ANPD.
- **Descrição:** botão "Denunciar" em avaliação, post, comentário, foto, perfil e página de estabelecimento. A pessoa
  escolhe um motivo de uma lista fechada, aponta o item exato e pode escrever um texto livre. A denúncia entra numa fila
  priorizada pela gravidade. Violação a direito de criança e o rol de crimes graves do STF vão para o topo. Nudez não
  consentida segue o MCI, art. 21.
- **Critérios de aceite:**
  1. **Motivos com código estável e versão:**
     - avaliação falsa ou com conflito de interesse;
     - assédio ou cyberbullying;
     - discurso de ódio ou discriminação;
     - dado pessoal de terceiro;
     - nudez ou conteúdo sexual;
     - **violação a direito de criança ou adolescente**;
     - direito autoral;
     - alerta sanitário falso;
     - spam ou golpe;
     - fora do tema;
     - perfil inautêntico;
     - outro.
  2. **Fila e prazos:**
     - a denúncia de **criança** (ECA Digital, art. 29; Decreto 12.880, art. 43) feita por vítima, representante, MP,
       polícia ou entidade habilitada fica `CRITICA`: o conteúdo some da vista pública **na hora** e tem revisão humana
       em até X horas (meta a definir ⚖️);
     - o art. 29, §2º, **veda a denúncia anônima nesse fluxo**: exige identificar o notificante e o conteúdo;
     - os outros motivos têm SLA visível por motivo.
  3. **Acompanhamento:** o denunciante recebe protocolo, acompanha o estado (`RECEBIDA → EM_ANALISE → DECIDIDA`) e é
     avisado da decisão.
  4. **Conteúdo de exploração sexual, abuso ou aliciamento de criança (ECA Digital, art. 27; Decreto, arts. 36 e 42):**
     - sai do ar;
     - é comunicado ao Centro Nacional de Triagem de Notificações da Polícia Federal;
     - fica preservado (conteúdo e metadados) pelo prazo do MCI, art. 15;
     - ⚖️ o formato do relatório depende de regulamento.
  5. **Tela acessível:** funciona por teclado e leitor de tela e tem rótulo escrito, não só ícone (LBI, art. 63). O
     canal fica "público e de fácil acesso" (ECA Digital, art. 29, §3º).
- **Base legal ou normativa:**
  - STF, Temas 987 e 533, itens 3, 5, 8 e 9 ([resumo](https://tesesesumulas.com.br/tema/transparencia));
  - MCI, art. 21;
  - ECA Digital, arts. 27 a 29;
  - Decreto 12.880, arts. 41 a 43;
  - referência estrangeira: DSA, art. 16, notice-and-action
    ([resumo](https://presencis.com/questions/dsa-illegal-content-reporting/); o texto do regulamento não foi lido).
- **Riscos:**
  - fila sem equipe vira passivo, porque o dever de cuidado do STF pune **falha sistêmica**;
  - retirada automática na fila crítica pode ser usada para censura, o que o RC-SEG-03 mitiga;
  - o volume de denúncias de fraude em avaliação pode afogar a fila.
- **Referências de mercado:**
  - [Google: conteúdo proibido](https://support.google.com/contributionpolicy/answer/7400114?hl=en), com categorias
    semelhantes;
  - [Tripadvisor 2025](https://tripadvisor.mediaroom.com/2025-03-18-Tripadvisors-2025-Transparency-Report-reveals-strong-review-submissions-and-improved-fraud-detection):
    moderou 13,5% das avaliações.
- **Prioridade:** **Must, por obrigação legal.** O canal de notificação é dever da tese do STF e do ECA Digital,
  arts. 28 e 29.
- **Depende de:** RF4 (sessão), RF8, RF9, RF12, RF15 e RF13 (planejado). Este item detalha o RF13.
- **Esforço:** G.

### RC-SEG-02 · Decisão com motivo, aviso ao autor e recurso com revisão humana

- **Ator(es):** autor do conteúdo; denunciante; moderador; revisor sênior.
- **Descrição:** toda remoção, rebaixamento, ocultação de avaliação pelo filtro antifraude, suspensão ou banimento
  gera uma **declaração de motivos**. Ela traz a regra violada, o trecho, se a decisão foi **humana ou automatizada** e
  como recorrer. O recurso é revisto por outra pessoa, num prazo publicado. Quem pediu pode ver, no lugar do conteúdo
  removido, o motivo da remoção (MCI, art. 20, parágrafo único).
- **Critérios de aceite:**
  1. A notificação ao autor traz cinco itens (ECA Digital, art. 30): o aviso, o motivo e a fundamentação, a origem
     (humana ou automatizada), o link do recurso e os prazos para recorrer e para a resposta.
  2. O recurso é decidido por um moderador diferente do primeiro. O resultado e o motivo ficam registrados no histórico
     append-only.
  3. Decisão **só automatizada** que afete a pessoa abre um pedido de **revisão humana** (LGPD, art. 20). Exemplos:
     avaliação "não recomendada", conta marcada como suspeita, idade estimada. O pedido vem acompanhado dos **critérios**
     usados, sem expor o segredo do modelo (art. 20, §1º).
  4. A pessoa pode pedir que o conteúdo removido seja trocado pelo motivo ou pela ordem judicial (MCI, art. 20,
     parágrafo único).
  5. O denunciante também é avisado da decisão e pode recorrer de um arquivamento.
- **Base legal ou normativa:**
  - ECA Digital, art. 30;
  - LGPD, art. 20;
  - MCI, art. 20;
  - STF, item 8 ("devido processo");
  - referência estrangeira: DSA, arts. 17 e 20 (via
    [Bundesnetzagentur](https://bundesnetzagentur.de/SharedDocs/Pressemitteilungen/EN/2026/20260430_TB_DSC.html?nn=666514),
    que fiscaliza justamente esses artigos).
- **Riscos:**
  - recursos em massa;
  - a explicação do antifraude pode ensinar o fraudador, então convém mostrar critérios gerais e não pesos;
  - prazos não cumpridos.
- **Referências de mercado:**
  - Tripadvisor: das 244 mil avaliações contestadas em 2024, 72% ficaram no ar
    ([relatório](https://tripadvisor.mediaroom.com/2025-03-18-Tripadvisors-2025-Transparency-Report-reveals-strong-review-submissions-and-improved-fraud-detection));
  - Google tem apelação das restrições do Maps
    ([Consumer Alerts](https://support.google.com/contributionpolicy/answer/15178562?hl=en)).
- **Prioridade:** **Must, por obrigação legal.** Valem o ECA Digital, art. 30, a LGPD, art. 20, e a tese do STF.
- **Depende de:** RC-SEG-01, RF13, RF4 e RF8.
- **Esforço:** M.

### RC-SEG-03 · Uso abusivo de denúncia e sinalizadores confiáveis

- **Ator(es):** moderador; denunciante abusivo; entidade habilitada pela ANPD; órgão público.
- **Descrição:** o sistema detecta denúncias em massa, coordenadas ou repetidamente infundadas. Um exemplo é o dono
  denunciando todas as avaliações negativas, ou um grupo derrubando um concorrente. O sistema aplica sanções graduais
  com aviso e recurso. Em paralelo, há uma via prioritária para **sinalizadores confiáveis**: entidades habilitadas pela
  ANPD (Decreto 12.880, art. 44), MP, polícia e, no futuro, Procons e a vigilância sanitária.
- **Critérios de aceite:**
  1. **Política pública** em linguagem clara (ECA Digital, art. 33), com critérios objetivos de abuso. Exemplos: taxa
     de denúncias improcedentes acima de X% em N dias, ou denúncias vindas de contas ligadas entre si.
  2. **Sanções em escada:** aviso, depois suspensão temporária da **capacidade de denunciar** ou da conta, depois
     cancelamento por reincidência (art. 33, §1º). Cada passo é notificado e cabe recurso com prazo (art. 33, §2º).
  3. **Registro detalhado** de cada caso de abuso e de cada sanção (art. 33, §3º), reaproveitando a trilha do
     RC-SEG-15.
  4. **Sinalizadores confiáveis:**
     - o sinalizador é um papel cadastrado por um administrador, com validade e área de atuação;
     - a denúncia dele entra na fila prioritária;
     - a taxa de acerto é medida;
     - o papel é suspenso se houver abuso (Decreto, art. 44, §3º, II).
- **Base legal ou normativa:**
  - ECA Digital, arts. 32 e 33;
  - Decreto 12.880, arts. 43 e 44;
  - referência estrangeira: DSA, arts. 22 e 23
    ([Prighter, art. 22](https://prighter.com/resources/laws/dsa/articles/article-22)).
- **Riscos:**
  - punir quem denuncia de boa-fé;
  - a lista de entidades habilitadas ainda está em formação (Decreto, art. 52, regime provisório).
- **Referências de mercado:** a EDRi questionou na Comissão Europeia o tratamento de sinalizadores confiáveis pelo X
  ([PDF](https://edri.org/wp-content/uploads/2025/03/DSA-Complaint-X-EDRi-ApTI-trusted-flaggers.pdf)).
- **Prioridade:** **Must, por obrigação legal,** na parte de abuso de denúncia (ECA Digital, arts. 32 e 33). A via
  dos sinalizadores é **Should**.
- **Depende de:** RC-SEG-01, RC-SEG-02 e RF4.
- **Esforço:** M.

### RC-SEG-04 · RF13 · Motor de integridade das avaliações: fraude, incentivo e conflito de interesse

- **Ator(es):** sistema antifraude; moderador; avaliador; dono do estabelecimento.
- **Descrição:** o motor dá uma pontuação de risco a cada avaliação e a cada conta, a partir de sinais. O resultado é
  um de três estados:
  - **recomendada**: entra na nota bayesiana do RF8;
  - **não recomendada**: fica visível num link à parte e fora da nota, como faz a Yelp;
  - **removida**.

  Uma **política de incentivos** proíbe vincular benefício à nota e exige que o avaliador declare o vínculo, por exemplo
  "recebi cortesia" ou "sou funcionário".
- **Critérios de aceite:**
  1. **Sinais mínimos, cada um com código:**
     - rajada de contas novas avaliando o mesmo lugar;
     - mesmo IP, por HMAC, ou mesmo aparelho em várias contas;
     - avaliação de dono ou funcionário, ou de concorrente num raio de X km com a mesma categoria (o RF8 já impede o
       dono de avaliar o próprio lugar; aqui o caso é o **concorrente**);
     - texto quase idêntico entre contas;
     - texto gerado por IA, quando detectável;
     - pico fora do padrão histórico;
     - menção a troca ("ganhei sobremesa pra dar 5").
  2. **Declaração de vínculo:**
     - o avaliador declara cortesia, convite ou relação com o lugar;
     - a avaliação mostra um selo escrito, não só cor;
     - o peso na nota é reduzido;
     - avaliação de quem tem vínculo e não declarou é removida.
  3. **Denúncia de oferta ao dono:** o dono que oferece vantagem **condicionada à nota**, por exemplo "5 estrelas e
     ganhe um chope", vira caso no RC-SEG-05. Pedido **neutro** de avaliação continua permitido, como na FTC, §465.4.
  4. **Recálculo:** a nota agregada é refeita sem as avaliações não recomendadas ou removidas, e a mudança fica
     auditada.
  5. **Revisão e LGPD:**
     - a decisão automatizada é revisável (RC-SEG-02);
     - os sinais com dado pessoal (IP, aparelho) entram no registro de operações (LGPD, art. 37);
     - ⚖️ a base legal provável é o legítimo interesse, com teste de balanceamento, e a minimização vem do art. 6º, III.
- **Base legal ou normativa:**
  - CDC, art. 37 (publicidade enganosa, "mesmo por omissão") e art. 38 (ônus de quem patrocina);
  - CDC, art. 36 (a publicidade tem de ser identificável);
  - STF, item 4 (presunção de responsabilidade por **redes artificiais**);
  - no Brasil, **não encontrei lei específica sobre avaliação falsa**. O PL 123/2024
    ([Câmara](https://www.camara.leg.br/enquetes/2417168)) traz a internet para dentro do CDC, mas não cita avaliações.
- **Referências estrangeiras:**
  - FTC, 16 CFR 465 (avaliação falsa §465.2; incentivo por sentimento §465.4; insider §465.5)
    ([FTC](https://www.ftc.gov/news-events/news/press-releases/2024/08/federal-trade-commission-announces-final-rule-banning-fake-reviews-testimonials));
  - Diretiva Omnibus, Anexo I, 23b e 23c
    ([Cuatrecasas](https://www.cuatrecasas.com/en/global/art/consumer-protection-in-the-digital-single-market-directive-2019-2161)).
- **Riscos:**
  - falso positivo apaga avaliação legítima. Houve queixas à FTC e processos contra o filtro da Yelp, e a Yelp ganhou
    em apelação
    ([Bottom Line](https://www.bottomlineinc.com/?p=13610));
  - o fingerprint de aparelho é dado pessoal;
  - o modelo enviesado pode punir quem tem conta nova legítima.
- **Referências de mercado:**
  - Yelp 2024: 78% recomendadas, 18% não recomendadas, 3% removidas
    ([Yelp](https://www.yelp-press.com/press-releases/press-release-details/2025/Yelp-Releases-2024-Trust--Safety-Report/default.aspx));
  - Yelp 2025: cerca de 500 mil avaliações suspeitas de IA filtradas
    ([BusinessWire](https://www.businesswire.com/news/home/20260225039344/en/Yelp-Releases-2025-Trust-Safety-Report/));
  - Tripadvisor 2024: 2,7 milhões de avaliações fraudulentas (cerca de 8%); "review boosting" foi 54% da fraude; 214 mil
    removidas por IA; cerca de 9 mil empresas advertidas por incentivo
    ([relatório](https://tripadvisor.mediaroom.com/2025-03-18-Tripadvisors-2025-Transparency-Report-reveals-strong-review-submissions-and-improved-fraud-detection));
  - Google proíbe conflito de interesse, incentivo, várias contas e "padrões incomuns de volume"
    ([política](https://support.google.com/contributionpolicy/answer/7400114?hl=en)).
- **Prioridade:** **Must.** É o núcleo do RF13. Não é obrigação legal específica no Brasil, mas há risco de
  publicidade enganosa pelo CDC e da presunção de responsabilidade do STF.
- **Depende de:** RF8, RF6, RF4 (`ClientInfo`), RF13, RC-SEG-02 e RC-SEG-15.
- **Esforço:** G.

### RC-SEG-05 · Alertas ao consumidor na página e congelamento em pico ("review bombing")

- **Ator(es):** visitante; moderador; dono do estabelecimento.
- **Descrição:** a página do estabelecimento (RF11) mostra **alertas ao consumidor** quando há provas de compra de
  avaliação, de incentivo condicionado ou de atividade anormal. Quando o lugar vira notícia e recebe uma onda de
  avaliações que não são de experiência própria, o moderador pode **congelar novas avaliações** por um tempo, mostrar o
  aviso e depois limpar a página.
- **Critérios de aceite:**
  1. **Tipos de alerta** com texto escrito e ícone, sem depender de cor, conforme o DESIGN_SYSTEM:
     - `AVALIACAO_PAGA`: há prova de pagamento ou de troca;
     - `ATIVIDADE_SUSPEITA`: avaliações falsas foram removidas;
     - `ATENCAO_PUBLICA`: pico ligado a notícia ou rede social.
  2. **Congelamento:** pode ser automático, por limiar de pico, ou manual. Bloqueia **novas** avaliações e posts de
     avaliação por N dias, mostra a data de fim e é revisto ao término.
  3. **Histórico:** fica um "Histórico de alertas" público na página, com data de início e de fim.
  4. **Recurso do dono:** cabe recurso (RC-SEG-02). O alerta pode continuar enquanto o recurso corre, como no Google.
  5. **Bloco "Como verificamos as avaliações":** explica os critérios de "visita verificada" e do filtro, inspirado no
     art. 7º, §6º, da Diretiva UCPD, incluído pela Omnibus.
- **Base legal ou normativa:**
  - CDC, art. 6º, III (informação adequada) e art. 37;
  - referência estrangeira: Diretiva Omnibus 2019/2161 (dever de informar como se verifica a origem das avaliações;
    item 23b) ([Cuatrecasas](https://www.cuatrecasas.com/en/global/art/consumer-protection-in-the-digital-single-market-directive-2019-2161)).
  - ⚖️ Um alerta público acusando um estabelecimento identificado tem **risco de dano à honra ou à imagem da empresa**.
    O texto tem de ser factual ("removemos avaliações suspeitas") e baseado em prova guardada.
- **Riscos:**
  - processo do estabelecimento;
  - congelar avaliações pode esconder experiência legítima, por exemplo um surto real de intoxicação, que o RC-SEG-18
    trata;
  - um concorrente pode tentar provocar o alerta com avaliações falsas positivas.
- **Referências de mercado:**
  - Yelp 2025: 128 Compensated Activity Alerts; Unusual Activity Alert (mais de 1.190) e Public Attention Alert (266),
    com **desativação temporária** e mais de 80 mil avaliações removidas
    ([BusinessWire](https://www.businesswire.com/news/home/20260225039344/en/Yelp-Releases-2025-Trust-Safety-Report/));
  - Google: banner de avaliações suspeitas removidas, bloqueio temporário de novas avaliações e ocultação
    ([Consumer Alerts](https://support.google.com/contributionpolicy/answer/15178562?hl=en);
    [9to5Google](https://9to5google.com/2024/09/25/google-maps-warning-shows-which-business-profiles-might-have-fake-reviews/));
  - Tripadvisor: selo vermelho para reincidentes
    ([relatório](https://tripadvisor.mediaroom.com/2025-03-18-Tripadvisors-2025-Transparency-Report-reveals-strong-review-submissions-and-improved-fraud-detection)).
- **Prioridade:** **Should.** É boa prática e reduz o risco do CDC, mas não é obrigação legal específica.
- **Depende de:** RF11 (planejado), RF8, RC-SEG-04 e RC-SEG-02.
- **Esforço:** M.

### RC-SEG-06 · Visita comprovada pela NFC-e (QR Code da nota)

- **Ator(es):** avaliador; sistema; SEFAZ da UF, como serviço externo.
- **Descrição:** o avaliador pode ler o QR Code da NFC-e do restaurante para ganhar o selo "visita comprovada". Isso
  estende a "visita verificada" do RF8. O app extrai a **chave de acesso** de 44 dígitos e confere localmente, sem
  chamar ninguém:
  - o **CNPJ do emitente** contra o CNPJ do estabelecimento (RF6);
  - o **ano e mês** de emissão;
  - o **modelo 65** (NFC-e);
  - o dígito verificador.

  Depois guarda só um **hash da chave**, para impedir que a mesma nota sirva para várias avaliações.
- **Viabilidade, pelo que confirmei:**
  - **QR v3** (NT 2025.001, produção a partir de set/2025): para nota emitida **on-line**, a URL leva só
    `p=<chave>|<versão>|<tpAmb>`, então **não traz valor nem itens**. Em **contingência**, leva também dia, valor,
    identificação do destinatário e assinatura
    ([NT 2025.001](https://inventti.com.br/wp-content/uploads/2025/09/NT2025.001_v1.02-NFCe_qrCode_v3.pdf)).
  - A composição da chave (UF, AAMM, CNPJ, modelo, série, número, tipo de emissão, código e DV) é o padrão da NF-e.
    Conheço esse padrão, mas **não reli o MOC nesta pesquisa**.
  - **Autenticidade real** (a nota existe e foi autorizada) exige consultar o **portal da SEFAZ de cada UF**, que
    muda de URL. O RJ, por exemplo, trocou a URL do QR em dez/2023
    ([Fazenda RJ](https://portal.fazenda.rj.gov.br/dfe)). Há também relatos de divergência entre versões
    ([ACBr](https://www.projetoacbr.com.br/forum/topic/46235-site-da-uf-da-consulta-divergente-para-go/)).
  - **Não verificado:** a existência de uma API pública e unificada de consulta de NFC-e para terceiros. Fazer
    scraping de 27 portais é frágil e pode violar os termos de uso deles ⚖️.
- **Critérios de aceite:**
  1. A câmera lê o QR, ou a pessoa digita a chave. A validação é **local**: DV, modelo 65, CNPJ igual ao do
     estabelecimento e AAMM em até 90 dias.
  2. O app **não guarda** a imagem da nota, os itens, o valor nem o CPF. Guarda só `hmac(chave)`, o CNPJ e o
     ano-mês, segundo a LGPD, art. 6º, III.
  3. Uma chave só vale para **uma** avaliação no sistema todo. Repetição gera `409` e soma um sinal de fraude no
     RC-SEG-04.
  4. O selo "Visita comprovada por nota fiscal" aparece escrito na avaliação. O bloco do RC-SEG-05 explica o que ele
     garante e o que não garante: prova que alguém comprou, não quem comprou.
  5. A confirmação on-line na SEFAZ é **opcional** e fica atrás de uma flag, só nas UFs com integração estável.
- **Base legal ou normativa:**
  - LGPD, arts. 6º, III, e 7º, IX (legítimo interesse) ⚖️;
  - CDC, art. 37, porque a afirmação de que a visita foi verificada precisa ser verdadeira;
  - referência estrangeira: Omnibus 23b, que exige **medidas razoáveis** para afirmar que a avaliação vem de quem
    comprou.
- **Riscos:**
  - **Privacidade:** a página da SEFAZ pode mostrar itens e o **CPF** do consumidor, se ele o informou. Se a máscara
    do CPF é feita em todas as UFs é **não verificado**. Por isso o app não acessa nem guarda essa página por padrão.
  - **Fraude:** a nota de outra pessoa pode ser fotografada. A dedupe limita, mas não impede.
  - **Exclusão:** restaurantes sem NFC-e (MEI ou informais) ficam fora. Por isso o selo é um bônus, nunca um requisito
    para avaliar.
  - A nota pode sair em nome de outra razão social, por exemplo uma filial ou o CNPJ de um shopping, e o RF6 teria de
    aceitar vários CNPJs.
- **Referências de mercado:**
  - Amazon "Verified Purchase": exige compra pela Amazon perto do preço cheio; avaliações do Vine levam rótulo próprio
    ([ppc.land](https://ppc.land/verified-purchase/);
    [E-Commerce Times](https://www.ecommercetimes.com/story/amazon-cracks-down-on-review-freebies-83962.html)). Os
    critérios exatos não foram confirmados em página oficial.
  - Não encontrei app brasileiro que use NFC-e para avaliar restaurante. Há apps de controle de gastos que leem a NFC-e
    ([UFGD](https://repositorio.ufgd.edu.br/jspui/bitstream/prefix/4644/1/EricHenriqueHellerLopes.pdf)).
- **Prioridade:** **Could.** Diferencia o produto, mas não é obrigação. Pode ser feito em fases: primeiro só a
  validação local.
- **Depende de:** RF8 (`VisitMode`), RF6 (CNPJ) e RC-SEG-04.
- **Esforço:** M para a validação local; G com a consulta à SEFAZ.

### RC-SEG-07 · Proteção de quem avalia contra retaliação

- **Ator(es):** avaliador; dono do estabelecimento; moderador; Judiciário.
- **Descrição:** o dono não recebe dado pessoal de quem avaliou, além do que já é público. O avaliador pode publicar
  com **apelido público**. A plataforma detecta e pune retaliação:
  - ameaça;
  - exposição do avaliador (doxxing);
  - denúncias em massa contra as avaliações dele;
  - recusa de atendimento anunciada.

  A identificação do avaliador só sai por **ordem judicial**.
- **Critérios de aceite:**
  1. A página do dono e a resposta pública **não mostram** e-mail, telefone, data da visita com hora nem o histórico de
     outros lugares do avaliador, quando o perfil é privado (RF15).
  2. **Pedidos de identificação:** o pedido de "quem é este avaliador" feito pelo dono é **recusado**. A plataforma só
     fornece registros e dados de cadastro por **ordem judicial** (MCI, arts. 10, §1º, e 22) e registra o
     atendimento.
  3. **Retaliação:** resposta do dono com ameaça, exposição de dado pessoal ou intimidação é removida (RC-SEG-01) e
     soma pontos na conta do estabelecimento.
  4. O avaliador pode **bloquear** o perfil do estabelecimento (RC-SEG-09), sem que isso apague a avaliação.
  5. **Denúncias abusivas** do dono contra avaliações ativam o RC-SEG-03.
- **Base legal ou normativa:**
  - CF, art. 5º, IV e IX;
  - MCI, arts. 10 e 22;
  - CDC, art. 6º, IV (proteção contra métodos coercitivos);
  - referência estrangeira: FTC, **§465.7**, que proíbe ameaça jurídica infundada, intimidação e falsa acusação para
    tirar avaliação negativa
    ([FTC](https://www.ftc.gov/news-events/news/press-releases/2024/08/federal-trade-commission-announces-final-rule-banning-fake-reviews-testimonials)).
  - ⚖️ No Brasil, crítica com ofensa gera indenização. No caso Coco Bambu, em 2024, clientes foram condenados por
    comentários "injuriosos e caluniosos"
    ([Migalhas](https://www.migalhas.com.br/quentes/414998/coco-bambu-sera-indenizado-por-comentarios-ofensivos-nas-redes-sociais)).
    A proteção é para a **crítica**, não para a ofensa.
- **Riscos:**
  - o apelido facilita avaliação falsa, então o RC-SEG-04 tem de compensar;
  - a ordem judicial pode exigir o IP, e hoje o produto guarda só o HMAC (RC-SEG-15).
- **Referências de mercado:**
  - a Yelp descreve Consumer Alerts contra empresas que ameaçam avaliadores. Isso é **não verificado** nesta pesquisa;
  - o Google proíbe assédio e doxxing ([política](https://support.google.com/contributionpolicy/answer/7400114?hl=en)).
- **Prioridade:** **Should.** A parte de entregar dados só por ordem judicial é **obrigação legal** (MCI, art. 10,
  §1º).
- **Depende de:** RF8, RF15, RF12 (resposta identificada do dono, planejada), RC-SEG-01, RC-SEG-03 e RC-SEG-15.
- **Esforço:** M.

### RC-SEG-08 · Contestação pelo dono e resposta pública

- **Ator(es):** dono ou gestor verificado do estabelecimento; moderador; avaliador.
- **Descrição:** o dono pode **responder em público**, uma vez por avaliação, com edição registrada. Pode também
  **contestar** uma avaliação, mas só pelos motivos da política: não é experiência própria, conflito de interesse,
  ofensa ou crime, dado pessoal, fora do tema, ou lugar errado. "Discordo da nota" **não é motivo**. A avaliação
  contestada **fica no ar** até a decisão, salvo no caso de crime grave.
- **Critérios de aceite:**
  1. A contestação exige motivo de uma lista e um texto. É limitada a N por mês, por estabelecimento.
  2. **Fatos em disputa:** quando a divergência é só sobre o que aconteceu, a plataforma **não arbitra**. A avaliação
     fica, e o dono responde em público.
  3. **Honra:** alegação de **crime contra a honra** pode gerar remoção por notificação. A plataforma "não está
     impedida" (STF, item 3.1), mas a decisão é de um moderador treinado, e o dono é informado de que pode buscar a via
     judicial (MCI, art. 19) ⚖️.
  4. O resultado da contestação e o motivo ficam registrados. Cabe recurso tanto ao dono quanto ao avaliador
     (RC-SEG-02).
  5. A taxa de contestações improcedentes alimenta o RC-SEG-03 (abuso de denúncia).
- **Base legal ou normativa:**
  - STF, itens 3 e 3.1 ([resumo](https://tesesesumulas.com.br/tema/transparencia));
  - MCI, arts. 19 e 20;
  - CDC, art. 6º, III.
- **Riscos:**
  - a contestação vira ferramenta de supressão de avaliação negativa, e a FTC, §465.7, trata esconder avaliação
    negativa como prática enganosa;
  - o volume de casos sobre a honra.
- **Referências de mercado:**
  - Google: "não se envolve quando comerciante e cliente discordam sobre fatos"
    ([Verpex, resumo](https://verpex.com/blog/marketing-tips/remove-a-google-review-what-works-now); a política
    original não foi relida);
  - Tripadvisor: 28% das 244 mil contestações resultaram em remoção
    ([relatório](https://tripadvisor.mediaroom.com/2025-03-18-Tripadvisors-2025-Transparency-Report-reveals-strong-review-submissions-and-improved-fraud-detection)).
- **Prioridade:** **Should.** A resposta do dono já está prevista no RF12; a contestação é extensão nova.
- **Depende de:** RF8, RF12, RF6 (selo de dono), RC-SEG-01 e RC-SEG-02.
- **Esforço:** M.

### RC-SEG-09 · Bloquear, silenciar, palavras filtradas e proteção contra cyberbullying

- **Ator(es):** qualquer usuário; menor de 18 e o responsável; moderador.
- **Descrição:** a pessoa pode:
  - **bloquear**: o outro não vê, não comenta, não menciona e não interage;
  - **silenciar**: deixa de ver, sem que o outro saiba;
  - definir **palavras filtradas**: comentários com elas ficam ocultos para ela, com uma lista padrão de ofensas.

  Para menores, o padrão é mais protetor: só contas aprovadas comentam ou mencionam, e adultos desconhecidos não iniciam
  contato. Há uma página de **apoio a vítimas** de intimidação.
- **Critérios de aceite:**
  1. Bloquear é recíproco nas interações (RF12) e sigiloso. Desbloquear é possível a qualquer momento.
  2. As palavras filtradas valem para comentários e menções, aceitam variações (acentos, l33t) e têm uma **lista padrão
     ligada** para menores de 18.
  3. **Menor de 16:**
     - comentário só de seguidores aprovados;
     - menção bloqueada para quem não segue;
     - o padrão não pode ser reduzido sem o responsável (ECA Digital, arts. 17, §4º, I, e 24, §5º).
  4. **Rajada de comentários hostis** contra um mesmo perfil gera um alerta ao moderador e oferece à vítima o "modo
     proteção": limita comentários por 7 dias.
  5. Há uma página "Sofreu intimidação?" com passos práticos, a denúncia (RC-SEG-01) e canais de apoio, como o CVV 188
     e a SaferNet ⚖️ (os canais precisam ser confirmados). Isso atende ao ECA Digital, art. 6º, §2º, e ao art. 17, §4º,
     IX.
- **Base legal ou normativa:**
  - Código Penal, art. 146-A, parágrafo único (Lei 14.811/2024);
  - ECA Digital, arts. 6º, II e §2º, 17, §4º, I e IX, e 24, §5º.
- **Riscos:**
  - um filtro excessivo vira censura;
  - o bloqueio pode ser usado pelo dono para "não ver" quem critica; nesse caso o bloqueio não deve esconder a
    avaliação da página pública.
- **Referências de mercado:** o Google proíbe assédio e ameaça
  ([política](https://support.google.com/contributionpolicy/answer/7400114?hl=en)). Não pesquisei as ferramentas
  equivalentes de Instagram e TikTok nesta rodada.
- **Prioridade:** **Must** para a proteção de menores (obrigação legal do ECA Digital). Para adultos, **Should**.
- **Depende de:** RF12, RF15, RF3 (travas de menor) e RC-SEG-14.
- **Esforço:** M.

### RC-SEG-10 · Dado pessoal de terceiros em fotos: desfoque de rosto e placa, e pedido de remoção

- **Ator(es):** autor da foto; pessoa retratada (titular, que pode não ser usuária); moderador.
- **Descrição:** ao publicar foto de prato ou de ambiente (RF9 e RF2), o app detecta **rostos e placas de veículo** e
  **sugere desfoque**, que vem ligado por padrão para quem não é o autor. Qualquer pessoa, mesmo sem conta, pode pedir a
  remoção ou o desfoque da própria imagem. Foto de **criança** identificável que não seja do autor recebe desfoque
  obrigatório.
- **Critérios de aceite:**
  1. A detecção roda **no aparelho ou no servidor sem gerar template biométrico**, ou seja, sem reconhecimento, só
     localização do rosto. O desfoque é aplicado **antes** de a foto ir para o bucket e é irreversível no arquivo
     público.
  2. O autor pode desligar o desfoque **do próprio rosto** marcando "sou eu". Para outras pessoas, desligar exige
     declarar que tem a autorização delas.
  3. Há um formulário público "Minha imagem foi publicada", que gera um item na fila do RC-SEG-01 com prazo. O conteúdo
     é retirado na hora quando a imagem é de criança (ECA Digital, art. 29).
  4. A foto de criança em conteúdo **monetizado ou impulsionado** exige a autorização judicial do Decreto 12.880,
     art. 34, que vale para monetização iniciada 90 dias depois do decreto. Sem ela, a foto é retirada.
  5. O EXIF e o GPS continuam sendo removidos, como já faz o `ImageSanitizer`.
- **Base legal ou normativa:**
  - CF, art. 5º, X;
  - Código Civil, art. 20 (imagem), não relido nesta pesquisa;
  - LGPD, art. 5º, I e II (o dado biométrico é sensível, por isso é importante **não** gerar template);
  - ECA Digital, arts. 23 e 29;
  - Decreto 12.880, arts. 34 e 35.
  - ⚖️ É preciso confirmar se placa de veículo é dado pessoal neste contexto.
- **Riscos:**
  - falso negativo na detecção;
  - custo de processamento;
  - o desfoque pode estragar a foto de ambiente, mas é aceitável.
- **Referências de mercado:** o Google Street View desfoca rostos e placas automaticamente. **Não verificado**: não
  reli a página oficial.
- **Prioridade:** **Should.** A parte de criança é **Must por obrigação legal** (ECA Digital, art. 29, e Decreto,
  arts. 34 e 35).
- **Depende de:** RF9, RF2 (upload e sanitização), RF16 e RC-SEG-01.
- **Esforço:** M.

### RC-SEG-11 · Direito autoral: notificação, contranotificação e reincidência

- **Ator(es):** titular do direito, como fotógrafo, restaurante ou marca; autor do post; moderador.
- **Descrição:** um formulário próprio para violação de **direito autoral** pede:
  - a obra original;
  - o link do conteúdo;
  - a identificação do titular;
  - a declaração de boa-fé.

  O conteúdo pode ser retirado, e o autor recebe aviso com a possibilidade de **contranotificação**. Contas que violam
  de forma repetida são suspensas. A foto de cardápio ou do prato do próprio restaurante, reaproveitada por terceiros,
  é o caso mais comum.
- **Critérios de aceite:**
  1. O formulário exige os quatro itens acima. A notificação incompleta é devolvida com o motivo.
  2. O autor é avisado e pode contranotificar com justificativa, por exemplo foto própria, licença ou citação. A
     decisão é motivada (RC-SEG-02).
  3. Há uma política de **reincidência**, com N violações procedentes em 12 meses levando à suspensão.
  4. Um **hash perceptual** impede repostagem do mesmo arquivo removido. Isso já está entre as pendências do RF9.
- **Base legal ou normativa:**
  - MCI, art. 19, §2º, e **art. 31**: até sair lei específica, a responsabilidade por direito autoral segue a **Lei
    9.610/1998**, que não reli nesta pesquisa. Não há um procedimento legal brasileiro de notice-and-takedown para
    direito autoral ⚖️.
  - Referência estrangeira: o DMCA §512 dos EUA. **Não verificado**: não reli o texto.
- **Riscos:**
  - a notificação falsa usada para derrubar concorrente, que o RC-SEG-03 mitiga;
  - fotos de prato raramente têm autoria clara.
- **Referências de mercado:** não pesquisadas nesta rodada, porque o limite de buscas acabou.
- **Prioridade:** **Should.** Reduz risco, mas não há obrigação procedimental específica no Brasil.
- **Depende de:** RF9, RC-SEG-01 e RC-SEG-02.
- **Esforço:** P (formulário mais fila); M com o hash perceptual.

### RC-SEG-12 · Central do titular: exportar meus dados, pedidos LGPD e canal do encarregado

- **Ator(es):** titular, usuário ou não; encarregado (DPO); a ANPD.
- **Descrição:** a página "Seus dados" reúne três coisas:
  - **exportar** em formato legível por máquina: JSON e mídia num ZIP, por link pré-assinado com validade curta;
  - abrir os **pedidos do art. 18**: confirmação, acesso, correção, anonimização, informação sobre compartilhamento,
    oposição e revogação;
  - o **canal do encarregado**, com nome e contato públicos.

  Cada pedido tem protocolo e prazo.
- **Critérios de aceite:**
  1. **Exportação:**
     - inclui perfil, consentimentos e o histórico deles (RF3), avaliações e revisões, posts, comentários, fotos
       originais (sem EXIF), sessões e preferências;
     - fica pronta em até 15 dias, sendo a meta 48 h;
     - atende o art. 19, II e §3º;
     - pede reautenticação forte, por 2FA (RF4).
  2. **Pedidos:**
     - a confirmação simplificada é **imediata** (art. 19, I);
     - a declaração completa sai em até **15 dias** (art. 19, II);
     - cada pedido tem estado, histórico e resposta.
  3. **Encarregado:** a identidade e o contato ficam publicados em local de destaque (LGPD, art. 41, §1º, e Resolução
     CD/ANPD 18/2024). O canal aceita não usuários e tem um **substituto** designado (Resolução 18/2024, art. 4º,
     segundo fonte secundária).
  4. **Pedidos de terceiros:** o pedido sobre dado de outra pessoa, como menção ou foto, é encaminhado ao RC-SEG-10.
  5. **Responsável legal:** pode fazer o pedido pelo menor vinculado (RC-SEG-14).
- **Base legal ou normativa:**
  - LGPD, arts. 18, 19 e 41;
  - Resolução CD/ANPD 18/2024
    ([texto, cópia no TJBA](https://www.tjba.jus.br/extrajudicial/wp-content/uploads/2024/08/RESOLUCAO-ANPD-No-18-Encarregado-de-Dados.pdf));
  - o **regulamento da ANPD sobre direitos do titular e portabilidade** estava em elaboração. A situação em out/2026 é
    **não verificada**
    ([Teletime](https://teletime.com.br/28/01/2025/anpd-quer-concluir-regulamento-de-direitos-do-titular-de-dados-em-2025/)).
- **Riscos:**
  - a exportação pode ser usada para vazar dados se a sessão for sequestrada, por isso o 2FA e o aviso por e-mail;
  - incluir dado de terceiros, como comentários de outras pessoas, na exportação.
- **Referências de mercado:** Google Takeout e o "Baixar suas informações" da Meta. Não pesquisei de novo nesta
  rodada.
- **Prioridade:** **Must, por obrigação legal** (LGPD, arts. 18, 19 e 41). Os Termos já prometem "Seus dados →
  Exportar".
- **Depende de:** RF3, RF4, RF5 (pendência "exportar dados") e RF2.
- **Esforço:** M.

### RC-SEG-13 · Verificação de idade proporcional ao risco

- **Ator(es):** usuário; responsável; provedor de verificação, como a loja de apps e o sistema operacional pelo
  **sinal de idade**, ou a solução pública do MGI; moderador.
- **Descrição:** a autodeclaração deixa de bastar onde a lei exige mais. O produto adota **três níveis**:
  1. **Declaração no cadastro**, para a experiência geral, com padrões protetores.
  2. **Sinal de idade** da loja ou do sistema operacional (Decreto 12.880, art. 25), ou estimativa, para ligar as
     travas de menor.
  3. **Verificação de idade** de alta confiabilidade, sem autodeclaração, **antes de ver ou publicar conteúdo de
     bebida alcoólica**, que é proibido a menores (Decreto, arts. 15, §1º, II, e 19).

  A alternativa legal é uma **versão sem esse conteúdo**, que vale sempre para quem não está logado (art. 19, §1º).
- **Critérios de aceite:**
  1. **Visitante não logado** nunca vê post 18+ de bebida nem a página de bar com foco alcoólico com fotos de bebida
     (Decreto, art. 19, I e §1º).
  2. **Desbloqueio do 18+:** exige verificação por método aceito pela ANPD. O app guarda **só o atributo** "18+
     verificado em DATA por MÉTODO". **Não guarda** a imagem nem a cópia do documento (Decreto, art. 24, §3º; ECA
     Digital, art. 13).
  3. **Divergência:** se o sinal da loja divergir da data declarada, vale a hipótese **mais protetora** (Decreto,
     art. 25, §4º).
  4. **Contestação:** a idade aferida pode ser contestada por meio adequado (Decreto, art. 27). Uma conta com indícios
     de ser de criança abaixo da idade mínima é **suspensa**, com apelação rápida do responsável (ECA Digital, art. 24,
     §4º).
  5. **Uso exclusivo:** os dados de verificação servem **só** para verificar. Não alimentam perfil, publicidade nem
     antifraude (ECA Digital, arts. 13 e 26; Decreto, art. 24, §1º).
  6. Há proteção contra múltiplas contas criadas para burlar a verificação (Decreto, art. 25, §2º, IV, por analogia).
- **Base legal ou normativa:**
  - ECA Digital, arts. 9º, 10, 13, 14 e 24, §§3º e 4º;
  - Decreto 12.880, arts. 2º, IV a VII, 15, 19, 24 a 27 e 49;
  - orientações preliminares da ANPD: abordagem por risco, autodeclaração isolada insuficiente e biometria só com
    justificativa robusta
    ([Chambers](https://chambers.com/articles/eca-digital-é-regulamentado-e-anpd-publica-orientações-sobre-verificação-etária));
  - ⚖️ As **etapas de implementação** da ANPD (Decreto, art. 49) podem dar prazo, mas o cronograma é **não
    verificado**.
- **Riscos:**
  - atrito e abandono no cadastro;
  - exclusão de quem não tem documento ou smartphone (Decreto, art. 24, X);
  - um fornecedor de verificação vira operador de dados, e o contrato precisa ser revisto ⚖️;
  - biometria facial é dado sensível.
- **Referências de mercado:** os sinais de idade das lojas são exigidos pelo ECA Digital, art. 12, III. A
  disponibilidade das APIs da Apple e do Google no Brasil é **não verificada**.
- **Prioridade:** **Must, por obrigação legal.** É o risco mais alto da lista, porque hoje o 18+ do RF9 depende de data
  autodeclarada.
- **Depende de:** RF1, RF3, RF9, RF14, RF15 e RF4.
- **Esforço:** G.

### RC-SEG-14 · Conta do responsável vinculada à do menor, com supervisão parental

- **Ator(es):** adolescente de 13 a 16 anos; responsável legal; o sistema.
- **Descrição:** o cadastro de menor de 16 só é concluído quando a conta é **vinculada** à de um responsável, que
  precisa ser adulto verificado e confirmar o vínculo. Hoje o RF1 pede só o e-mail. O responsável tem um painel com as
  ferramentas do ECA Digital, arts. 17 e 18. O adolescente vê um aviso claro de que a supervisão está ativa.
- **Critérios de aceite:**
  1. **Vínculo:**
     - o responsável cria ou usa a própria conta, confirma o vínculo e verifica que é maior de idade;
     - sem vínculo, a conta do menor de 16 fica **restrita**: só leitura de conteúdo livre;
     - **não é possível reduzir proteções** sem vínculo (ECA Digital, art. 24, §5º).
  2. **Painel do responsável (art. 18):**
     - ver e mudar as configurações de privacidade;
     - ver os **perfis adultos** com quem o menor interage;
     - ver o **tempo de uso** consolidado;
     - restringir transações, se um dia houver;
     - ligar e desligar salvaguardas;
     - tudo em português.
  3. **Padrões no máximo de proteção (art. 17, §4º):**
     - contato só de autorizados;
     - sem autoplay nem recompensa por tempo de uso;
     - notificações limitadas;
     - recomendação personalizada desligada, como o RF3 já trava;
     - localização desligada.
  4. **Aviso visível ao adolescente** de que a supervisão está ativa e de quais controles foram aplicados (art. 17,
     III).
  5. **Consentimento do responsável:** o consentimento para dado sensível do adolescente (pendência do RF3) passa a ser
     dado pelo responsável vinculado (LGPD, art. 14).
  6. **Quando o adolescente faz 16:** é avisado antes, o vínculo vira opcional e as travas seguem as regras dos 16 aos
     18 anos.
- **Base legal ou normativa:**
  - ECA Digital, arts. 3º, parágrafo único, 7º, 16 a 18 e 24;
  - Decreto 12.880, art. 20: o MGI **pode** oferecer uma solução pública para confirmar o vínculo;
  - LGPD, art. 14.
  - ⚖️ É preciso confirmar como provar que a pessoa é **de fato** o responsável legal. O decreto prevê uma solução
    pública, mas ela ainda não foi verificada.
- **Riscos:**
  - fraude no vínculo, como um amigo maior de idade se passando por responsável;
  - conflito familiar (guarda);
  - o painel pode virar vigilância excessiva, então deve-se observar o melhor interesse e a autonomia progressiva
    (art. 17, §2º).
- **Referências de mercado:** as reportagens sobre a obrigação de vincular contas de menores de 16 citam práticas de
  redes sociais
  ([Xataka](https://www.xataka.com.br/diversos/adeus-as-contas-secretas-redes-sociais-serao-obrigadas-a-vincular-perfil-menores-16-anos-ao-celular-dos-pais-gracas-ao-eca-digital)).
  Não pesquisei a Central da Família da Meta nem o Family Pairing do TikTok.
- **Prioridade:** **Must, por obrigação legal** (ECA Digital, arts. 17, 18 e 24, em vigor desde 17/03/2026).
- **Depende de:** RF1, RF3, RF4, RF15 e RC-SEG-13.
- **Esforço:** G.

### RC-SEG-15 · Registros de acesso (Marco Civil), trilha de auditoria e atendimento a ordens judiciais

- **Ator(es):** sistema; equipe jurídica ou de compliance; Judiciário, polícia e MP; auditor interno.
- **Descrição:** o sistema guarda os **registros de acesso a aplicações**: data, hora com fuso e **IP** de cada login
  e de cada ação relevante. Ficam por **6 meses**, cifrados e sob sigilo, separados do banco principal, com
  **inventário de quem consultou**. Uma **trilha de auditoria append-only** registra ações de moderação, administração
  e acesso a dado pessoal. Um fluxo próprio atende ordens judiciais de remoção e de entrega de dados, com prazo e
  comprovação.
- **Critérios de aceite:**
  1. **Registros de acesso:**
     - guardam IP, data, hora e fuso de cada sessão e de cada publicação;
     - o IP fica **cifrado**, não só em HMAC, porque precisa ser entregue a um juiz;
     - a retenção é de 6 meses, com expurgo automático;
     - a guarda é **estendida** quando há pedido cautelar de autoridade (MCI, art. 15, §2º) e para relatórios de abuso
       infantil (ECA Digital, art. 27, §2º).
     - ⚖️ Se a **porta lógica de origem** também precisa ser guardada (pelo entendimento do STJ) é **não verificado**.
  2. **Acesso aos registros:**
     - só papéis específicos, com **2FA**;
     - cada consulta grava quem, quando, o que e por quê (Decreto 8.771, art. 13, I a IV);
     - a entrega exige ordem judicial anexada (MCI, arts. 10, §1º, e 22).
  3. **Trilha de auditoria:**
     - append-only e com hash encadeado;
     - cobre moderação, mudança de papel, exportação de dados, acesso de suporte a conta de usuário e mudança de nota
       agregada;
     - fica retida por um prazo definido ⚖️.
  4. **Ordens judiciais:** a ordem de remoção (MCI, art. 19) entra com prazo do juiz e responsável. O cumprimento gera
     evidência com hora. As **réplicas** do mesmo conteúdo também saem (STF, item 3.2).
  5. **Coerência com os Termos:** o §10 dos Termos passa a refletir o que é guardado de fato.
- **Base legal ou normativa:**
  - MCI, arts. 5º, VIII, 10, 13, 15, 19 e 22;
  - Decreto 8.771/2016, art. 13;
  - LGPD, arts. 37 e 46;
  - ECA Digital, arts. 27, §2º, e 33, §3º;
  - STF, item 3.2.
- **Riscos:**
  - IP em claro é dado pessoal sensível ao contexto, então exige cifragem, controle de acesso e minimização (Decreto
    8.771, art. 13, §2º);
  - guardar além de 6 meses sem base viola a minimização;
  - vazamento dos logs.
- **Referências de mercado:** não pesquisei nesta rodada.
- **Prioridade:** **Must, por obrigação legal** (MCI, art. 15). Hoje o código guarda só o HMAC do IP, e os Termos
  prometem mais do que isso.
- **Depende de:** RF4 (`ClientInfo`, sessões), RF5 (o que sobrevive à exclusão) e RC-SEG-01.
- **Esforço:** M.

### RC-SEG-16 · Incidentes de segurança: resposta, registro e comunicação à ANPD e aos titulares

- **Ator(es):** equipe de segurança; encarregado; ANPD; titulares afetados.
- **Descrição:** um processo e uma ferramenta para **registrar todo incidente**, inclusive os que não exigem
  comunicação. O sistema avalia o risco e, se ele for relevante, comunica a ANPD e os titulares **em 3 dias úteis**,
  com complemento em até 20. O titular recebe aviso no app e por e-mail, em linguagem clara.
- **Critérios de aceite:**
  1. **Registro de incidente** com data em que foi descoberto, data em que se soube que atingiu dado pessoal, dados e
     titulares afetados, medidas e decisão de comunicar ou não, com o motivo. A retenção é de **5 anos**.
  2. **Relógio de 3 dias úteis:** dispara quando se sabe que o incidente atingiu dados pessoais. Um alerta avisa o
     encarregado. O comunicado segue o modelo do art. 48, §1º, I a VI.
  3. **Aviso aos titulares:** lista de afetados gerada a partir do banco, com mensagem no app e e-mail. Um modelo de
     texto fica aprovado previamente.
  4. **Mitigação imediata:** revogar sessões (RF4), forçar troca de senha e trocar segredos (`FRIENDLY_EATS_APP_SECRET`
     e chaves de cifra).
  5. **Teste anual** do processo, com um exercício simulado registrado.
- **Base legal ou normativa:**
  - LGPD, arts. 46 a 50 (o art. 48 foi lido);
  - Resolução CD/ANPD 15/2024 (prazos por fontes secundárias:
    [Mattos Filho](https://www.mattosfilho.com.br/unico/regulamento-incidente-seguranca/) e
    [Cescon Barrieu](https://cesconbarrieu.com.br/anpd-aprova-o-regulamento-de-comunicacao-de-incidente-de-seguranca/));
  - ⚖️ Para agente de pequeno porte o prazo é em dobro, segundo uma fonte secundária. Isso é **não verificado**.
- **Riscos:**
  - perder o prazo por não saber quando o relógio começou;
  - comunicar demais ou de menos;
  - dados sensíveis (estilos alimentares, RF3) elevam o risco do incidente.
- **Referências de mercado:** não pesquisei nesta rodada.
- **Prioridade:** **Must, por obrigação legal** (LGPD, art. 48).
- **Depende de:** RF4, RF3, RC-SEG-12 (encarregado) e RC-SEG-15.
- **Esforço:** M (processo mais ferramenta simples).

### RC-SEG-17 · Relatório de transparência público

- **Ator(es):** público; imprensa; pesquisadores; a ANPD; equipe de confiança e segurança.
- **Descrição:** uma página pública com relatório **anual**, que pode ser semestral se o produto passar de 1 milhão de
  usuários menores. Reúne as notificações recebidas por motivo e origem, as ações tomadas, os recursos e reversões, a
  fraude em avaliações e os alertas ao consumidor, as ordens judiciais e os pedidos de dados, e as medidas de proteção
  a menores. Os números saem direto das filas (RC-SEG-01 a 04), não de planilha.
- **Critérios de aceite:**
  1. O relatório anual traz:
     - notificações extrajudiciais por motivo;
     - remoções, separadas entre humanas e automatizadas;
     - recursos e reversões;
     - tempo médio de decisão;
     - ordens judiciais recebidas e cumpridas;
     - pedidos de dados de autoridades;
     - anúncios e impulsionamentos moderados (STF, item 8).
  2. **Seção de avaliações:**
     - quantas foram recomendadas, não recomendadas e removidas;
     - alertas ao consumidor aplicados;
     - congelamentos por pico;
     - inspirado em Yelp e Tripadvisor.
  3. **Seção de menores:**
     - canais de denúncia;
     - quantidade de denúncias;
     - moderação por tipo;
     - medidas de identificação de contas infantis;
     - avanços no consentimento parental;
     - resumo da **avaliação de impacto à segurança da criança** (Decreto, art. 47, §1º).
     - O relatório **semestral** e em português é obrigatório acima de 1 milhão de usuários menores (ECA Digital,
       art. 31).
  4. **Dados para pesquisa:** os dados agregados são exportáveis em CSV, com metodologia publicada. O acesso de
     pesquisadores segue o ECA Digital, art. 31, parágrafo único, e o Decreto, art. 48, quando aplicável.
- **Base legal ou normativa:**
  - STF, item 8 (relatórios anuais de transparência);
  - ECA Digital, art. 31;
  - Decreto 12.880, arts. 45 e 47;
  - referência estrangeira: DSA, arts. 15 e 24, cujo texto não foi lido.
- **Riscos:**
  - publicar números ruins;
  - números que permitam reidentificar pessoas em cidades pequenas.
- **Referências de mercado:**
  - [Tripadvisor 2025](https://tripadvisor.mediaroom.com/2025-03-18-Tripadvisors-2025-Transparency-Report-reveals-strong-review-submissions-and-improved-fraud-detection);
  - [Yelp 2025](https://www.businesswire.com/news/home/20260225039344/en/Yelp-Releases-2025-Trust-Safety-Report/).
- **Prioridade:** **Should** no lançamento. Passa a **Must por obrigação legal** quando houver um ano de operação
  (STF, item 8 ⚖️; é preciso confirmar se vale para plataforma pequena, já que o ECA Digital, art. 39, modula pelo
  porte) ou mais de 1 milhão de usuários menores (ECA Digital, art. 31).
- **Depende de:** RC-SEG-01 a RC-SEG-05, RC-SEG-13, RC-SEG-14 e RC-SEG-15.
- **Esforço:** M.

### RC-SEG-18 · Alerta sanitário: fonte oficial, vínculo com o recolhimento da Anvisa e combate à desinformação

- **Ator(es):** conta Oficial verificada (RF9); usuário que relata um problema de saúde; moderador; a Anvisa e as
  vigilâncias, como fonte.
- **Descrição:** o RF9 já restringe o **alerta sanitário** à conta Oficial verificada e exige fonte HTTPS oficial. A
  extensão nova tem três partes:
  - **ligar o alerta ao recolhimento oficial**: Resolução-RE no DOU ou comunicado da Anvisa;
  - tratar **alegações de usuários** como "me intoxiquei aqui" em avaliações e posts: ficam como **relato**, não como
    alerta, e um pico delas vai para revisão;
  - **rotular e rebaixar** "alertas" falsos ou não oficiais que circulam como post comum.
- **Critérios de aceite:**
  1. **Alerta oficial:**
     - exige o número do ato (RE ou DOU) ou a URL do comunicado em domínio `gov.br`, de vigilância estadual ou
       municipal;
     - mostra "Fonte: Anvisa, RE nº …, data";
     - o encerramento acontece quando a fonte encerra o caso (pendência do RF9).
  2. **Post comum que imita um alerta:**
     - palavras como "recall" ou "lote contaminado", sem fonte oficial, recebem o rótulo "Não é um alerta oficial";
     - mostram um link para a lista oficial;
     - têm alcance reduzido até a revisão;
     - sem remoção automática ⚖️, por causa da liberdade de expressão.
  3. **Relatos de mal-estar em avaliações:** são permitidos como experiência própria. Um **pico** (N relatos em M
     dias) abre um caso para o moderador e sugere ao relator a notificação à vigilância sanitária local. Esse pico
     **não** dispara o congelamento do RC-SEG-05.
  4. **Importação de recolhimentos:** **não encontrei um conjunto de dados abertos da Anvisa sobre recolhimento de
     alimentos**. O Plano de Dados Abertos 2023 a 2025 lista "alertas em tecnovigilância", mas não alimentos.
     Alternativas:
     - ler as Resoluções-RE no DOU;
     - acompanhar a página de comunicados da Anvisa;
     - fazer um pedido pela LAI.
     - A integração é opcional e fica atrás de uma flag.
  5. **Desinformação comprovada:** um "alerta" falso que cita marca ou estabelecimento real vai para a fila prioritária
     do RC-SEG-01. Ele pode configurar dano à honra ou ao CDC.
- **Base legal ou normativa:**
  - **RDC Anvisa 655/2022** (recolhimento de alimentos: a empresa comunica a Anvisa e os consumidores, com texto e plano
    de mídia aprovados previamente) ([texto](https://ses.sp.bvs.br/wp-content/uploads/2022/03/U_RS-MS-ANVISA-RDC-655_240322.pdf);
    [perguntas e respostas da Anvisa](https://www.gov.br/anvisa/pt-br/centraisdeconteudo/publicacoes/certificacao-e-fiscalizacao/perguntas-e-respostas/perguntas-e-respostas-recolhimento-de-alimentos.pdf));
  - [Plano de Dados Abertos da Anvisa 2023 a 2025](https://dados.anvisa.gov.br/dados/Plano_de_Dados_Abertos_Anvisa_2023-2025.pdf);
  - CDC, arts. 6º, I e III, e 37;
  - STF, item 5 (dever de cuidado), cujo rol taxativo **não** inclui desinformação sanitária ⚖️.
- **Riscos:**
  - um relato real de intoxicação ser abafado;
  - a plataforma parecer endossar a acusação ou a defesa;
  - responsabilidade por um "alerta" errado.
- **Referências de mercado:** 82 ocorrências de recolhimento de alimentos em 2024, somando 237 produtos
  ([Food Safety Brazil](https://foodsafetybrazil.org/tag/recall)). As referências de plataformas sobre isso não foram
  pesquisadas.
- **Prioridade:** **Should.** O RF9 já cobre o essencial; isto reduz desinformação e risco reputacional.
- **Depende de:** RF9, RF8, RC-SEG-01 e RC-SEG-05.
- **Esforço:** M (P sem a importação de recolhimentos).

---

## Tabela-resumo

| RC | Título | Prioridade | Obrigação legal? | Esforço | Depende de |
|---|---|---|---|---|---|
| RC-SEG-01 | Denunciar conteúdo e conta, fila e prazos | Must | Sim: STF itens 8 e 9; ECA Digital, arts. 27 a 29 | G | RF4, RF8, RF9, RF12, RF15, RF13 |
| RC-SEG-02 | Decisão motivada e recurso com revisão humana | Must | Sim: ECA Digital, art. 30; LGPD, art. 20; MCI, art. 20 | M | RC-01, RF13 |
| RC-SEG-03 | Abuso de denúncia e sinalizadores confiáveis | Must (abuso) / Should (sinalizadores) | Sim: ECA Digital, arts. 32 e 33 | M | RC-01, RC-02 |
| RC-SEG-04 | RF13: integridade das avaliações (fraude, incentivo, conflito) | Must | Indireta: CDC, art. 37; STF, item 4 | G | RF8, RF6, RF4, RF13 |
| RC-SEG-05 | Alertas ao consumidor e congelamento em pico | Should | Não (boa prática; Omnibus como referência) | M | RF11, RF8, RC-04 |
| RC-SEG-06 | Visita comprovada pela NFC-e | Could | Não | M/G | RF8, RF6, RC-04 |
| RC-SEG-07 | Proteção de quem avalia contra retaliação | Should | Parcial: MCI, arts. 10 e 22 | M | RF8, RF15, RF12, RC-03 |
| RC-SEG-08 | Contestação pelo dono e resposta pública | Should | Não (segue o STF, item 3.1) | M | RF8, RF12, RF6 |
| RC-SEG-09 | Bloquear, silenciar, palavras filtradas, cyberbullying | Must (menores) / Should | Sim, para menores: ECA Digital, arts. 6º, §2º, 17 e 24 | M | RF12, RF15, RF3 |
| RC-SEG-10 | Desfoque de rosto e placa; imagem de terceiros | Should (Must para criança) | Parcial: ECA Digital, art. 29; Decreto, arts. 34 e 35 | M | RF9, RF2, RF16 |
| RC-SEG-11 | Direito autoral: notificação e contranotificação | Should | Não há procedimento legal específico (MCI, art. 31) | P/M | RF9, RC-01 |
| RC-SEG-12 | Central do titular: exportar, pedidos, encarregado | Must | Sim: LGPD, arts. 18, 19 e 41 | M | RF3, RF4, RF5 |
| RC-SEG-13 | Verificação de idade proporcional (18+ de bebida) | Must | Sim: ECA Digital, art. 9º; Decreto, arts. 15 e 19 | G | RF1, RF3, RF9, RF14 |
| RC-SEG-14 | Conta do responsável vinculada e supervisão parental | Must | Sim: ECA Digital, arts. 17, 18 e 24 | G | RF1, RF3, RF4, RC-13 |
| RC-SEG-15 | Registros de acesso (MCI), auditoria e ordens judiciais | Must | Sim: MCI, art. 15; Decreto 8.771, art. 13 | M | RF4, RF5 |
| RC-SEG-16 | Incidentes de segurança e comunicação à ANPD | Must | Sim: LGPD, art. 48; Resolução ANPD 15/2024 | M | RF4, RF3, RC-12 |
| RC-SEG-17 | Relatório de transparência | Should, depois Must | Sim, com gatilhos: STF, item 8; ECA Digital, art. 31 | M | RC-01 a 05, RC-13 a 15 |
| RC-SEG-18 | Alerta sanitário: fonte oficial e desinformação | Should | Não (RDC 655/2022 obriga a empresa, não a plataforma) | M | RF9, RF8, RC-01 |

## Obrigações legais que o produto ainda não cumpre (em ordem de risco)

> Ordem estimada por gravidade da sanção, por chance de fiscalização e por quão longe o código está. ⚖️ A ordem e o
> enquadramento precisam ser validados por um advogado. O ECA Digital, art. 39, modula várias obrigações pelo **porte**
> e pelo número de usuários, o que pode aliviar um produto em lançamento.

1. **Verificação de idade sem autodeclaração para conteúdo de bebida alcoólica.**
   - **Base:** ECA Digital, art. 9º, §1º, com o Decreto 12.880, arts. 15, §1º, II, e 19. Em vigor desde 17/03/2026;
     multa de até R$ 50 milhões por infração (art. 35).
   - **Situação:** o 18+ do RF9 usa a data **autodeclarada** no cadastro, e o visitante não logado precisa ver a
     versão sem esse conteúdo.
   - **Requisito:** RC-SEG-13.
2. **Contas de menores de 16 vinculadas a um responsável, com ferramentas de supervisão parental.**
   - **Base:** ECA Digital, arts. 17, 18 e 24.
   - **Situação:** o RF1 só pede o **e-mail** do responsável, e não há painel.
   - **Requisito:** RC-SEG-14.
3. **Mecanismo de notificação de violações a direitos de crianças.**
   - **Base:** ECA Digital, arts. 27 a 30, e Decreto, arts. 36 e 41 a 43. Inclui a retirada **imediata** sem ordem
     judicial, a contestação e a comunicação de conteúdo de exploração sexual infantil à PF.
   - **Situação:** não há denúncia no produto; é pendência do RF9 e do RF13.
   - **Requisitos:** RC-SEG-01 e RC-SEG-02.
4. **Guarda dos registros de acesso por 6 meses, com IP recuperável.**
   - **Base:** MCI, art. 15, e Decreto 8.771, art. 13.
   - **Situação:** o código guarda o IP **só como HMAC**, e os Termos prometem guardar o IP. Pode haver log de
     infraestrutura que cumpra isso; é preciso confirmar.
   - **Requisito:** RC-SEG-15.
5. **Sistema de notificações, devido processo e canal para usuários e não usuários.**
   - **Base:** STF, Temas 987 e 533, itens 8 e 9.
   - **Situação:** os Termos ainda tratam o "art. 19" como regra geral, o que ficou desatualizado depois da tese.
   - **Requisitos:** RC-SEG-01, RC-SEG-02 e revisão dos Termos ⚖️.
6. **Abuso de denúncia: política pública, sanções com recurso e registros.**
   - **Base:** ECA Digital, arts. 32 e 33.
   - **Requisito:** RC-SEG-03.
7. **Encarregado identificado em público e canal do titular funcionando.**
   - **Base:** LGPD, art. 41, e Resolução ANPD 18/2024.
   - **Situação:** os Termos têm `{{ENCARREGADO_NOME}}` sem preenchimento.
   - **Requisito:** RC-SEG-12.
8. **Portabilidade, exportação e prazos de atendimento ao titular.**
   - **Base:** LGPD, arts. 18, V, e 19.
   - **Situação:** os Termos prometem "Exportar", mas é pendência do RF5.
   - **Requisito:** RC-SEG-12.
9. **Processo de incidente de segurança.**
   - **Base:** LGPD, art. 48, e Resolução ANPD 15/2024: comunicação em 3 dias úteis e registro por 5 anos.
   - **Situação:** não encontrei processo nem registro no repositório. Pode existir fora dele (**não verificado**).
   - **Requisito:** RC-SEG-16.
10. **Avaliação de impacto à segurança da criança, com resumo público, e relatório de impacto do tratamento de dados
    de menores.**
    - **Base:** ECA Digital, art. 16, parágrafo único, II, e Decreto, art. 47.
    - **Situação:** não há documento no repositório.
    - **Requisito:** é documento, não funcionalidade. Os números dele entram no RC-SEG-17.
11. **Política contra intimidação sistemática virtual, com apoio às vítimas e padrões de contato restrito para
    menores.**
    - **Base:** ECA Digital, arts. 6º, §2º, e 17, §4º, I.
    - **Requisito:** RC-SEG-09.
12. **Publicidade a menores sem perfilamento e conteúdo monetizado com criança só com autorização judicial.**
    - **Base:** ECA Digital, arts. 22, 23 e 26, e Decreto, arts. 33 a 35.
    - **Situação:** o RF3 já trava `MARKETING` e `PERSONALIZED_FEED` para menores de 16. Os de 16 e 17 anos também são
      protegidos pela lei, então é preciso conferir. A autorização judicial para conteúdo monetizado com criança não
      existe.
    - **Requisitos:** RC-SEG-10 e revisão do RF3 e do RF9.
13. **Relatório anual de transparência.**
    - **Base:** STF, item 8, e ECA Digital, art. 31, que é semestral e só acima de 1 milhão de usuários menores.
    - **Situação:** a obrigação começa com a operação.
    - **Requisito:** RC-SEG-17.
14. **Acessibilidade do site.**
    - **Base:** LBI, art. 63, com o símbolo de acessibilidade em destaque (§1º).
    - **Situação:** o projeto já mede contraste e usa rótulos escritos, mas o símbolo e uma auditoria completa não
      foram verificados. Também não confirmei a **ABNT NBR 17225**, porque o limite de buscas acabou.

### Não verificado ou pendente de fonte primária

- Resultado dos embargos do STF (jun/2026) e eventual prazo de adequação.
- Cronograma de etapas da ANPD para a aferição de idade (Decreto 12.880, art. 49).
- Disponibilidade das APIs de sinal de idade das lojas no Brasil.
- Texto da Resolução ANPD 15/2024 (prazos lidos em fontes secundárias) e prazo dobrado para agente de pequeno porte.
- Situação do regulamento da ANPD sobre direitos do titular e portabilidade.
- Se a consulta pública da NFC-e mascara o CPF em todas as UFs, e se existe API unificada de consulta.
- Detalhes do CONAR, Anexo P (bebidas), aplicados a influenciadores. O novo "Guia de Marketing e Publicidade por
  Influenciadores Digitais" foi publicado em maio de 2026 e exige identificação clara, ferramentas de "parceria paga" e
  mais rigor com menores
  ([Propmark](https://propmark.com.br/artigos/o-novo-guia-do-conar-para-influenciadores-e-os-riscos-da-falta-de-transparencia/)).
  Esse tema já é coberto pelo RF9.
- STJ sobre a guarda da porta lógica de origem.
- ABNT NBR 17225.
- Ações da Senacon e dos Procons sobre avaliação falsa.
- Texto oficial do DSA, da Omnibus e do DMCA.
- Desfoque do Google Street View.
- Online Safety Act do Reino Unido, que não pesquisei por relevância baixa para o mercado brasileiro e por limite de
  buscas.
