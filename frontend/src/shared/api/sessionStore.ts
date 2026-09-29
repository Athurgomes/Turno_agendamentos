/**
 * Fonte única da sessão em memória (RNF-02): access token e usuário nunca vão
 * para `localStorage`/`sessionStorage`. `client.ts` lê/escreve aqui para anexar
 * o token e reagir a refresh; `features/auth/session.tsx` expõe isso ao React.
 */
import type { SessionUser } from "./types";

interface Session {
  accessToken: string | null;
  user: SessionUser | null;
}

let session: Session = { accessToken: null, user: null };
const listeners = new Set<() => void>();

function notify() {
  listeners.forEach((listener) => listener());
}

export function getSession(): Session {
  return session;
}

export function setSession(next: { accessToken: string; user: SessionUser }) {
  session = { accessToken: next.accessToken, user: next.user };
  notify();
}

export function clearSession() {
  session = { accessToken: null, user: null };
  notify();
}

export function subscribeSession(listener: () => void): () => void {
  listeners.add(listener);
  return () => listeners.delete(listener);
}

/** Ponto único de "expulsão" da sessão, sobrescrito pelo router (`RequireAuth`) para navegar sem recarregar a página. */
export let redirectToLogin = () => {
  window.location.assign("/login");
};

export function setRedirectToLogin(fn: () => void) {
  redirectToLogin = fn;
}
