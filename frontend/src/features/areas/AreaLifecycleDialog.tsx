/**
 * Modal compartilhado por "Alterar status" (`PATCH /areas/{id}/status`) e
 * "Excluir" (`DELETE /areas/{id}`) — mesma lógica de reservas afetadas
 * (RN-16): 409 `AREA_HAS_FUTURE_RESERVATIONS` mostra a lista e passa a exigir
 * justificativa (≥ 10 caracteres) antes de reenviar com
 * `confirmCancelAffected: true`.
 */
import { useState } from "react";
import { Modal } from "../../shared/components/Modal";
import { ApiError } from "../../shared/api/client";
import type { AreaStatus, ReservationSummary } from "../../shared/api/types";
import { areaStatusLabels } from "../../shared/utils/labels";
import { formatDate, formatTime } from "../../shared/utils/format";

const MIN_JUSTIFICATION_LENGTH = 10;

export interface AreaLifecycleSubmitPayload {
  status?: AreaStatus;
  justification?: string;
  confirmCancelAffected?: boolean;
}

export interface AreaLifecycleDialogProps {
  open: boolean;
  title: string;
  description: string;
  confirmLabel: string;
  /** Presente só na mudança de status (RN-14); ausente na exclusão (RN-15). */
  statusOptions?: AreaStatus[];
  onCancel: () => void;
  onSubmit: (payload: AreaLifecycleSubmitPayload) => Promise<unknown>;
}

export function AreaLifecycleDialog({
  open,
  title,
  description,
  confirmLabel,
  statusOptions,
  onCancel,
  onSubmit,
}: AreaLifecycleDialogProps) {
  const [status, setStatus] = useState<AreaStatus | "">(statusOptions?.[0] ?? "");
  const [justification, setJustification] = useState("");
  const [affected, setAffected] = useState<ReservationSummary[] | null>(null);
  const [error, setError] = useState<string | null>(null);
  const [isSubmitting, setIsSubmitting] = useState(false);

  function reset() {
    setStatus(statusOptions?.[0] ?? "");
    setJustification("");
    setAffected(null);
    setError(null);
  }

  function handleCancel() {
    reset();
    onCancel();
  }

  async function handleConfirm() {
    setIsSubmitting(true);
    setError(null);
    try {
      await onSubmit({
        status: statusOptions ? (status as AreaStatus) : undefined,
        justification: affected ? justification.trim() : undefined,
        confirmCancelAffected: affected ? true : undefined,
      });
      reset();
    } catch (err) {
      if (err instanceof ApiError && err.code === "AREA_HAS_FUTURE_RESERVATIONS") {
        setAffected((err.problem.affectedReservations as ReservationSummary[] | undefined) ?? []);
        setError(err.detail);
      } else {
        setError(
          err instanceof ApiError
            ? err.detail
            : "Não foi possível concluir a ação. Tente novamente.",
        );
      }
    } finally {
      setIsSubmitting(false);
    }
  }

  const justificationTooShort =
    affected !== null && justification.trim().length < MIN_JUSTIFICATION_LENGTH;

  return (
    <Modal open={open} onClose={handleCancel} titleId="area-lifecycle-title" title={title}>
      <p className="text-sm text-neutral-700">{description}</p>

      {statusOptions && !affected && (
        <div className="mt-4 flex flex-col gap-1">
          <label htmlFor="lifecycle-status" className="text-sm font-medium text-neutral-700">
            Novo status
          </label>
          <select
            id="lifecycle-status"
            value={status}
            onChange={(event) => setStatus(event.target.value as AreaStatus)}
            className="h-11 rounded-sm border border-neutral-300 bg-neutral-0 px-3 text-base text-neutral-900"
          >
            {statusOptions.map((option) => (
              <option key={option} value={option}>
                {areaStatusLabels[option]}
              </option>
            ))}
          </select>
        </div>
      )}

      <div aria-live="polite">
        {error && (
          <p
            role="alert"
            className="mt-4 rounded-sm border border-danger-600 bg-danger-50 px-3 py-2 text-sm text-danger-700"
          >
            {error}
          </p>
        )}
      </div>

      {affected && (
        <div className="mt-4 flex flex-col gap-3">
          <p className="text-sm font-medium text-neutral-900">
            Reservas futuras afetadas ({affected.length}):
          </p>
          <ul className="max-h-48 space-y-1 overflow-y-auto text-sm text-neutral-700">
            {affected.map((reservation) => (
              <li key={reservation.id} className="rounded-sm border border-neutral-200 p-2">
                {reservation.code} — {formatDate(reservation.date)} {formatTime(reservation.startTime)}–
                {formatTime(reservation.endTime)} — Unidade {reservation.unitIdentifier}
              </li>
            ))}
          </ul>
          <div className="flex flex-col gap-1">
            <label
              htmlFor="lifecycle-justification"
              className="text-sm font-medium text-neutral-700"
            >
              Justificativa (mínimo 10 caracteres)
            </label>
            <textarea
              id="lifecycle-justification"
              rows={3}
              value={justification}
              onChange={(event) => setJustification(event.target.value)}
              className="w-full rounded-sm border border-neutral-300 bg-neutral-0 px-3 py-2 text-base text-neutral-900"
            />
            <p className="text-sm text-neutral-600">
              Esta justificativa fica visível para cada morador com reserva cancelada (RN-16).
            </p>
          </div>
        </div>
      )}

      <div className="mt-6 flex flex-col-reverse gap-3 sm:flex-row sm:justify-end">
        <button
          type="button"
          onClick={handleCancel}
          className="h-11 rounded-md border border-neutral-300 px-4 text-sm font-medium text-neutral-700 hover:bg-neutral-100"
        >
          Cancelar
        </button>
        <button
          type="button"
          onClick={() => void handleConfirm()}
          disabled={isSubmitting || (affected !== null && justificationTooShort)}
          className="h-11 rounded-md bg-danger-700 px-4 text-sm font-medium text-white hover:bg-danger-800 disabled:cursor-not-allowed disabled:opacity-70"
        >
          {isSubmitting ? "Enviando…" : affected ? "Confirmar cancelamento" : confirmLabel}
        </button>
      </div>
    </Modal>
  );
}
