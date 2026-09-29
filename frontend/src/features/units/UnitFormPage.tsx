/**
 * Página de cadastro/edição de unidade (RF-UNI-01, RF-UNI-03). `/admin/unidades/nova`
 * cria; `/admin/unidades/{id}/editar` edita (bloco/número fixos, D-43). Ao
 * criar, mostra o modal de credenciais (RF-UNI-02) e só sai da tela quando o
 * ADMIN fecha o modal — assim a senha é lida antes de qualquer navegação.
 */
import { useState } from "react";
import { useNavigate, useParams } from "react-router-dom";
import { CredentialsModal } from "./CredentialsModal";
import { UnitForm } from "./UnitForm";
import { useCreateUnit, useUnitQuery, useUpdateUnit } from "./hooks";
import { buildCreatePayload, buildEditPayload, type UnitFormValues } from "./residentForm";
import { ApiError } from "../../shared/api/client";
import type { Credentials } from "../../shared/api/types";

function toFormValues(unit: {
  residents: { id: string; name: string; phone: string; email: string | null; cpf: string | null; primary: boolean }[];
}): UnitFormValues {
  return {
    block: "",
    number: "",
    residents: unit.residents.map((resident) => ({
      id: resident.id,
      name: resident.name,
      phone: resident.phone,
      email: resident.email ?? "",
      cpf: resident.cpf ?? "",
      primary: resident.primary,
    })),
  };
}

export function CreateUnitPage() {
  const navigate = useNavigate();
  const createUnit = useCreateUnit();
  const [formError, setFormError] = useState<string | null>(null);
  const [credentials, setCredentials] = useState<Credentials | null>(null);
  const [primaryResident, setPrimaryResident] = useState<{ name: string; phone: string } | null>(
    null,
  );

  async function handleSubmit(values: UnitFormValues) {
    setFormError(null);
    const payload = buildCreatePayload(values);
    try {
      const result = await createUnit.mutateAsync(payload);
      setCredentials(result.credentials);
      setPrimaryResident({ name: payload.primary.name, phone: payload.primary.phone });
    } catch (err) {
      setFormError(err instanceof ApiError ? err.detail : "Não foi possível cadastrar a unidade. Tente novamente.");
    }
  }

  return (
    <div className="mx-auto max-w-2xl">
      <h1 className="mb-6 text-xl font-semibold text-neutral-900 sm:text-2xl">
        Nova unidade
      </h1>
      <UnitForm
        defaultValues={{ block: "", number: "", residents: [{ name: "", phone: "", email: "", cpf: "", primary: true }] }}
        showUnitFields
        submitLabel="Cadastrar unidade"
        submittingLabel="Cadastrando…"
        onSubmit={handleSubmit}
        formError={formError}
      />
      <CredentialsModal
        open={!!credentials}
        credentials={credentials}
        residentName={primaryResident?.name ?? ""}
        residentPhone={primaryResident?.phone ?? ""}
        onClose={() => {
          setCredentials(null);
          navigate("/admin/unidades");
        }}
      />
    </div>
  );
}

export function EditUnitPage() {
  const { id } = useParams<{ id: string }>();
  const navigate = useNavigate();
  const { data: unit, isLoading } = useUnitQuery(id);
  const updateUnit = useUpdateUnit(id ?? "");
  const [formError, setFormError] = useState<string | null>(null);

  if (isLoading || !unit) {
    return <p className="text-sm text-neutral-600">Carregando unidade…</p>;
  }

  async function handleSubmit(values: UnitFormValues) {
    setFormError(null);
    try {
      await updateUnit.mutateAsync(buildEditPayload(values));
      navigate("/admin/unidades");
    } catch (err) {
      setFormError(err instanceof ApiError ? err.detail : "Não foi possível salvar as alterações. Tente novamente.");
    }
  }

  return (
    <div className="mx-auto max-w-2xl">
      <h1 className="mb-1 text-xl font-semibold text-neutral-900 sm:text-2xl">
        Editar unidade {unit.identifier}
      </h1>
      <p className="mb-6 text-sm text-neutral-600">
        Bloco e número não podem ser alterados aqui. Para corrigi-los, desative esta unidade e
        cadastre uma nova.
      </p>
      <UnitForm
        defaultValues={toFormValues(unit)}
        showUnitFields={false}
        submitLabel="Salvar alterações"
        submittingLabel="Salvando…"
        onSubmit={handleSubmit}
        formError={formError}
      />
    </div>
  );
}
