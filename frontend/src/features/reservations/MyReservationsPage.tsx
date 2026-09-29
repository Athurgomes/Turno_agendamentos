/**
 * "Minhas reservas" (RF-RES-05/06): abas Próximas/Anteriores, status e motivo
 * (RN-29), ações conforme `canCancel`/`canReport`/`whatsappPaymentUrl`.
 * Cancelamento de reserva paga já `CONFIRMED` mostra o aviso da RN-30.
 */
import { useState } from "react";
import { Link } from "react-router-dom";
import { ConfirmDialog } from "../../shared/components/ConfirmDialog";
import { ReservationStatusBadge } from "../../shared/components/ReservationStatusBadge";
import { ApiError } from "../../shared/api/client";
import { formatCurrency, formatDate, formatTime } from "../../shared/utils/format";
import { useCancelMyReservation, useMyReservationsQuery } from "./hooks";
import type { ReservationDto } from "../../shared/api/types";
import type { ReservationScope } from "./api";

const RETURN_WARNING =
  "Eventual devolução do valor é tratada diretamente com a administração.";

const TABS: { key: ReservationScope; label: string }[] = [
  { key: "upcoming", label: "Próximas" },
  { key: "past", label: "Anteriores" },
];

export function MyReservationsPage() {
  const [scope, setScope] = useState<ReservationScope>("upcoming");
  const { data, isLoading } = useMyReservationsQuery(scope);
  const cancelReservation = useCancelMyReservation();

  const [toCancel, setToCancel] = useState<ReservationDto | null>(null);
  const [cancelError, setCancelError] = useState<string | null>(null);

  const reservations = data?.content ?? [];

  async function handleConfirmCancel() {
    if (!toCancel) return;
    setCancelError(null);
    try {
      await cancelReservation.mutateAsync(toCancel.id);
      setToCancel(null);
    } catch (err) {
      setCancelError(
        err instanceof ApiError ? err.detail : "Não foi possível cancelar esta reserva. Tente novamente.",
      );
    }
  }

  return (
    <div className="mx-auto max-w-2xl">
      <h1 className="mb-4 text-xl font-semibold text-neutral-900 sm:text-2xl">Minhas reservas</h1>

      <div role="tablist" className="mb-4 flex gap-2 border-b border-neutral-200">
        {TABS.map((tab) => (
          <button
            key={tab.key}
            type="button"
            role="tab"
            aria-selected={scope === tab.key}
            onClick={() => setScope(tab.key)}
            className={[
              "h-11 px-4 text-sm font-medium",
              scope === tab.key
                ? "border-b-2 border-primary-600 text-primary-700"
                : "text-neutral-600 hover:text-neutral-900",
            ].join(" ")}
          >
            {tab.label}
          </button>
        ))}
      </div>

      {isLoading && <p className="text-sm text-neutral-600">Carregando reservas…</p>}

      {!isLoading && reservations.length === 0 && (
        <p className="text-sm text-neutral-600">
          {scope === "upcoming"
            ? "Nenhuma reserva futura ainda — reserve uma área comum."
            : "Nenhuma reserva anterior."}
        </p>
      )}

      <ul className="flex flex-col gap-3">
        {reservations.map((reservation) => (
          <li key={reservation.id} className="rounded-md border border-neutral-200 bg-neutral-0 p-4">
            <div className="flex items-start justify-between gap-2">
              <div>
                <p className="text-base font-medium text-neutral-900">{reservation.areaName}</p>
                <p className="text-sm text-neutral-600">
                  {formatDate(reservation.date)} · {formatTime(reservation.startTime)} às{" "}
                  {formatTime(reservation.endTime)}
                </p>
              </div>
              <ReservationStatusBadge status={reservation.status} completed={reservation.completed} />
            </div>

            <dl className="mt-2 text-sm text-neutral-700">
              <div>
                <dt className="inline font-medium">Responsável: </dt>
                <dd className="inline">{reservation.residentName}</dd>
              </div>
              {reservation.requiresPayment && reservation.price != null && (
                <div>
                  <dt className="inline font-medium">Valor: </dt>
                  <dd className="inline">{formatCurrency(reservation.price)}</dd>
                </div>
              )}
            </dl>

            {reservation.statusReason && (
              <p className="mt-2 text-sm text-neutral-600">{reservation.statusReason}</p>
            )}

            <div className="mt-3 flex flex-wrap gap-2">
              {reservation.whatsappPaymentUrl && (
                <a
                  href={reservation.whatsappPaymentUrl}
                  target="_blank"
                  rel="noopener noreferrer"
                  className="flex h-10 items-center justify-center rounded-md bg-primary-600 px-3 text-sm font-medium text-white hover:bg-primary-700"
                >
                  Pagar via WhatsApp
                </a>
              )}
              {reservation.canCancel && (
                <button
                  type="button"
                  onClick={() => setToCancel(reservation)}
                  className="flex h-10 items-center justify-center rounded-md border border-danger-700 px-3 text-sm font-medium text-danger-700 hover:bg-danger-50"
                >
                  Cancelar
                </button>
              )}
              {reservation.canReport && (
                <Link
                  to={`/minhas-reservas/${reservation.id}/reportar`}
                  className="flex h-10 items-center justify-center rounded-md border border-neutral-300 px-3 text-sm font-medium text-neutral-700 hover:bg-neutral-100"
                >
                  Reportar
                </Link>
              )}
            </div>
          </li>
        ))}
      </ul>

      <ConfirmDialog
        open={!!toCancel}
        title="Cancelar reserva"
        message={
          cancelError ??
          (toCancel?.requiresPayment && toCancel?.status === "CONFIRMED"
            ? `Tem certeza que deseja cancelar esta reserva? ${RETURN_WARNING}`
            : "Tem certeza que deseja cancelar esta reserva?")
        }
        confirmLabel="Cancelar reserva"
        isLoading={cancelReservation.isPending}
        onConfirm={() => void handleConfirmCancel()}
        onCancel={() => {
          setToCancel(null);
          setCancelError(null);
        }}
      />
    </div>
  );
}
