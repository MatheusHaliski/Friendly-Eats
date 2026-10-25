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
