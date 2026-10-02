/**
 * Chamadas HTTP da página inicial do síndico (`docs/03-api.md` "Dashboard e
 * exportação", F7-1). Camada fina sobre `apiFetch`: nenhuma regra de negócio
 * aqui, só o contrato da API.
 */
import { apiFetch } from "../../shared/api/client";
import type { DashboardHomeDto } from "../../shared/api/types";

/** `GET /dashboard/home` (RF-SIN-01, F7-1): uma chamada só para a página inteira. */
export function getDashboardHome(): Promise<DashboardHomeDto> {
  return apiFetch("/dashboard/home");
}
