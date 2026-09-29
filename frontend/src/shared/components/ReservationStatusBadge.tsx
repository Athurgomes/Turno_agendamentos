/**
 * Pílula de status de reserva (`DESIGN.md` "Badges de status" — RN-29, RN-32):
 * "Realizada" é o estado derivado quando `status = CONFIRMED` e `completed = true`,
 * vindo pronto do backend (RN-32) — nunca recalculado no front.
 */
import { RESERVATION_DONE_LABEL, reservationStatusLabels } from "../utils/labels";
import { StatusBadge } from "./StatusBadge";
import type { ReservationStatus } from "../api/types";

const RESERVATION_STATUS_TONE: Record<ReservationStatus, string> = {
  PENDING_PAYMENT: "bg-status-pending-bg text-status-pending-text",
  CONFIRMED: "bg-status-confirmed-bg text-status-confirmed-text",
  CANCELLED: "bg-status-cancelled-bg text-status-cancelled-text",
};

const DONE_TONE = "bg-status-done-bg text-status-done-text";

export interface ReservationStatusBadgeProps {
  status: ReservationStatus;
  completed: boolean;
}

export function ReservationStatusBadge({ status, completed }: ReservationStatusBadgeProps) {
  if (status === "CONFIRMED" && completed) {
    return <StatusBadge label={RESERVATION_DONE_LABEL} toneClassName={DONE_TONE} />;
  }
  return (
    <StatusBadge label={reservationStatusLabels[status]} toneClassName={RESERVATION_STATUS_TONE[status]} />
  );
}
