/**
 * "Novo bloqueio" (S/A, RF-RES-09/10, RN-33): área, data e horário em slots de
 * 30 min (mesmo seletor de disponibilidade da reserva do morador) + motivo.
 * Sobreposição com reserva ativa → `409 RESERVATION_OVERLAP`: só a
 * administração pode cancelar a reserva antes de criar o bloqueio.
 */
import { useState } from "react";
import { Modal } from "../../shared/components/Modal";
import { fieldClassName, textareaClassName } from "../../shared/components/formStyles";
import { ApiError } from "../../shared/api/client";
import { useAreasQuery } from "../areas/hooks";
import { useAreaAvailabilityQuery, useCreateBlock, usePublicSettingsQuery } from "./hooks";
import { TimeSlotPicker } from "./TimeSlotPicker";

// Mínimo arbitrário só de UX (evitar clique com o campo vazio/1-2 letras); RN-33 não
// define tamanho mínimo para o motivo do bloqueio. O backend é a validação real.
const MIN_REASON_LENGTH = 3;

export interface BlockFormDialogProps {
  open: boolean;
  today: string;
  onClose: () => void;
  onCreated: () => void;
}

export function BlockFormDialog({ open, today, onClose, onCreated }: BlockFormDialogProps) {
  const { data: areas } = useAreasQuery({});
  const { data: settings } = usePublicSettingsQuery();
  const [areaId, setAreaId] = useState("");
  const [date, setDate] = useState(today);
  const [startTime, setStartTime] = useState<string | null>(null);
  const [endTime, setEndTime] = useState<string | null>(null);
  const [reason, setReason] = useState("");
  const [error, setError] = useState<string | null>(null);

  const { data: availability, isLoading: loadingAvailability } = useAreaAvailabilityQuery(
    areaId || undefined,
    date,
    date,
  );
  const day = availability?.find((d) => d.date === date);
  const createBlock = useCreateBlock();

  function reset() {
    setAreaId("");
    setDate(today);
    setStartTime(null);
    setEndTime(null);
    setReason("");
    setError(null);
  }

  function handleClose() {
    reset();
    onClose();
  }

  async function handleSubmit() {
    if (!areaId || !date || !startTime || !endTime || reason.trim().length < MIN_REASON_LENGTH) return;
    setError(null);
    try {
      await createBlock.mutateAsync({ areaId, date, startTime, endTime, reason: reason.trim() });
      reset();
      onCreated();
    } catch (err) {
      if (err instanceof ApiError && err.code === "RESERVATION_OVERLAP") {
        setError(
          "Já existe uma reserva ativa nesse horário. Só a administração pode cancelá-la antes de criar o bloqueio.",
        );
      } else {
        setError(
          err instanceof ApiError ? err.detail : "Não foi possível criar o bloqueio. Tente novamente.",
        );
      }
    }
  }

  const canSubmit = !!areaId && !!date && !!startTime && !!endTime && reason.trim().length >= MIN_REASON_LENGTH;

  return (
    <Modal open={open} onClose={handleClose} titleId="block-form-title" title="Novo bloqueio">
      <div className="flex flex-col gap-4">
        <div className="flex flex-col gap-1">
          <label htmlFor="block-area" className="text-sm font-medium text-neutral-700">
            Área
          </label>
          <select
            id="block-area"
            value={areaId}
            onChange={(event) => {
              setAreaId(event.target.value);
              setStartTime(null);
              setEndTime(null);
            }}
            className={fieldClassName(false)}
          >
            <option value="">Selecione…</option>
            {(areas ?? []).map((area) => (
              <option key={area.id} value={area.id}>
                {area.name}
              </option>
            ))}
          </select>
        </div>

        <div className="flex flex-col gap-1">
          <label htmlFor="block-date" className="text-sm font-medium text-neutral-700">
            Data
          </label>
          <input
            id="block-date"
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

        {areaId && (
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
        )}

        <div className="flex flex-col gap-1">
          <label htmlFor="block-reason" className="text-sm font-medium text-neutral-700">
            Motivo
          </label>
          <textarea
            id="block-reason"
            rows={3}
            value={reason}
            onChange={(event) => setReason(event.target.value)}
            className={textareaClassName(false)}
          />
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
          Cancelar
        </button>
        <button
          type="button"
          disabled={!canSubmit || createBlock.isPending}
          onClick={() => void handleSubmit()}
          className="h-11 rounded-md bg-primary-600 px-4 text-sm font-medium text-white hover:bg-primary-700 disabled:cursor-not-allowed disabled:opacity-70"
        >
          {createBlock.isPending ? "Enviando…" : "Criar bloqueio"}
        </button>
      </div>
    </Modal>
  );
}
