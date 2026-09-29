-- F2-1: unit, resident + FK user_account.unit_id.
CREATE TABLE unit (
    id uuid PRIMARY KEY DEFAULT gen_random_uuid(),
    condominium_id uuid NOT NULL REFERENCES condominium (id),
    block varchar(10),
    number varchar(10) NOT NULL,
    identifier varchar(30) NOT NULL,
    active boolean NOT NULL DEFAULT true,
    deleted_at timestamptz,
    created_at timestamptz NOT NULL DEFAULT now(),
    updated_at timestamptz NOT NULL DEFAULT now()
);

-- RN-02: identifier unico por condominio apenas entre unidades nao excluidas
-- (unicidade parcial, nao total): D-43 permite "desativar e cadastrar de novo"
-- para corrigir uma unidade, entao uma unidade soft-deleted precisa liberar o
-- identifier para uma nova unidade. Consequencia para o backend: o
-- user_account.username (V2) e unico por condominio SEM filtro de soft delete
-- (a tabela user_account nao tem deleted_at) -- se a aplicacao recriar a
-- unidade com o mesmo username da conta antiga, ela precisa desativar/liberar
-- (ex.: trocar username ou marcar active=false) a conta antiga antes de criar
-- a nova, senao esbarra em user_account_condominium_username_idx. Registrar
-- isso como responsabilidade do modulo unit na Fase 2 (fora do escopo desta
-- migration).
CREATE UNIQUE INDEX unit_condominium_identifier_idx
    ON unit (condominium_id, lower(identifier))
    WHERE deleted_at IS NULL;

CREATE TABLE resident (
    id uuid PRIMARY KEY DEFAULT gen_random_uuid(),
    unit_id uuid NOT NULL REFERENCES unit (id),
    name varchar(150) NOT NULL,
    phone varchar(20) NOT NULL,
    email varchar(150),
    cpf char(11),
    is_primary boolean NOT NULL DEFAULT false,
    deleted_at timestamptz,
    created_at timestamptz NOT NULL DEFAULT now(),
    updated_at timestamptz NOT NULL DEFAULT now(),
    CONSTRAINT resident_phone_digits_check CHECK (phone ~ '^[0-9]{12,13}$'),
    CONSTRAINT resident_cpf_digits_check CHECK (cpf IS NULL OR cpf ~ '^[0-9]{11}$'),
    -- RN-07: morador principal precisa de e-mail e CPF.
    CONSTRAINT resident_primary_requires_email_and_cpf_check
        CHECK (NOT is_primary OR (email IS NOT NULL AND cpf IS NOT NULL))
);

-- RN-07: exatamente 1 morador principal ativo por unidade.
CREATE UNIQUE INDEX resident_unit_primary_idx
    ON resident (unit_id)
    WHERE is_primary AND deleted_at IS NULL;

-- RN-08: CPF unico por unidade entre moradores ativos.
CREATE UNIQUE INDEX resident_unit_cpf_idx
    ON resident (unit_id, cpf)
    WHERE cpf IS NOT NULL AND deleted_at IS NULL;

CREATE INDEX resident_unit_id_idx ON resident (unit_id);
CREATE INDEX resident_name_idx ON resident (lower(name));

ALTER TABLE user_account
    ADD CONSTRAINT user_account_unit_id_fkey FOREIGN KEY (unit_id) REFERENCES unit (id);
