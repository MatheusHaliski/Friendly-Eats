
-- =============================================================================
-- V3 · posts de comida, interações e feed
--   RF9  publicar conteúdo: foto de prato, receita, pergunta, prato do dia, promoção, evento e alerta sanitário,
--        com foto, culinária, formato, estilos, alergênicos e classificação etária
--   RF12 interações: curtir, salvar, comentar e compartilhar
--   RF14 feed e página do post · RF15 perfil público com os posts do autor
--
-- `post` é o registro canônico (a timeline no Cassandra fica para depois). Os contadores (like_count, comment_count,
-- share_count) são mantidos pela aplicação na MESMA transação da interação (PostInteractionService), nunca
-- recalculados por COUNT(*) na leitura. Os vocabulários têm códigos estáveis (docs/TAXONOMIA_FRIENDLY_EATS.md) e são
-- validados na aplicação; aqui ficam os CHECKs dos valores fechados e o formato dos JSON.
-- =============================================================================

CREATE TABLE IF NOT EXISTS post (
    id                UUID          PRIMARY KEY,         -- UUIDv7 gerado na aplicação (ordenável por tempo)
    author_id         BIGINT        NOT NULL REFERENCES user_account (id),
    author_user_type  VARCHAR(16)   NOT NULL,            -- tipo de conta copiado na publicação (cor sem join)
    kind              VARCHAR(20)   NOT NULL,
    feed_type         VARCHAR(10)   NOT NULL,            -- deriva do tipo: ALERT só para o alerta sanitário
    media_type        VARCHAR(10)   NOT NULL DEFAULT 'TEXT',
    title             VARCHAR(140)  NOT NULL,
    body              VARCHAR(5000),
    media_object_key  VARCHAR(512),                      -- chave da versão limpa no S3 (posts/…)
    alt_text          VARCHAR(500),                      -- obrigatório com foto (WCAG 1.1.1)

    -- ligação com a taxonomia: lugar visível para o autor, culinária, formato, estilos e alergênicos do prato
    establishment_id  BIGINT        REFERENCES establishment (id) ON DELETE SET NULL,
    cuisine           VARCHAR(32),
    food_format       VARCHAR(32),
    dietary_styles    JSONB         NOT NULL DEFAULT '[]',
    allergens         JSONB         NOT NULL DEFAULT '{}',   -- {"LEITE": "CONTEM", "AMENDOIM": "PODE_CONTER"}
    food_warnings     JSONB         NOT NULL DEFAULT '[]',   -- advertências: LACTOSE, GLUTEN

    age_rating        VARCHAR(10)   NOT NULL DEFAULT 'LIVRE',
    advertising       BOOLEAN       NOT NULL DEFAULT FALSE,  -- chip Publicidade (CDC art. 36)

    -- vigência (promoção, evento) e quando o post sai do feed (prato do dia às 23h59 de America/Sao_Paulo)
    starts_at         TIMESTAMPTZ,
    ends_at           TIMESTAMPTZ,
    expires_at        TIMESTAMPTZ,

    -- preço em centavos: "de/por" ou desconto da promoção; preço do prato do dia ou do evento (ou gratuito)
    price_cents       INTEGER,
    price_from_cents  INTEGER,
    price_to_cents    INTEGER,
    discount_percent  INTEGER,
    free_entry        BOOLEAN,

    recipe            JSONB,                             -- {ingredients[], steps[], servings, prepMinutes}
    food_alert        JSONB,                             -- {product, brand, lots[], reason, sourceUrl}
    city              VARCHAR(120),
    background        JSONB,                             -- moldura do post (RF16); NULL = moldura padrão

    status            VARCHAR(12)   NOT NULL DEFAULT 'DRAFT',
    like_count        BIGINT        NOT NULL DEFAULT 0,
    comment_count     BIGINT        NOT NULL DEFAULT 0,
    share_count       BIGINT        NOT NULL DEFAULT 0,
    created_at        TIMESTAMPTZ   NOT NULL DEFAULT now(),
    updated_at        TIMESTAMPTZ   NOT NULL DEFAULT now(),
    removed_at        TIMESTAMPTZ,
    version           BIGINT        NOT NULL DEFAULT 0,

    CONSTRAINT chk_post_author_user_type
        CHECK (author_user_type IN ('COMENSAL','CRIADOR','CHEF','ESTABELECIMENTO','VAREJO','COMUNIDADE','OFICIAL')),
    CONSTRAINT chk_post_kind
        CHECK (kind IN ('FOTO_PRATO','RECEITA','PERGUNTA','PRATO_DO_DIA','PROMOCAO','EVENTO','ALERTA_SANITARIO')),
    CONSTRAINT chk_post_feed_type CHECK ((feed_type = 'ALERT') = (kind = 'ALERTA_SANITARIO')
                                         AND feed_type IN ('POST','ALERT')),
    CONSTRAINT chk_post_media_type CHECK (media_type IN ('TEXT','IMAGE')),
    CONSTRAINT chk_post_photo_alt
        CHECK (status = 'REMOVED' OR media_type = 'TEXT' OR (media_object_key IS NOT NULL AND alt_text IS NOT NULL)),
    CONSTRAINT chk_post_age_rating CHECK (age_rating IN ('LIVRE','AGE_18')),
    CONSTRAINT chk_post_status CHECK (status IN ('DRAFT','PUBLISHED','ARCHIVED','REMOVED')),
    CONSTRAINT chk_post_period CHECK (starts_at IS NULL OR ends_at IS NULL OR ends_at > starts_at),
    CONSTRAINT chk_post_prices
        CHECK ((price_cents IS NULL OR price_cents >= 0) AND (price_to_cents IS NULL OR price_to_cents > 0)
               AND (price_from_cents IS NULL OR price_to_cents IS NULL OR price_from_cents > price_to_cents)),
    CONSTRAINT chk_post_discount CHECK (discount_percent IS NULL OR discount_percent BETWEEN 1 AND 90),
    CONSTRAINT chk_post_json
        CHECK (jsonb_typeof(dietary_styles) = 'array' AND jsonb_typeof(allergens) = 'object'
               AND jsonb_typeof(food_warnings) = 'array'),
    CONSTRAINT chk_post_background_json
        CHECK (background IS NULL OR (jsonb_typeof(background) = 'object' AND octet_length(background::text) <= 2048)),
    -- alerta sanitário nunca tem moldura: a faixa hazard precisa ser reconhecida na hora
    CONSTRAINT chk_post_alert_without_background CHECK (feed_type <> 'ALERT' OR background IS NULL),
    CONSTRAINT chk_post_counters CHECK (like_count >= 0 AND comment_count >= 0 AND share_count >= 0)
);

COMMENT ON TABLE  post IS 'Friendly Eats · post de comida (RF9); a timeline no Cassandra virá como projeção';
COMMENT ON COLUMN post.created_at IS 'Data de publicação exibida no card (relativa) e na página do post (absoluta)';
COMMENT ON COLUMN post.expires_at IS 'Prato do dia: 23h59 de America/Sao_Paulo do dia; promoção e evento: ends_at';
COMMENT ON COLUMN post.removed_at IS 'Autor, moderação ou eliminação da conta (LGPD Art. 18, VI): conteúdo apagado';
COMMENT ON COLUMN post.background IS 'Moldura do post (RF16): forma canônica do BackgroundValidator, só ids do catálogo';

-- Feed (RF14): mais recentes primeiro, paginação por cursor (created_at, id)
CREATE INDEX IF NOT EXISTS idx_post_feed
    ON post (feed_type, created_at DESC, id DESC) WHERE status = 'PUBLISHED';
-- Perfil do autor (RF15): publicados e arquivados
CREATE INDEX IF NOT EXISTS idx_post_author_created
    ON post (author_id, created_at DESC, id DESC) WHERE status IN ('PUBLISHED','ARCHIVED');
-- Posts sobre um lugar (RF14)
CREATE INDEX IF NOT EXISTS idx_post_establishment
    ON post (establishment_id, created_at DESC, id DESC) WHERE status = 'PUBLISHED';

-- =============================================================================
-- Reações: LIKE (público, contado) e SAVE (privado, nunca mostrado a terceiros).
-- A PK impede curtir duas vezes; curtir e descurtir são idempotentes.
-- =============================================================================

CREATE TABLE IF NOT EXISTS post_reaction (
    post_id     UUID         NOT NULL REFERENCES post (id) ON DELETE CASCADE,
    user_id     BIGINT       NOT NULL REFERENCES user_account (id) ON DELETE CASCADE,
    kind        VARCHAR(10)  NOT NULL,
    created_at  TIMESTAMPTZ  NOT NULL DEFAULT now(),
    PRIMARY KEY (post_id, user_id, kind),
    CONSTRAINT chk_post_reaction_kind CHECK (kind IN ('LIKE','SAVE'))
);

-- "Meus salvos" e eliminação por usuário
CREATE INDEX IF NOT EXISTS idx_post_reaction_user
    ON post_reaction (user_id, kind, created_at DESC);

-- =============================================================================
-- Comentários (caixa de mensagens do post). A remoção é lógica: o texto é apagado e o contador desce, e o id fica
-- para a auditoria de moderação.
-- =============================================================================

CREATE TABLE IF NOT EXISTS post_comment (
    id          BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    post_id     UUID           NOT NULL REFERENCES post (id) ON DELETE CASCADE,
    author_id   BIGINT         NOT NULL REFERENCES user_account (id),
    body        VARCHAR(2000),                     -- NULL depois de removido
    status      VARCHAR(10)    NOT NULL DEFAULT 'VISIBLE',
    created_at  TIMESTAMPTZ    NOT NULL DEFAULT now(),
    removed_at  TIMESTAMPTZ,
    CONSTRAINT chk_post_comment_status CHECK (status IN ('VISIBLE','REMOVED')),
    CONSTRAINT chk_post_comment_body
        CHECK ((status = 'VISIBLE' AND body IS NOT NULL AND length(btrim(body)) > 0)
            OR (status = 'REMOVED' AND body IS NULL))
);

CREATE INDEX IF NOT EXISTS idx_post_comment_post
    ON post_comment (post_id, id) WHERE status = 'VISIBLE';
CREATE INDEX IF NOT EXISTS idx_post_comment_author
    ON post_comment (author_id);

-- =============================================================================
-- Compartilhamentos: um por (post, usuário, canal). share_count conta pares distintos, então repetir o mesmo
-- compartilhamento não infla o número. user_id vira NULL na eliminação da conta; o total agregado continua certo.
-- =============================================================================

CREATE TABLE IF NOT EXISTS post_share (
    id          BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    post_id     UUID         NOT NULL REFERENCES post (id) ON DELETE CASCADE,
    user_id     BIGINT       REFERENCES user_account (id) ON DELETE SET NULL,
    channel     VARCHAR(20)  NOT NULL,
    created_at  TIMESTAMPTZ  NOT NULL DEFAULT now(),
    CONSTRAINT uk_post_share_user_channel UNIQUE (post_id, user_id, channel),
    CONSTRAINT chk_post_share_channel
        CHECK (channel IN ('COPY_LINK','NATIVE','WHATSAPP','TELEGRAM','X','EMAIL'))
);

CREATE INDEX IF NOT EXISTS idx_post_share_user
    ON post_share (user_id);
