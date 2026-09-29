import { apiFetch } from "../../shared/api/client";
import {
  clearSession,
  setSession as storeSession,
} from "../../shared/api/sessionStore";
import type { LoginResponse } from "../../shared/api/types";

/** Tenta restaurar a sessão a partir do cookie de refresh. Silencioso em caso de falha (visitante sem sessão). */
export async function bootstrapSession(): Promise<void> {
  try {
    const data = await apiFetch<LoginResponse>("/auth/refresh", {
      method: "POST",
    });
    storeSession({ accessToken: data.accessToken, user: data.user });
  } catch {
    clearSession();
  }
}
