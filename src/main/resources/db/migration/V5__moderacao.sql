
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
