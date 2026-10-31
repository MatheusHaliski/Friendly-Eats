
-- =============================================================================
-- V4 · idade, responsável e central do titular (onda 1 de obrigações legais)
--   RF17 verificação de idade efetiva (sem autodeclaração) para conteúdo de bebida alcoólica,
--        contestação revisada por pessoa e conta de menor vinculada à do responsável
--   RF18 central do titular: exportar meus dados, registros de acesso do Marco Civil (com o
--        inventário de consultas) e registro de incidentes de segurança
-- =============================================================================

-- =============================================================================
-- RF17 · Verificação de idade (ECA Digital, art. 9º, §1º · Decreto 12.880/2026, arts. 19, 24 e 27).
-- Guarda só o resultado, o método, a data e o id da verificação no fornecedor: nunca a imagem nem a
-- cópia do documento (Decreto, art. 24, §3º). Os dados servem só para verificar a idade.
-- =============================================================================

CREATE TABLE IF NOT EXISTS age_verification (
    user_id             BIGINT        PRIMARY KEY REFERENCES user_account (id) ON DELETE CASCADE,
    status              VARCHAR(16)   NOT NULL DEFAULT 'UNVERIFIED',
    method              VARCHAR(40),                         -- ex.: DOCUMENTO_E_SELFIE, BASE_GOVERNAMENTAL
    provider            VARCHAR(40),                         -- fornecedor que aferiu (fake-dev só em dev/it)
    provider_ref        VARCHAR(120),                        -- id da verificação no fornecedor
    pending_expires_at  TIMESTAMPTZ,                         -- sessão aberta no fornecedor (status PENDING)
    verified_at         TIMESTAMPTZ,
    updated_at          TIMESTAMPTZ   NOT NULL DEFAULT now(),
    CONSTRAINT chk_age_verification_status
        CHECK (status IN ('UNVERIFIED','PENDING','VERIFIED_ADULT','VERIFIED_MINOR','FAILED'))
);

COMMENT ON TABLE age_verification IS 'RF17 · estado atual da verificação de idade; sem imagem de documento (Decreto 12.880, art. 24, §3º)';

-- Histórico append-only de cada mudança de estado (início, resultado, contestação decidida)
CREATE TABLE IF NOT EXISTS age_verification_event (
    id            BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    user_id       BIGINT        NOT NULL REFERENCES user_account (id) ON DELETE CASCADE,
    status        VARCHAR(16)   NOT NULL,
    method        VARCHAR(40),
    provider      VARCHAR(40),
    provider_ref  VARCHAR(120),
    note          VARCHAR(300),
    occurred_at   TIMESTAMPTZ   NOT NULL DEFAULT now(),
    CONSTRAINT chk_age_verification_event_status
        CHECK (status IN ('UNVERIFIED','PENDING','VERIFIED_ADULT','VERIFIED_MINOR','FAILED'))
);

CREATE INDEX IF NOT EXISTS idx_age_verification_event_user ON age_verification_event (user_id, occurred_at DESC);

-- Contestação da idade aferida (Decreto 12.880, art. 27): fila simples revisada por uma pessoa
CREATE TABLE IF NOT EXISTS age_appeal (
    id             BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    user_id        BIGINT         NOT NULL REFERENCES user_account (id) ON DELETE CASCADE,
    contested      VARCHAR(16)    NOT NULL,                  -- estado contestado (VERIFIED_MINOR ou FAILED)
    reason         VARCHAR(1000)  NOT NULL,
    status         VARCHAR(10)    NOT NULL DEFAULT 'OPEN',
    created_at     TIMESTAMPTZ    NOT NULL DEFAULT now(),
    decided_at     TIMESTAMPTZ,
    decided_by     VARCHAR(254),                             -- e-mail de quem revisou
    decision_note  VARCHAR(500),
    CONSTRAINT chk_age_appeal_status CHECK (status IN ('OPEN','ACCEPTED','REJECTED'))
);

CREATE UNIQUE INDEX IF NOT EXISTS uk_age_appeal_open ON age_appeal (user_id) WHERE status = 'OPEN';
CREATE INDEX IF NOT EXISTS idx_age_appeal_queue ON age_appeal (created_at) WHERE status = 'OPEN';

-- =============================================================================
-- RF17 · Conta de menor vinculada à do responsável (ECA Digital, arts. 17, 18 e 24; LGPD, art. 14).
-- O convite vai por e-mail com um token de uso único (só o HMAC fica aqui). Só um vínculo pendente
-- ou ativo por menor.
-- =============================================================================

CREATE TABLE IF NOT EXISTS guardian_link (
    id                      BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    minor_id                BIGINT        NOT NULL REFERENCES user_account (id) ON DELETE CASCADE,
    guardian_id             BIGINT        REFERENCES user_account (id) ON DELETE SET NULL,
    guardian_email          VARCHAR(254)  NOT NULL,
    status                  VARCHAR(10)   NOT NULL DEFAULT 'PENDING',
    invite_token_hash       CHAR(64),                       -- HMAC-SHA256 do token (AppSecrets)
    invited_at              TIMESTAMPTZ   NOT NULL DEFAULT now(),
    invite_expires_at       TIMESTAMPTZ,
    accepted_at             TIMESTAMPTZ,
    ended_at                TIMESTAMPTZ,
    ended_by                VARCHAR(10),
    sensitive_consent_at    TIMESTAMPTZ,                    -- consentimento do responsável (LGPD, art. 14, §1º)
    reminder_sent_at        TIMESTAMPTZ,
    created_at              TIMESTAMPTZ   NOT NULL DEFAULT now(),
    CONSTRAINT chk_guardian_link_status CHECK (status IN ('PENDING','ACTIVE','ENDED')),
    CONSTRAINT chk_guardian_link_ended_by CHECK (ended_by IS NULL OR ended_by IN ('MINOR','GUARDIAN','SYSTEM')),
    CONSTRAINT chk_guardian_link_not_self CHECK (guardian_id IS NULL OR guardian_id <> minor_id)
);

CREATE UNIQUE INDEX IF NOT EXISTS uk_guardian_link_open ON guardian_link (minor_id) WHERE status IN ('PENDING','ACTIVE');
CREATE UNIQUE INDEX IF NOT EXISTS uk_guardian_link_token ON guardian_link (invite_token_hash) WHERE invite_token_hash IS NOT NULL;
CREATE INDEX IF NOT EXISTS idx_guardian_link_guardian ON guardian_link (guardian_id) WHERE status = 'ACTIVE';
CREATE INDEX IF NOT EXISTS idx_guardian_link_pending ON guardian_link (invited_at) WHERE status = 'PENDING';

-- Pedido do menor para afrouxar uma proteção; o responsável aprova ou nega
CREATE TABLE IF NOT EXISTS guardian_request (
    id          BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    link_id     BIGINT       NOT NULL REFERENCES guardian_link (id) ON DELETE CASCADE,
    protection  VARCHAR(30)  NOT NULL,
    status      VARCHAR(10)  NOT NULL DEFAULT 'PENDING',
    created_at  TIMESTAMPTZ  NOT NULL DEFAULT now(),
    decided_at  TIMESTAMPTZ,
    CONSTRAINT chk_guardian_request_protection
        CHECK (protection IN ('PUBLIC_PROFILE','SENSITIVE_CONTENT','COMMENTS_FROM_EVERYONE','NO_USAGE_REMINDER')),
    CONSTRAINT chk_guardian_request_status CHECK (status IN ('PENDING','APPROVED','DENIED','CANCELLED'))
);

CREATE UNIQUE INDEX IF NOT EXISTS uk_guardian_request_pending
    ON guardian_request (link_id, protection) WHERE status = 'PENDING';

-- Histórico append-only do vínculo: convite, aceite, decisões e mudanças feitas pelo responsável
CREATE TABLE IF NOT EXISTS guardian_action (
    id           BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    link_id      BIGINT       NOT NULL REFERENCES guardian_link (id) ON DELETE CASCADE,
    actor        VARCHAR(10)  NOT NULL,
    action       VARCHAR(40)  NOT NULL,
    detail       VARCHAR(300),
    occurred_at  TIMESTAMPTZ  NOT NULL DEFAULT now(),
    CONSTRAINT chk_guardian_action_actor CHECK (actor IN ('MINOR','GUARDIAN','SYSTEM'))
);

CREATE INDEX IF NOT EXISTS idx_guardian_action_link ON guardian_action (link_id, occurred_at DESC);

-- =============================================================================
-- RF18 · Exportar meus dados (LGPD, art. 18, V, e art. 19): um pedido por dia, gerado por um job,
-- ZIP com JSON no bucket (exports/…), link pré-assinado por 72 h e prazo de atendimento de 15 dias.
-- =============================================================================

CREATE TABLE IF NOT EXISTS data_export (
    id            UUID          PRIMARY KEY,                 -- UUIDv7 gerado na aplicação
    user_id       BIGINT        NOT NULL REFERENCES user_account (id) ON DELETE CASCADE,
    status        VARCHAR(12)   NOT NULL DEFAULT 'REQUESTED',
    requested_at  TIMESTAMPTZ   NOT NULL DEFAULT now(),
    due_at        TIMESTAMPTZ   NOT NULL,                    -- requested_at + 15 dias (art. 19, II)
    started_at    TIMESTAMPTZ,
    ready_at      TIMESTAMPTZ,
    expires_at    TIMESTAMPTZ,                               -- ready_at + 72 h: o link deixa de valer
    object_key    VARCHAR(512),                              -- exports/{usuário}/{id}.zip
    size_bytes    BIGINT,
    attempts      SMALLINT      NOT NULL DEFAULT 0,
    error         VARCHAR(300),
    CONSTRAINT chk_data_export_status CHECK (status IN ('REQUESTED','PROCESSING','READY','EXPIRED','FAILED'))
);

CREATE INDEX IF NOT EXISTS idx_data_export_user ON data_export (user_id, requested_at DESC);
CREATE INDEX IF NOT EXISTS idx_data_export_queue ON data_export (requested_at) WHERE status IN ('REQUESTED','PROCESSING');
CREATE INDEX IF NOT EXISTS idx_data_export_ready ON data_export (expires_at) WHERE status = 'READY';

-- =============================================================================
-- RF18 · Registros de acesso a aplicações (Marco Civil, art. 15; Decreto 8.771/2016, art. 13):
-- data e hora (UTC) e o IP com a porta lógica de origem, cifrados com AES-256-GCM pela chave da
-- aplicação (AppSecrets). Guardados por 6 meses, sob sigilo; a entrega exige ordem judicial.
-- user_id sem chave estrangeira: o registro sobrevive à eliminação da conta pelo prazo legal.
-- =============================================================================

CREATE TABLE IF NOT EXISTS access_log (
    id                BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    user_id           BIGINT        NOT NULL,
    event             VARCHAR(12)   NOT NULL,
    occurred_at       TIMESTAMPTZ   NOT NULL DEFAULT now(),
    ip_cipher         BYTEA         NOT NULL,                -- "ip|porta" cifrado; nunca em claro
    channel           VARCHAR(20)   NOT NULL,
    legal_hold_until  TIMESTAMPTZ,                           -- guarda estendida a pedido de autoridade (art. 15, §2º)
    CONSTRAINT chk_access_log_event CHECK (event IN ('LOGIN','ACCESS'))
);

CREATE INDEX IF NOT EXISTS idx_access_log_user ON access_log (user_id, occurred_at);
CREATE INDEX IF NOT EXISTS idx_access_log_age ON access_log (occurred_at);

COMMENT ON TABLE access_log IS 'RF18 · Marco Civil art. 15: 6 meses, sob sigilo; consulta só com motivo e ordem (access_log_query)';

-- Inventário de quem consultou os registros (Decreto 8.771, art. 13, III): append-only
CREATE TABLE IF NOT EXISTS access_log_query (
    id              BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    requested_by    VARCHAR(254)  NOT NULL,
    reason          VARCHAR(500)  NOT NULL,
    legal_order     VARCHAR(120)  NOT NULL,                  -- número do processo ou da ordem judicial
    target_user_id  BIGINT        NOT NULL,
    period_from     TIMESTAMPTZ   NOT NULL,
    period_to       TIMESTAMPTZ   NOT NULL,
    rows_returned   INTEGER       NOT NULL,
    queried_at      TIMESTAMPTZ   NOT NULL DEFAULT now()
);

CREATE INDEX IF NOT EXISTS idx_access_log_query_target ON access_log_query (target_user_id, queried_at DESC);

-- =============================================================================
-- RF18 · Incidentes de segurança (LGPD, art. 48; Resolução CD/ANPD 15/2024): todo incidente é
-- registrado, inclusive os que não exigem comunicação, e o registro fica por 5 anos.
-- Runbook em docs/legal/INCIDENTES.md.
-- =============================================================================

CREATE TABLE IF NOT EXISTS security_incident (
    id                       BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    title                    VARCHAR(200)   NOT NULL,
    description              VARCHAR(4000)  NOT NULL,
    discovered_at            TIMESTAMPTZ    NOT NULL,
    personal_data_aware_at   TIMESTAMPTZ,                     -- quando se soube que atingiu dado pessoal: o relógio
    communication_deadline   TIMESTAMPTZ,                     -- 3 dias úteis depois de personal_data_aware_at
    data_categories          JSONB          NOT NULL DEFAULT '[]',
    affected_subjects        INTEGER,
    measures                 VARCHAR(4000),
    risk_relevant            BOOLEAN,
    decision                 VARCHAR(14)    NOT NULL DEFAULT 'PENDING',
    decision_reason          VARCHAR(1000),
    anpd_notified_at         TIMESTAMPTZ,
    subjects_notified_at     TIMESTAMPTZ,
    recorded_by              VARCHAR(254)   NOT NULL,
    created_at               TIMESTAMPTZ    NOT NULL DEFAULT now(),
    retain_until             TIMESTAMPTZ    NOT NULL,         -- created_at + 5 anos
    CONSTRAINT chk_security_incident_decision CHECK (decision IN ('PENDING','NOTIFY','NOT_REQUIRED')),
    CONSTRAINT chk_security_incident_categories CHECK (jsonb_typeof(data_categories) = 'array')
);

CREATE INDEX IF NOT EXISTS idx_security_incident_open ON security_incident (communication_deadline) WHERE decision = 'PENDING';
