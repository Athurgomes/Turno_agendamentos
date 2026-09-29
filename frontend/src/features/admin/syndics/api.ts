/**
 * Chamadas HTTP de contas de síndico (`docs/03-api.md` "Contas de síndico e
 * configurações", RF-UNI-07). Camada fina sobre `apiFetch`.
 */
import { apiFetch } from "../../../shared/api/client";
import type { Credentials, SyndicDto } from "../../../shared/api/types";

export interface CreateSyndicPayload {
  name: string;
  email: string;
  phone: string;
}

export function listSyndics(): Promise<SyndicDto[]> {
  return apiFetch<SyndicDto[]>("/admin/syndics");
}

export function createSyndic(
  payload: CreateSyndicPayload,
): Promise<{ syndic: SyndicDto; credentials: Credentials }> {
  return apiFetch("/admin/syndics", { method: "POST", body: payload });
}

export function deactivateSyndic(id: string): Promise<void> {
  return apiFetch<void>(`/admin/syndics/${id}`, { method: "DELETE" });
}
