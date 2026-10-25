# Pesquisa de requisitos · 02 · Lado do estabelecimento, do dono e do varejo (B2B)

> Friendly Eats · pesquisa de requisitos candidatos (RC), sem código. Data: 25/10/2026.
> Eixo: o que o **estabelecimento**, o **dono** e a conta **Varejo & Marca** fazem na plataforma.

## Como ler

- **Fora do escopo** (já existem ou estão planejados e não são propostos de novo): RF1 (contas `ESTABELECIMENTO` e
  `VAREJO` com selo), RF6 (cadastro do lugar, `ACTIVE`/`CLOSED`/`REMOVED`), RF7, RF8, RF9 (incluindo `PRATO_DO_DIA`,
  `PROMOCAO` e `EVENTO`), RF10, RF11 (página com as facetas da seção 8 da taxonomia), RF12, RF13, a resposta
  identificada do dono (`RULE_OWNER_REPLY`) e o cardápio por item com alergênicos.
- Também já estão esboçados na taxonomia, sem RF: a matriz de verificação da seção 7.4 (CNPJ pela BrasilAPI, QSA,
  posse do endereço e reverificação), as facetas de acessibilidade, pet e pagamento (seção 8) e o chip ⏳
  **Patrocinado** (14.3). Os RCs abaixo só **estendem** esses pontos quando trazem regra nova.
- **Fonte:** cada afirmação sobre mercado ou lei traz a URL. "**não verificado**" = não encontrei fonte primária ou
  a fonte era secundária e não consegui confirmar. Várias páginas de ajuda (Tripadvisor, texto da RDC 727/2022 na
  Anvisa) devolveram 403 e foram lidas só pelo trecho do buscador; isso está marcado.
- **Limite da pesquisa:** o orçamento de buscas na web acabou antes de eu conferir a RDC 429/2020 e a IN 75/2020
  (rotulagem nutricional), a RDC 216/2004 (boas práticas) e o Menu Dino. Esses pontos estão como **não verificado**.
- Prioridade **MoSCoW**: M = Must, S = Should, C = Could, W = Won't (por agora). Esforço: P (até 1 sprint),
  M (2 a 3), G (4 ou mais).

---

## Regulação brasileira encontrada (resumo usado nos RCs)

| Tema | O que achei | Fonte | Situação |
|---|---|---|---|
| Preço na porta | Decreto 5.903/2006, **art. 8º, § 2º**: "a relação de preços deverá ser também afixada, externamente, nas entradas de restaurantes, bares, casas noturnas e similares". Art. 2º, § 1º, V: legibilidade. Art. 3º: preço total **à vista**. Art. 9º, I a VIII: infrações (letras não uniformes, cor de letra igual à do fundo, preço só em parcelas, moeda estrangeira sem conversão, referência ambígua, preços diferentes para o mesmo item, texto na vertical). Art. 10, parágrafo único (incluído pelo Decreto 7.962/2013): os arts. 2º, 3º e 9º valem para o **comércio eletrônico** | [Câmara, texto atualizado](https://www2.camara.leg.br/legin/fed/decret/2006/decreto-5903-20-setembro-2006-545543-normaatualizada-pe.html) | ✅ verificado no texto |
| Afixação de preços | Lei 10.962/2004 dispõe sobre oferta e afixação de preços; o **art. 5º-A** (Lei 13.455/2017) obriga informar, "em local e formato visíveis", o desconto por forma ou prazo de pagamento (ex.: Pix) | [Planalto](https://planalto.gov.br/ccivil_03/_ato2004-2006/2004/lei/l10.962.htm), [Câmara (ficha da norma)](https://www2.camara.leg.br/legin/fed/lei/2004/lei-10962-11-outubro-2004-534307-norma-pl.html), [Procon Jundiaí](https://jundiai.sp.gov.br/noticias/2017/06/27/procon-orienta-sobre-a-lei-de-diferencas-de-precos/) | ✅ (art. 5º-A pela ficha da Câmara; Planalto deu 503 na leitura) |
| Taxa de serviço (10%) | Lei 13.419/2017 alterou o **art. 457 da CLT** (§ 3º e §§ 4º a 11): a "gorjeta" inclui a taxa de serviço cobrada pela empresa; regras de rateio e anotação na CTPS. Procons: a taxa é **opcional**, deve vir discriminada e com a indicação de que é opcional; cobrança imposta é abusiva (CDC art. 39, V e 51, IV, citados nas fontes); cobrança indevida dá devolução em dobro (CDC art. 42, parágrafo único, citado pela OAB/MS) | [Saipos (resumo da lei)](https://saipos.com/gestao-de-restaurante/lei-da-gorjeta), [Folha Vitória / Procon-ES](https://www.folhavitoria.com.br/economia/consumacao-minima-e-10-de-gorjeta-sao-cobrancas-abusivas-diz-procon/), [Procon Santo André](https://abcdoabc.com.br/procon-santo-andre-orienta-sobre-diretos-do-consumidor-em-bares-e-restaurantes/), [OAB/MS](https://www.sedep.com.br/?p=54314) | ✅ lei; jurisprudência de tribunal superior **não verificada** (só orientação de Procons) |
| Couvert artístico | Só com apresentação **ao vivo** e com **aviso prévio na entrada** (preço e programação); sem aviso, o consumidor não é obrigado a pagar (CDC art. 6º, III; art. 31; art. 39, parágrafo único, conforme as fontes). Couvert de petiscos não pedidos = amostra grátis. Não se confunde com os 10% | [Idec](https://idec.org.br/idec-na-imprensa/11-direitos-que-voce-tem-em-restaurantes-e-bares-e-talvez-nao-saiba), [Correio Braziliense](https://www.correiobraziliense.com.br/cbradar/cobranca-couvert-artistico-restaurante-regras-cdc-direitos/), [Contábeis](https://www.contabeis.com.br/noticias/42127/couvert-artistico-4-situacoes-em-que-o-consumidor-nao-e-obrigado-a-pagar/) | ✅ (orientação de órgãos de defesa do consumidor; PL do Pará sem sanção confirmada) |
| Consumação mínima | Procon-ES considera abusiva | [Folha Vitória](https://www.folhavitoria.com.br/geral/procon-es-notifica-cobranca-de-consumacao-minima-em-bares-e-restaurantes/) | ✅ orientação de Procon |
| Cardápio em braille | **PB**: Lei 7.776/2005 (alterada pela Lei 9.800/2012) exige um exemplar em braille. **PE**: Lei 13.401/2008, alterada pela Lei 15.636/2015 (braille e fonte ampliada) e pela Lei 16.656/2019 (admite **áudio** como alternativa). SP: PL 741/2013 com status não confirmado | [LegisWeb PB](https://www.legisweb.com.br/legislacao/?id=145537), [Alepe](https://legis.alepe.pe.gov.br/texto.aspx?id=9603) | ✅ PB e PE; demais estados **não verificado** |
| Cardápio físico obrigatório (além do QR) | **Rio de Janeiro (capital)**: Lei 8.224/2023 (impresso obrigatório, QR permitido). **Contagem (MG)**: Lei 5.350/2023. **CE**: aprovado na Alece em 2023, sanção não confirmada. **São Paulo**: PL 120/2024, fontes conflitantes sobre vigência | [Central do Varejo](https://centraldovarejo.com.br/bares-e-restaurantes-serao-obrigados-a-ter-cardapio-fisico-no-rio/?amp=1), [Itatiaia](https://www.itatiaia.com.br/brasil/sudeste/mg/contagem-na-grande-bh-ja-tem-lei-que-exige-cardapio-impresso-e-proibe-uso-exclusivo-do-qr-code/), [O Povo](https://www.opovo.com.br/noticias/politica/2023/10/25/alece-aprova-lei-que-obriga-bares-e-restaurantes-a-disponibilizarem-cardapio-fisico.html), [Câmara SP](https://www.saopaulo.sp.leg.br/blog/projeto-preve-obrigatoriedade-de-fornecimento-de-cardapio-fisico-em-bares-e-restaurantes/) | ✅ Rio e Contagem; CE e SP **não verificado** |
| Acessibilidade digital | LBI (Lei 13.146/2015) **art. 63**: acessibilidade obrigatória em sítios mantidos por empresas com sede no país; § 1º: símbolo de acessibilidade. O MPF diz que o artigo ainda não tem regulamentação técnica; PL 981/2022 estenderia a aplicativos | [MPF](https://www.mpf.mp.br/o-mpf/unidades/pr-sp/noticias/mpf-recomenda-regulamentacao-de-trecho-da-lei-brasileira-de-inclusao-sobre-acessibilidade-na-internet), [Fiocruz (resumo)](https://mooc.campusvirtual.fiocruz.br/rea/acessibilidade-sus/downloads/modulo_3/mod3-leis-panorama-web-brasil.pdf) | ✅ art. 63; texto do art. 9º **não verificado** |
| Cão-guia | Lei 11.126/2005 (caput alterado pela LBI): direito de entrar e permanecer em locais abertos ao público e privados de uso coletivo; impedir é discriminação (art. 3º: interdição e multa) | [Planalto](https://planalto.gov.br/ccivil_03/_ato2004-2006/2005/lei/l11126.htm), [Câmara](https://www2.camara.leg.br/legin/fed/lei/2005/lei-11126-27-junho-2005-537609-normaatualizada-pl.html) | ✅ (a taxonomia marcava "não verificado"; pode trocar) |
| Pet em restaurante | Não há lei estadual paulista em vigor encontrada; há leis municipais (Santo André Lei 10.527/2022; Mauá Lei 6.108/2023, selo "Pet Friendly"). Onde não há lei, a vigilância sanitária pode autuar (reportagem local, antiga) | [ABC do ABC](https://abcdoabc.com.br/pet-friendly-estabelecimentos-podem-ser-notificados-se-o-municipio-nao-tiver-lei/), [Mauá](https://dom.maua.sp.gov.br/public/docs/9f818eadd3767bc6d087fbb891798dde.pdf) | parcial |
| Alergênicos | RDC 727/2022: aplica-se aos alimentos **embalados na ausência do consumidor**, inclusive os destinados a serviços de alimentação; declarações "Alérgicos: Contém…" e "Alérgicos: Pode conter…". A revisão em consulta (Projeto de Resolução 03/2025 Rev. 1) **exclui** os alimentos embalados preparados nos serviços de alimentação e vendidos no próprio local. Conclusão: o **prato servido** não está sob a RDC; o **produto embalado** do varejo está | [Anvisa (RDC compilada, lida só pelo trecho)](https://antigo.anvisa.gov.br/documents/10181/6503668/(1)RDC_727_2022_COMP.pdf/8167fbac-395a-4189-ab43-024e4d3aa667), [Anvisa, minuta 2025](https://www.gov.br/anvisa/pt-br/assuntos/noticias-anvisa/2025/copy_of_MinutadeCPdarevisodoregulamentoderotulagemgeraldealimentos.pdf), [Anvisa, diálogo setorial](https://www.gov.br/anvisa/pt-br/assuntos/noticias-anvisa/2025/Apresentao_Dilogosetorialvirtualsobrerevisodaregulamentaoderotulagemdealimentosalergnicos.pdf) | ✅ escopo; status final da revisão **não verificado** |
| Informação nutricional | RDC 429/2020 e IN 75/2020 | — | **não verificado** nesta pesquisa (orçamento de busca esgotado) |
| Meia-entrada | Lei 12.933/2013, art. 1º: cinemas, teatros, espetáculos musicais e circenses, eventos educativos, esportivos, de lazer e entretenimento, em estabelecimentos públicos ou particulares; cota de 40%; não cumulativa. **Não se aplica à refeição.** Para show com ingresso em casa noturna ou restaurante, a lei pode alcançar o **ingresso do espetáculo** (o texto fala em "espetáculos musicais" em "estabelecimentos particulares"); não achei posição de Procon sobre couvert artístico | [Câmara, texto atualizado](https://www2.camara.leg.br/legin/fed/lei/2013/lei-12933-26-dezembro-2013-777776-normaatualizada-pl.html), [Procon-MG, roteiro shows e eventos](https://mpmg.mp.br/data/files/27/B0/21/4F/ACCD491061202D49760849A8/Procon-MG-Roteiro-de-Atuacao-Shows-e-Eventos-5-2-2025.pdf) | ✅ refeição fora; couvert artístico **não verificado** |
| Alvará e licença sanitária | A Lei 13.874/2019 (Liberdade Econômica) dispensa atos de liberação para atividade de **baixo risco**; o enquadramento é municipal ou estadual por CNAE (ex.: Joinville, Rio Branco, Resolução SES-RJ 2.191/2020). Dá para usar alvará ou licença como **prova de posse** (seção 7.4), não como selo público | [Porto Real (decreto)](https://portoreal.rj.gov.br/arquivos/files/leis-e-decretos/Decreto_N_2691_DE_15.02.2022-alvara-automatizado.pdf), [Joinville](https://www.joinville.sc.gov.br/wp-content/uploads/2023/09/Parecer-Sanitario-Dispensa-de-ato-publico-de-liberacao-para-os-estabelecimentos-enquadrados-na-condicao-de-Microempreendedor-Individual-MEI.pdf), [Fiocruz (licenciamento sanitário)](https://ideiasus.fiocruz.br/wp-content/uploads/2025/05/APRESENTACAO-SIMBRAVISA-2-1.pdf) | parcial; RDC 216/2004 **não verificado** |
| Validar CNPJ | (1) **Consulta oficial** gratuita no portal da Receita; (2) **API do Serpro** "Consulta CNPJ" (paga, dados D-1; preço de 2017: R$ 662,40 por até 999 consultas, desatualizado); (3) **Dados abertos do CNPJ** da Receita (CSV mensal, ~27 GB, com situação cadastral e CNAE principal e secundárias; atraso de semanas na baixa); (4) terceiros: BrasilAPI (já prevista na seção 7.4), CNPJá aberta (5 consultas/min por IP, defasagem até 45 dias), CNPJ.ws | [gov.br Conecta](https://www.gov.br/conecta/catalogo/apis/consulta-cnpj), [Serpro](https://serpro.gov.br/menu/noticias/noticias-2020/consulta-cnpj-ja-verificou-25-milhoes-de-cadastros), [Baguete (preço 2017)](https://www.baguete.com.br/noticias/serpro-vende-acesso-para-cpf-cnpj-e-nfe), [dados abertos (guia)](https://www.socialhub.pro/blog/dados-abertos-receita-federal-cnpj/), [CNPJá](https://cnpja.com/api/open) | ✅; preço atual do Serpro **não verificado** |
| Publicidade | CDC art. 36 (publicidade identificável de imediato), art. 37 (enganosa ou abusiva); Código CONAR art. 10 (publicidade indireta) | [Migalhas](https://www.migalhas.com.br/depeso/281406/publicidade-velada-nas-redes-sociais), [Acontecendo Aqui](https://acontecendoaqui.com.br/?p=105113) | ✅ (secundárias; Guia de Influenciadores do CONAR **não lido**) |
| Oferta e cupom | CDC art. 30 (oferta vincula), art. 35 (recusa: cumprimento forçado, equivalente ou rescisão), art. 37 | [Jus](https://jus.com.br/artigos/703/a-disciplina-civil-da-publicidade-no-codigo-de-defesa-do-consumidor), [STJ BDJur](https://bdjur.stj.jus.br/jspui/bitstream/2011/178796/11_publicidade_enganosa_vinculacao_frota.pdf) | ✅ |
| Comércio eletrônico | Decreto 7.962/2013: identificação do fornecedor (nome empresarial, CNPJ ou CPF, endereço), condições integrais da oferta | [Idec](https://idec.org.br/em-acao/artigo/novas-regras-de-comercio-eletronico-entram-em-vigor-no-proximo-dia-15-de-maio), [LegisWeb](https://www.legisweb.com.br/noticia/?id=8006) | ✅ |
| Vagas | CLT art. 373-A, I (anúncio sem referência a sexo, idade, cor ou situação familiar); Lei 9.029/1995; CLT art. 442-A (experiência acima de 6 meses) | [Catho](https://www.catho.com.br/carreira-sucesso/carreira/anuncio-de-emprego-quando-suas-exigencias-transgridem-leis/), [Jus](https://jus.com.br/artigos/105354/anuncios-ilegais-de-vagas-de-emprego-como-evita-los) | ✅ (secundárias) |
| LGPD | Art. 7º, IX e art. 10 (legítimo interesse: mínimo necessário, transparência, RIPD a pedido da ANPD); art. 18, IV e V (anonimização; portabilidade, observados segredos comercial e industrial) | [Confidata](https://confidata.com.br/blog/lgpd-comentada-12-direitos-titular), [Modelo Inicial](https://modeloinicial.com.br/lei/LGPD/requisitos-o-tratamento-dados-pessoais-@____II_I) | ✅; texto do art. 12 **não lido** |
| Doação de excedentes | Lei 14.016/2020: doação de excedentes por estabelecimentos; responsabilidade penal só com dolo específico na primeira entrega (art. 4º) | [Dizer o Direito](https://www.dizerodireito.com.br/2020/06/comentarios-lei-140162020-que-autoriza.html), [Agência Senado](https://www12.senado.leg.br/noticias/materias/2020/06/24/fornecedores-de-alimentos-poderao-doar-excedentes-a-pessoas-necessitadas) | ✅ (usado só nas ideias descartadas) |

---

## Requisitos candidatos

### RC-EST-01 · Reivindicar a página e disputar a posse

- **Ator(es):** dono ou sócio do negócio (conta `ESTABELECIMENTO` ou `VAREJO`); dono atual da página; moderação
  (RF13); comensal que sugeriu o lugar.
- **Descrição:** hoje só a conta dona cria a página (RF6). Quando a comunidade puder sugerir um lugar, ou quando
  houver importação (a regra R6 da taxonomia já fala em importar do Google), a página nasce **não reivindicada** e o
  negócio precisa assumir a posse. O pedido de posse de uma página que já tem dono notifica esse dono, que tem prazo
  para responder; sem resposta, a moderação decide pela matriz da seção 7.4. A posse muda por um processo auditado,
  e nunca apaga as avaliações.
- **Critérios de aceite:**
  1. A página sem dono mostra "Este lugar ainda não foi reivindicado" e o botão "É o seu negócio?". O pedido exige
     uma conta `ESTABELECIMENTO` ou `VAREJO` `ACTIVE` e passa pelas etapas 1 a 6 da seção 7.4.
  2. Se a página já tem dono, o pedido o notifica por e-mail e no app. Ele tem **3 dias** para aceitar, recusar ou
     não responder. Sem resposta, o caso vai à moderação, nunca à transferência automática.
  3. Durante a disputa, a página não troca de dono, e nenhum dos dois apaga conteúdo do outro.
  4. Toda troca de dono grava quem pediu, as provas (só o hash do documento) e a decisão. Nos 7 dias seguintes à troca,
     o novo dono não remove outros membros nem fecha a página de vez.
  5. Reivindicar não mexe em avaliações, nota nem histórico de preço (RF8 e RF6).
- **Regras e riscos:** golpe de tomada de página por concorrente ou ex-sócio. Por isso a posse exige vínculo
  (QSA, termo de sócio, domínio) e a disputa vai para pessoa, não para robô. LGPD: guardar só o necessário (CNPJ,
  resultado e data, hash do documento), como já diz a seção 7.4, com base no art. 7º, IX e art. 10, § 1º. Filial
  tem CNPJ próprio (seção 7.4, etapa 4). Página de cozinha caseira com CPF: o pedido revela endereço residencial,
  então a prova fica só com a moderação.
- **Referências de mercado:**
  - Google: o dono atual tem 3 dias para responder ao pedido de posse; sem resposta, pode haver a opção de
    reivindicar, e a recusa admite recurso ([Google](https://support.google.com/business/answer/4566671)).
  - Uma semana de carência depois da troca, antes de remover donos ou transferir a posse (só em fonte secundária,
    **não verificado** no Google: [Thryv](https://learn.thryv.com/hc/en-us/articles/18508485314445-Claim-or-Request-Ownership-of-Your-Google-Business-Profile)).
  - Tripadvisor: reivindicação gratuita com verificação por telefone ou cartão, segundo guias de terceiros
    ([BrightLocal](https://www.brightlocal.com/learn/how-to-add-or-claim-your-tripadvisor-listing/)).
- **Prioridade:** **S**. Vira M no dia em que existir página criada pela comunidade ou por importação.
- **Depende de:** RF1, RF6, RF13, seção 7.4 da taxonomia.
- **Esforço:** M.

### RC-EST-02 · Equipe da página com papéis (dono principal, dono, gerente, social media, analista)

- **Ator(es):** dono principal; donos; gerentes; social media; analista (só leitura); moderação.
- **Descrição:** hoje a página pertence a uma única conta. Restaurantes delegam: o gerente muda o horário, a agência
  posta e o contador vê as métricas. A página passa a ter membros convidados por e-mail, cada um com um papel e com
  as permissões fechadas desse papel. Toda ação de um membro é registrada e assinada pelo **estabelecimento**, nunca
  pelo nome pessoal do membro.
- **Critérios de aceite:**
  1. Papéis e permissões:
     - `DONO_PRINCIPAL` (exatamente 1): tudo, inclusive transferir a posse e fechar de vez;
     - `DONO`: o mesmo, menos remover o dono principal;
     - `GERENTE`: edita dados, horários, cardápio e links, publica e responde;
     - `SOCIAL`: só publica posts e responde a comentários;
     - `ANALISTA`: só vê métricas e exporta.
  2. O convite expira em 7 dias. O convidado precisa ter conta própria, `ACTIVE`, com 2FA ligado (RF4) para os papéis
     `DONO` e `GERENTE`.
  3. A conta pessoal de um membro (comensal ou criador) **não avalia** o lugar em que tem papel: vale
     `RULE_NO_INSIDER`, com o vínculo virando `REVIEW_CONFLICT`.
  4. O registro de auditoria lista quem fez o quê e quando, e só donos o veem. Remover um membro corta o acesso na hora
     e invalida as sessões dele nessa página.
  5. Limite de 15 membros por página na v1.
- **Regras e riscos:** LGPD: o membro é titular. O nome dele aparece só para a equipe, e o público vê a marca.
  Funcionário que sai da empresa com acesso é risco real; por isso há expiração, 2FA e remoção imediata. O membro de
  equipe não pode ganhar peso de avaliação.
- **Referências de mercado:**
  - Google: dono principal, dono e gerente. Só o dono adiciona ou remove usuários, e o gerente edita, posta e responde
    ([Google](https://support.google.com/business/answer/3403100)).
  - Yelp: convite por e-mail com escolha das unidades; até 15 usuários por perfil, segundo fonte secundária
    **não verificada** ([Leadsie](https://www.leadsie.com/blog/give-request-access-yelp)).
- **Prioridade:** **M**. Sem papéis, o dono compartilha a senha, o que contradiz a sessão com 2FA do RF4.
- **Depende de:** RF1, RF4, RF6, RF8 (`RULE_NO_INSIDER`).
- **Esforço:** M.

### RC-EST-03 · Marca e unidades (redes e filiais)

- **Ator(es):** dono da marca (rede, franquia, marca de varejo); gerente de unidade; comensal.
- **Descrição:** uma marca com várias unidades ganha uma **página de marca** que agrupa as páginas de unidade. Cada
  unidade mantém endereço, horário, nota e CNPJ de filial próprios. A marca define dados comuns (logo, culinária,
  cardápio-base) que as unidades herdam e podem sobrescrever. A nota **nunca** é somada entre unidades.
- **Critérios de aceite:**
  1. Uma página de unidade pertence a no máximo uma marca, e o vínculo exige aceite dos dois lados (dono da marca e
     dono da unidade, no caso de franquia).
  2. A página da marca lista as unidades com mapa e nota de cada uma; não existe "nota da marca" agregada.
  3. Campo herdado mostra a origem ("definido pela marca"). A unidade sobrescreve campo a campo.
  4. A marca com 10 ou mais unidades pode pedir **verificação em lote**: a matriz CNPJ (8 primeiros dígitos) igual nas
     filiais libera as etapas 1 a 3 da seção 7.4, mas a posse do endereço (etapa 4) continua por unidade.
  5. O papel da marca (RC-EST-02) desce para as unidades, e um gerente pode ter papel só numa unidade.
- **Regras e riscos:** franquia não é filial (CNPJ diferente): o vínculo é contratual e tem de ser aceito pelos dois.
  A marca não pode esconder unidade mal avaliada nem mover avaliações entre unidades (CDC art. 6º, III). Um selo da
  marca não vale como selo da unidade.
- **Referências de mercado:**
  - Google: grupo de locais (conta de empresa) e verificação em lote com 10 ou mais locais da mesma empresa
    ([Google](https://support.google.com/business/answer/4490296),
    [BrightLocal](https://www.brightlocal.com/learn/verifying-google-business-profile-for-multiple-locations/)).
  - Cozinhas virtuais: marca distinta, endereço oculto quando só entrega
    ([Whitespark](https://whitespark.ca/blog/are-virtual-kitchens-eligible-for-a-google-business-profile/)).
  - Tripadvisor: painel com várias unidades (fonte de fornecedor, **não verificado**:
    [Local Falcon](https://www.localfalcon.com/blog/how-to-claim-a-tripadvisor-listing-everything-local-businesses-need-to-know)).
- **Prioridade:** **S**. Redes como Outback já usam fila digital ([Baguete](https://www.baguete.com.br/noticias/outback-adota-fila-de-espera-virtual)) e serão clientes B2B, mas o produto funciona sem isso no começo.
- **Depende de:** RF1, RF6, RC-EST-02, seção 7.4.
- **Esforço:** G.

### RC-EST-04 · Horários de funcionamento, feriados e "fechado temporariamente"

- **Ator(es):** dono ou gerente; comensal; sistema (lembrete de feriado); comunidade (sugestão "estava fechado").
- **Descrição:** a página ganha o horário regular por dia da semana, com mais de uma faixa por dia (almoço e jantar),
  os **horários especiais** por data (feriado, evento) e os **horários adicionais** (cozinha, happy hour, delivery,
  retirada) contidos no horário principal. O estado `TEMPORARIAMENTE_FECHADO` (com data prevista de volta opcional)
  se soma ao `ACTIVE` e ao `CLOSED` do RF6. O "aberto agora" usa o fuso do município.
- **Critérios de aceite:**
  1. Horário por dia com até 3 faixas; faixa que passa da meia-noite (18h às 2h) pertence ao dia em que começa. Sem
     horário cadastrado, a página mostra "Horário não informado", nunca "Fechado".
  2. Horário especial por data substitui o regular naquele dia. Para fechar por **mais de 7 dias seguidos**, o sistema
     pede `TEMPORARIAMENTE_FECHADO` em vez de 7 horários especiais.
  3. 14 dias antes de cada feriado nacional, e dos estaduais e municipais da UF e do município da página (lista mantida
     pela plataforma), o dono recebe o pedido "Confirme o horário de <feriado>".
  4. `TEMPORARIAMENTE_FECHADO` tira a página dos resultados com filtro "aberto agora" (RF10), mas a mantém na busca
     por nome com o rótulo; ao passar a data de volta sem ação do dono, vira "Pode estar fechado" e pede confirmação.
  5. Três ou mais comensais distintos dizendo "Estava fechado" no horário publicado, em 14 dias, geram aviso ao dono
     e à moderação, sem mudar o estado sozinhos.
  6. As facetas de refeição da seção 8.2 (café da manhã, almoço, jantar, madrugada) passam a derivar deste horário.
- **Regras e riscos:** horário errado é informação inadequada ao consumidor (CDC art. 6º, III). Concorrente pode
  marcar "estava fechado" em massa: conta só de conta `ACTIVE`, uma por pessoa e por dia, com limiar. O selo e a
  reverificação do CNPJ (seção 7.4, etapa 7) continuam decidindo o "pode estar fechado" por motivo cadastral.
  Acessibilidade: o estado é dito em texto ("Aberto agora, fecha às 23h"), nunca só por cor.
- **Referências de mercado:**
  - Google: horário especial para fechamento de até 7 dias, "Temporariamente fechado" acima disso e "Permanentemente
    fechado" ([Google](https://support.google.com/business/answer/15300196)).
  - Google: "Mais horários" como subconjunto do principal (happy hour, delivery, cozinha e outros), sem tipo
    personalizado ([Google](https://support.google.com/business/answer/9876800)).
  - Google: lembrete de feriado, segundo fonte secundária
    ([BrightLocal](https://www.brightlocal.com/learn/google-business-profile-opening-hours/)).
  - Rappi: o cadastro exige horário, e o aliado pede inativação temporária da loja
    ([Rappi Partners](https://merchants.rappi.com/es-co/que-es-plataforma-partners-rappi)).
- **Prioridade:** **M**. É o dado operacional mais consultado e já está implícito nas facetas 8.2.
- **Depende de:** RF6, RF10, RF11.
- **Esforço:** M.

### RC-EST-05 · Cobranças declaradas: taxa de serviço, couvert, consumação e desconto por forma de pagamento

- **Ator(es):** dono ou gerente; comensal; moderação.
- **Descrição:** a seção 8.5 da taxonomia reserva os códigos `TAXA_SERVICO`, `COUVERT` e `SINAL_RESERVA`, mas sem
  regra. Este RC define o que a página mostra e o que o dono **não** pode declarar: taxa de serviço sempre como
  "opcional", couvert artístico só com apresentação ao vivo, valor e dias, e desconto por Pix ou dinheiro de forma
  visível. Assim a página vira o "aviso prévio" digital que o CDC pede, sem substituir o aviso no local.
- **Critérios de aceite:**
  1. `TAXA_SERVICO`: percentual de 0 a 20%, e o texto público é fixo: "Taxa de serviço de N% (opcional)". Não existe
     a opção "obrigatória".
  2. `COUVERT`: tipo `ARTISTICO` (exige dias ou eventos com apresentação **ao vivo** e o valor por pessoa) ou
     `ENTRADA_MESA` (pão, petiscos), com o texto "cobrado só se você aceitar". Couvert artístico ligado a um `EVENTO`
     (RF9) aparece no post.
  3. `CONSUMACAO_MINIMA` não é aceita: a API recusa com `422` e a mensagem que explica o motivo (orientação de Procon).
  4. Desconto por forma de pagamento (Pix, dinheiro) aparece ao lado do preço, como "R$ 50 à vista; 5% de desconto no
     Pix" (Lei 10.962/2004, art. 5º-A).
  5. Toda cobrança declarada mostra "declarado pelo estabelecimento em <data>". Um comensal pode marcar "cobrança
     diferente da página" na avaliação (RF8), e isso alimenta a moderação.
- **Regras e riscos:** CDC art. 6º, III e art. 31 (informação prévia), art. 39, V (vantagem excessiva), art. 42,
  parágrafo único (devolução em dobro). Lei 13.419/2017 e CLT art. 457: a taxa é gorjeta e opcional ao cliente. A
  página **não** substitui a afixação física (Decreto 5.903/2006, art. 8º, § 2º), e o texto de ajuda deve dizer isso
  ao dono. Risco de a plataforma parecer endossar uma cobrança irregular, mitigado pelos textos fixos.
- **Referências de mercado:**
  - Procon-ES e Procon Santo André: a taxa é opcional e vem discriminada
    ([Folha Vitória](https://www.folhavitoria.com.br/economia/consumacao-minima-e-10-de-gorjeta-sao-cobrancas-abusivas-diz-procon/),
    [ABC do ABC](https://abcdoabc.com.br/procon-santo-andre-orienta-sobre-diretos-do-consumidor-em-bares-e-restaurantes/)).
  - Idec: couvert artístico só ao vivo e com aviso na entrada
    ([Idec](https://idec.org.br/idec-na-imprensa/11-direitos-que-voce-tem-em-restaurantes-e-bares-e-talvez-nao-saiba)).
  - Não encontrei plataforma estrangeira com esse campo; é uma adaptação brasileira.
- **Prioridade:** **M**. A faceta `MUSICA_AO_VIVO` (8.3) já promete "aviso de couvert", e sem regra vira texto livre
  arriscado.
- **Depende de:** RF6, RF9 (`EVENTO`), RF11, RF8.
- **Esforço:** P.

### RC-EST-06 · Cardápio digital operacional: QR de mesa e porta, disponibilidade e cardápios por horário

- **Ator(es):** dono, gerente ou equipe do salão; comensal no local; comensal remoto.
- **Descrição:** é uma extensão do cardápio por item já pendente, sem repetir item, preço nem alergênico. O cardápio
  ganha:
  - **seções com horário** (café, almoço, jantar), ligadas ao RC-EST-04;
  - **disponibilidade em tempo real** ("esgotado hoje"), que o salão liga e desliga com um toque;
  - **QR codes** impressos para a mesa e para a porta, que abrem a página certa (sem pedido nem pagamento);
  - um **"preço na porta"** gerado do cardápio, que atende ao art. 8º, § 2º do Decreto 5.903/2006 no meio digital.
- **Critérios de aceite:**
  1. O QR leva a uma URL curta e estável (`/c/{codigo}`), com o identificador da mesa opcional, e não exige login nem
     instalar app.
  2. Um item marcado `ESGOTADO` some do "disponível agora", mas continua visível e riscado, com o texto "esgotado hoje";
     à meia-noite (fuso do município) volta a disponível, salvo se o dono marcar `INDISPONIVEL` (fixo).
  3. Cada preço aparece com o total **à vista** em R$, com tamanho de letra uniforme, e meia porção com preço e
     quantidade próprios. Itens iguais não podem ter preços diferentes no mesmo cardápio (Decreto 5.903/2006, art. 3º
     e art. 9º, I, VI e VII).
  4. Com o cardápio estruturado, o preço derivado `PRICE_SRC_MENU` (seção 9) passa a valer.
  5. A leitura do QR conta como métrica (RC-EST-08), só em agregado, sem identificar a pessoa.
- **Regras e riscos:** leis municipais e estaduais exigem cardápio **físico** além do QR (Rio, Lei 8.224/2023;
  Contagem, Lei 5.350/2023), e a tela de QR precisa avisar o dono disso. O QR não deve virar canal de pedido com
  pagamento (fora do escopo). Phishing: QR adulterado colado na mesa, então a URL é sempre do domínio da plataforma e
  a página mostra o nome do lugar no topo.
- **Referências de mercado:**
  - Goomer: QR na mesa, no balcão ou na comanda; a versão gratuita só mostra o cardápio com descrição, preço e foto
    ([Saipos](https://saipos.com/cardapio-digital/goomer),
    [Baguete](https://www.baguete.com.br/noticias/heineken-tem-cardapio-digital-com-goomer)).
  - Anota AI: cardápio digital com QR e robô no WhatsApp
    ([Gironews](https://gironews.com/food-service/melhores-plataformas-de-cardapio-digital-para-restaurante-no-brasil/)).
  - 99Food: link da loja e QR para divulgar
    ([99Food](https://99app.com/99food/restaurantes/guias/como-compartilhar-sua-loja/)).
  - Google: editor de cardápio com seções, descrição e preço
    ([Google](https://support.google.com/business/answer/9455840));
    API com informação nutricional e porção ([Google Developers](https://developers.google.com/my-business/content/update-food-menus)).
  - Menu Dino: **não verificado** (não encontrei fonte sobre o produto).
- **Prioridade:** **S**. Dá valor diário ao dono e completa o `PRICE_SRC_MENU`, mas depende do cardápio por item.
- **Depende de:** cardápio por item (pendente), RF6, RF11, RC-EST-04.
- **Esforço:** M.

### RC-EST-07 · Cardápio acessível e versão para impressão (fonte ampliada, braille e áudio)

- **Ator(es):** dono; comensal com deficiência visual ou baixa visão; comensal idoso; equipe do salão.
- **Descrição:** do mesmo cardápio estruturado saem a versão web acessível (leitor de tela, zoom de 200%, contraste) e
  uma **versão para impressão** em fonte ampliada, um arquivo de texto pronto para gráfica braille e um **áudio**
  gerado por voz sintética. O dono cumpre leis locais de cardápio físico e braille sem refazer o cardápio, e a
  faceta `CARDAPIO_BRAILLE` (seção 8.4) passa a ter evidência.
- **Critérios de aceite:**
  1. O cardápio web passa no axe sem violação e segue WCAG 2.1 AA: preço lido como "cinquenta reais", alergênico lido
     por extenso e nenhum dado só em imagem.
  2. A exportação gera PDF com fonte ampliada (corpo de 18 pt ou mais) e um `.txt` estruturado (seção, item, preço,
     alergênicos) para transcrição em braille.
  3. O áudio do cardápio é gerado sob demanda, com duração e índice por seção.
  4. A faceta `CARDAPIO_BRAILLE` só fica "Sim" com a declaração do dono **e** a confirmação de ao menos um visitante;
     antes disso, mostra "declarado pelo estabelecimento".
  5. A página mostra "Peça o cardápio impresso ao atendente" quando o município da página tem lei de cardápio físico
     (lista mantida pela plataforma).
- **Regras e riscos:** LBI art. 63 (sítios acessíveis, sem regulamentação técnica) e o PL 981/2022 para apps; PB Lei
  7.776/2005; PE Lei 13.401/2008, alterada pelas Leis 15.636/2015 e 16.656/2019, que admitem áudio como alternativa. O
  `.txt` não é braille impresso: o texto de ajuda não pode prometer conformidade legal.
- **Referências de mercado:** não encontrei plataforma que gere braille ou áudio do cardápio (lacuna de mercado).
  O Guiaderodas avalia a acessibilidade de locais por questionário colaborativo
  ([Catraca Livre](https://catracalivre.com.br/cidadania/app-guia-de-rodas-classifica-locais-com-acessibilidade/)).
- **Prioridade:** **S**. O custo é baixo depois do RC-EST-06, há obrigação legal em alguns estados, e o design system
  já exige acessibilidade.
- **Depende de:** cardápio por item (pendente), RC-EST-06, RF11.
- **Esforço:** M.

### RC-EST-08 · Painel de métricas do estabelecimento, com privacidade

- **Ator(es):** dono, gerente e analista (RC-EST-02); dono da marca (RC-EST-03).
- **Descrição:** um painel com visualizações da página, aparições na busca, cliques em rota, telefone, site,
  WhatsApp, reserva e delivery (RC-EST-09), leituras de QR (RC-EST-06), salvamentos (RF12) e a **tendência da nota
  bayesiana** (RF8) por mês. Os dados são só agregados, com limiar mínimo, e o painel não identifica quem viu,
  salvou ou clicou.
- **Critérios de aceite:**
  1. Séries diárias e mensais dos últimos 18 meses, exportáveis em CSV (RC-EST-15).
  2. Todo número abaixo de **10** pessoas distintas no período aparece como "menos de 10". Termos de busca só aparecem
     quando 10 pessoas ou mais os usaram.
  3. Não há lista de quem salvou, viu ou clicou. Seguidores ficam fora do painel; só a contagem aparece.
  4. A tendência da nota mostra a média bayesiana mês a mês e o histograma; a moderação (RF13) pode anotar um mês
     ("pico de avaliações sob revisão").
  5. Clique registrado com dedupe por sessão e por hora; tráfego de robô fica de fora.
- **Regras e riscos:** LGPD: as métricas por pessoa usam legítimo interesse (art. 7º, IX), com o mínimo necessário
  (art. 10, § 1º) e transparência na política (art. 10, § 2º). O dono recebe só dado agregado acima do limiar, como o
  Google Ads faz com termos de busca. Perfil de menor de 16 (RF3) não entra em nenhuma contagem segmentada. Risco:
  segmentar por bairro ou por idade num lugar pequeno reidentifica a pessoa, então na v1 não há segmentação
  demográfica.
- **Referências de mercado:**
  - Google: chamadas, cliques no site, rotas e reservas
    ([Google](https://support.google.com/business/answer/9918094)); até 6 meses de dados
    ([BrightLocal](https://www.brightlocal.com/learn/google-business-profile-insights/)).
  - Chamadas subcontadas, porque só o toque no ícone é medido
    ([Whitespark](https://whitespark.ca/google-business-profile-guide/gbp-performance/)).
  - Google Ads: termos abaixo do limiar de privacidade agrupados em "Outros"
    ([Google Ads](https://support.google.com/google-ads/answer/11127882)).
  - iFood: vendas, ticket médio e avaliações no gestor (pedidos, fora do nosso escopo)
    ([Saipos](https://saipos.com/ifood/extranet-ifood)).
  - Rappi: disponibilidade no horário, cancelamentos e reclamações
    ([Rappi](https://merchants.rappi.com/es-mx/en-que-benefician-a-mi-restaurante-las-metricas-que-ofrece-la-app-de-rappi-partners)).
- **Prioridade:** **S**. É o principal motivo para o dono manter a página atualizada, mas não bloqueia o lançamento.
- **Depende de:** RF6, RF8, RF10, RF12, RF3, RC-EST-02.
- **Esforço:** G (eventos, agregação e limiar).

### RC-EST-09 · Botões de ação: reserva, delivery, WhatsApp e site (deep links, sem pagamento)

- **Ator(es):** dono ou gerente; comensal; plataforma parceira (iFood, Rappi, aiqfome, 99Food, Get In, Tagme, TheFork,
  OpenTable) apenas como destino do link.
- **Descrição:** a página ganha até 6 botões de ação com destino externo: **Reservar**, **Pedir delivery** (um por
  app, com um preferido), **WhatsApp**, **Ligar**, **Site** e **Rota**. A plataforma não processa reserva nem
  pagamento; só valida o link, abre o app quando ele está instalado e conta o clique para o RC-EST-08. A faceta
  `DELIVERY_APP` (8.1) passa a derivar dos links.
- **Critérios de aceite:**
  1. Cada link de app é validado contra uma lista de domínios permitidos por provedor (ex.: `ifood.com.br`), com HTTPS.
     Domínio fora da lista exige revisão.
  2. O dono marca um provedor de delivery como **preferido**, e ele aparece primeiro; os demais seguem em ordem
     aleatória estável por dia (sem favorecer quem paga).
  3. O botão WhatsApp usa `https://wa.me/<E.164>` com texto inicial opcional, sem o número pessoal de quem clica.
  4. Link quebrado (HTTP 4xx ou 5xx em 3 verificações seguidas) some e avisa o dono.
  5. Qualquer comensal pode marcar "link errado"; com 3 marcações distintas, o caso vai ao dono e à moderação.
  6. O texto fixo diz "Você vai sair do Friendly Eats. Pedido e pagamento são com <provedor>."
- **Regras e riscos:** não intermediar pagamento evita o papel de fornecedor solidário na venda (CDC) e as regras de
  arranjo de pagamento. Risco de link de golpe (falso iFood): por isso a lista de domínios. Decreto 7.962/2013: a
  identificação do fornecedor fica no site de destino, mas a página do estabelecimento já mostra CNPJ ou cidade (RF6).
  Link de delivery de bebida alcoólica em página 18+ segue o bloqueio do RF6.
- **Referências de mercado:**
  - Google: provedores de pedido autorizados entram sozinhos, o dono adiciona links próprios e escolhe um preferido,
    que aparece primeiro; o provedor tem 5 dias para remover o link a pedido do dono
    ([Google](https://support.google.com/business/answer/10842217)).
  - iFood: botão "pedir comida" no Instagram e no Facebook, que leva à loja no app
    ([Gironews](https://gironews.com/food-service/botao-para-pedir-comida-66994/)).
  - Yelp: DoorDash como parceiro de delivery preferido desde 1/10/2025
    ([Business Wire](https://www.businesswire.com/news/home/20250930976549/en)).
  - TheFork Manager: centraliza reservas vindas do TheFork, do Google, do Tripadvisor e do site
    ([Capterra](https://www.capterra.com/p/10036136/TheFork-Manager/)).
- **Prioridade:** **M**. O custo é baixo e é o principal "próximo passo" de quem acha um lugar; a faceta 8.1 já supõe
  esse dado.
- **Depende de:** RF6, RF11, RC-EST-08.
- **Esforço:** P.

### RC-EST-10 · Fila de espera virtual

- **Ator(es):** equipe do salão (gerente); comensal remoto; comensal no local (sem conta, por QR).
- **Descrição:** o estabelecimento abre a fila do dia, e o comensal entra pelo app ou pelo QR da porta (RC-EST-06)
  com nome e tamanho do grupo. Ele acompanha a posição e é avisado quando a mesa fica pronta. A equipe chama,
  marca "sentou" ou "não veio" e fecha a fila. Sem sinal, sem pagamento e sem reserva com hora marcada.
- **Critérios de aceite:**
  1. Entrar na fila exige nome (ou apelido) e tamanho do grupo; o telefone é opcional e serve só para o aviso.
  2. A posição e a estimativa de espera atualizam em até 30 s. A estimativa mostra "aprox." e nunca promete horário.
  3. Aviso "sua mesa está pronta" por push e, sem app, por SMS ou WhatsApp com consentimento expresso. O comensal
     responde "estou chegando" ou "desistir".
  4. Se o comensal não aparecer em 10 minutos (configurável), a equipe pode tirá-lo da fila. Três "não veio" em 30 dias
     limitam a entrada remota da pessoa nesse lugar por 7 dias.
  5. A fila **exclui** o telefone e o nome 24 h depois de fechar; o agregado (tempo médio de espera) vai para o
     RC-EST-08.
  6. Atendimento prioritário (Lei 10.048/2000, faceta fixa da seção 8.4) aparece como opção que a equipe aplica no
     local, sem o comensal ter de declarar deficiência no app.
- **Regras e riscos:** LGPD: telefone só para a finalidade do aviso (art. 6º, I e III) e eliminado logo depois.
  Nunca pedir dado de saúde ou deficiência no app. Fila falsa de concorrente: limite por conta e por dispositivo, e o
  QR do local dá prioridade a quem está presente. Custo de SMS: decidir quem paga (fora do escopo deste RC).
- **Referências de mercado:**
  - Yelp Waitlist: tempo de espera ao vivo, entrada remota, SMS com link e aviso de mesa pronta
    ([Yelp](https://yelp-support.force.com/article/What-is-Yelp-Waitlist)).
  - Resy Notify: lista para quando está lotado, aviso por push e e-mail, sem garantia de mesa
    ([Resy](https://helpdesk.resy.com/what-is-notify-and-how-does-it-work-BJrJzPQLu)).
  - Get In: fila em tempo real e entrada a distância, presente em mais de 10 mil restaurantes, segundo a loja de apps
    ([App Store](https://apps.apple.com/BR/app/id919815190)).
  - Tagme: fila remota com aviso por SMS; o widget responde por cerca de 70% das entradas digitais em SP
    ([Mobile Time](https://www.mobiletime.com.br/tag/tagme/)).
- **Prioridade:** **C**. Get In e Tagme já dominam o recurso; a integração por link (RC-EST-09) cobre o básico.
- **Depende de:** RF4, RF3 (consentimento do contato), RF6, RC-EST-04, RC-EST-06.
- **Esforço:** G (tempo real, SMS e operação no salão).

### RC-EST-11 · Cupons e ofertas com regulamento (extensão do post `PROMOCAO`)

- **Ator(es):** dono, gerente ou social media; comensal; equipe do caixa (validação); moderação.
- **Descrição:** o post `PROMOCAO` (RF9) já tem vigência e "de/por". Este RC acrescenta o **cupom**: código, limite
  total e por pessoa, regulamento obrigatório, dias e horários válidos, canais (salão, retirada, delivery) e
  **resgate** com código único por pessoa, que a equipe valida no caixa. O texto do regulamento é estruturado, para
  que nenhuma condição essencial fique escondida.
- **Critérios de aceite:**
  1. Campos obrigatórios: o que se ganha, a vigência (início e fim com hora), as unidades válidas (RC-EST-03) e os
     canais. Campos condicionais: limite total ("até 100 cupons"), limite por pessoa, gasto mínimo e dias e horários.
  2. Toda condição aparece **antes** de pegar o cupom, no card e na página; o regulamento não fica atrás de um link.
  3. Quando o limite total esgota, o card mostra "Esgotado" na hora. O cupom já pego segue válido até o fim da
     vigência.
  4. Encerrar antes do prazo só vale para quem ainda não pegou; os cupons já emitidos são honrados (CDC art. 30 e 35).
  5. O resgate gera um código de 6 caracteres válido por 10 minutos, e a equipe confirma pelo app da página
     (RC-EST-02). O comensal pode reclamar "cupom recusado", e a reclamação vai ao dono e à moderação.
  6. Cupom não pode ser condicionado a avaliar, a dar nota alta nem a apagar avaliação (ver RC-EST-14).
- **Regras e riscos:** CDC art. 30 (oferta vincula), art. 31 (informação clara), art. 35 (recusa), art. 37, § 1º
  (enganosa por omissão: limite escondido); Lei 10.962/2004, art. 5º-A (desconto por forma de pagamento visível).
  Promoção de bebida alcoólica segue o 18+ do RF6 e do RF9. Sorteio ou "vale-brinde" com álea depende de autorização
  (Lei 5.768/1971, **não verificado** nesta pesquisa), por isso fica fora.
- **Referências de mercado:**
  - Google: oferta com título, datas, código, link de resgate e termos
    ([StoryChief](https://help.storychief.io/en/articles/11026851-publish-to-google-business-profile); fonte de
    terceiros, campos oficiais **não verificados**).
  - Yelp Check-in Offers: desconto em %, valor fixo ou preço fixo; a equipe marca como usado para evitar o resgate
    duplo ([Booksy](https://biz.booksy.com/en-us/blog/how-to-use-yelp), [ClickZ, 2010](https://clickz.com/?p=53705)).
  - aiqfome: o restaurante cria promoções exclusivas, e há cupons pagos pela plataforma (fonte de fornecedor)
    ([Saipos](https://saipos.com/aiqfome/cupom-aiqfome)).
  - Toast: ofertas promocionais na suíte de marketing
    ([Toast](https://pos.toasttab.com/news/toast-launches-digital-storefront-and-marketing-suites)).
- **Prioridade:** **S**. Gera receita B2B e engajamento, e a regra do CDC é bem definida.
- **Depende de:** RF9 (`PROMOCAO`), RF6, RC-EST-02, RC-EST-03.
- **Esforço:** M.

### RC-EST-12 · Programa de fidelidade (cartão de selos)

- **Ator(es):** dono; equipe do caixa; comensal.
- **Descrição:** o estabelecimento cria um cartão de selos simples ("10 cafés, ganha 1"), e o comensal ganha um selo
  quando a equipe lê um **QR dinâmico** no app dele, ou ele lê o QR do caixa, que muda a cada 30 s. Não há integração
  com o PDV nem pontos por valor gasto na v1. O programa tem regulamento, validade dos selos e encerramento com aviso.
- **Critérios de aceite:**
  1. O regulamento traz a recompensa, os selos necessários, a validade dos selos (de 3 a 24 meses) e as unidades
     válidas; ele aparece antes da adesão.
  2. Um selo por pessoa e por visita (mínimo de 4 h entre selos no mesmo lugar). O QR do caixa expira em 30 s e só
     vale com a localização a até 300 m do ponto **ou** com a leitura feita pela equipe.
  3. Encerrar o programa exige aviso com 30 dias; os cartões completos até a data são honrados.
  4. O comensal vê os próprios cartões e sai do programa quando quiser. O dono vê a contagem de participantes e de
     resgates, nunca a lista nominal, salvo com consentimento específico do comensal para receber comunicações.
  5. Selo e recompensa **nunca** dependem de avaliação, post ou nota (Google e Yelp proíbem o incentivo).
- **Regras e riscos:** CDC art. 30 (o regulamento vincula). LGPD: a localização é usada só no ato (RF3,
  `LOCATION_TRACKING`) e não é guardada. A frequência de visita é dado comportamental, então nada de perfilamento para
  terceiros. Fraude de selo: QR rotativo, limite por pessoa e por visita.
- **Referências de mercado:**
  - Square Loyalty: adesão pelo telefone, regras do lojista, recompensa em desconto ou item grátis
    ([Square](https://squareup.com/help/article/5744), [Square FAQ](https://squareup.com/help/article/6462)).
  - Toast: acúmulo e resgate de fidelidade na suíte de marketing
    ([Toast](https://pos.toasttab.com/news/toast-launches-digital-storefront-and-marketing-suites)).
  - iFood: participação de 20% na CRMBonus (cashback e giftback), sujeita a aprovação regulatória
    ([Mercado & Consumo](https://mercadoeconsumo.com.br/29/07/2025/foodservice/ifood-anuncia-compra-de-20-da-crmbonus-com-foco-em-fidelizacao-via-ia/)).
- **Prioridade:** **C**. Atrai o pequeno dono, mas compete com PDV e iFood e traz risco de fraude.
- **Depende de:** RF4, RF3, RF6, RC-EST-02, RC-EST-03.
- **Esforço:** M.

### RC-EST-13 · Eventos com ingresso externo, lotação e meia-entrada (extensão do post `EVENTO`)

- **Ator(es):** dono, gerente ou social media; comensal; plataforma de ingressos (destino do link).
- **Descrição:** o post `EVENTO` (RF9) já tem início, fim e preço ou "gratuito". Este RC acrescenta:
  - link de **ingresso externo** com domínio validado;
  - tipo de cobrança: ingresso, couvert artístico (RC-EST-05), consumação por pessoa (menu fechado) ou gratuito;
  - lotação e "esgotado";
  - classificação 18+ do próprio evento;
  - a nota de **meia-entrada** quando o evento é espetáculo e há ingresso;
  - "salvar na agenda" (`.ics`).
- **Critérios de aceite:**
  1. Evento com cobrança `INGRESSO` exige um link HTTPS de domínio permitido (ou revisado) e mostra "a venda é feita
     por <domínio>".
  2. Com `INGRESSO` e categoria `SHOW`, `TEATRO` ou `ESPORTE`, o post mostra o texto fixo "Meia-entrada conforme a Lei
     12.933/2013, nas condições da bilheteria". Jantar ou degustação com preço fixo não mostra meia-entrada.
  3. Couvert artístico ligado ao evento aparece com o valor no card, antes do clique.
  4. "Esgotado" e "cancelado" são estados do evento; um evento cancelado notifica quem salvou (RF12) e continua
     visível até a data, riscado.
  5. O `.ics` traz o nome, o endereço público (respeitando a visibilidade do RF6) e o link da página.
- **Regras e riscos:** Lei 12.933/2013 (meia-entrada em espetáculos e eventos de lazer e entretenimento, cota de 40%);
  a refeição não tem meia-entrada. A aplicação a show em restaurante sem ingresso (só couvert) **não foi verificada**,
  por isso o texto remete à bilheteria. CDC art. 6º, III e art. 31. Evento de bebida alcoólica é 18+ (RF9).
- **Referências de mercado:**
  - OpenTable Experiences: menu degustação, mesa do chef e evento com ingresso, pré-pago ou pago no local; o
    ingresso não depende da disponibilidade de mesa
    ([OpenTable](https://opentable.com/restaurant-solutions/products/features/experiences)).
  - Google: posts de evento com datas (campos por fonte de terceiros, **não verificado**:
    [Publer](https://publer.com/docs/posting/create-posts/content-types/platform-specific-formats/google-business-profiles-updates-photos-events-and-offers)).
- **Prioridade:** **C**. O post `EVENTO` já cobre o essencial; isto melhora a conversão e a conformidade.
- **Depende de:** RF9 (`EVENTO`), RF12, RC-EST-05, RC-EST-09.
- **Esforço:** P.

### RC-EST-14 · Contestação de avaliação pelo dono, sem retaliação e sem incentivo

- **Ator(es):** dono, gerente; autor da avaliação; moderação (RF13); comensal que lê a página.
- **Descrição:** a resposta pública do dono (`RULE_OWNER_REPLY`) já está pendente e não é repetida aqui. Este RC trata
  do que fica **em volta** dela:
  - um fluxo de **contestação** para o dono pedir revisão de uma avaliação por motivo fechado (não é cliente,
    conflito de interesse, conteúdo proibido, extorsão), com prova;
  - regras que impedem retaliação (ameaça, exposição de dados, oferta de vantagem para apagar);
  - um **alerta ao consumidor** na página quando a moderação confirma incentivo ou ameaça jurídica.
- **Critérios de aceite:**
  1. A contestação tem motivo de lista fechada e texto até 1.000 caracteres. A avaliação continua no ar e conta na nota
     até a decisão da moderação (nunca some por pedido do dono).
  2. Uma contestação por avaliação; o resultado é comunicado aos dois lados, com um recurso possível.
  3. A resposta e a contestação são recusadas se trouxerem nome completo, telefone, endereço ou dados da comanda do
     autor, ameaça de processo, oferta de brinde ou dinheiro para mudar ou apagar a avaliação, ou acusação de fraude
     em público (o lugar disso é a contestação).
  4. A moderação pode marcar a página com um alerta temporário: `INCENTIVO_COMPROVADO` ("este lugar ofereceu vantagem
     por avaliações") ou `AMEACA_JURIDICA` ("este lugar ameaçou avaliadores com processo"), por 90 dias, com histórico
     público.
  5. O painel do dono (RC-EST-08) **não** mostra quem avaliou além do que já é público na avaliação.
  6. Pedir avaliação só para cliente satisfeito (*review gating*) e cota de avaliações para a equipe violam os
     Termos; a denúncia vai a RF13.
- **Regras e riscos:** CDC art. 6º, III; Marco Civil e liberdade de expressão do avaliador; a LGPD protege o autor
  contra a exposição de dados da comanda. Risco de a plataforma ser usada para intimidar: o alerta só sai com prova
  analisada por pessoa. Risco jurídico de difamação do lugar pelo alerta: texto factual, com data e recurso.
- **Referências de mercado:**
  - Tripadvisor: a resposta não pode ameaçar nem coagir o avaliador, oferecer vantagem, acusar de fraude, especular
    sobre a identidade nem expor dados pessoais
    ([Tripadvisor](https://www.tripadvisor.com/Trust-lpgT1CmsfQ3E.html), lido pelo trecho do buscador; a página deu 403).
  - Yelp: alertas de atividade compensada, de avaliações suspeitas e de "ameaças jurídicas questionáveis", com
    histórico na página ([Yelp Trust & Safety 2024](https://www.yelp-press.com/press-releases/press-release-details/2025/Yelp-Releases-2024-Trust--Safety-Report/default.aspx),
    [ClickZ](https://www.clickz.com/yelp-targets-litigious-businesses-who-threaten-reviewers/104054/)).
  - Google: proíbe incentivo, desestimular avaliação negativa, pedir só para cliente satisfeito e pressionar no local
    ([Google](https://support.google.com/contributionpolicy/answer/7400114)); a regra de cota da equipe e do nome do
    funcionário teria entrado em 17/04/2026, data **não verificada** no Google
    ([PPC Land](https://ppc.land/google-tightens-maps-review-policy-staff-names-and-quotas-now-banned/)).
- **Prioridade:** **S**. Precisa estar pronto junto com a resposta do dono para a resposta não virar ferramenta de
  retaliação.
- **Depende de:** RF8, RF12 (`RULE_OWNER_REPLY`), RF13, RC-EST-02.
- **Esforço:** M.

### RC-EST-15 · Exportar os dados do estabelecimento

- **Ator(es):** dono principal e donos; analista (RC-EST-02).
- **Descrição:** o dono baixa um pacote com tudo o que é da página: os dados cadastrais, o histórico de horários, o
  cardápio (JSON e CSV), os posts e as fotos dele, as avaliações públicas com as respostas, as métricas agregadas
  (RC-EST-08), os cupons e os resgates agregados. É a portabilidade do lado B2B, para trocar de ferramenta ou
  auditar.
- **Critérios de aceite:**
  1. O pedido gera um arquivo assíncrono (ZIP) disponível por 7 dias, com link assinado e aviso por e-mail.
  2. As avaliações saem com o texto e a nota públicos e o **nome público** do autor, sem e-mail, sem ID interno e sem
     dado privado. Avaliações removidas pela moderação não saem.
  3. As métricas saem só agregadas, com o mesmo limiar do RC-EST-08.
  4. Um pedido por página a cada 24 h; o pedido e o download ficam na auditoria (RC-EST-02).
  5. O formato do cardápio é documentado (esquema JSON versionado) para ser importado de volta.
- **Regras e riscos:** LGPD art. 18, V: a portabilidade é direito do titular pessoa natural. Para a empresa, é
  obrigação contratual (Termos), com a ressalva dos segredos comercial e industrial da plataforma (o modelo de nota e
  os sinais antifraude não saem). As avaliações são dado pessoal dos autores: exportar só o que já é público e dentro
  da finalidade (art. 6º, I). Cozinha caseira com CPF: o pacote é do próprio titular.
- **Referências de mercado:** Google: até 6 meses de métricas para baixar
  ([BrightLocal](https://www.brightlocal.com/learn/google-business-profile-insights/)). Não encontrei exportação
  completa da página (cardápio e avaliações) nas plataformas pesquisadas: **não verificado**.
- **Prioridade:** **S**. É barato com o resto pronto e reduz o receio de prender o dono na plataforma.
- **Depende de:** RF5 (padrão de exportação e eliminação), RF6, RF8, RF9, RC-EST-02, RC-EST-08.
- **Esforço:** M.

### RC-EST-16 · Catálogo de produtos da marca e "onde encontrar" (Varejo & Marca)

- **Ator(es):** conta `VAREJO` (marca ou indústria); conta `VAREJO` (mercado, empório); comensal; conta `OFICIAL`
  (recall).
- **Descrição:** a taxonomia já anota "⏳ catálogo e onde encontrar" para a conta `VAREJO`, sem RF. A marca cadastra
  produtos (nome, foto, EAN/GTIN, categoria, alergênicos e advertências **copiados do rótulo**). Os pontos de venda
  (páginas do grupo `VAREJO`) declaram "temos este produto", e o comensal busca "onde encontrar perto de mim". Um
  alerta sanitário (RF9) que cita o EAN aparece no produto.
- **Critérios de aceite:**
  1. O produto exige nome, EAN de 8 ou 13 dígitos com dígito verificador válido e foto do rótulo. Alergênicos e
     advertências usam os códigos da seção 6.2 com o selo "conforme o rótulo, declarado pela marca".
  2. Um ponto de venda marca "temos" e a data; após 60 dias sem reconfirmação, vira "visto em <data>". Comensais
     confirmam ou negam ("não achei").
  3. A busca "onde encontrar" usa o raio do RF10 e nunca ordena por quem pagou (RC-EST-17).
  4. Um `ALERTA_SANITARIO` com o EAN ou a marca e o lote faz o produto mostrar a faixa de alerta, e os pontos de venda
     que o declararam recebem aviso.
  5. Marca e ponto de venda não avaliam o produto (`RULE_NO_INSIDER`).
- **Regras e riscos:** RDC 727/2022 vale para o produto embalado: a plataforma reproduz o rótulo e não o certifica (o
  texto de ajuda diz "confira o rótulo"). Rotulagem nutricional (RDC 429/2020, IN 75/2020) **não verificada**. CDC
  art. 31. Bebida alcoólica: produto 18+ (RF6). Publicidade de alimento infantil tem regra própria (**não
  verificado**).
- **Referências de mercado:**
  - Destini: localizador "onde comprar" por produto (UPC), com dados de varejo
    ([Destini](https://destini.co/product-locators/)); concorrentes dizem que os dados têm até 2 semanas de atraso
    ([Pear Commerce](https://inbound.pearcommerce.com/pear-vs-destini)).
  - Google: catálogo de produtos no perfil, sem detalhe verificado.
- **Prioridade:** **S**. É o principal valor da conta `VAREJO`, que hoje só cadastra o lugar.
- **Depende de:** RF1, RF6, RF7, RF9 (`ALERTA_SANITARIO`), RF10.
- **Esforço:** G.

### RC-EST-17 · Conteúdo patrocinado e anúncios separados do ranking orgânico

- **Ator(es):** dono ou gerente (anunciante); conta `VAREJO`; comensal; moderação; equipe comercial da plataforma.
- **Descrição:** antes de vender qualquer impulsionamento (o chip ⏳ **Patrocinado** da seção 14.3), estas regras
  ficam fixas:
  - o anúncio ocupa **posições reservadas** e rotuladas;
  - o pagamento nunca entra no cálculo da nota, do `WR`, do "em alta" (RF14) nem da ordem orgânica da busca (RF10);
  - o anunciante não ganha nenhum poder sobre avaliações.
- **Critérios de aceite:**
  1. Item patrocinado mostra o chip "Patrocinado" (texto, não só cor; contraste do design system), e o mesmo item não
     se repete na lista orgânica da mesma tela.
  2. No máximo 1 posição patrocinada a cada 10 resultados ou cards, e nunca na posição 1 de um filtro de alergênico ou
     de estilo alimentar sensível.
  3. Teste automatizado: com e sem campanha ativa, a ordem orgânica e a nota de todas as páginas são idênticas.
  4. O comensal vê "Por que estou vendo isto?" (anunciante, segmentação usada) e pode ocultar o anunciante.
  5. Segmentação proibida: estilos alimentares e culinárias sensíveis (seção 6.3), idade abaixo de 18 e dado de
     saúde. Anúncio de bebida alcoólica só para quem tem 18 anos ou mais.
  6. O post de criador com relação comercial continua com o chip **Publicidade** (14.3); Patrocinado e Publicidade são
     independentes.
- **Regras e riscos:** CDC art. 36 (identificação imediata), art. 37; Código CONAR art. 10. LGPD art. 11 (dado
  sensível: estilos alimentares podem revelar saúde ou religião, conforme o `CLAUDE.md`). O risco é de reputação: a
  suspeita de pagar por nota destruiu confiança no Yelp. O teste do critério 3 é a garantia verificável.
- **Referências de mercado:**
  - Yelp declara que nenhum valor pago muda avaliações ou notas, e que avaliações de anunciantes e não anunciantes são
    tratadas igual; houve processos e acusações, arquivados
    ([Smash Balloon, citando a Yelp](https://smashballoon.com/buy-yelp-reviews/),
    [FSR Magazine](https://www.fsrmagazine.com/feature/jon-taffer-yelp-lock-horns-over-review-policies/),
    [Brafton](https://www.brafton.com.au/news/yelp-yips-that-ads-will-be-separate-from-reviews-amid-lawsuit-threats-1369402/)).
  - Google Maps: pinos promovidos e anúncios acima dos orgânicos, às vezes difíceis de distinguir, segundo fonte de
    terceiros ([Shopify](https://www.shopify.com/blog/google-maps-ads)).
  - aiqfome Ads: anúncios dentro do app ([Saipos](https://saipos.com/aiqfome-ads)).
- **Prioridade:** **S**. É **M** antes da primeira venda de anúncio; sem anúncio, pode esperar.
- **Depende de:** RF10, RF14, RF8, RF3, RF9 (chip Publicidade), RF13.
- **Esforço:** G.

---

## Tabela-resumo

| RC | Título | Ator principal | MoSCoW | Esforço | Depende de |
|---|---|---|---|---|---|
| RC-EST-01 | Reivindicar a página e disputar a posse | Dono, moderação | S (M com página da comunidade) | M | RF1, RF6, RF13, tax. 7.4 |
| RC-EST-02 | Equipe da página com papéis | Dono principal | **M** | M | RF1, RF4, RF6, RF8 |
| RC-EST-03 | Marca e unidades (redes e filiais) | Dono da marca | S | G | RF1, RF6, RC-02, tax. 7.4 |
| RC-EST-04 | Horários, feriados e fechado temporariamente | Dono, gerente | **M** | M | RF6, RF10, RF11 |
| RC-EST-05 | Cobranças declaradas: taxa de serviço, couvert, desconto | Dono | **M** | P | RF6, RF8, RF9, RF11 |
| RC-EST-06 | Cardápio operacional: QR, esgotado, horários | Equipe do salão | S | M | cardápio por item, RF6, RF11, RC-04 |
| RC-EST-07 | Cardápio acessível e impresso (fonte ampliada, braille, áudio) | Dono, comensal com deficiência | S | M | cardápio por item, RC-06, RF11 |
| RC-EST-08 | Painel de métricas com privacidade | Dono, analista | S | G | RF3, RF6, RF8, RF10, RF12 |
| RC-EST-09 | Botões de ação: reserva, delivery, WhatsApp | Dono, comensal | **M** | P | RF6, RF11, RC-08 |
| RC-EST-10 | Fila de espera virtual | Equipe do salão, comensal | C | G | RF3, RF4, RF6, RC-04, RC-06 |
| RC-EST-11 | Cupons com regulamento (extensão de `PROMOCAO`) | Dono, caixa | S | M | RF9, RF6, RC-02, RC-03 |
| RC-EST-12 | Programa de fidelidade (cartão de selos) | Dono, comensal | C | M | RF3, RF4, RF6, RC-02 |
| RC-EST-13 | Eventos com ingresso externo e meia-entrada (extensão de `EVENTO`) | Dono | C | P | RF9, RF12, RC-05, RC-09 |
| RC-EST-14 | Contestação de avaliação, sem retaliação nem incentivo | Dono, moderação | S | M | RF8, RF12, RF13 |
| RC-EST-15 | Exportar os dados do estabelecimento | Dono | S | M | RF5, RF6, RF8, RF9, RC-08 |
| RC-EST-16 | Catálogo da marca e "onde encontrar" | Conta `VAREJO` | S | G | RF1, RF6, RF7, RF9, RF10 |
| RC-EST-17 | Patrocinado separado do ranking orgânico | Anunciante, comensal | S (M antes de vender anúncio) | G | RF3, RF8, RF9, RF10, RF13, RF14 |

Ordem sugerida: RC-02, RC-04, RC-05 e RC-09 (Must, baratos e destravam o resto) → RC-06, RC-07, RC-14 e RC-08 →
RC-01, RC-03, RC-11, RC-15 e RC-16 → RC-17, antes de qualquer receita de anúncio → RC-10, RC-12 e RC-13.

---

## Ideias descartadas e por quê

| Ideia | Por que ficou de fora |
|---|---|
| **Selo de acessibilidade e de "pet friendly"** como RC próprio | Já está na taxonomia (8.3 `PET_FRIENDLY`, 8.4 acessibilidade, com origem "declarado" e "confirmado por N"), com a regra "nunca 'conforme NBR 9050'", e entra no RF11. O único acréscimo novo (evidência do cardápio em braille) foi para o RC-EST-07. Nota: a Lei 11.126/2005 (cão-guia) está verificada, e a taxonomia pode tirar o "não verificado"; pet em restaurante depende de lei municipal (Santo André, Mauá), então "aceita pets" deve vir com o aviso "confira as regras locais". |
| **Mostrar alvará ou licença sanitária como selo público** | O licenciamento é municipal e estadual por risco e CNAE (Lei 13.874/2019); atividade de baixo risco pode ser dispensada, e "sem alvará" não significa irregular. Um selo induziria erro (CDC art. 37). Serve só como **prova de posse** na verificação (seção 7.4, etapa 6). |
| **Validação do CNPJ como RC novo** | Já prevista na seção 7.4 (BrasilAPI, reverificação). A pesquisa só acrescenta as fontes: a API paga do Serpro (D-1) e os dados abertos mensais da Receita como alternativa ou cópia local, com atraso de semanas na baixa. |
| **Perguntas e respostas (Q&A) do dono** | O Google desligou a API de Q&A em 3/11/2025 e trocou o recurso por respostas de IA ("Ask Maps"), segundo fontes secundárias ([BrightLocal](https://www.brightlocal.com/learn/google-business-profile-qa)). Aqui o post `PERGUNTA` (RF9), os comentários (RF12) e os dados estruturados (horário, cobranças, facetas) cobrem a necessidade. |
| **Reserva própria, pedido e pagamento no app** | Exige intermediar pagamento (responsabilidade solidária no CDC, arranjo de pagamento, antifraude) e concorre com iFood, Get In, Tagme e TheFork. Os deep links (RC-EST-09) entregam o valor sem o risco. |
| **Vale-presente (gift card)** | É pagamento antecipado e passivo financeiro do lojista; Toast e Square fazem isso integrados ao PDV ([Toast](https://pos.toasttab.com/th-us/products/gift-card)). Fora do escopo sem pagamento. |
| **Vagas de emprego** | Não é o núcleo do produto. O risco jurídico é alto (CLT art. 373-A, I; Lei 9.029/1995; art. 442-A) e exige moderação de anúncio discriminatório e LGPD de candidato (currículo). Um post livre com link externo já basta. |
| **Sacola surpresa de excedentes (estilo Too Good To Go)** | É venda com pagamento e logística. A Lei 14.016/2020 cobre a **doação** de excedentes, não a venda com desconto. Pode virar ideia para a conta `COMUNIDADE` (ponte de doação) no futuro. |
| **Marketplace de fornecedores (B2B de insumos)** | O iFood testou um marketplace de embalagens e laticínios em 2017 ([DOL](https://dol.com.br/noticias/tecnologia/noticia-401077-ifood-vai-conectar-restaurantes-a-fornecedores.html)), e não achei sinal de que continue. É outro negócio (compra, frete, nota fiscal). A parte social ("onde encontrar o produto da marca") ficou no RC-EST-16. Um selo "ingrediente de origem", com o fornecedor confirmado pelos dois lados, fica como evolução do RC-EST-16. |
| **Respostas automáticas por IA às avaliações** | Contradiz a resposta **identificada** do dono e cria risco de resposta genérica ou ofensiva em nome do lugar. No máximo, um rascunho que o dono edita, decidido no RF12. |
| **Mensagem direta (chat) do cliente com o estabelecimento** | É um canal de atendimento com moderação, assédio e LGPD próprios; o botão WhatsApp (RC-EST-09) cobre o caso, como fazem Anota AI e iFood. |
| **Nota agregada da marca (soma das unidades)** | Esconderia a unidade ruim atrás da média da rede e induziria erro (CDC art. 6º, III). O RC-EST-03 mostra a nota por unidade. |
| **Sorteios e promoções com prêmio por sorte** | Dependem de autorização (Lei 5.768/1971, **não verificado**) e o risco é alto; ficaram fora do RC-EST-11. |
| **Meia-entrada na conta do restaurante** | A Lei 12.933/2013 cobre espetáculos e eventos, não refeição. Só aparece como aviso no evento com ingresso (RC-EST-13). |
| **Informação nutricional obrigatória por prato** | Não confirmei se a RDC 429/2020 e a IN 75/2020 alcançam o prato servido (orçamento de busca esgotado). Por analogia com a RDC 727/2022, que vale para o produto embalado, a obrigação deve ficar só no produto do RC-EST-16. **Não verificado**: conferir antes de qualquer RF. |

---

## Fontes não confirmadas e pendências de verificação

- Texto do art. 9º da LBI (acessibilidade em estabelecimentos) e do art. 12 da LGPD (anonimização): não lidos.
- RDC 429/2020, IN 75/2020 e RDC 216/2004: não pesquisados (limite de buscas).
- Estado final da revisão da RDC 727/2022 (consulta pública de 2025).
- Vigência das leis de cardápio físico no Ceará e na cidade de São Paulo (PL 120/2024) e do PL estadual paulista de
  braille (PL 741/2013).
- Política de respostas do Tripadvisor: lida só pelo trecho do buscador (a página deu 403).
- Menu Dino: nenhuma fonte encontrada.
- Preço atual da API Consulta CNPJ do Serpro (o valor achado é de 2017).
- Posição de Procon ou de tribunal sobre a meia-entrada no couvert artístico de restaurante.
