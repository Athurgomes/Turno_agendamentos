/**
 * Páginas de cadastro (`/areas/nova`, ADMIN) e edição (`/areas/:id/editar`,
 * SYNDIC/ADMIN, D-20) de área. O ADMIN usa o formulário completo (`AreaForm`);
 * o SYNDIC vê só os campos que pode alterar (`SyndicAreaEditForm`).
 */
import { useState } from "react";
import { useNavigate, useParams } from "react-router-dom";
import { useSession } from "../auth/useSession";
import { useSettingsQuery } from "../admin/settings/hooks";
import { AreaForm } from "./AreaForm";
import { SyndicAreaEditForm } from "./SyndicAreaEditForm";
import {
  useAreaCategoriesQuery,
  useAreaQuery,
  useCreateArea,
  useUpdateArea,
} from "./hooks";
import {
  buildAreaPayload,
  buildSyndicEditPayload,
  defaultSchedule,
  scheduleFromOpeningHours,
  type AreaFormValues,
} from "./areaFormSchema";
import { ApiError } from "../../shared/api/client";
import type { AreaDetail } from "../../shared/api/types";

function emptyFormValues(): AreaFormValues {
  return {
    name: "",
    category: "",
    description: "",
    rules: "",
    conductGuidelines: "",
    capacity: 1,
    requiresPayment: false,
    price: "",
    paymentWhatsapp: "",
    schedule: defaultSchedule(),
  };
}

function formValuesFromArea(area: AreaDetail): AreaFormValues {
  return {
    name: area.name,
    category: area.category,
    description: area.description,
    rules: area.rules,
    conductGuidelines: area.conductGuidelines,
    capacity: area.capacity,
    requiresPayment: area.requiresPayment,
    price: area.price ?? "",
    paymentWhatsapp: area.paymentWhatsapp ?? "",
    schedule: scheduleFromOpeningHours(area.openingHours),
  };
}

export function CreateAreaPage() {
  const navigate = useNavigate();
  const { data: categories } = useAreaCategoriesQuery();
  const { data: settings } = useSettingsQuery();
  const createArea = useCreateArea();
  const [formError, setFormError] = useState<string | null>(null);

  async function handleSubmit(values: AreaFormValues, photos: File[]) {
    setFormError(null);
    try {
      const area = await createArea.mutateAsync({ payload: buildAreaPayload(values), photos });
      navigate(`/areas/${area.id}`);
    } catch (err) {
      setFormError(
        err instanceof ApiError ? err.detail : "Não foi possível cadastrar a área. Tente novamente.",
      );
    }
  }

  return (
    <div className="mx-auto max-w-2xl">
      <h1 className="mb-6 text-xl font-semibold text-neutral-900 sm:text-2xl">Nova área</h1>
      <AreaForm
        categories={categories ?? []}
        defaultValues={emptyFormValues()}
        showPhotos
        defaultPaymentWhatsapp={settings?.defaultPaymentWhatsapp ?? undefined}
        submitLabel="Cadastrar área"
        submittingLabel="Cadastrando…"
        onSubmit={handleSubmit}
        formError={formError}
      />
    </div>
  );
}

export function EditAreaPage() {
  const { id } = useParams<{ id: string }>();
  const navigate = useNavigate();
  const { user } = useSession();
  const { data: categories } = useAreaCategoriesQuery();
  const { data: area, isLoading } = useAreaQuery(id);
  const updateArea = useUpdateArea(id ?? "");
  const [formError, setFormError] = useState<string | null>(null);

  if (isLoading || !area) {
    return <p className="text-sm text-neutral-600">Carregando área…</p>;
  }

  async function handleAdminSubmit(values: AreaFormValues) {
    setFormError(null);
    try {
      await updateArea.mutateAsync(buildAreaPayload(values));
      navigate(`/areas/${id}`);
    } catch (err) {
      setFormError(
        err instanceof ApiError ? err.detail : "Não foi possível salvar as alterações. Tente novamente.",
      );
    }
  }

  async function handleSyndicSubmit(values: Parameters<typeof buildSyndicEditPayload>[0]) {
    setFormError(null);
    try {
      await updateArea.mutateAsync(buildSyndicEditPayload(values));
      navigate(`/areas/${id}`);
    } catch (err) {
      setFormError(
        err instanceof ApiError ? err.detail : "Não foi possível salvar as alterações. Tente novamente.",
      );
    }
  }

  return (
    <div className="mx-auto max-w-2xl">
      <h1 className="mb-6 text-xl font-semibold text-neutral-900 sm:text-2xl">
        Editar {area.name}
      </h1>
      {user?.role === "ADMIN" ? (
        <AreaForm
          categories={categories ?? []}
          defaultValues={formValuesFromArea(area)}
          showPhotos={false}
          submitLabel="Salvar alterações"
          submittingLabel="Salvando…"
          onSubmit={handleAdminSubmit}
          formError={formError}
        />
      ) : (
        <SyndicAreaEditForm
          defaultValues={{
            description: area.description,
            rules: area.rules,
            conductGuidelines: area.conductGuidelines,
            capacity: area.capacity,
          }}
          onSubmit={handleSyndicSubmit}
          formError={formError}
        />
      )}
    </div>
  );
}
