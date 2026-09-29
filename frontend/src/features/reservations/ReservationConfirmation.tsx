/**
 * Tela de conclusão (RF-RES-04): área gratuita confirma na hora; área paga
 * nasce `PENDING_PAYMENT` (RN-25) com o botão "Pagar via WhatsApp" que abre o
 * link pronto do backend (`whatsappPaymentUrl`, RN-26) em nova aba.
 */
import { Link } from "react-router-dom";
import { formatCurrency, formatDate, formatTime } from "../../shared/utils/format";
import type { ReservationDto } from "../../shared/api/types";

export interface ReservationConfirmationProps {
  reservation: ReservationDto;
  whatsappPaymentUrl: string | null;
}

export function ReservationConfirmation({ reservation, whatsappPaymentUrl }: ReservationConfirmationProps) {
  const isPending = reservation.status === "PENDING_PAYMENT";

  return (
    <div className="mx-auto max-w-md">
      <h1 className="text-xl font-semibold text-neutral-900 sm:text-2xl">
        {isPending ? "Reserva pendente de pagamento" : "Reserva confirmada"}
      </h1>
      <p className="mt-1 text-sm text-neutral-600">Protocolo {reservation.code}</p>

      <dl className="mt-6 flex flex-col gap-2 rounded-md border border-neutral-200 bg-neutral-0 p-4 text-sm">
        <div className="flex justify-between gap-2">
          <dt className="text-neutral-600">Área</dt>
          <dd className="font-medium text-neutral-900">{reservation.areaName}</dd>
        </div>
        <div className="flex justify-between gap-2">
          <dt className="text-neutral-600">Data</dt>
          <dd className="font-medium text-neutral-900">{formatDate(reservation.date)}</dd>
        </div>
        <div className="flex justify-between gap-2">
          <dt className="text-neutral-600">Horário</dt>
          <dd className="font-medium text-neutral-900">
            {formatTime(reservation.startTime)} às {formatTime(reservation.endTime)}
          </dd>
        </div>
        {reservation.requiresPayment && reservation.price != null && (
          <div className="flex justify-between gap-2">
            <dt className="text-neutral-600">Valor</dt>
            <dd className="font-medium text-neutral-900">{formatCurrency(reservation.price)}</dd>
          </div>
        )}
      </dl>

      {isPending && (
        <div className="mt-6 flex flex-col gap-3">
          <p className="text-sm text-neutral-700">
            {reservation.statusReason ?? "Aguardando confirmação de pagamento pela administração."}
          </p>
          {whatsappPaymentUrl && (
            <a
              href={whatsappPaymentUrl}
              target="_blank"
              rel="noopener noreferrer"
              className="flex h-11 items-center justify-center rounded-md bg-primary-600 px-4 text-sm font-medium text-white hover:bg-primary-700"
            >
              Pagar via WhatsApp
            </a>
          )}
        </div>
      )}

      <Link
        to="/minhas-reservas"
        className="mt-6 flex h-11 items-center justify-center rounded-md border border-neutral-300 px-4 text-sm font-medium text-neutral-700 hover:bg-neutral-100"
      >
        Ver minhas reservas
      </Link>
    </div>
  );
}
