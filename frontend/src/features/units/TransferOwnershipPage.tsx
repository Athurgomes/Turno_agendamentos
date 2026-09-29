/**
 * Troca de titularidade (RF-UNI-04, RN-10): desativa os moradores atuais,
 * cadastra o novo principal e adicionais, gera nova senha temporária. Depois
 * do sucesso, mostra o modal de credenciais e a lista de reservas futuras da
 * titularidade anterior para o ADMIN decidir se cancela cada uma na agenda —
 * nada é cancelado automaticamente.
 */
import { useState } from "react";
import { useForm, useFieldArray, useWatch } from "react-hook-form";
import { zodResolver } from "@hookform/resolvers/zod";
import { useNavigate, useParams } from "react-router-dom";
import { CredentialsModal } from "./CredentialsModal";
import { ResidentFields } from "./ResidentFields";
import { useTransferUnit, useUnitQuery } from "./hooks";
import {
  buildTransferPayload,
  emptyResident,
  transferFormSchema,
  type TransferFormValues,
} from "./residentForm";
import { formatDate, formatTime } from "../../shared/utils/format";
import { ApiError } from "../../shared/api/client";
import type { Credentials, ReservationSummary } from "../../shared/api/types";

export function TransferOwnershipPage() {
  const { id } = useParams<{ id: string }>();
  const navigate = useNavigate();
  const { data: unit, isLoading } = useUnitQuery(id);
  const transferUnit = useTransferUnit(id ?? "");

  const [formError, setFormError] = useState<string | null>(null);
  const [credentials, setCredentials] = useState<Credentials | null>(null);
  const [affectedReservations, setAffectedReservations] = useState<ReservationSummary[]>([]);
  const [newPrimary, setNewPrimary] = useState<{ name: string; phone: string } | null>(null);

  const {
    register,
    handleSubmit,
    control,
    setValue,
    formState: { errors, isSubmitting },
  } = useForm<TransferFormValues>({
    resolver: zodResolver(transferFormSchema),
    defaultValues: { residents: [emptyResident(true)] },
  });
  const { fields, append, remove } = useFieldArray({ control, name: "residents" });
  const residents = useWatch({ control, name: "residents" });

  if (isLoading || !unit) {
    return <p className="text-sm text-neutral-600">Carregando unidade…</p>;
  }

  function handleSetPrimary(targetIndex: number) {
    fields.forEach((_, index) => {
      setValue(`residents.${index}.primary`, index === targetIndex, { shouldDirty: true });
    });
  }

  async function onSubmit(values: TransferFormValues) {
    setFormError(null);
    const payload = buildTransferPayload(values);
    try {
      const result = await transferUnit.mutateAsync(payload);
      setCredentials(result.credentials);
      setAffectedReservations(result.affectedReservations);
      setNewPrimary({ name: payload.primary.name, phone: payload.primary.phone });
    } catch (err) {
      setFormError(
        err instanceof ApiError
          ? err.detail
          : "Não foi possível trocar a titularidade. Tente novamente.",
      );
    }
  }

  const residentsError =
    errors.residents && "message" in errors.residents ? errors.residents.message : undefined;

  return (
    <div className="mx-auto max-w-2xl">
      <h1 className="mb-1 text-xl font-semibold text-neutral-900 sm:text-2xl">
        Troca de titularidade — unidade {unit.identifier}
      </h1>
      <p className="mb-6 text-sm text-neutral-600">
        Os moradores atuais serão desativados. Cadastre o novo morador principal (nome, telefone,
        e-mail e CPF) e, se houver, os moradores adicionais.
      </p>

      <form
        noValidate
        onSubmit={(event) => void handleSubmit(onSubmit)(event)}
        className="flex flex-col gap-6"
      >
        <div aria-live="polite">
          {formError && (
            <p
              role="alert"
              className="rounded-sm border border-danger-600 bg-danger-50 px-3 py-2 text-sm text-danger-700"
            >
              {formError}
            </p>
          )}
        </div>

        <div className="flex flex-col gap-4">
          <div className="flex items-center justify-between">
            <h2 className="text-lg font-semibold text-neutral-900">Novos moradores</h2>
            <button
              type="button"
              onClick={() => append(emptyResident(false))}
              className="rounded-md border border-neutral-300 px-3 py-1.5 text-sm font-medium text-neutral-700 hover:bg-neutral-100"
            >
              + Adicionar morador
            </button>
          </div>

          {residentsError && (
            <p role="alert" className="text-sm text-danger-700">
              {residentsError}
            </p>
          )}

          {fields.map((field, index) => (
            <ResidentFields
              key={field.id}
              index={index}
              register={register}
              errors={errors}
              isPrimary={!!residents?.[index]?.primary}
              onSetPrimary={handleSetPrimary}
              onRemove={remove}
              canRemove={fields.length > 1}
            />
          ))}
        </div>

        <button
          type="submit"
          disabled={isSubmitting}
          className="h-11 w-full rounded-md bg-primary-600 text-sm font-medium text-white hover:bg-primary-700 disabled:cursor-not-allowed disabled:opacity-70 sm:w-auto sm:self-start sm:px-6"
        >
          {isSubmitting ? "Trocando titularidade…" : "Trocar titularidade"}
        </button>
      </form>

      <CredentialsModal
        open={!!credentials}
        credentials={credentials}
        residentName={newPrimary?.name ?? ""}
        residentPhone={newPrimary?.phone ?? ""}
        onClose={() => {
          setCredentials(null);
          if (affectedReservations.length === 0) {
            navigate("/admin/unidades");
          }
        }}
      />

      {credentials === null && affectedReservations.length > 0 && (
        <div className="mt-8 rounded-md border border-warning-800 bg-warning-50 p-4">
          <h2 className="text-lg font-semibold text-warning-800">
            Reservas futuras da titularidade anterior
          </h2>
          <p className="mt-1 text-sm text-warning-800">
            Estas reservas continuam ativas. Cancele cada uma na agenda, com justificativa, se não
            fizerem mais sentido para o novo morador — nada foi cancelado automaticamente.
          </p>
          <ul className="mt-3 space-y-2 text-sm text-neutral-800">
            {affectedReservations.map((reservation) => (
              <li key={reservation.id} className="rounded-sm bg-neutral-0 p-3">
                {reservation.areaName} — {formatDate(reservation.date)} {formatTime(reservation.startTime)}–
                {formatTime(reservation.endTime)} (código {reservation.code})
              </li>
            ))}
          </ul>
          <button
            type="button"
            onClick={() => navigate("/admin/unidades")}
            className="mt-4 h-11 rounded-md border border-neutral-300 px-4 text-sm font-medium text-neutral-700 hover:bg-neutral-100"
          >
            Voltar para unidades
          </button>
        </div>
      )}
    </div>
  );
}
