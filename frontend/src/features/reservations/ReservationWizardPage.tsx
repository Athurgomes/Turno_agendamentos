/**
 * Fluxo de reserva em até 4 passos (RF-RES-02/03, RNF-05): data → horário →
 * responsável/convidados → revisão. Erros do backend (RF-RES-03, RN-18..24)
 * voltam ao passo correspondente (`stepForErrorCode`) com a mensagem pronta
 * (`ApiError.detail`, já em pt-BR — `docs/03-api.md`).
 */
import { useState } from "react";
import { useParams } from "react-router-dom";
import { useAreaQuery } from "../areas/hooks";
import { useMyUnitQuery } from "../units/hooks";
import { serverNow, useServerClock } from "../../shared/hooks/useServerClock";
import { useAreaAvailabilityQuery, useCreateReservation, usePublicSettingsQuery } from "./hooks";
import { AvailabilityCalendar } from "./AvailabilityCalendar";
import { TimeSlotPicker } from "./TimeSlotPicker";
import { ReservationConfirmation } from "./ReservationConfirmation";
import { addDays, addMonths, dateKeyInTimezone, monthKey } from "./dateUtils";
import { stepForErrorCode, type WizardStep } from "./reservationRules";
import { ApiError } from "../../shared/api/client";
import { formatCurrency, formatDate, formatTime } from "../../shared/utils/format";
import { fieldClassName, textareaClassName } from "../../shared/components/formStyles";
import type { ReservationDto } from "../../shared/api/types";

const STEPS: { key: WizardStep; label: string }[] = [
  { key: "date", label: "Data" },
  { key: "time", label: "Horário" },
  { key: "details", label: "Responsável e convidados" },
  { key: "review", label: "Revisão" },
];

export function ReservationWizardPage() {
  const { id } = useParams<{ id: string }>();
  const { data: area } = useAreaQuery(id);
  const { data: unit } = useMyUnitQuery();
  const { data: settings } = usePublicSettingsQuery();
  useServerClock();

  const timezone = settings?.timezone ?? "America/Sao_Paulo";
  const today = dateKeyInTimezone(serverNow(), timezone);

  const [step, setStep] = useState<WizardStep>("date");
  const [monthAnchor, setMonthAnchor] = useState(() => `${monthKey(today)}-01`);
  const [date, setDate] = useState<string | null>(null);
  const [startTime, setStartTime] = useState<string | null>(null);
  const [endTime, setEndTime] = useState<string | null>(null);
  const [residentId, setResidentId] = useState("");
  const [guests, setGuests] = useState("");
  const [notes, setNotes] = useState("");
  const [submitError, setSubmitError] = useState<string | null>(null);
  const [result, setResult] = useState<{
    reservation: ReservationDto;
    whatsappPaymentUrl: string | null;
  } | null>(null);

  const monthEnd = addDays(addMonths(monthAnchor, 1), -1);
  const { data: availability, isLoading: loadingAvailability } = useAreaAvailabilityQuery(
    id,
    monthAnchor,
    monthEnd,
  );

  const createReservation = useCreateReservation();

  const days = availability ?? [];
  const selectedDay = days.find((d) => d.date === date);

  if (result) {
    return (
      <ReservationConfirmation reservation={result.reservation} whatsappPaymentUrl={result.whatsappPaymentUrl} />
    );
  }

  if (!area || !unit) {
    return <p className="text-sm text-neutral-600">Carregando…</p>;
  }

  const stepIndex = STEPS.findIndex((s) => s.key === step);
  const guestsNumber = Number(guests);
  const detailsValid = !!residentId && guests !== "" && guestsNumber >= 1 && guestsNumber <= area.capacity;

  function goTo(next: WizardStep) {
    setSubmitError(null);
    setStep(next);
  }

  async function handleConfirm() {
    if (!id || !date || !startTime || !endTime || !residentId || !guests) return;
    setSubmitError(null);
    try {
      const { reservation, whatsappPaymentUrl } = await createReservation.mutateAsync({
        areaId: id,
        date,
        startTime,
        endTime,
        residentId,
        guests: guestsNumber,
        notes: notes.trim() ? notes.trim() : undefined,
      });
      setResult({ reservation, whatsappPaymentUrl });
    } catch (err) {
      if (err instanceof ApiError) {
        setSubmitError(err.detail);
        setStep(stepForErrorCode(err.code));
      } else {
        setSubmitError("Não foi possível concluir a reserva. Tente novamente.");
      }
    }
  }

  return (
    <div className="mx-auto max-w-2xl">
      <p className="mb-1 text-sm font-medium text-primary-700">
        Passo {stepIndex + 1} de {STEPS.length}
      </p>
      <h1 className="mb-4 text-xl font-semibold text-neutral-900 sm:text-2xl">
        Reservar {area.name} — {STEPS[stepIndex].label}
      </h1>

      <div aria-live="polite">
        {submitError && (
          <p
            role="alert"
            className="mb-4 rounded-sm border border-danger-600 bg-danger-50 px-3 py-2 text-sm text-danger-700"
          >
            {submitError}
          </p>
        )}
      </div>

      {step === "date" && (
        <>
          <AvailabilityCalendar
            monthAnchor={monthAnchor}
            onMonthChange={setMonthAnchor}
            today={today}
            days={days}
            isLoading={loadingAvailability}
            selectedDate={date}
            onSelectDate={(d) => {
              setDate(d);
              setStartTime(null);
              setEndTime(null);
            }}
            settings={settings}
          />
          <div className="mt-6 flex justify-end">
            <button
              type="button"
              disabled={!date}
              onClick={() => goTo("time")}
              className="h-11 rounded-md bg-primary-600 px-6 text-sm font-medium text-white hover:bg-primary-700 disabled:cursor-not-allowed disabled:opacity-50"
            >
              Continuar
            </button>
          </div>
        </>
      )}

      {step === "time" && selectedDay && (
        <>
          <p className="mb-3 text-sm text-neutral-700">{formatDate(selectedDay.date)}</p>
          <TimeSlotPicker
            day={selectedDay}
            slotMinutes={settings?.slotMinutes ?? 30}
            startTime={startTime}
            endTime={endTime}
            onSelect={(s, e) => {
              setStartTime(s);
              setEndTime(e);
            }}
          />
          <div className="mt-6 flex justify-between">
            <button
              type="button"
              onClick={() => goTo("date")}
              className="h-11 rounded-md border border-neutral-300 px-4 text-sm font-medium text-neutral-700 hover:bg-neutral-100"
            >
              Voltar
            </button>
            <button
              type="button"
              disabled={!startTime || !endTime}
              onClick={() => goTo("details")}
              className="h-11 rounded-md bg-primary-600 px-6 text-sm font-medium text-white hover:bg-primary-700 disabled:cursor-not-allowed disabled:opacity-50"
            >
              Continuar
            </button>
          </div>
        </>
      )}

      {step === "details" && (
        <>
          <div className="flex flex-col gap-4">
            <div className="flex flex-col gap-1">
              <span className="text-sm font-medium text-neutral-700">Unidade</span>
              <p className="text-base text-neutral-900">{unit.identifier}</p>
            </div>

            <div className="flex flex-col gap-1">
              <label htmlFor="resident" className="text-sm font-medium text-neutral-700">
                Morador responsável
              </label>
              <select
                id="resident"
                value={residentId}
                onChange={(e) => setResidentId(e.target.value)}
                className={fieldClassName(false)}
              >
                <option value="">Selecione…</option>
                {unit.residents.map((resident) => (
                  <option key={resident.id} value={resident.id}>
                    {resident.name}
                  </option>
                ))}
              </select>
            </div>

            <div className="flex flex-col gap-1">
              <label htmlFor="guests" className="text-sm font-medium text-neutral-700">
                Número de convidados
              </label>
              <input
                id="guests"
                type="number"
                inputMode="numeric"
                min={1}
                max={area.capacity}
                value={guests}
                onChange={(e) => setGuests(e.target.value)}
                className={fieldClassName(guests !== "" && (guestsNumber < 1 || guestsNumber > area.capacity))}
              />
              <p className="text-sm text-neutral-600">Capacidade da área: {area.capacity} pessoas.</p>
              {guests !== "" && guestsNumber > area.capacity && (
                <p className="text-sm text-danger-700">
                  O número de convidados não pode passar da capacidade da área.
                </p>
              )}
            </div>

            <div className="flex flex-col gap-1">
              <label htmlFor="notes" className="text-sm font-medium text-neutral-700">
                Observação (opcional)
              </label>
              <textarea
                id="notes"
                value={notes}
                onChange={(e) => setNotes(e.target.value)}
                className={textareaClassName(false)}
                rows={3}
              />
            </div>
          </div>

          <div className="mt-6 flex justify-between">
            <button
              type="button"
              onClick={() => goTo("time")}
              className="h-11 rounded-md border border-neutral-300 px-4 text-sm font-medium text-neutral-700 hover:bg-neutral-100"
            >
              Voltar
            </button>
            <button
              type="button"
              disabled={!detailsValid}
              onClick={() => goTo("review")}
              className="h-11 rounded-md bg-primary-600 px-6 text-sm font-medium text-white hover:bg-primary-700 disabled:cursor-not-allowed disabled:opacity-50"
            >
              Continuar
            </button>
          </div>
        </>
      )}

      {step === "review" && date && startTime && endTime && (
        <>
          <dl className="flex flex-col gap-2 rounded-md border border-neutral-200 bg-neutral-0 p-4 text-sm">
            <div className="flex justify-between gap-2">
              <dt className="text-neutral-600">Área</dt>
              <dd className="font-medium text-neutral-900">{area.name}</dd>
            </div>
            <div className="flex justify-between gap-2">
              <dt className="text-neutral-600">Data</dt>
              <dd className="font-medium text-neutral-900">{formatDate(date)}</dd>
            </div>
            <div className="flex justify-between gap-2">
              <dt className="text-neutral-600">Horário</dt>
              <dd className="font-medium text-neutral-900">
                {formatTime(startTime)} às {formatTime(endTime)}
              </dd>
            </div>
            <div className="flex justify-between gap-2">
              <dt className="text-neutral-600">Responsável</dt>
              <dd className="font-medium text-neutral-900">
                {unit.residents.find((r) => r.id === residentId)?.name}
              </dd>
            </div>
            <div className="flex justify-between gap-2">
              <dt className="text-neutral-600">Convidados</dt>
              <dd className="font-medium text-neutral-900">{guests}</dd>
            </div>
            {notes.trim() && (
              <div className="flex justify-between gap-2">
                <dt className="text-neutral-600">Observação</dt>
                <dd className="font-medium text-neutral-900">{notes}</dd>
              </div>
            )}
            <div className="flex justify-between gap-2">
              <dt className="text-neutral-600">Valor</dt>
              <dd className="font-medium text-neutral-900">
                {area.requiresPayment && area.price ? formatCurrency(area.price) : "Gratuita"}
              </dd>
            </div>
          </dl>

          <div className="mt-6 flex justify-between">
            <button
              type="button"
              onClick={() => goTo("details")}
              className="h-11 rounded-md border border-neutral-300 px-4 text-sm font-medium text-neutral-700 hover:bg-neutral-100"
            >
              Voltar
            </button>
            <button
              type="button"
              disabled={createReservation.isPending}
              onClick={() => void handleConfirm()}
              className="h-11 rounded-md bg-primary-600 px-6 text-sm font-medium text-white hover:bg-primary-700 disabled:cursor-not-allowed disabled:opacity-70"
            >
              {createReservation.isPending ? "Enviando…" : "Confirmar reserva"}
            </button>
          </div>
        </>
      )}
    </div>
  );
}
