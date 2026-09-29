-- F3-1: common_area, area_opening_hours, area_inspection, area_photo.
CREATE TABLE common_area (
    id uuid PRIMARY KEY DEFAULT gen_random_uuid(),
    condominium_id uuid NOT NULL REFERENCES condominium (id),
    name varchar(100) NOT NULL,
    category varchar(20) NOT NULL,
    description text NOT NULL,
    rules text NOT NULL,
    conduct_guidelines text NOT NULL,
    capacity int NOT NULL,
    status varchar(15) NOT NULL DEFAULT 'ACTIVE',
    requires_payment boolean NOT NULL DEFAULT false,
    price numeric(10, 2),
    payment_whatsapp varchar(20),
    deleted_at timestamptz,
    version int NOT NULL DEFAULT 0,
    created_at timestamptz NOT NULL DEFAULT now(),
    updated_at timestamptz NOT NULL DEFAULT now(),
    -- RN-13
    CONSTRAINT common_area_category_check CHECK (category IN (
        'PARTY_ROOM', 'BARBECUE', 'GOURMET_SPACE', 'POOL', 'SPORTS_COURT', 'TENNIS_COURT',
        'GYM', 'GAME_ROOM', 'TOY_ROOM', 'PLAYGROUND', 'SAUNA', 'CINEMA', 'COWORKING',
        'PET_PLACE', 'OTHER'
    )),
    -- RN-14
    CONSTRAINT common_area_status_check
        CHECK (status IN ('ACTIVE', 'MAINTENANCE', 'RENOVATION', 'INTERDICTED', 'INACTIVE')),
    -- RN-11
    CONSTRAINT common_area_capacity_positive_check CHECK (capacity > 0),
    -- RN-12
    CONSTRAINT common_area_payment_requirements_check
        CHECK (NOT requires_payment OR (price > 0 AND payment_whatsapp IS NOT NULL)),
    CONSTRAINT common_area_payment_whatsapp_digits_check
        CHECK (payment_whatsapp IS NULL OR payment_whatsapp ~ '^[0-9]{12,13}$')
);

-- RN-15: soft delete; listagens filtram por condominio entre areas ativas.
CREATE INDEX common_area_condominium_id_idx
    ON common_area (condominium_id)
    WHERE deleted_at IS NULL;

CREATE TABLE area_opening_hours (
    id uuid PRIMARY KEY DEFAULT gen_random_uuid(),
    area_id uuid NOT NULL REFERENCES common_area (id),
    day_of_week smallint NOT NULL,
    open_time time NOT NULL,
    close_time time NOT NULL,
    CONSTRAINT area_opening_hours_day_of_week_check CHECK (day_of_week BETWEEN 1 AND 7),
    -- D-44: sem virar a meia-noite no MVP.
    CONSTRAINT area_opening_hours_close_after_open_check CHECK (close_time > open_time),
    -- D-44: horarios em multiplos de 30 minutos, sem segundos.
    CONSTRAINT area_opening_hours_open_time_step_check
        CHECK (extract(minute FROM open_time)::int IN (0, 30) AND extract(second FROM open_time) = 0),
    CONSTRAINT area_opening_hours_close_time_step_check
        CHECK (extract(minute FROM close_time)::int IN (0, 30) AND extract(second FROM close_time) = 0)
);

-- Dia sem registro = area fechada nesse dia.
CREATE UNIQUE INDEX area_opening_hours_area_day_idx ON area_opening_hours (area_id, day_of_week);

CREATE TABLE area_inspection (
    id uuid PRIMARY KEY DEFAULT gen_random_uuid(),
    area_id uuid NOT NULL REFERENCES common_area (id),
    inspected_at date NOT NULL,
    overall_condition varchar(5) NOT NULL,
    notes text,
    author_id uuid NOT NULL REFERENCES user_account (id),
    created_at timestamptz NOT NULL DEFAULT now(),
    CONSTRAINT area_inspection_overall_condition_check
        CHECK (overall_condition IN ('GOOD', 'FAIR', 'POOR'))
);

CREATE INDEX area_inspection_area_id_inspected_at_idx
    ON area_inspection (area_id, inspected_at DESC);

CREATE TABLE area_photo (
    id uuid PRIMARY KEY DEFAULT gen_random_uuid(),
    area_id uuid NOT NULL REFERENCES common_area (id),
    inspection_id uuid REFERENCES area_inspection (id),
    storage_key varchar(255) NOT NULL UNIQUE,
    content_type varchar(30) NOT NULL,
    caption varchar(200),
    featured boolean NOT NULL DEFAULT false,
    archived boolean NOT NULL DEFAULT false,
    taken_at date NOT NULL,
    uploaded_by uuid NOT NULL REFERENCES user_account (id),
    created_at timestamptz NOT NULL DEFAULT now(),
    CONSTRAINT area_photo_content_type_check
        CHECK (content_type IN ('image/jpeg', 'image/png', 'image/webp')),
    -- RN-17: fotos nunca sao sobrescritas; featured e archived sao exclusivos.
    CONSTRAINT area_photo_not_featured_and_archived_check CHECK (NOT (featured AND archived))
);

CREATE INDEX area_photo_area_id_taken_at_idx ON area_photo (area_id, taken_at DESC);
