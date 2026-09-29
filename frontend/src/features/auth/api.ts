/**
 * Chamadas HTTP de autenticação (`docs/03-api.md` "Auth"). Camada fina sobre
 * `apiFetch`: nenhuma regra de negócio aqui, só o contrato da API.
 */
import { apiFetch } from "../../shared/api/client";
import type { LoginResponse } from "../../shared/api/types";

export function login(login: string, password: string): Promise<LoginResponse> {
  return apiFetch<LoginResponse>("/auth/login", {
    method: "POST",
    body: { login, password },
  });
}

export function changePassword(
  currentPassword: string,
  newPassword: string,
): Promise<void> {
  return apiFetch<void>("/auth/password", {
    method: "PUT",
    body: { currentPassword, newPassword },
  });
}
