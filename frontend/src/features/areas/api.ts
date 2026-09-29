/**
 * Chamadas HTTP de áreas comuns (`docs/03-api.md` "Áreas", D-44). Camada fina
 * sobre `apiFetch`: nenhuma regra de negócio aqui, só o contrato da API.
 */
import { apiFetch } from "../../shared/api/client";
import type {
  AreaCategory,
  AreaCategoryTemplateDto,
  AreaDetail,
  AreaStatus,
  AreaSummary,
  InspectionDto,
  PhotoDto,
  ReservationSummary,
} from "../../shared/api/types";
import type { CreateAreaPayload, UpdateAreaPayload } from "./areaFormSchema";

export function listAreaCategories(): Promise<AreaCategoryTemplateDto[]> {
  return apiFetch("/area-categories");
}

export interface ListAreasParams {
  category?: AreaCategory;
  status?: AreaStatus;
}

export function listAreas(params: ListAreasParams = {}): Promise<AreaSummary[]> {
  const query = new URLSearchParams();
  if (params.category) query.set("category", params.category);
  if (params.status) query.set("status", params.status);
  const queryString = query.toString();
  return apiFetch(`/areas${queryString ? `?${queryString}` : ""}`);
}

export function getArea(id: string): Promise<AreaDetail> {
  return apiFetch(`/areas/${id}`);
}

function toDataPart(payload: unknown): Blob {
  return new Blob([JSON.stringify(payload)], { type: "application/json" });
}

/** `POST /areas` (multipart `data` + `photos`, ≥ 1 — RN-11, RN-12). */
export function createArea(payload: CreateAreaPayload, photos: File[]): Promise<AreaDetail> {
  const formData = new FormData();
  formData.append("data", toDataPart(payload));
  photos.forEach((photo) => formData.append("photos", photo));
  return apiFetch("/areas", { method: "POST", body: formData });
}

/** `PUT /areas/{id}` (JSON parcial). SYNDIC só envia os campos que o D-20 permite. */
export function updateArea(id: string, payload: UpdateAreaPayload): Promise<AreaDetail> {
  return apiFetch(`/areas/${id}`, { method: "PUT", body: payload });
}

export interface AreaStatusPayload {
  status: AreaStatus;
  justification?: string;
  confirmCancelAffected?: boolean;
}

export interface AreaStatusResult {
  area: AreaDetail;
  cancelledReservations: number;
}

export function updateAreaStatus(
  id: string,
  payload: AreaStatusPayload,
): Promise<AreaStatusResult> {
  return apiFetch(`/areas/${id}/status`, { method: "PATCH", body: payload });
}

export interface DeleteAreaPayload {
  justification?: string;
  confirmCancelAffected?: boolean;
}

export function deleteArea(id: string, payload: DeleteAreaPayload): Promise<void> {
  return apiFetch(`/areas/${id}`, { method: "DELETE", body: payload });
}

export interface ListAreaPhotosParams {
  from?: string;
  to?: string;
  includeArchived?: boolean;
}

export function getAreaPhotos(
  id: string,
  params: ListAreaPhotosParams = {},
): Promise<PhotoDto[]> {
  const query = new URLSearchParams();
  if (params.from) query.set("from", params.from);
  if (params.to) query.set("to", params.to);
  if (params.includeArchived) query.set("includeArchived", "true");
  const queryString = query.toString();
  return apiFetch(`/areas/${id}/photos${queryString ? `?${queryString}` : ""}`);
}

export interface UploadAreaPhotosParams {
  caption?: string;
  takenAt?: string;
  inspectionId?: string;
}

export function uploadAreaPhotos(
  id: string,
  photos: File[],
  params: UploadAreaPhotosParams = {},
): Promise<PhotoDto[]> {
  const formData = new FormData();
  photos.forEach((photo) => formData.append("photos", photo));
  if (params.caption) formData.append("caption", params.caption);
  if (params.takenAt) formData.append("takenAt", params.takenAt);
  if (params.inspectionId) formData.append("inspectionId", params.inspectionId);
  return apiFetch(`/areas/${id}/photos`, { method: "POST", body: formData });
}

export function updateAreaPhoto(
  areaId: string,
  photoId: string,
  payload: { caption?: string; featured?: boolean },
): Promise<PhotoDto> {
  return apiFetch(`/areas/${areaId}/photos/${photoId}`, { method: "PATCH", body: payload });
}

export function archiveAreaPhoto(areaId: string, photoId: string): Promise<PhotoDto> {
  return apiFetch(`/areas/${areaId}/photos/${photoId}/archive`, { method: "POST" });
}

export function listInspections(areaId: string): Promise<InspectionDto[]> {
  return apiFetch(`/areas/${areaId}/inspections`);
}

export interface CreateInspectionData {
  inspectedAt: string;
  overallCondition: InspectionDto["overallCondition"];
  notes?: string;
}

export function createInspection(
  areaId: string,
  data: CreateInspectionData,
  photos: File[],
): Promise<InspectionDto> {
  const formData = new FormData();
  formData.append("data", toDataPart(data));
  photos.forEach((photo) => formData.append("photos", photo));
  return apiFetch(`/areas/${areaId}/inspections`, { method: "POST", body: formData });
}

/** Reexportado para telas que só precisam do tipo de "reservas afetadas" (RN-16). */
export type { ReservationSummary };
