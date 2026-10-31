-- =============================================================================
-- Friendly Eats · PostgreSQL 17 + PostGIS (fonte da verdade / ACID)
-- V1 · contas, consentimentos, Termos de Uso, 2FA, preferências e exclusão com carência
--   RF1  cadastro com tipo de conta e aceite dos Termos de Uso
--   RF2  perfil, preferências e foto
--   RF3  consentimentos (Privacy by Default)
--   RF4  sessão, autenticação, controle de acesso e 2FA
--   RF5  direitos do titular: exclusão com 30 dias para reativar e eliminação
-- Sessões NÃO ficam no PostgreSQL: são opacas no Valkey (Spring Session).
-- =============================================================================

CREATE EXTENSION IF NOT EXISTS postgis;
CREATE EXTENSION IF NOT EXISTS pg_trgm;    -- busca aproximada por nome
CREATE EXTENSION IF NOT EXISTS unaccent;   -- ignora acentos em pt-BR

-- unaccent() não é IMMUTABLE (depende do dicionário), logo não pode entrar em índice.
-- Wrapper imutável fixando o dicionário padrão: usar f_unaccent() nas consultas por nome.
CREATE OR REPLACE FUNCTION f_unaccent(text)
    RETURNS text
    LANGUAGE sql IMMUTABLE PARALLEL SAFE STRICT
    RETURN public.unaccent('public.unaccent', $1);

-- =============================================================================
-- Conta. Só o necessário para uma rede de comida (LGPD Art. 6º, III): sem gênero, endereço,
-- cidadania nem orientação sexual. Estilos alimentares podem revelar saúde (alergia, doença
-- celíaca) ou religião (halal, kosher): dado sensível, gravado só com consentimento específico.
-- =============================================================================

CREATE TABLE IF NOT EXISTS user_account (
    id                      BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,

    -- identificação e credenciais
    name                    VARCHAR(120)  NOT NULL,
    password_hash           VARCHAR(255)  NOT NULL,             -- Argon2id, nunca texto puro
    email                   VARCHAR(254)  NOT NULL,
    recovery_email          VARCHAR(254),

    -- residência (filtro "perto de mim" sem GPS) e contato
    country_of_residence    VARCHAR(2),                         -- ISO 3166-1 alpha-2
    state_of_residence      VARCHAR(80),
    city_of_residence       VARCHAR(120),
    cellphone_number        VARCHAR(20),                        -- E.164

    -- idade: menores de 16 com padrões protetivos (ECA Digital); conteúdo de bebida alcoólica só 18+
    user_date_of_birth      DATE,

    -- gosto: códigos do catálogo de culinárias e de estilos alimentares (docs/TAXONOMIA_FRIENDLY_EATS.md)
    favorite_cuisines       JSONB,
    dietary_styles          JSONB,                              -- SENSÍVEL · Art. 11

    -- perfil público
    photo_object_key        VARCHAR(512),                       -- chave no S3 (avatars/…), nunca o binário
    handle                  VARCHAR(30),
    bio                     VARCHAR(150),
    pronouns                JSONB,
    links                   JSONB,

    -- tipo de conta (cor + glifo + rótulo) e campos do tipo
    user_type               VARCHAR(16)   NOT NULL DEFAULT 'COMENSAL',
    type_details            JSONB,
    category_verification   VARCHAR(12)   NOT NULL DEFAULT 'NOT_REQUIRED',

    -- última localização (só com consentimento LOCATION_TRACKING)
    last_saved_location     JSONB,
    last_saved_point        GEOGRAPHY(POINT, 4326) GENERATED ALWAYS AS (
                                CASE
                                    WHEN last_saved_location ? 'lat' AND last_saved_location ? 'lng'
                                    THEN ST_SetSRID(ST_MakePoint(
                                             (last_saved_location->>'lng')::double precision,
                                             (last_saved_location->>'lat')::double precision), 4326)::geography
                                END) STORED,

    -- ciclo de vida
    account_status          VARCHAR(30)   NOT NULL DEFAULT 'PENDING_VERIFICATION',
    email_verified_at       TIMESTAMPTZ,
    deletion_requested_at   TIMESTAMPTZ,
    deletion_scheduled_for  TIMESTAMPTZ,
    status_before_deletion  VARCHAR(30),                        -- status ao qual a conta volta se reativar
    created_at              TIMESTAMPTZ   NOT NULL DEFAULT now(),
    updated_at              TIMESTAMPTZ   NOT NULL DEFAULT now(),
    deleted_at              TIMESTAMPTZ,
    version                 BIGINT        NOT NULL DEFAULT 0,

    CONSTRAINT uk_user_account_email UNIQUE (email),
    CONSTRAINT chk_user_account_user_type
        CHECK (user_type IN ('COMENSAL','CRIADOR','CHEF','ESTABELECIMENTO','VAREJO','COMUNIDADE','OFICIAL')),
    CONSTRAINT chk_user_account_status
        CHECK (account_status IN ('PENDING_VERIFICATION','ACTIVE','SUSPENDED','PENDING_DELETION','ANONYMOUS_LGPD_DELETED')),
    CONSTRAINT chk_user_account_category_verification
        CHECK (category_verification IN ('NOT_REQUIRED','PENDING','VERIFIED','REJECTED')),
    CONSTRAINT chk_user_account_handle
        CHECK (handle IS NULL OR handle ~ '^[a-z0-9._]{3,30}$'),
    CONSTRAINT chk_user_account_cuisines_json
        CHECK (favorite_cuisines IS NULL OR jsonb_typeof(favorite_cuisines) = 'array'),
    CONSTRAINT chk_user_account_dietary_json
        CHECK (dietary_styles IS NULL OR jsonb_typeof(dietary_styles) = 'array'),
    CONSTRAINT chk_user_account_type_details_json
        CHECK (type_details IS NULL OR jsonb_typeof(type_details) = 'object'),
    CONSTRAINT chk_user_account_location_json
        CHECK (last_saved_location IS NULL OR jsonb_typeof(last_saved_location) = 'object')
);

COMMENT ON TABLE  user_account IS 'Friendly Eats · contas (comensais, criadores, chefs, estabelecimentos, varejo, comunidades e órgãos oficiais)';
COMMENT ON COLUMN user_account.dietary_styles IS 'Dado sensível (LGPD Art. 5º II / Art. 11): só com consentimento SENSITIVE_PROFILE_DATA';
COMMENT ON COLUMN user_account.type_details
    IS 'Campos do tipo de conta, ex.: {"cnpj":"…","legalName":"…","tradeName":"…","cnae":"5611-2/01"}';
COMMENT ON COLUMN user_account.last_saved_location IS '{"lat":-25.42,"lng":-49.27,"timestamp":"2026-10-25T12:00:00Z"}';
COMMENT ON COLUMN user_account.deletion_scheduled_for
    IS 'Fim da carência de 30 dias; depois disso a saga de eliminação (RF5) roda e não há volta';

CREATE UNIQUE INDEX IF NOT EXISTS uk_user_account_handle ON user_account (handle);
CREATE INDEX IF NOT EXISTS idx_user_account_user_type ON user_account (user_type);
CREATE INDEX IF NOT EXISTS idx_user_account_location
    ON user_account (country_of_residence, state_of_residence, city_of_residence);
CREATE INDEX IF NOT EXISTS idx_user_account_status ON user_account (account_status);
CREATE INDEX IF NOT EXISTS idx_user_account_name_trgm
    ON user_account USING GIN (lower(f_unaccent(name)) gin_trgm_ops);
CREATE INDEX IF NOT EXISTS idx_user_account_point ON user_account USING GIST (last_saved_point);
CREATE INDEX IF NOT EXISTS idx_user_account_cuisines ON user_account USING GIN (favorite_cuisines);
-- job diário de eliminação: só as contas cujo prazo venceu
CREATE INDEX IF NOT EXISTS idx_user_account_deletion_due
    ON user_account (deletion_scheduled_for)
    WHERE account_status = 'PENDING_DELETION';

-- =============================================================================
-- Consentimentos (Art. 7º, I · Art. 8º §5º · Art. 11) — append-only. Cada mudança de toggle gera
-- uma linha; o estado atual é a linha mais recente por (user_id, purpose).
-- =============================================================================

CREATE TABLE IF NOT EXISTS consent_event (
    id              BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    user_id         BIGINT       NOT NULL REFERENCES user_account (id) ON DELETE CASCADE,
    purpose         VARCHAR(40)  NOT NULL,
    legal_basis     VARCHAR(40)  NOT NULL,       -- ex.: ART_7_I, ART_11_I
    granted         BOOLEAN      NOT NULL DEFAULT FALSE,   -- Privacy by Default
    policy_version  VARCHAR(20)  NOT NULL,
    channel         VARCHAR(20)  NOT NULL,       -- WEB, IOS, ANDROID, SUPPORT
    ip_hash         CHAR(64),                    -- HMAC-SHA256, nunca o IP em claro
    occurred_at     TIMESTAMPTZ  NOT NULL DEFAULT now(),
    CONSTRAINT chk_consent_purpose CHECK (purpose IN (
        'MARKETING','LOCATION_TRACKING','PERSONALIZED_FEED','SENSITIVE_PROFILE_DATA','THIRD_PARTY_SHARING'))
);

CREATE INDEX IF NOT EXISTS idx_consent_event_user_purpose
    ON consent_event (user_id, purpose, occurred_at DESC);

CREATE OR REPLACE VIEW user_consent_current AS
SELECT DISTINCT ON (user_id, purpose)
       user_id, purpose, legal_basis, granted, policy_version, occurred_at
FROM consent_event
ORDER BY user_id, purpose, occurred_at DESC;

-- =============================================================================
-- Chave de dados por usuário (envelope encryption · crypto-shredding): destruí-la na exclusão
-- torna ilegíveis os dados sensíveis em backups, Cassandra e S3 (Art. 16 · Art. 18, VI).
-- =============================================================================

CREATE TABLE IF NOT EXISTS user_key (
    user_id         BIGINT       PRIMARY KEY REFERENCES user_account (id) ON DELETE CASCADE,
    wrapped_dek     BYTEA        NOT NULL,
    kms_key_id      VARCHAR(255) NOT NULL,
    created_at      TIMESTAMPTZ  NOT NULL DEFAULT now(),
    destroyed_at    TIMESTAMPTZ
);

-- Outbox transacional: eventos gravados na mesma transação do domínio e publicados para Cassandra e Valkey
CREATE TABLE IF NOT EXISTS outbox_event (
    id              UUID         PRIMARY KEY,
    aggregate_type  VARCHAR(40)  NOT NULL,       -- user, place, review, media
    aggregate_id    VARCHAR(64)  NOT NULL,
    event_type      VARCHAR(60)  NOT NULL,
    payload         JSONB        NOT NULL,
    created_at      TIMESTAMPTZ  NOT NULL DEFAULT now()
);

-- =============================================================================
-- Aceite dos Termos de Uso e da Política de Privacidade — append-only (Art. 7º, V).
-- =============================================================================

CREATE TABLE IF NOT EXISTS terms_acceptance (
    id           BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    user_id      BIGINT       NOT NULL REFERENCES user_account (id) ON DELETE CASCADE,
    document     VARCHAR(30)  NOT NULL,
    version      VARCHAR(20)  NOT NULL,
    accepted_at  TIMESTAMPTZ  NOT NULL DEFAULT now(),
    channel      VARCHAR(20)  NOT NULL,              -- WEB, IOS, ANDROID
    ip_hash      VARCHAR(64),                        -- HMAC-SHA256 do IP (AppSecrets), nunca o IP em claro
    CONSTRAINT chk_terms_acceptance_document CHECK (document IN ('TERMS_OF_USE','PRIVACY_POLICY'))
);

CREATE INDEX IF NOT EXISTS idx_terms_acceptance_user
    ON terms_acceptance (user_id, document, accepted_at DESC);

-- =============================================================================
-- 2FA por aplicativo autenticador (TOTP · RFC 6238). Segredo cifrado na aplicação (AES-256-GCM);
-- last_used_step impede reusar o mesmo código dentro da janela.
-- =============================================================================

CREATE TABLE IF NOT EXISTS user_mfa (
    user_id         BIGINT       PRIMARY KEY REFERENCES user_account (id) ON DELETE CASCADE,
    totp_secret     BYTEA        NOT NULL,
    enabled_at      TIMESTAMPTZ  NOT NULL DEFAULT now(),
    last_used_step  BIGINT       NOT NULL DEFAULT 0
);

-- Códigos de recuperação: mostrados uma única vez; guardados só como hash Argon2id
CREATE TABLE IF NOT EXISTS user_recovery_code (
    id          BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    user_id     BIGINT        NOT NULL REFERENCES user_account (id) ON DELETE CASCADE,
    code_hash   VARCHAR(255)  NOT NULL,
    created_at  TIMESTAMPTZ   NOT NULL DEFAULT now(),
    used_at     TIMESTAMPTZ
);

CREATE INDEX IF NOT EXISTS idx_user_recovery_code_user
    ON user_recovery_code (user_id) WHERE used_at IS NULL;

-- =============================================================================
-- Configurações e preferências. Uma linha por usuário, criada no cadastro. Menores de 16
-- (ECA Digital) nascem com o perfil privado e conteúdo sensível limitado.
-- =============================================================================

CREATE TABLE IF NOT EXISTS user_preference (
    user_id                 BIGINT       PRIMARY KEY REFERENCES user_account (id) ON DELETE CASCADE,
    -- Exibição e acessibilidade
    theme                   VARCHAR(10)  NOT NULL DEFAULT 'SYSTEM',
    app_background          VARCHAR(20)  NOT NULL DEFAULT 'PLAIN',
    language                VARCHAR(10)  NOT NULL DEFAULT 'pt-BR',
    font_scale              SMALLINT     NOT NULL DEFAULT 100,
    reduce_motion           BOOLEAN      NOT NULL DEFAULT FALSE,
    -- Privacidade
    profile_private         BOOLEAN      NOT NULL DEFAULT FALSE,
    who_can_comment         VARCHAR(10)  NOT NULL DEFAULT 'EVERYONE',
    show_birth_year         BOOLEAN      NOT NULL DEFAULT FALSE,
    -- Conteúdo e bem-estar
    home_tab                VARCHAR(10)  NOT NULL DEFAULT 'FEED',
    sensitive_content       VARCHAR(10)  NOT NULL DEFAULT 'LIMIT',
    usage_reminder_minutes  SMALLINT,
    -- Notificações e segurança
    notifications           JSONB        NOT NULL DEFAULT '{"likes":true,"comments":true,"shares":true,"foodAlerts":true}',
    login_alerts            BOOLEAN      NOT NULL DEFAULT TRUE,
    updated_at              TIMESTAMPTZ  NOT NULL DEFAULT now(),
    CONSTRAINT chk_user_preference_theme       CHECK (theme IN ('SYSTEM','DARK','LIGHT')),
    CONSTRAINT chk_user_preference_background  CHECK (app_background IN ('PLAIN','PATTERN')),
    CONSTRAINT chk_user_preference_font_scale  CHECK (font_scale BETWEEN 85 AND 150),
    CONSTRAINT chk_user_preference_comment     CHECK (who_can_comment IN ('EVERYONE','FOLLOWERS','NOBODY')),
    CONSTRAINT chk_user_preference_home_tab    CHECK (home_tab IN ('FEED','EXPLORE','NEARBY','SAVED')),
    CONSTRAINT chk_user_preference_sensitive   CHECK (sensitive_content IN ('LIMIT','ALLOW')),
    CONSTRAINT chk_user_preference_reminder    CHECK (usage_reminder_minutes IS NULL OR usage_reminder_minutes BETWEEN 10 AND 600),
    CONSTRAINT chk_user_preference_notif_json  CHECK (jsonb_typeof(notifications) = 'object')
);

COMMENT ON COLUMN user_preference.home_tab IS 'Aba inicial: FEED, EXPLORE (busca por filtros), NEARBY (perto de mim) ou SAVED';
COMMENT ON COLUMN user_preference.notifications IS 'foodAlerts = alertas de recall e vigilância sanitária (Anvisa)';

-- =============================================================================
-- V2 · estabelecimentos, classificação e avaliações com estrelas
--   RF6  cadastro do estabelecimento: tipo principal + até 3 secundários, local e preço declarado
--   RF7  classificação: culinária, formato, abordagem e estilos alimentares
--   RF8  avaliação com estrelas (1 a 5), dimensões, gasto por pessoa e nota agregada
-- Os vocabulários têm códigos estáveis (docs/TAXONOMIA_FRIENDLY_EATS.md) e são validados na aplicação;
-- aqui ficam só os CHECKs dos valores fechados e o formato dos JSON.
-- =============================================================================

CREATE TABLE IF NOT EXISTS establishment (
    id                      BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    owner_id                BIGINT        NOT NULL REFERENCES user_account (id),
    name                    VARCHAR(120)  NOT NULL,
    description             VARCHAR(500),

    -- tipo (RF6): 1 principal + até 3 secundários; o grupo deriva do principal
    primary_type            VARCHAR(32)   NOT NULL,
    secondary_types         JSONB         NOT NULL DEFAULT '[]',
    type_group              VARCHAR(16)   NOT NULL,

    -- classificação (RF7): culinária 1 + até 2, formato 1 + até 4, abordagem e estilos {"VEGANO":"EXCLUSIVO"}
    primary_cuisine         VARCHAR(32),
    secondary_cuisines      JSONB         NOT NULL DEFAULT '[]',
    primary_format          VARCHAR(32),
    secondary_formats       JSONB         NOT NULL DEFAULT '[]',
    approach                VARCHAR(16)   NOT NULL DEFAULT 'TRADICIONAL',
    dietary_offers          JSONB         NOT NULL DEFAULT '{}',
    -- todas as culinárias e formatos juntos, para o filtro da busca (RF10) com índice GIN. Coluna gerada só aceita
    -- função IMMUTABLE (jsonb_build_array não é); os códigos são [A-Z_], então o texto não precisa de escape.
    cuisines_all            JSONB GENERATED ALWAYS AS (
                                CASE WHEN primary_cuisine IS NULL THEN '[]'::jsonb
                                     ELSE ('["' || primary_cuisine || '"]')::jsonb END || secondary_cuisines) STORED,
    formats_all             JSONB GENERATED ALWAYS AS (
                                CASE WHEN primary_format IS NULL THEN '[]'::jsonb
                                     ELSE ('["' || primary_format || '"]')::jsonb END || secondary_formats) STORED,

    -- preço declarado pelo dono, por pessoa, em centavos de real; o nível ($ a $$$$) é derivado na leitura
    price_min_cents         INTEGER,
    price_max_cents         INTEGER,
    price_not_applicable    BOOLEAN       NOT NULL DEFAULT FALSE,

    -- local: o município (código IBGE de 7 dígitos) é a chave; o CEP muda e não serve de chave
    country                 VARCHAR(2)    NOT NULL DEFAULT 'BR',
    uf                      VARCHAR(2)    NOT NULL,
    municipality_ibge       VARCHAR(7)    NOT NULL,
    city                    VARCHAR(120)  NOT NULL,
    neighborhood            VARCHAR(120),
    street                  VARCHAR(160),
    street_number           VARCHAR(20),
    address_complement      VARCHAR(80),
    cep                     VARCHAR(8),
    latitude                DOUBLE PRECISION,
    longitude               DOUBLE PRECISION,
    point                   GEOGRAPHY(POINT, 4326) GENERATED ALWAYS AS (
                                CASE WHEN latitude IS NOT NULL AND longitude IS NOT NULL
                                     THEN ST_SetSRID(ST_MakePoint(longitude, latitude), 4326)::geography END) STORED,
    geo_precision           VARCHAR(24)   NOT NULL DEFAULT 'GEO_MUNICIPIO_CENTROID',
    -- cozinha caseira: o endereço é dado pessoal do dono; o público vê só bairro e um ponto arredondado
    address_visibility      VARCHAR(20)   NOT NULL DEFAULT 'ADDR_PUBLIC_FULL',

    status                  VARCHAR(10)   NOT NULL DEFAULT 'ACTIVE',
    created_at              TIMESTAMPTZ   NOT NULL DEFAULT now(),
    updated_at              TIMESTAMPTZ   NOT NULL DEFAULT now(),
    version                 BIGINT        NOT NULL DEFAULT 0,

    CONSTRAINT chk_establishment_group
        CHECK (type_group IN ('RESTAURANTE','RAPIDO','BAR','CAFE_DOCE','PRODUCAO','VAREJO','POLO')),
    CONSTRAINT chk_establishment_secondary_types
        CHECK (jsonb_typeof(secondary_types) = 'array' AND jsonb_array_length(secondary_types) <= 3),
    CONSTRAINT chk_establishment_secondary_cuisines
        CHECK (jsonb_typeof(secondary_cuisines) = 'array' AND jsonb_array_length(secondary_cuisines) <= 2),
    CONSTRAINT chk_establishment_secondary_formats
        CHECK (jsonb_typeof(secondary_formats) = 'array' AND jsonb_array_length(secondary_formats) <= 4),
    CONSTRAINT chk_establishment_approach
        CHECK (approach IN ('TRADICIONAL','CASEIRA','CONTEMPORANEA','FUSION','VARIADA')),
    CONSTRAINT chk_establishment_dietary_json CHECK (jsonb_typeof(dietary_offers) = 'object'),
    CONSTRAINT chk_establishment_price
        CHECK ((price_min_cents IS NULL OR price_min_cents >= 0)
           AND (price_max_cents IS NULL OR price_max_cents >= COALESCE(price_min_cents, 0))
           AND NOT (price_not_applicable AND (price_min_cents IS NOT NULL OR price_max_cents IS NOT NULL))),
    CONSTRAINT chk_establishment_codes
        CHECK (primary_type ~ '^[A-Z_]+$' AND (primary_cuisine IS NULL OR primary_cuisine ~ '^[A-Z_]+$')
           AND (primary_format IS NULL OR primary_format ~ '^[A-Z_]+$')),
    CONSTRAINT chk_establishment_municipality CHECK (municipality_ibge ~ '^[0-9]{7}$'),
    CONSTRAINT chk_establishment_cep CHECK (cep IS NULL OR cep ~ '^[0-9]{8}$'),
    CONSTRAINT chk_establishment_coordinates
        CHECK ((latitude IS NULL) = (longitude IS NULL)
           AND (latitude IS NULL OR (latitude BETWEEN -90 AND 90 AND longitude BETWEEN -180 AND 180))),
    CONSTRAINT chk_establishment_geo_precision
        CHECK (geo_precision IN ('GEO_PIN_CONFIRMED','GEO_ROOFTOP','GEO_STREET','GEO_CEP_CENTROID',
                                 'GEO_BAIRRO_CENTROID','GEO_MUNICIPIO_CENTROID')),
    CONSTRAINT chk_establishment_address_visibility
        CHECK (address_visibility IN ('ADDR_PUBLIC_FULL','ADDR_PUBLIC_BAIRRO','ADDR_HIDDEN')),
    CONSTRAINT chk_establishment_status CHECK (status IN ('ACTIVE','CLOSED','REMOVED'))
);

COMMENT ON TABLE  establishment IS 'Friendly Eats · estabelecimentos (RF6) com a classificação da taxonomia (RF7)';
COMMENT ON COLUMN establishment.owner_id IS 'Conta ESTABELECIMENTO ou VAREJO; a página só é pública com o selo VERIFIED';
COMMENT ON COLUMN establishment.dietary_offers IS '{"VEGANO":"EXCLUSIVO","SEM_GLUTEN":"OFERECE"}: oferta da casa, não dado de pessoa';
COMMENT ON COLUMN establishment.price_min_cents IS 'Gasto típico por pessoa (prato + bebida não alcoólica), em centavos de BRL';

CREATE INDEX IF NOT EXISTS idx_establishment_owner ON establishment (owner_id);
CREATE INDEX IF NOT EXISTS idx_establishment_municipality ON establishment (municipality_ibge, type_group);
CREATE INDEX IF NOT EXISTS idx_establishment_point ON establishment USING GIST (point);
CREATE INDEX IF NOT EXISTS idx_establishment_cuisines ON establishment USING GIN (cuisines_all jsonb_path_ops);
CREATE INDEX IF NOT EXISTS idx_establishment_formats ON establishment USING GIN (formats_all jsonb_path_ops);
CREATE INDEX IF NOT EXISTS idx_establishment_name_trgm
    ON establishment USING GIN (lower(f_unaccent(name)) gin_trgm_ops);

-- =============================================================================
-- Avaliação (RF8): nota inteira de 1 a 5 (não existe 0; "sem avaliação" é ausência de linha).
-- Uma avaliação por pessoa e estabelecimento; editar guarda a versão anterior em review_revision.
-- Publicada direto (sem censura prévia · Marco Civil art. 19); moderação posterior tira de circulação.
-- =============================================================================

CREATE TABLE IF NOT EXISTS review (
    id                      BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    establishment_id        BIGINT        NOT NULL REFERENCES establishment (id) ON DELETE CASCADE,
    author_id               BIGINT        NOT NULL REFERENCES user_account (id),
    rating                  SMALLINT      NOT NULL,
    visit_mode              VARCHAR(10)   NOT NULL,
    dimensions              JSONB         NOT NULL DEFAULT '{}',
    spent_per_person_cents  INTEGER,
    body                    VARCHAR(2000),
    trust                   VARCHAR(24)   NOT NULL DEFAULT 'REVIEW_UNVERIFIED',
    status                  VARCHAR(14)   NOT NULL DEFAULT 'PUBLISHED',
    edit_count              INTEGER       NOT NULL DEFAULT 0,
    created_at              TIMESTAMPTZ   NOT NULL DEFAULT now(),
    updated_at              TIMESTAMPTZ   NOT NULL DEFAULT now(),
    version                 BIGINT        NOT NULL DEFAULT 0,

    CONSTRAINT uk_review_author_establishment UNIQUE (establishment_id, author_id),
    CONSTRAINT chk_review_rating CHECK (rating BETWEEN 1 AND 5),
    CONSTRAINT chk_review_visit_mode CHECK (visit_mode IN ('DINE_IN','TAKEOUT','DELIVERY')),
    CONSTRAINT chk_review_dimensions_json CHECK (jsonb_typeof(dimensions) = 'object'),
    CONSTRAINT chk_review_spent CHECK (spent_per_person_cents IS NULL OR spent_per_person_cents BETWEEN 0 AND 500000),
    CONSTRAINT chk_review_trust
        CHECK (trust IN ('REVIEW_VERIFIED_ORDER','REVIEW_VERIFIED_RECEIPT','REVIEW_VERIFIED_CHECKIN',
                         'REVIEW_UNVERIFIED','REVIEW_INCENTIVIZED','REVIEW_CONFLICT')),
    CONSTRAINT chk_review_status CHECK (status IN ('PUBLISHED','UNDER_REVIEW','REMOVED'))
);

COMMENT ON COLUMN review.updated_at IS 'Data da experiência para o peso de recência (meia-vida de 365 dias)';
COMMENT ON COLUMN review.spent_per_person_cents IS 'Opcional: "quanto você gastou por pessoa?"; a mediana de 12 meses deriva a faixa de preço';

CREATE INDEX IF NOT EXISTS idx_review_establishment ON review (establishment_id, status, updated_at DESC);
CREATE INDEX IF NOT EXISTS idx_review_author ON review (author_id);

CREATE TABLE IF NOT EXISTS review_revision (
    id                      BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    review_id               BIGINT        NOT NULL REFERENCES review (id) ON DELETE CASCADE,
    rating                  SMALLINT      NOT NULL,
    visit_mode              VARCHAR(10)   NOT NULL,
    dimensions              JSONB         NOT NULL,
    spent_per_person_cents  INTEGER,
    body                    VARCHAR(2000),
    valid_from              TIMESTAMPTZ   NOT NULL,
    replaced_at             TIMESTAMPTZ   NOT NULL DEFAULT now()
);

COMMENT ON TABLE review_revision IS 'Versões anteriores de uma avaliação (RF8): a edição substitui, o histórico fica';

CREATE INDEX IF NOT EXISTS idx_review_revision_review ON review_revision (review_id, replaced_at);

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

-- =============================================================================
-- V5 · moderação e segurança entre usuários
--   RF13 moderação: papel de moderador, denúncia com protocolo (usuário e formulário público), fila priorizada com
--        prazo, decisão motivada com histórico imutável, recurso revisto por outra pessoa, integridade das avaliações
--        (sinais de fraude, alerta ao consumidor e congelamento) e contestação pelo dono
--   RF19 segurança entre usuários: bloquear, silenciar e palavras filtradas
--
-- Nada aqui guarda IP em claro: a origem da avaliação e o limite do formulário público usam o HMAC do AppSecrets.
-- =============================================================================

-- =============================================================================
-- Papéis. Hoje só MODERATOR, concedido por configuração (friendly-eats.moderation.bootstrap-moderators) ou pelo
-- DevSeed; vira a autoridade ROLE_MODERATOR da sessão e libera /api/v1/moderation/**.
-- =============================================================================

CREATE TABLE IF NOT EXISTS user_role (
    user_id     BIGINT        NOT NULL REFERENCES user_account (id) ON DELETE CASCADE,
    role        VARCHAR(16)   NOT NULL,
    granted_by  VARCHAR(40)   NOT NULL,                 -- config, seed ou o id de quem concedeu
    granted_at  TIMESTAMPTZ   NOT NULL DEFAULT now(),
    PRIMARY KEY (user_id, role),
    CONSTRAINT chk_user_role_role CHECK (role IN ('MODERATOR'))
);

-- =============================================================================
-- Conteúdo sob moderação. NULL = normal. PREVENTIVE: retirado na hora por denúncia de criança em risco (Decreto
-- 12.880, arts. 41 a 44) ou por sinal automático, até a decisão; HIDDEN e REMOVED: decisão de moderação. Em todos os
-- casos o conteúdo some para os outros, mas o texto fica guardado para o recurso e para a preservação (MCI, art. 15).
-- =============================================================================

ALTER TABLE post ADD COLUMN IF NOT EXISTS moderation_state VARCHAR(12);
ALTER TABLE post ADD CONSTRAINT chk_post_moderation_state
    CHECK (moderation_state IS NULL OR moderation_state IN ('PREVENTIVE','HIDDEN','REMOVED'));

ALTER TABLE post_comment ADD COLUMN IF NOT EXISTS moderation_state VARCHAR(12);
ALTER TABLE post_comment ADD CONSTRAINT chk_post_comment_moderation_state
    CHECK (moderation_state IS NULL OR moderation_state IN ('PREVENTIVE','HIDDEN','REMOVED'));

-- Avaliação (RF8): origem da escrita (HMAC do IP) para o sinal "mesma origem"
ALTER TABLE review ADD COLUMN IF NOT EXISTS origin_hash VARCHAR(64);
COMMENT ON COLUMN review.origin_hash IS 'HMAC-SHA256 do IP de quem escreveu (AppSecrets), nunca o IP; só para o sinal de mesma origem (RF13)';
CREATE INDEX IF NOT EXISTS idx_review_origin
    ON review (establishment_id, origin_hash, created_at) WHERE origin_hash IS NOT NULL;

-- =============================================================================
-- Integridade das avaliações (RF13 · RC-SEG-04 e 05)
-- =============================================================================

CREATE TABLE IF NOT EXISTS review_signal (
    id          BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    review_id   BIGINT        NOT NULL REFERENCES review (id) ON DELETE CASCADE,
    code        VARCHAR(24)   NOT NULL,
    detail      VARCHAR(200),
    created_at  TIMESTAMPTZ   NOT NULL DEFAULT now(),
    CONSTRAINT uk_review_signal UNIQUE (review_id, code),
    CONSTRAINT chk_review_signal_code
        CHECK (code IN ('BURST_NEW_ACCOUNTS','SAME_ORIGIN','INCENTIVE_DECLARED','CONFLICT_DECLARED'))
);

COMMENT ON TABLE review_signal IS 'Sinais de fraude de uma avaliação (RF13); critérios gerais, sem os pesos, vão para o autor';

-- Alerta ao consumidor na página do lugar e congelamento de novas avaliações num pico ("review bombing")
CREATE TABLE IF NOT EXISTS establishment_alert (
    id                BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    establishment_id  BIGINT        NOT NULL REFERENCES establishment (id) ON DELETE CASCADE,
    kind              VARCHAR(20)   NOT NULL,
    message           VARCHAR(500)  NOT NULL,
    freeze_reviews    BOOLEAN       NOT NULL DEFAULT FALSE,
    starts_at         TIMESTAMPTZ   NOT NULL DEFAULT now(),
    ends_at           TIMESTAMPTZ,
    lifted_at         TIMESTAMPTZ,
    automated         BOOLEAN       NOT NULL DEFAULT FALSE,
    created_by        BIGINT,                              -- moderador; NULL = automático
    created_at        TIMESTAMPTZ   NOT NULL DEFAULT now(),
    CONSTRAINT chk_establishment_alert_kind CHECK (kind IN ('AVALIACAO_PAGA','ATIVIDADE_SUSPEITA','ATENCAO_PUBLICA')),
    CONSTRAINT chk_establishment_alert_freeze_end CHECK (NOT freeze_reviews OR ends_at IS NOT NULL),
    CONSTRAINT chk_establishment_alert_period CHECK (ends_at IS NULL OR ends_at > starts_at),
    CONSTRAINT chk_establishment_alert_author CHECK (automated OR created_by IS NOT NULL)
);

COMMENT ON TABLE establishment_alert IS 'Alertas ao consumidor (RF13): texto factual, histórico público com início e fim';

CREATE INDEX IF NOT EXISTS idx_establishment_alert ON establishment_alert (establishment_id, starts_at DESC);

-- =============================================================================
-- Fila de moderação (RF13 · RC-SEG-01 a 03). Um caso por alvo enquanto estiver aberto; as denúncias de várias
-- pessoas sobre o mesmo alvo entram no mesmo caso, que fica com a maior prioridade e o menor prazo.
-- =============================================================================

CREATE TABLE IF NOT EXISTS moderation_case (
    id                   BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    target_type          VARCHAR(16)   NOT NULL,
    target_key           VARCHAR(40)   NOT NULL,          -- id do post (UUID) ou id numérico do resto
    target_owner_id      BIGINT        REFERENCES user_account (id) ON DELETE SET NULL,
    establishment_id     BIGINT,                          -- avaliação e página de lugar: o lugar envolvido
    status               VARCHAR(12)   NOT NULL DEFAULT 'OPEN',
    priority             SMALLINT      NOT NULL,          -- 0 crítica · 1 urgente · 2 alta · 3 normal · 4 baixa
    top_reason           VARCHAR(32)   NOT NULL,
    report_count         INTEGER       NOT NULL DEFAULT 0,
    preventive           BOOLEAN       NOT NULL DEFAULT FALSE,
    authority_referral   VARCHAR(10)   NOT NULL DEFAULT 'NONE',
    authority_reference  VARCHAR(200),
    authority_sent_at    TIMESTAMPTZ,
    snapshot             JSONB         NOT NULL DEFAULT '{}',
    due_at               TIMESTAMPTZ   NOT NULL,
    created_at           TIMESTAMPTZ   NOT NULL DEFAULT now(),
    updated_at           TIMESTAMPTZ   NOT NULL DEFAULT now(),
    decided_at           TIMESTAMPTZ,
    CONSTRAINT chk_moderation_case_target CHECK (target_type IN ('POST','COMMENT','REVIEW','PROFILE','ESTABLISHMENT')),
    CONSTRAINT chk_moderation_case_status CHECK (status IN ('OPEN','DECIDED','IN_APPEAL','FINAL')),
    CONSTRAINT chk_moderation_case_priority CHECK (priority BETWEEN 0 AND 4),
    CONSTRAINT chk_moderation_case_authority CHECK (authority_referral IN ('NONE','PENDING','SENT')),
    CONSTRAINT chk_moderation_case_snapshot CHECK (jsonb_typeof(snapshot) = 'object')
);

COMMENT ON COLUMN moderation_case.snapshot IS 'Cópia do conteúdo denunciado na abertura do caso (prova e recurso; MCI art. 15)';
COMMENT ON COLUMN moderation_case.authority_referral
    IS 'Indício de abuso sexual infantil: PENDING até a moderação registrar a comunicação às autoridades (nada automático)';

CREATE UNIQUE INDEX IF NOT EXISTS uk_moderation_case_open
    ON moderation_case (target_type, target_key) WHERE status = 'OPEN';
CREATE INDEX IF NOT EXISTS idx_moderation_case_queue ON moderation_case (status, priority, due_at);
CREATE INDEX IF NOT EXISTS idx_moderation_case_owner ON moderation_case (target_owner_id, updated_at DESC);
CREATE INDEX IF NOT EXISTS idx_moderation_case_establishment ON moderation_case (establishment_id, created_at);

CREATE TABLE IF NOT EXISTS moderation_report (
    id                  BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    case_id             BIGINT        NOT NULL REFERENCES moderation_case (id) ON DELETE CASCADE,
    source              VARCHAR(16)   NOT NULL,
    reporter_id         BIGINT        REFERENCES user_account (id) ON DELETE SET NULL,
    reporter_name       VARCHAR(120),                     -- só no formulário público (não usuário)
    reporter_email      VARCHAR(254),
    reason              VARCHAR(32)   NOT NULL,
    details             VARCHAR(1000),
    child_sexual_abuse  BOOLEAN       NOT NULL DEFAULT FALSE,
    low_trust           BOOLEAN       NOT NULL DEFAULT FALSE,
    ip_hash             VARCHAR(64),                      -- HMAC do IP: limite de ritmo do formulário público
    status              VARCHAR(10)   NOT NULL DEFAULT 'OPEN',
    created_at          TIMESTAMPTZ   NOT NULL DEFAULT now(),
    resolved_at         TIMESTAMPTZ,
    CONSTRAINT chk_moderation_report_source CHECK (source IN ('USER','PUBLIC_FORM','OWNER_CONTEST','SYSTEM')),
    CONSTRAINT chk_moderation_report_reason
        CHECK (reason IN ('CRIANCA_EM_RISCO','DESINFORMACAO_SANITARIA','ASSEDIO','ODIO','CONTEUDO_SEXUAL',
                          'DADO_PESSOAL','GOLPE_FRAUDE','AVALIACAO_FALSA','CONFLITO_INTERESSE','OFENSA_OU_CRIME',
                          'DIREITO_AUTORAL','SPAM','FORA_DO_TEMA','OUTRO')),
    CONSTRAINT chk_moderation_report_status CHECK (status IN ('OPEN','UPHELD','DISMISSED')),
    -- canal de não usuários (STF, item 9): identificado, nunca anônimo (ECA Digital, art. 29, §2º)
    CONSTRAINT chk_moderation_report_public
        CHECK (source <> 'PUBLIC_FORM' OR (reporter_name IS NOT NULL AND reporter_email IS NOT NULL))
);

-- uma denúncia aberta por pessoa e alvo (o caso aberto é único por alvo)
CREATE UNIQUE INDEX IF NOT EXISTS uk_moderation_report_open
    ON moderation_report (case_id, reporter_id) WHERE status = 'OPEN' AND reporter_id IS NOT NULL;
CREATE INDEX IF NOT EXISTS idx_moderation_report_reporter ON moderation_report (reporter_id, created_at DESC);
CREATE INDEX IF NOT EXISTS idx_moderation_report_ip
    ON moderation_report (ip_hash, created_at DESC) WHERE source = 'PUBLIC_FORM';

-- Decisões: histórico append-only (o trigger recusa UPDATE e DELETE). moderator_id sem FK de propósito.
CREATE TABLE IF NOT EXISTS moderation_decision (
    id             BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    case_id        BIGINT        NOT NULL REFERENCES moderation_case (id) ON DELETE CASCADE,
    stage          VARCHAR(10)   NOT NULL,
    action         VARCHAR(10)   NOT NULL,
    policy_reason  VARCHAR(32),
    statement      VARCHAR(2000) NOT NULL,
    automated      BOOLEAN       NOT NULL DEFAULT FALSE,
    moderator_id   BIGINT,
    created_at     TIMESTAMPTZ   NOT NULL DEFAULT now(),
    CONSTRAINT chk_moderation_decision_stage CHECK (stage IN ('PREVENTIVE','FIRST','APPEAL')),
    CONSTRAINT chk_moderation_decision_action CHECK (action IN ('KEEP','HIDE','REMOVE','WARN')),
    CONSTRAINT chk_moderation_decision_motive CHECK (action = 'KEEP' OR policy_reason IS NOT NULL),
    CONSTRAINT chk_moderation_decision_who CHECK (automated OR moderator_id IS NOT NULL)
);

CREATE INDEX IF NOT EXISTS idx_moderation_decision_case ON moderation_decision (case_id, id);
CREATE INDEX IF NOT EXISTS idx_moderation_decision_created ON moderation_decision (created_at);

CREATE OR REPLACE FUNCTION moderation_decision_append_only()
    RETURNS trigger
    LANGUAGE plpgsql
AS $$
BEGIN
    RAISE EXCEPTION 'moderation_decision é só de inclusão: o histórico de decisões não muda (RF13)';
END;
$$;

DROP TRIGGER IF EXISTS trg_moderation_decision_append_only ON moderation_decision;
CREATE TRIGGER trg_moderation_decision_append_only
    BEFORE UPDATE OR DELETE ON moderation_decision
    FOR EACH ROW EXECUTE FUNCTION moderation_decision_append_only();

-- Recurso (ECA Digital, art. 30): do autor contra a retirada ou o aviso, ou de quem denunciou contra o arquivamento.
CREATE TABLE IF NOT EXISTS moderation_appeal (
    id                  BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    case_id             BIGINT        NOT NULL REFERENCES moderation_case (id) ON DELETE CASCADE,
    decision_id         BIGINT        NOT NULL REFERENCES moderation_decision (id) ON DELETE CASCADE,
    appellant_id        BIGINT        REFERENCES user_account (id) ON DELETE SET NULL,
    appellant_role      VARCHAR(10)   NOT NULL,
    reason              VARCHAR(2000) NOT NULL,
    status              VARCHAR(10)   NOT NULL DEFAULT 'OPEN',
    result_decision_id  BIGINT        REFERENCES moderation_decision (id) ON DELETE CASCADE,
    created_at          TIMESTAMPTZ   NOT NULL DEFAULT now(),
    resolved_at         TIMESTAMPTZ,
    CONSTRAINT uk_moderation_appeal UNIQUE (decision_id, appellant_id),
    CONSTRAINT chk_moderation_appeal_role CHECK (appellant_role IN ('AUTHOR','REPORTER')),
    CONSTRAINT chk_moderation_appeal_status CHECK (status IN ('OPEN','MAINTAINED','REVERSED'))
);

CREATE INDEX IF NOT EXISTS idx_moderation_appeal_case ON moderation_appeal (case_id, status);

-- =============================================================================
-- Segurança entre usuários (RF19 · RC-SEG-09). Bloquear vale nos dois sentidos e é sigiloso; silenciar só esconde
-- para quem silenciou. Palavras filtradas escondem comentários nos posts de quem filtrou (o autor do comentário
-- ainda vê o seu). Sem linha em user_safety_setting, o filtro padrão vem ligado para menor de 16 (ECA Digital).
-- =============================================================================

CREATE TABLE IF NOT EXISTS user_block (
    blocker_id  BIGINT       NOT NULL REFERENCES user_account (id) ON DELETE CASCADE,
    blocked_id  BIGINT       NOT NULL REFERENCES user_account (id) ON DELETE CASCADE,
    created_at  TIMESTAMPTZ  NOT NULL DEFAULT now(),
    PRIMARY KEY (blocker_id, blocked_id),
    CONSTRAINT chk_user_block_self CHECK (blocker_id <> blocked_id)
);

CREATE INDEX IF NOT EXISTS idx_user_block_blocked ON user_block (blocked_id);

CREATE TABLE IF NOT EXISTS user_mute (
    muter_id    BIGINT       NOT NULL REFERENCES user_account (id) ON DELETE CASCADE,
    muted_id    BIGINT       NOT NULL REFERENCES user_account (id) ON DELETE CASCADE,
    created_at  TIMESTAMPTZ  NOT NULL DEFAULT now(),
    PRIMARY KEY (muter_id, muted_id),
    CONSTRAINT chk_user_mute_self CHECK (muter_id <> muted_id)
);

CREATE TABLE IF NOT EXISTS user_filtered_word (
    user_id     BIGINT       NOT NULL REFERENCES user_account (id) ON DELETE CASCADE,
    word        VARCHAR(40)  NOT NULL,                     -- já normalizada (minúsculas, sem acento)
    created_at  TIMESTAMPTZ  NOT NULL DEFAULT now(),
    PRIMARY KEY (user_id, word),
    CONSTRAINT chk_user_filtered_word CHECK (length(word) BETWEEN 2 AND 40)
);

CREATE TABLE IF NOT EXISTS user_safety_setting (
    user_id              BIGINT       PRIMARY KEY REFERENCES user_account (id) ON DELETE CASCADE,
    default_word_filter  BOOLEAN      NOT NULL,
    updated_at           TIMESTAMPTZ  NOT NULL DEFAULT now()
);

-- =============================================================================
-- V6 · página do estabelecimento (RF11)
--   horários por dia (várias faixas, virada da meia-noite), datas especiais e "fechado temporariamente";
--   cobranças declaradas (taxa de serviço opcional, couvert, desconto ou acréscimo por forma de pagamento);
--   facetas declaradas pelo dono (atendimento, ambiente, acessibilidade e pagamento) e botões de ação (links).
-- Os vocabulários e os limites são validados na aplicação (docs/RF11); aqui ficam o formato dos JSON e os CHECKs
-- dos valores fechados. "Fechou de vez" continua sendo status = 'CLOSED' (RF6).
-- =============================================================================

ALTER TABLE establishment
    -- {"weekly": {"MONDAY": ["11:30-15:00", "18:00-02:00"]} | null, "special": [{"date": "2026-12-25", ...}]}
    ADD COLUMN IF NOT EXISTS opening_hours        JSONB,
    ADD COLUMN IF NOT EXISTS temporarily_closed   BOOLEAN      NOT NULL DEFAULT FALSE,
    ADD COLUMN IF NOT EXISTS reopens_on           DATE,
    -- {"serviceFeePercent": 10, "couvert": {...}, "paymentAdjustments": [...]}
    ADD COLUMN IF NOT EXISTS charges              JSONB,
    ADD COLUMN IF NOT EXISTS charges_declared_at  TIMESTAMPTZ,
    -- {"WIFI": "SIM", "PET_FRIENDLY": "SO_AREA_EXTERNA"}: só o que o dono declarou; ausente = sem informação
    ADD COLUMN IF NOT EXISTS facets               JSONB        NOT NULL DEFAULT '{}',
    -- {"links": [{"provider": "IFOOD", "url": "https://www.ifood.com.br/...", "preferred": true}], "whatsapp": "55..."}
    ADD COLUMN IF NOT EXISTS action_links         JSONB;

ALTER TABLE establishment
    ADD CONSTRAINT chk_establishment_opening_hours_json
        CHECK (opening_hours IS NULL OR jsonb_typeof(opening_hours) = 'object'),
    ADD CONSTRAINT chk_establishment_reopens_on CHECK (reopens_on IS NULL OR temporarily_closed),
    ADD CONSTRAINT chk_establishment_charges_json CHECK (charges IS NULL OR jsonb_typeof(charges) = 'object'),
    ADD CONSTRAINT chk_establishment_facets_json CHECK (jsonb_typeof(facets) = 'object'),
    ADD CONSTRAINT chk_establishment_action_links_json
        CHECK (action_links IS NULL OR jsonb_typeof(action_links) = 'object');

COMMENT ON COLUMN establishment.opening_hours IS 'Horário no fuso do município (derivado da UF); faixa que passa da meia-noite pertence ao dia em que começa';
COMMENT ON COLUMN establishment.temporarily_closed IS 'Fechado temporariamente (RF11), com data de volta opcional; CLOSED no status é "fechou de vez"';
COMMENT ON COLUMN establishment.charges IS 'Cobranças declaradas: a taxa de serviço é sempre opcional (Lei 13.419/2017); consumação mínima não é aceita';
COMMENT ON COLUMN establishment.action_links IS 'Só links (reserva, delivery, WhatsApp): domínios permitidos por provedor; nada de pagamento no app';
