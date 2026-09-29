/**
 * Chamadas HTTP dos parâmetros de regra do condomínio (`docs/03-api.md`
 * "Contas de síndico e configurações", RN-20/21/22/30/34). Camada fina sobre
 * `apiFetch`: nenhuma regra de negócio aqui, só o contrato da API.
 */
import { apiFetch } from "../../../shared/api/client";
import type { SettingsDto } from "../../../shared/api/types";

export function getSettings(): Promise<SettingsDto> {
  return apiFetch<SettingsDto>("/admin/settings");
}

export function updateSettings(payload: SettingsDto): Promise<SettingsDto> {
  return apiFetch<SettingsDto>("/admin/settings", { method: "PUT", body: payload });
}
