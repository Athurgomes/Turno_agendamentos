/**
 * Chamadas HTTP de unidades e moradores (`docs/03-api.md` "Unidades e
 * moradores", D-43). Camada fina sobre `apiFetch`: nenhuma regra de negócio
 * aqui, só o contrato da API.
 */
import { apiFetch } from "../../shared/api/client";
import type {
  Credentials,
  MyUnitDto,
  Page,
  ReservationSummary,
  ResidentDto,
  UnitDetail,
  UnitSummary,
} from "../../shared/api/types";
import type {
  AddMyResidentPayload,
  CreateUnitPayload,
  EditMyResidentPayload,
  EditUnitPayload,
  PrimaryAndMembersPayload,
} from "./residentForm";

export interface ListUnitsParams {
  search?: string;
  block?: string;
  page?: number;
}

export function listUnits(params: ListUnitsParams = {}): Promise<Page<UnitSummary>> {
  const query = new URLSearchParams();
  if (params.search) query.set("search", params.search);
  if (params.block) query.set("block", params.block);
  if (params.page !== undefined) query.set("page", String(params.page));
  const queryString = query.toString();
  return apiFetch<Page<UnitSummary>>(`/units${queryString ? `?${queryString}` : ""}`);
}

export function getUnit(id: string): Promise<UnitDetail> {
  return apiFetch<UnitDetail>(`/units/${id}`);
}

export function createUnit(
  payload: CreateUnitPayload,
): Promise<{ unit: UnitDetail; credentials: Credentials }> {
  return apiFetch("/units", { method: "POST", body: payload });
}

export function updateUnit(id: string, payload: EditUnitPayload): Promise<UnitDetail> {
  return apiFetch<UnitDetail>(`/units/${id}`, { method: "PUT", body: payload });
}

export function deactivateUnit(id: string): Promise<void> {
  return apiFetch<void>(`/units/${id}`, { method: "DELETE" });
}

export function resetUnitPassword(id: string): Promise<Credentials> {
  return apiFetch<Credentials>(`/units/${id}/reset-password`, { method: "POST" });
}

export function transferUnit(
  id: string,
  payload: PrimaryAndMembersPayload,
): Promise<{ credentials: Credentials; affectedReservations: ReservationSummary[] }> {
  return apiFetch(`/units/${id}/transfer`, { method: "POST", body: payload });
}

export function getMyUnit(): Promise<MyUnitDto> {
  return apiFetch<MyUnitDto>("/me/unit");
}

export function addMyUnitResident(payload: AddMyResidentPayload): Promise<ResidentDto> {
  return apiFetch<ResidentDto>("/me/unit/residents", { method: "POST", body: payload });
}

export function updateMyUnitResident(
  id: string,
  payload: EditMyResidentPayload,
): Promise<ResidentDto> {
  return apiFetch<ResidentDto>(`/me/unit/residents/${id}`, { method: "PUT", body: payload });
}

export function removeMyUnitResident(id: string): Promise<void> {
  return apiFetch<void>(`/me/unit/residents/${id}`, { method: "DELETE" });
}
