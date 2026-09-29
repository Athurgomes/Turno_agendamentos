/**
 * "Minha unidade" (RF-UNI-05, RF-UNI-06, RN-09): a conta UNIT vê os
 * moradores da própria unidade e edita nome/telefone/e-mail de qualquer um,
 * adiciona adicionais e remove adicionais. O CPF chega mascarado
 * (`***.456.789-**`) e nunca é editável por aqui (RNF-01); trocar o
 * principal ou o CPF de alguém é assunto da administração (RN-07).
 */
import { useState } from "react";
import { useForm } from "react-hook-form";
import { zodResolver } from "@hookform/resolvers/zod";
import { ConfirmDialog } from "../../shared/components/ConfirmDialog";
import { ApiError } from "../../shared/api/client";
import { formatPhone } from "../../shared/utils/format";
import {
  useAddMyUnitResident,
  useMyUnitQuery,
  useRemoveMyUnitResident,
  useUpdateMyUnitResident,
} from "./hooks";
import {
  addMyResidentSchema,
  buildAddMyResidentPayload,
  buildEditMyResidentPayload,
  editMyResidentSchema,
  type AddMyResidentValues,
  type EditMyResidentValues,
} from "./residentForm";
import type { ResidentDto } from "../../shared/api/types";

function fieldClassName(hasError: boolean) {
  return [
    "h-11 w-full rounded-sm border bg-neutral-0 px-3 text-base text-neutral-900",
    "focus-visible:outline-2 focus-visible:outline-offset-2 focus-visible:outline-primary-600",
    hasError ? "border-danger-600" : "border-neutral-300",
  ].join(" ");
}

function genericErrorMessage(err: unknown, fallback: string): string {
  return err instanceof ApiError ? err.detail : fallback;
}

export function MyUnitPage() {
  const { data: unit, isLoading } = useMyUnitQuery();
  const addResident = useAddMyUnitResident();
  const removeResident = useRemoveMyUnitResident();

  const [editingId, setEditingId] = useState<string | null>(null);
  const [residentToRemove, setResidentToRemove] = useState<ResidentDto | null>(null);
  const [removeError, setRemoveError] = useState<string | null>(null);
  const [addError, setAddError] = useState<string | null>(null);

  async function handleConfirmRemove() {
    if (!residentToRemove) return;
    setRemoveError(null);
    try {
      await removeResident.mutateAsync(residentToRemove.id);
      setResidentToRemove(null);
    } catch (err) {
      setRemoveError(genericErrorMessage(err, "Não foi possível remover este morador. Tente novamente."));
    }
  }

  if (isLoading) {
    return <p className="text-sm text-neutral-600">Carregando dados da unidade…</p>;
  }

  if (!unit) {
    return <p className="text-sm text-neutral-600">Não foi possível carregar sua unidade.</p>;
  }

  const residents = unit.residents;

  return (
    <div className="mx-auto max-w-2xl">
      <div className="mb-6">
        <h1 className="text-xl font-semibold text-neutral-900 sm:text-2xl">
          Unidade {unit.identifier}
        </h1>
        <p className="text-sm text-neutral-600">Usuário: {unit.username}</p>
      </div>

      <section aria-labelledby="residents-heading" className="mb-8">
        <h2 id="residents-heading" className="mb-3 text-lg font-semibold text-neutral-900">
          Moradores
        </h2>
        <p className="mb-4 text-sm text-neutral-600">
          Trocar o morador principal ou o CPF de alguém só pode ser feito pela administração do
          condomínio.
        </p>

        <ul className="flex flex-col gap-3">
          {residents.map((resident) => (
            <li key={resident.id} className="rounded-md border border-neutral-200 bg-neutral-0 p-4">
              {editingId === resident.id ? (
                <ResidentEditForm
                  resident={resident}
                  onCancel={() => setEditingId(null)}
                  onSaved={() => setEditingId(null)}
                />
              ) : (
                <ResidentCard
                  resident={resident}
                  onEdit={() => setEditingId(resident.id)}
                  onRemove={() => setResidentToRemove(resident)}
                />
              )}
            </li>
          ))}
        </ul>
      </section>

      <section aria-labelledby="add-resident-heading">
        <h2 id="add-resident-heading" className="mb-3 text-lg font-semibold text-neutral-900">
          Adicionar morador adicional
        </h2>
        <AddResidentForm
          onSubmit={async (values) => {
            setAddError(null);
            try {
              await addResident.mutateAsync(buildAddMyResidentPayload(values));
            } catch (err) {
              setAddError(genericErrorMessage(err, "Não foi possível adicionar este morador. Tente novamente."));
              throw err;
            }
          }}
          error={addError}
        />
      </section>

      <ConfirmDialog
        open={!!residentToRemove}
        title="Remover morador"
        message={
          removeError ??
          `Tem certeza que deseja remover ${residentToRemove?.name ?? ""} da unidade?`
        }
        confirmLabel="Remover"
        isLoading={removeResident.isPending}
        onConfirm={() => void handleConfirmRemove()}
        onCancel={() => {
          setResidentToRemove(null);
          setRemoveError(null);
        }}
      />
    </div>
  );
}

function ResidentCard({
  resident,
  onEdit,
  onRemove,
}: {
  resident: ResidentDto;
  onEdit: () => void;
  onRemove: () => void;
}) {
  return (
    <div className="flex flex-col gap-2 sm:flex-row sm:items-start sm:justify-between">
      <dl className="text-sm text-neutral-700">
        <div className="flex items-center gap-2">
          <dt className="sr-only">Nome</dt>
          <dd className="text-base font-medium text-neutral-900">{resident.name}</dd>
          {resident.primary && (
            <span className="rounded-pill bg-status-active-bg px-2 py-0.5 text-xs text-status-active-text">
              Principal
            </span>
          )}
        </div>
        <div>
          <dt className="inline font-medium">Telefone: </dt>
          <dd className="inline">{formatPhone(resident.phone)}</dd>
        </div>
        <div>
          <dt className="inline font-medium">E-mail: </dt>
          <dd className="inline">{resident.email ?? "Não informado"}</dd>
        </div>
        <div>
          <dt className="inline font-medium">CPF: </dt>
          <dd className="inline">{resident.cpf ?? "Não informado"}</dd>
        </div>
      </dl>
      <div className="flex gap-2">
        <button
          type="button"
          onClick={onEdit}
          className="rounded-md border border-neutral-300 px-3 py-1.5 text-sm font-medium text-neutral-700 hover:bg-neutral-100"
        >
          Editar
        </button>
        {!resident.primary && (
          <button
            type="button"
            onClick={onRemove}
            className="rounded-md border border-danger-700 px-3 py-1.5 text-sm font-medium text-danger-700 hover:bg-danger-50"
          >
            Remover
          </button>
        )}
      </div>
    </div>
  );
}

function ResidentEditForm({
  resident,
  onCancel,
  onSaved,
}: {
  resident: ResidentDto;
  onCancel: () => void;
  onSaved: () => void;
}) {
  const updateResident = useUpdateMyUnitResident();
  const [formError, setFormError] = useState<string | null>(null);
  const schema = editMyResidentSchema(resident.primary);
  const {
    register,
    handleSubmit,
    formState: { errors, isSubmitting },
  } = useForm<EditMyResidentValues>({
    resolver: zodResolver(schema),
    defaultValues: {
      name: resident.name,
      phone: resident.phone,
      email: resident.email ?? "",
    },
  });

  async function onSubmit(values: EditMyResidentValues) {
    setFormError(null);
    try {
      await updateResident.mutateAsync({
        id: resident.id,
        payload: buildEditMyResidentPayload(values),
      });
      onSaved();
    } catch (err) {
      setFormError(genericErrorMessage(err, "Não foi possível salvar as alterações. Tente novamente."));
    }
  }

  const idPrefix = `edit-resident-${resident.id}`;

  return (
    <form noValidate onSubmit={(event) => void handleSubmit(onSubmit)(event)} className="flex flex-col gap-3">
      <div aria-live="polite">
        {formError && (
          <p role="alert" className="rounded-sm border border-danger-600 bg-danger-50 px-3 py-2 text-sm text-danger-700">
            {formError}
          </p>
        )}
      </div>

      <div className="grid gap-3 sm:grid-cols-2">
        <div className="flex flex-col gap-1">
          <label htmlFor={`${idPrefix}-name`} className="text-sm font-medium text-neutral-700">
            Nome
          </label>
          <input
            id={`${idPrefix}-name`}
            className={fieldClassName(!!errors.name)}
            aria-invalid={!!errors.name}
            {...register("name")}
          />
          {errors.name && <p className="text-sm text-danger-700">{errors.name.message}</p>}
        </div>
        <div className="flex flex-col gap-1">
          <label htmlFor={`${idPrefix}-phone`} className="text-sm font-medium text-neutral-700">
            Telefone (com DDI, só dígitos)
          </label>
          <input
            id={`${idPrefix}-phone`}
            inputMode="numeric"
            className={fieldClassName(!!errors.phone)}
            aria-invalid={!!errors.phone}
            {...register("phone")}
          />
          {errors.phone && <p className="text-sm text-danger-700">{errors.phone.message}</p>}
        </div>
        <div className="flex flex-col gap-1 sm:col-span-2">
          <label htmlFor={`${idPrefix}-email`} className="text-sm font-medium text-neutral-700">
            E-mail{resident.primary ? "" : " (opcional)"}
          </label>
          <input
            id={`${idPrefix}-email`}
            type="email"
            className={fieldClassName(!!errors.email)}
            aria-invalid={!!errors.email}
            {...register("email")}
          />
          {errors.email && <p className="text-sm text-danger-700">{errors.email.message}</p>}
        </div>
      </div>

      <div className="flex gap-2">
        <button
          type="submit"
          disabled={isSubmitting}
          className="h-11 rounded-md bg-primary-600 px-4 text-sm font-medium text-white hover:bg-primary-700 disabled:cursor-not-allowed disabled:opacity-70"
        >
          {isSubmitting ? "Salvando…" : "Salvar"}
        </button>
        <button
          type="button"
          onClick={onCancel}
          className="h-11 rounded-md border border-neutral-300 px-4 text-sm font-medium text-neutral-700 hover:bg-neutral-100"
        >
          Cancelar
        </button>
      </div>
    </form>
  );
}

function AddResidentForm({
  onSubmit,
  error,
}: {
  onSubmit: (values: AddMyResidentValues) => Promise<void>;
  error: string | null;
}) {
  const {
    register,
    handleSubmit,
    reset,
    formState: { errors, isSubmitting },
  } = useForm<AddMyResidentValues>({ resolver: zodResolver(addMyResidentSchema) });

  async function submit(values: AddMyResidentValues) {
    try {
      await onSubmit(values);
      reset();
    } catch {
      // Erro já foi armazenado pelo chamador (`error`); mantém os valores no formulário.
    }
  }

  return (
    <form
      noValidate
      onSubmit={(event) => void handleSubmit(submit)(event)}
      className="flex flex-col gap-4 rounded-md border border-neutral-200 bg-neutral-0 p-4"
    >
      <div aria-live="polite">
        {error && (
          <p role="alert" className="rounded-sm border border-danger-600 bg-danger-50 px-3 py-2 text-sm text-danger-700">
            {error}
          </p>
        )}
      </div>

      <div className="grid gap-3 sm:grid-cols-2">
        <div className="flex flex-col gap-1">
          <label htmlFor="add-resident-name" className="text-sm font-medium text-neutral-700">
            Nome
          </label>
          <input
            id="add-resident-name"
            className={fieldClassName(!!errors.name)}
            aria-invalid={!!errors.name}
            {...register("name")}
          />
          {errors.name && <p className="text-sm text-danger-700">{errors.name.message}</p>}
        </div>
        <div className="flex flex-col gap-1">
          <label htmlFor="add-resident-phone" className="text-sm font-medium text-neutral-700">
            Telefone (com DDI, só dígitos)
          </label>
          <input
            id="add-resident-phone"
            inputMode="numeric"
            placeholder="5562999998888"
            className={fieldClassName(!!errors.phone)}
            aria-invalid={!!errors.phone}
            {...register("phone")}
          />
          {errors.phone && <p className="text-sm text-danger-700">{errors.phone.message}</p>}
        </div>
        <div className="flex flex-col gap-1">
          <label htmlFor="add-resident-email" className="text-sm font-medium text-neutral-700">
            E-mail (opcional)
          </label>
          <input
            id="add-resident-email"
            type="email"
            className={fieldClassName(!!errors.email)}
            aria-invalid={!!errors.email}
            {...register("email")}
          />
          {errors.email && <p className="text-sm text-danger-700">{errors.email.message}</p>}
        </div>
        <div className="flex flex-col gap-1">
          <label htmlFor="add-resident-cpf" className="text-sm font-medium text-neutral-700">
            CPF (opcional)
          </label>
          <input
            id="add-resident-cpf"
            inputMode="numeric"
            placeholder="52998224725"
            className={fieldClassName(!!errors.cpf)}
            aria-invalid={!!errors.cpf}
            {...register("cpf")}
          />
          {errors.cpf && <p className="text-sm text-danger-700">{errors.cpf.message}</p>}
        </div>
      </div>

      <button
        type="submit"
        disabled={isSubmitting}
        className="h-11 rounded-md bg-primary-600 px-4 text-sm font-medium text-white hover:bg-primary-700 disabled:cursor-not-allowed disabled:opacity-70 sm:w-auto sm:self-start"
      >
        {isSubmitting ? "Adicionando…" : "Adicionar morador"}
      </button>
    </form>
  );
}
