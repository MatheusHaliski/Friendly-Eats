
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
