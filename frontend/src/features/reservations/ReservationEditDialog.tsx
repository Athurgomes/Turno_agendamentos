/**
 * "Alterar" (ADMIN, RF-RES-08, RN-27, RN-28): área/data/horário com o mesmo
 * seletor de disponibilidade da reserva do morador + justificativa (mínimo 10
 * caracteres, fica visível ao morador como `statusReason`). Síndico não tem
 * acesso a esta ação (D-16) — quem monta o botão decide isso, não este componente.
 */
import { useState } from "react";
import { Modal } from "../../shared/components/Modal";
import { fieldClassName, textareaClassName } from "../../shared/components/formStyles";
import { ApiError } from "../../shared/api/client";
import { useAreasQuery } from "../areas/hooks";
import { useAreaAvailabilityQuery, usePublicSettingsQuery, useUpdateReservation } from "./hooks";
import { TimeSlotPicker } from "./TimeSlotPicker";
import type { AdminReservationDto } from "../../shared/api/types";

const MIN_JUSTIFICATION_LENGTH = 10;

export interface ReservationEditDialogProps {
  open: boolean;
  reservation: AdminReservationDto;
  onClose: () => void;
  onUpdated: () => void;
}

export function ReservationEditDialog({
  open,
  reservation,
  onClose,
  onUpdated,
}: ReservationEditDialogProps) {
  const { data: areas } = useAreasQuery({});
  const { data: settings } = usePublicSettingsQuery();
  const [areaId, setAreaId] = useState(reservation.areaId);
  const [date, setDate] = useState(reservation.date);
  const [startTime, setStartTime] = useState<string | null>(reservation.startTime);
  const [endTime, setEndTime] = useState<string | null>(reservation.endTime);
  const [justification, setJustification] = useState("");
  const [error, setError] = useState<string | null>(null);

  const { data: availability, isLoading: loadingAvailability } = useAreaAvailabilityQuery(
    areaId || undefined,
    date,
    date,
  );
  const day = availability?.find((d) => d.date === date);
  const updateReservation = useUpdateReservation();

  const trimmedJustification = justification.trim();
  const justificationTooShort =
    trimmedJustification.length > 0 && trimmedJustification.length < MIN_JUSTIFICATION_LENGTH;
  const canSubmit =
    !!areaId &&
    !!date &&
    !!startTime &&
    !!endTime &&
    trimmedJustification.length >= MIN_JUSTIFICATION_LENGTH;

  function handleClose() {
    setJustification("");
    setError(null);
    onClose();
  }

  async function handleSubmit() {
    if (!canSubmit || !startTime || !endTime) return;
    setError(null);
    try {
      await updateReservation.mutateAsync({
        id: reservation.id,
        payload: { areaId, date, startTime, endTime, justification: trimmedJustification },
      });
      setJustification("");
      onUpdated();
    } catch (err) {
      setError(
        err instanceof ApiError ? err.detail : "Não foi possível alterar esta reserva. Tente novamente.",
      );
    }
  }

  return (
    <Modal open={open} onClose={handleClose} titleId="reservation-edit-title" title="Alterar reserva">
      <div className="flex flex-col gap-4">
        <div className="flex flex-col gap-1">
          <label htmlFor="edit-area" className="text-sm font-medium text-neutral-700">
            Área
          </label>
          <select
            id="edit-area"
            value={areaId}
            onChange={(event) => {
              setAreaId(event.target.value);
              setStartTime(null);
              setEndTime(null);
            }}
            className={fieldClassName(false)}
          >
            {(areas ?? []).map((area) => (
              <option key={area.id} value={area.id}>
                {area.name}
              </option>
            ))}
          </select>
        </div>

        <div className="flex flex-col gap-1">
          <label htmlFor="edit-date" className="text-sm font-medium text-neutral-700">
            Data
          </label>
          <input
            id="edit-date"
            type="date"
            value={date}
            onChange={(event) => {
              setDate(event.target.value);
              setStartTime(null);
              setEndTime(null);
            }}
            className={fieldClassName(false)}
          />
        </div>

        <div>
          <p className="mb-2 text-sm font-medium text-neutral-700">Horário</p>
          {loadingAvailability && <p className="text-sm text-neutral-600">Carregando horários…</p>}
          {day && (
            <TimeSlotPicker
              day={day}
              slotMinutes={settings?.slotMinutes ?? 30}
              startTime={startTime}
              endTime={endTime}
              onSelect={(s, e) => {
                setStartTime(s);
                setEndTime(e);
              }}
            />
          )}
        </div>

        <div className="flex flex-col gap-1">
          <label htmlFor="edit-justification" className="text-sm font-medium text-neutral-700">
            Justificativa (mínimo 10 caracteres)
          </label>
          <textarea
            id="edit-justification"
            rows={3}
            value={justification}
            onChange={(event) => setJustification(event.target.value)}
            className={textareaClassName(justificationTooShort)}
          />
          <p className="text-sm text-neutral-600">
            Esta justificativa fica visível para o morador (RN-27).
          </p>
          {justificationTooShort && (
            <p className="text-sm text-danger-700">
              Escreva pelo menos 10 caracteres para que a justificativa fique clara.
            </p>
          )}
        </div>

        {error && (
          <p role="alert" className="rounded-sm border border-danger-600 bg-danger-50 px-3 py-2 text-sm text-danger-700">
            {error}
          </p>
        )}
      </div>

      <div className="mt-6 flex flex-col-reverse gap-3 sm:flex-row sm:justify-end">
        <button
          type="button"
          onClick={handleClose}
          className="h-11 rounded-md border border-neutral-300 px-4 text-sm font-medium text-neutral-700 hover:bg-neutral-100"
        >
          Voltar
        </button>
        <button
          type="button"
          disabled={!canSubmit || updateReservation.isPending}
          onClick={() => void handleSubmit()}
          className="h-11 rounded-md bg-primary-600 px-4 text-sm font-medium text-white hover:bg-primary-700 disabled:cursor-not-allowed disabled:opacity-70"
        >
          {updateReservation.isPending ? "Enviando…" : "Salvar alteração"}
        </button>
      </div>
    </Modal>
  );
}
