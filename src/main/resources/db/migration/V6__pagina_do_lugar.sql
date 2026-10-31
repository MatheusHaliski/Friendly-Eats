
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
