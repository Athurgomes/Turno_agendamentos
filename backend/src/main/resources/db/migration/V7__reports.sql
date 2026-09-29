-- F6-1: report, report_photo, report_comment (RF-REP-01..05).
CREATE SEQUENCE report_code_seq;

CREATE TABLE report (
    id uuid PRIMARY KEY DEFAULT gen_random_uuid(),
    code varchar(20) NOT NULL UNIQUE,
    condominium_id uuid NOT NULL REFERENCES condominium (id),
    reservation_id uuid NOT NULL REFERENCES reservation (id),
    area_id uuid NOT NULL REFERENCES common_area (id),
    unit_id uuid NOT NULL REFERENCES unit (id),
    resident_id uuid NOT NULL REFERENCES resident (id),
    resident_name_snapshot varchar(150) NOT NULL,
    category varchar(15) NOT NULL,
    description text NOT NULL,
    status varchar(15) NOT NULL DEFAULT 'OPEN',
    status_reason varchar(500),
    maintenance_cost numeric(10, 2),
    resolved_at timestamptz,
    version int NOT NULL DEFAULT 0,
    created_at timestamptz NOT NULL DEFAULT now(),
    updated_at timestamptz NOT NULL DEFAULT now(),
    deleted_at timestamptz,
    -- RN-35
    CONSTRAINT report_category_check
        CHECK (category IN ('DAMAGE', 'MALFUNCTION', 'CLEANLINESS', 'SAFETY', 'MISSING_ITEM', 'OTHER')),
    -- RN-35
    CONSTRAINT report_description_min_length_check CHECK (char_length(description) >= 10),
    -- RN-36
    CONSTRAINT report_status_check
        CHECK (status IN ('OPEN', 'IN_REVIEW', 'IN_MAINTENANCE', 'RESOLVED', 'DISMISSED')),
    -- RN-36: descartar exige justificativa.
    CONSTRAINT report_dismissed_requires_reason_check
        CHECK (status <> 'DISMISSED' OR status_reason IS NOT NULL),
    -- RN-37: custo de manutencao so ao resolver, e nao negativo.
    CONSTRAINT report_maintenance_cost_check
        CHECK (maintenance_cost IS NULL OR (maintenance_cost >= 0 AND status = 'RESOLVED'))
);

CREATE INDEX report_status_created_at_idx ON report (status, created_at);
CREATE INDEX report_area_id_created_at_idx ON report (area_id, created_at);
CREATE INDEX report_unit_id_created_at_idx ON report (unit_id, created_at);
CREATE INDEX report_reservation_id_idx ON report (reservation_id);

-- RN-35: ate 5 fotos por report, nos estagios "reportado" e "reparo".
CREATE TABLE report_photo (
    id uuid PRIMARY KEY DEFAULT gen_random_uuid(),
    report_id uuid NOT NULL REFERENCES report (id),
    storage_key varchar(255) NOT NULL,
    content_type varchar(30) NOT NULL,
    stage varchar(10) NOT NULL,
    uploaded_by uuid NOT NULL REFERENCES user_account (id),
    created_at timestamptz NOT NULL DEFAULT now(),
    deleted_at timestamptz,
    CONSTRAINT report_photo_stage_check CHECK (stage IN ('REPORTED', 'REPAIR'))
);

CREATE INDEX report_photo_report_id_idx ON report_photo (report_id);

-- RF-REP-03: comentarios do SYNDIC/ADMIN, opcionalmente visiveis ao morador.
CREATE TABLE report_comment (
    id uuid PRIMARY KEY DEFAULT gen_random_uuid(),
    report_id uuid NOT NULL REFERENCES report (id),
    author_id uuid NOT NULL REFERENCES user_account (id),
    text varchar(2000) NOT NULL,
    visible_to_resident boolean NOT NULL DEFAULT false,
    created_at timestamptz NOT NULL DEFAULT now()
);

CREATE INDEX report_comment_report_id_created_at_idx ON report_comment (report_id, created_at);
