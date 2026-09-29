/**
 * Chamadas HTTP de reports (`docs/03-api.md` "Reports", D-50). Camada fina
 * sobre `apiFetch`: nenhuma regra de negócio aqui, só o contrato da API.
 * Conferido contra o backend da F6 (`report/api/*`). Os testes desta feature
 * mockam `./hooks`.
 */
import { apiFetch } from "../../shared/api/client";
import type {
  AdminReportDto,
  Page,
  ReportCategory,
  ReportDto,
  ReportStatus,
} from "../../shared/api/types";

function toDataPart(payload: unknown): Blob {
  return new Blob([JSON.stringify(payload)], { type: "application/json" });
}

export interface CreateReportPayload {
  category: ReportCategory;
  description: string;
  residentId: string;
}

/** `POST /me/reservations/{id}/reports` (RF-REP-01, RN-34, RN-35). */
export function createReport(
  reservationId: string,
  payload: CreateReportPayload,
  photos: File[],
): Promise<ReportDto> {
  const formData = new FormData();
  formData.append("data", toDataPart(payload));
  photos.forEach((photo) => formData.append("photos", photo));
  return apiFetch(`/me/reservations/${reservationId}/reports`, { method: "POST", body: formData });
}

/** `GET /me/reports` (RF-REP-05): por criação decrescente, comentários já filtrados pelo backend. */
export function listMyReports(): Promise<ReportDto[]> {
  return apiFetch("/me/reports");
}

export interface AdminReportFilters {
  status?: ReportStatus;
  areaId?: string;
  category?: ReportCategory;
  from?: string;
  to?: string;
  page?: number;
}

/** `GET /reports` (S/A, RF-REP-02/03): caixa de reports. */
export function listAdminReports(filters: AdminReportFilters = {}): Promise<Page<AdminReportDto>> {
  const query = new URLSearchParams();
  if (filters.status) query.set("status", filters.status);
  if (filters.areaId) query.set("areaId", filters.areaId);
  if (filters.category) query.set("category", filters.category);
  if (filters.from) query.set("from", filters.from);
  if (filters.to) query.set("to", filters.to);
  query.set("page", String(filters.page ?? 0));
  return apiFetch(`/reports?${query.toString()}`);
}

/** `GET /reports/{id}` (S/A): detalhe completo, com todos os comentários. */
export function getReport(id: string): Promise<AdminReportDto> {
  return apiFetch(`/reports/${id}`);
}

export interface UpdateReportStatusPayload {
  status: ReportStatus;
  justification?: string;
  maintenanceCost?: number;
}

/** `PATCH /reports/{id}/status` (RN-36, RN-37). */
export function updateReportStatus(
  id: string,
  payload: UpdateReportStatusPayload,
): Promise<AdminReportDto> {
  return apiFetch(`/reports/${id}/status`, { method: "PATCH", body: payload });
}

export interface AddReportCommentPayload {
  text: string;
  visibleToResident: boolean;
}

/** `POST /reports/{id}/comments` (RF-REP-03). */
export function addReportComment(
  id: string,
  payload: AddReportCommentPayload,
): Promise<AdminReportDto> {
  return apiFetch(`/reports/${id}/comments`, { method: "POST", body: payload });
}

/** `POST /reports/{id}/photos` (multipart 1 a 5, fotos do reparo — `stage = REPAIR`). */
export function uploadReportPhotos(id: string, photos: File[]): Promise<AdminReportDto> {
  const formData = new FormData();
  photos.forEach((photo) => formData.append("photos", photo));
  return apiFetch(`/reports/${id}/photos`, { method: "POST", body: formData });
}

export interface ReportsSummaryDto {
  open: number;
}

/** `GET /reports/summary` (S/A): contador de reports em status não final, para o menu. */
export function getReportsSummary(): Promise<ReportsSummaryDto> {
  return apiFetch("/reports/summary");
}
