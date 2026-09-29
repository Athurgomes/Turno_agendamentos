-- F0-3: extensao btree_gist (usada pela exclusion constraint anti-sobreposicao
-- de reservas, V5) + tabelas condominium, condominium_settings e audit_log.
-- audit_log.actor_id fica sem FK aqui: user_account so existe a partir da V2.
CREATE EXTENSION IF NOT EXISTS btree_gist;

CREATE TABLE condominium (
    id uuid PRIMARY KEY DEFAULT gen_random_uuid(),
    name varchar(150) NOT NULL,
    timezone varchar(50) NOT NULL DEFAULT 'America/Sao_Paulo',
    default_payment_whatsapp varchar(20),
    created_at timestamptz NOT NULL DEFAULT now(),
    updated_at timestamptz NOT NULL DEFAULT now(),
    CONSTRAINT condominium_whatsapp_digits_check
        CHECK (default_payment_whatsapp IS NULL OR default_payment_whatsapp ~ '^[0-9]{12,13}$')
);

-- RN-20..22, RN-30, RN-19, RN-34: "motor de regras" v0, 1:1 com condominium.
CREATE TABLE condominium_settings (
    condominium_id uuid PRIMARY KEY REFERENCES condominium (id),
    min_advance_days int NOT NULL DEFAULT 1,
    next_day_window_start time NOT NULL DEFAULT '06:00',
    next_day_window_end time NOT NULL DEFAULT '16:00',
    max_advance_days int NOT NULL DEFAULT 60,
    max_active_bookings_per_unit int NOT NULL DEFAULT 3,
    resident_cancel_deadline_hours int NOT NULL DEFAULT 24,
    slot_minutes int NOT NULL DEFAULT 30,
    report_window_days int NOT NULL DEFAULT 7,
    created_at timestamptz NOT NULL DEFAULT now(),
    updated_at timestamptz NOT NULL DEFAULT now(),
    CONSTRAINT condominium_settings_min_advance_days_check CHECK (min_advance_days >= 0),
    CONSTRAINT condominium_settings_max_advance_days_check CHECK (max_advance_days >= min_advance_days),
    CONSTRAINT condominium_settings_next_day_window_check CHECK (next_day_window_start < next_day_window_end),
    CONSTRAINT condominium_settings_max_active_bookings_check CHECK (max_active_bookings_per_unit >= 0),
    CONSTRAINT condominium_settings_resident_cancel_deadline_check CHECK (resident_cancel_deadline_hours >= 0),
    CONSTRAINT condominium_settings_slot_minutes_check CHECK (slot_minutes > 0),
    CONSTRAINT condominium_settings_report_window_days_check CHECK (report_window_days >= 0)
);

-- RNF-07: trilha de auditoria. actor_id sem FK ate a V2 criar user_account.
CREATE TABLE audit_log (
    id uuid PRIMARY KEY DEFAULT gen_random_uuid(),
    condominium_id uuid NOT NULL REFERENCES condominium (id),
    actor_id uuid,
    actor_role varchar(10),
    action varchar(50) NOT NULL,
    entity_type varchar(30) NOT NULL,
    entity_id uuid,
    details jsonb,
    justification varchar(500),
    occurred_at timestamptz NOT NULL
);

CREATE INDEX audit_log_entity_idx ON audit_log (entity_type, entity_id);
CREATE INDEX audit_log_condominium_occurred_idx ON audit_log (condominium_id, occurred_at);
