/**
 * Chamadas HTTP da aba Confirmações (`docs/03-api.md` "Reservas e bloqueios",
 * RF-PAG-01..03, D-49). Camada fina sobre `apiFetch`: nenhuma regra de negócio
 * aqui, só o contrato da API. Cancelar reutiliza `cancelAdminReservation` de
 * `features/reservations/api.ts` — mesma rota `POST /reservations/{id}/cancel`
 * usada pela agenda.
 */
import { apiFetch } from "../../../shared/api/client";
import type { AdminReservationDto, PendingPaymentDto } from "../../../shared/api/types";

/** `GET /payments/pending` — já vem ordenada por início crescente. */
export function listPendingPayments(): Promise<PendingPaymentDto[]> {
  return apiFetch("/payments/pending");
}

/** `POST /reservations/{id}/confirm-payment` (`PENDING_PAYMENT → CONFIRMED`). */
export function confirmPayment(id: string): Promise<AdminReservationDto> {
  return apiFetch(`/reservations/${id}/confirm-payment`, { method: "POST" });
}
