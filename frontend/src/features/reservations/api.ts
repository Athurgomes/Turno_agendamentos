/**
 * Chamadas HTTP de reservas (`docs/03-api.md` "Reservas e bloqueios", D-49).
 * Camada fina sobre `apiFetch`: nenhuma regra de negócio aqui, só o contrato da API.
 */
import { apiFetch } from "../../shared/api/client";
import type {
  AdminReservationDto,
  AvailabilityDay,
  Page,
  PublicSettingsDto,
  ReservationDto,
  ReservationEventDto,
  ReservationKind,
  ReservationStatus,
} from "../../shared/api/types";

export function getAreaAvailability(
  areaId: string,
  from: string,
  to: string,
): Promise<AvailabilityDay[]> {
  const query = new URLSearchParams({ from, to });
  return apiFetch(`/areas/${areaId}/availability?${query.toString()}`);
}

/** Explica as regras da RN-20/21/22/30 ao morador (D-44). */
export function getPublicSettings(): Promise<PublicSettingsDto> {
  return apiFetch("/settings/public");
}

export interface CreateReservationPayload {
  areaId: string;
  date: string;
  startTime: string;
  endTime: string;
  residentId: string;
  guests: number;
  notes?: string;
}

export interface CreateReservationResult {
  reservation: ReservationDto;
  whatsappPaymentUrl: string | null;
}

/** `POST /reservations` (RF-RES-03): valida RN-18..24 na ordem e define o status inicial (RN-25). */
export function createReservation(
  payload: CreateReservationPayload,
): Promise<CreateReservationResult> {
  return apiFetch("/reservations", { method: "POST", body: payload });
}

export type ReservationScope = "upcoming" | "past";

export function listMyReservations(
  scope: ReservationScope,
  page = 0,
): Promise<Page<ReservationDto>> {
  const query = new URLSearchParams({ scope, page: String(page) });
  return apiFetch(`/me/reservations?${query.toString()}`);
}

/** `POST /me/reservations/{id}/cancel` (RN-30). */
export function cancelMyReservation(id: string): Promise<ReservationDto> {
  return apiFetch(`/me/reservations/${id}/cancel`, { method: "POST" });
}

/**
 * Filtros de `GET /reservations` (S/A, D-49). `unitId` (UUID) fica disponível para uso
 * futuro por outra tela que já tenha o id; o filtro de texto digitado pelo usuário é
 * sempre `unitIdentifier` (D-53) — nunca envie o texto digitado em `unitId`.
 */
export interface AdminReservationFilters {
  areaId?: string;
  from?: string;
  to?: string;
  status?: ReservationStatus;
  unitId?: string;
  unitIdentifier?: string;
  kind?: ReservationKind;
  page?: number;
}

export function listAdminReservations(
  filters: AdminReservationFilters = {},
): Promise<Page<AdminReservationDto>> {
  const query = new URLSearchParams();
  if (filters.areaId) query.set("areaId", filters.areaId);
  if (filters.from) query.set("from", filters.from);
  if (filters.to) query.set("to", filters.to);
  if (filters.status) query.set("status", filters.status);
  if (filters.unitId) query.set("unitId", filters.unitId);
  if (filters.unitIdentifier) query.set("unitIdentifier", filters.unitIdentifier);
  if (filters.kind) query.set("kind", filters.kind);
  query.set("page", String(filters.page ?? 0));
  return apiFetch(`/reservations?${query.toString()}`);
}

export function getAdminReservation(id: string): Promise<AdminReservationDto> {
  return apiFetch(`/reservations/${id}`);
}

/**
 * `GET /reservations/{id}` para a própria unidade (D-49): mesma rota de
 * `getAdminReservation`, mas o backend devolve `ReservationDto` (sem os campos
 * de contato) quando quem chama é `UNIT`. Usado para dar contexto (área, data)
 * na tela de report (RF-REP-01) sem expor dados de outra unidade.
 */
export function getReservation(id: string): Promise<ReservationDto> {
  return apiFetch(`/reservations/${id}`);
}

export function getReservationEvents(id: string): Promise<ReservationEventDto[]> {
  return apiFetch(`/reservations/${id}/events`);
}

export interface UpdateReservationPayload {
  areaId?: string;
  date?: string;
  startTime?: string;
  endTime?: string;
  justification: string;
}

/** `PUT /reservations/{id}` (ADMIN, RN-27, RN-28). */
export function updateReservation(
  id: string,
  payload: UpdateReservationPayload,
): Promise<AdminReservationDto> {
  return apiFetch(`/reservations/${id}`, { method: "PUT", body: payload });
}

/** `POST /reservations/{id}/cancel` (ADMIN, RN-27). */
export function cancelAdminReservation(
  id: string,
  justification: string,
): Promise<AdminReservationDto> {
  return apiFetch(`/reservations/${id}/cancel`, { method: "POST", body: { justification } });
}

export interface CreateBlockPayload {
  areaId: string;
  date: string;
  startTime: string;
  endTime: string;
  reason: string;
}

/** `POST /blocks` (S/A, RN-33): sobreposição com reserva ativa → `409 RESERVATION_OVERLAP`. */
export function createBlock(payload: CreateBlockPayload): Promise<AdminReservationDto> {
  return apiFetch("/blocks", { method: "POST", body: payload });
}

/** `DELETE /blocks/{id}` (S/A). */
export function deleteBlock(id: string): Promise<void> {
  return apiFetch(`/blocks/${id}`, { method: "DELETE" });
}
