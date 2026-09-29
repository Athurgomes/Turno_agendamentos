-- F1-1: user_account e refresh_token + FK de audit_log.actor_id.
-- unit_id fica sem FK aqui: a tabela unit so existe a partir da V3.
CREATE TABLE user_account (
    id uuid PRIMARY KEY DEFAULT gen_random_uuid(),
    condominium_id uuid NOT NULL REFERENCES condominium (id),
    role varchar(10) NOT NULL,
    username varchar(30),
    email varchar(150),
    display_name varchar(150),
    phone varchar(20),
    unit_id uuid,
    password_hash varchar(100) NOT NULL,
    temp_password boolean NOT NULL DEFAULT false,
    failed_attempts int NOT NULL DEFAULT 0,
    locked_until timestamptz,
    active boolean NOT NULL DEFAULT true,
    token_version int NOT NULL DEFAULT 0,
    created_at timestamptz NOT NULL DEFAULT now(),
    updated_at timestamptz NOT NULL DEFAULT now(),
    CONSTRAINT user_account_role_check CHECK (role IN ('ADMIN', 'SYNDIC', 'UNIT')),
    -- RN-02: conta UNIT precisa de unidade e username.
    CONSTRAINT user_account_unit_requires_unit_and_username_check
        CHECK (role <> 'UNIT' OR (unit_id IS NOT NULL AND username IS NOT NULL)),
    -- RN-06: conta ADMIN/SYNDIC precisa de e-mail.
    CONSTRAINT user_account_admin_syndic_requires_email_check
        CHECK (role = 'UNIT' OR email IS NOT NULL)
);

-- RN-02: username unico por condominio (gravado em minusculas pela aplicacao;
-- indice em lower(username) torna a unicidade case-insensitive e dispensa
-- normalizacao adicional no banco).
CREATE UNIQUE INDEX user_account_condominium_username_idx
    ON user_account (condominium_id, lower(username))
    WHERE username IS NOT NULL;

-- RN-06: e-mail unico globalmente quando presente (ADMIN/SYNDIC).
CREATE UNIQUE INDEX user_account_email_idx
    ON user_account (lower(email))
    WHERE email IS NOT NULL;

CREATE INDEX user_account_unit_id_idx ON user_account (unit_id);

-- RNF-02: refresh token so e guardado como hash (SHA-256 hex, 64 chars).
CREATE TABLE refresh_token (
    id uuid PRIMARY KEY DEFAULT gen_random_uuid(),
    user_id uuid NOT NULL REFERENCES user_account (id),
    token_hash varchar(64) NOT NULL UNIQUE,
    expires_at timestamptz NOT NULL,
    revoked_at timestamptz,
    created_at timestamptz NOT NULL DEFAULT now()
);

CREATE INDEX refresh_token_user_id_idx ON refresh_token (user_id);

ALTER TABLE audit_log
    ADD CONSTRAINT audit_log_actor_id_fkey FOREIGN KEY (actor_id) REFERENCES user_account (id);
