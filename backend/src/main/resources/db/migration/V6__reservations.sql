-- F4-1: reservation, reservation_event, exclusion constraint anti-sobreposicao.
CREATE SEQUENCE reservation_code_seq;

CREATE TABLE reservation (
    id uuid PRIMARY KEY DEFAULT gen_random_uuid(),
    code varchar(20) NOT NULL UNIQUE,
    condominium_id uuid NOT NULL REFERENCES condominium (id),
    area_id uuid NOT NULL REFERENCES common_area (id),
    kind varchar(10) NOT NULL,
    unit_id uuid REFERENCES unit (id),
    resident_id uuid REFERENCES resident (id),
    resident_name_snapshot varchar(150),
    resident_phone_snapshot varchar(20),
    start_at timestamptz NOT NULL,
    end_at timestamptz NOT NULL,
    guests int,
    notes varchar(500),
    status varchar(20) NOT NULL,
    status_reason varchar(500),
    requires_payment_snapshot boolean NOT NULL DEFAULT false,
    price_snapshot numeric(10, 2),
    payment_confirmed_at timestamptz,
    payment_confirmed_by uuid REFERENCES user_account (id),
    cancelled_by varchar(10),
    cancelled_at timestamptz,
    created_by uuid NOT NULL REFERENCES user_account (id),
    version int NOT NULL DEFAULT 0,
    created_at timestamptz NOT NULL DEFAULT now(),
    updated_at timestamptz NOT NULL DEFAULT now(),
    CONSTRAINT reservation_kind_check CHECK (kind IN ('BOOKING', 'BLOCK')),
    -- RN-32
    CONSTRAINT reservation_status_check
        CHECK (status IN ('PENDING_PAYMENT', 'CONFIRMED', 'CANCELLED')),
    CONSTRAINT reservation_cancelled_by_check
        CHECK (cancelled_by IS NULL OR cancelled_by IN ('RESIDENT', 'ADMIN', 'SYSTEM')),
    CONSTRAINT reservation_end_after_start_check CHECK (end_at > start_at),
    -- D-08: BOOKING exige unidade/morador/snapshot; BLOCK nao os tem.
    CONSTRAINT reservation_booking_requires_unit_and_resident_check
        CHECK (kind <> 'BOOKING' OR (
            unit_id IS NOT NULL
            AND resident_id IS NOT NULL
            AND resident_name_snapshot IS NOT NULL
            AND guests IS NOT NULL
        )),
    CONSTRAINT reservation_guests_positive_check CHECK (guests IS NULL OR guests >= 1),
    -- RN-25
    CONSTRAINT reservation_price_snapshot_positive_check
        CHECK (NOT requires_payment_snapshot OR price_snapshot > 0)
);

-- RN-24: sem sobreposicao na mesma area para reservas/bloqueios ativos
-- (PENDING_PAYMENT ou CONFIRMED), intervalo semiaberto [inicio, fim).
ALTER TABLE reservation
    ADD CONSTRAINT reservation_no_overlap
    EXCLUDE USING gist (
        area_id WITH =,
        tstzrange(start_at, end_at, '[)') WITH &&
    )
    WHERE (status IN ('PENDING_PAYMENT', 'CONFIRMED'));

CREATE INDEX reservation_area_id_start_at_idx ON reservation (area_id, start_at);
CREATE INDEX reservation_unit_id_start_at_idx ON reservation (unit_id, start_at);
CREATE INDEX reservation_status_start_at_idx ON reservation (status, start_at);

-- RF-RES-09: historico da reserva.
CREATE TABLE reservation_event (
    id uuid PRIMARY KEY DEFAULT gen_random_uuid(),
    reservation_id uuid NOT NULL REFERENCES reservation (id),
    type varchar(30) NOT NULL,
    changes jsonb,
    justification varchar(500),
    actor_id uuid REFERENCES user_account (id),
    occurred_at timestamptz NOT NULL DEFAULT now(),
    CONSTRAINT reservation_event_type_check
        CHECK (type IN ('CREATED', 'PAYMENT_CONFIRMED', 'UPDATED', 'CANCELLED', 'EXPIRED'))
);

CREATE INDEX reservation_event_reservation_id_occurred_at_idx
    ON reservation_event (reservation_id, occurred_at);
