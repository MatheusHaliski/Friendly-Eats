# Documentação do Friendly Eats

Cada requisito funcional (RF) tem uma pasta própria com os diagramas que descrevem o código **como ele está**. Mudou o
comportamento de um arquivo listado em `paths` de um RF? Atualize a pasta dele no mesmo commit; o job **Docs por RF**
do CI confere (`scripts/check_rf_docs.py`) e renderiza todo Mermaid de `docs/`.

## Estrutura de cada pasta

| Arquivo | Conteúdo |
|---|---|
| `README.md` | cabeçalho (`rf`, `titulo`, `status`, `cards`, `paths`), critérios de aceite, base legal e rastreabilidade |
| `casos-de-uso.md` | atores primários à esquerda, secundários à direita; «include» e «extend» |
| `atividades.md` | fluxo e validações |
| `classes.md` | classes de domínio, serviços e views envolvidos |
| `componentes.md` | controller → serviço → repositório → armazenamento |
| `sequencia.md` | chamadas principais, com os códigos HTTP |
| `estados.md` | máquina de estados do que o RF muda |

`status`: `planejado` · `parcial` · `pronto`. Nos diagramas, ✅ = no código e testado, ⏳ = planejado (linha
tracejada).

## Catálogo

| RF | Título | Status |
|---|---|---|
| [RF1](RF1/README.md) | Cadastro com tipo de conta e aceite dos Termos de Uso | 🟡 parcial |
| [RF2](RF2/README.md) | Perfil, preferências e foto de perfil | 🟡 parcial |
| [RF3](RF3/README.md) | Consentimentos e privacidade (Privacy by Default) | 🟡 parcial |
| [RF4](RF4/README.md) | Login, sessão, controle de acesso e 2FA | 🟡 parcial |
| [RF5](RF5/README.md) | Excluir conta com 30 dias para reativar e eliminação | 🟡 parcial |
| [RF6](RF6/README.md) | Cadastrar estabelecimento: tipo, local e preço declarado | 🟡 parcial |
| [RF7](RF7/README.md) | Classificar estabelecimento: culinária, formato, abordagem e estilos | 🟡 parcial |
| [RF8](RF8/README.md) | Avaliar com estrelas (1 a 5) e nota agregada | 🟡 parcial |
| [RF9](RF9/README.md) | Publicar conteúdo: posts de comida com foto, taxonomia e alergênicos | 🟡 parcial |
| [RF10](RF10/README.md) | Buscar e filtrar estabelecimentos (taxonomia, preço, estrelas e raio) | 🟡 parcial |
| [RF12](RF12/README.md) | Interações: curtir, salvar, comentar e compartilhar | 🟡 parcial |
| [RF14](RF14/README.md) | Feed e página do post, com o ranking em alta | 🟡 parcial |
| [RF15](RF15/README.md) | Perfil público e feed de perfis | 🟡 parcial |
| [RF16](RF16/README.md) | Moldura do post (estúdio de moldura) | 🟡 parcial |
| [RF17](RF17/README.md) | Verificação de idade e conta do responsável | 🟡 parcial |
| [RF18](RF18/README.md) | Central do titular e conformidade (exportar dados, encarregado, registros de acesso, incidentes) | 🟡 parcial |

Próximos (seguem a [taxonomia](TAXONOMIA_FRIENDLY_EATS.md#13-mapa-para-os-rfs)): RF11 página do estabelecimento,
com as facetas · RF13 moderação e avaliações falsas. Do RF9 e do RF12 ainda faltam o cardápio com alergênicos por item,
seguir perfis e a resposta identificada do dono.

## Outros documentos

- [Taxonomia](TAXONOMIA_FRIENDLY_EATS.md): atores, culinárias, formatos, abordagens, estilos, tipos de
  estabelecimento, preço, estrelas e local, com os códigos estáveis usados do RF6 ao RF8
- [Design System](DESIGN_SYSTEM.md): cores, glifos, chips e contraste das telas (a cor nunca é o único sinal)
- [Termos de Uso](legal/TERMOS_DE_USO.md) (rascunho com marcadores `{{…}}`, para revisão jurídica)
- [Runbook de incidentes de segurança](legal/INCIDENTES.md) (LGPD, art. 48; Resolução CD/ANPD 15/2024; RF18)
- [Pesquisa de requisitos](PESQUISA_REQUISITOS.md): 71 requisitos candidatos em quatro eixos (comunidade,
  estabelecimento, confiança e leis, receitas e IA), as obrigações legais ainda não cumpridas e a proposta do RF17 ao
  RF36, com os arquivos completos em [`pesquisa/requisitos/`](pesquisa/requisitos/)
