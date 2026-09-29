/**
 * Sessão da conta autenticada, exposta ao React. O token de acesso mora só em
 * memória (`sessionStore`) — some no F5; por isso `bootstrapSession` tenta um
 * refresh (cookie httpOnly) assim que o app carrega, para restaurar a sessão.
 */
import { useEffect, useState } from "react";
import type { ReactNode } from "react";
import { apiFetch } from "../../shared/api/client";
import {
  clearSession as clearStoredSession,
  getSession,
  setSession as storeSession,
  subscribeSession,
} from "../../shared/api/sessionStore";
import { bootstrapSession } from "./bootstrapSession";
import { SessionContext } from "./sessionContext";

export function SessionProvider({ children }: { children: ReactNode }) {
  const [{ accessToken, user }, setState] = useState(getSession());
  const [isLoading, setIsLoading] = useState(true);

  useEffect(() => subscribeSession(() => setState(getSession())), []);

  useEffect(() => {
    let active = true;
    bootstrapSession().finally(() => {
      if (active) setIsLoading(false);
    });
    return () => {
      active = false;
    };
  }, []);

  async function logout() {
    try {
      await apiFetch("/auth/logout", { method: "POST" });
    } finally {
      clearStoredSession();
    }
  }

  return (
    <SessionContext.Provider
      value={{ accessToken, user, isLoading, setSession: storeSession, logout }}
    >
      {children}
    </SessionContext.Provider>
  );
}
