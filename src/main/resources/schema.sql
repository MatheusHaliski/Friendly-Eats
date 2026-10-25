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
