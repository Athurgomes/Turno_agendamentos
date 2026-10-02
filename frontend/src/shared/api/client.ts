/**
 * Cliente HTTP sobre `fetch` para `/api/v1` (nunca URL absoluta — CLAUDE.md §11).
 * Anexa o access token em memória, tenta um único refresh em 401 (com fila para
 * requisições concorrentes) e traduz erros para `ApiError` a partir do
 * `problem+json` (`docs/03-api.md`).
 */
import type { LoginResponse, ProblemDetailBody } from "./types";
import {
  clearSession,
  getSession,
  redirectToLogin,
  setSession,
} from "./sessionStore";

const API_BASE = "/api/v1";

export class ApiError extends Error {
  status: number;
  code: string;
  /** Mensagem pt-BR pronta para exibir ao usuário (`ProblemDetail.detail`). */
  detail: string;
  errors?: ProblemDetailBody["errors"];
  /** Corpo completo do `problem+json`, para campos extras específicos de um erro (ex.: `affectedReservations` em `409`). */
  problem: ProblemDetailBody;

  constructor(problem: ProblemDetailBody) {
    super(problem.detail);
    this.name = "ApiError";
    this.status = problem.status;
    this.code = problem.code;
    this.detail = problem.detail;
    this.errors = problem.errors;
    this.problem = problem;
  }
}

export interface ApiFetchOptions extends Omit<RequestInit, "body"> {
  body?: unknown;
}

/** Refresh em andamento, compartilhado por todas as requisições que tomam 401 ao mesmo tempo. */
let refreshPromise: Promise<boolean> | null = null;

async function refreshSession(): Promise<boolean> {
  if (!refreshPromise) {
    refreshPromise = (async () => {
      try {
        const response = await fetch(`${API_BASE}/auth/refresh`, {
          method: "POST",
          credentials: "same-origin",
        });
        if (!response.ok) return false;
        const data: LoginResponse = await response.json();
        setSession({ accessToken: data.accessToken, user: data.user });
        return true;
      } catch {
        return false;
      }
    })().finally(() => {
      refreshPromise = null;
    });
  }
  return refreshPromise;
}

function buildRequestInit(options: ApiFetchOptions): RequestInit {
  const { body, headers, ...rest } = options;
  const init: RequestInit = {
    ...rest,
    credentials: "same-origin",
    headers: new Headers(headers),
  };

  const { accessToken } = getSession();
  if (accessToken) {
    (init.headers as Headers).set("Authorization", `Bearer ${accessToken}`);
  }

  if (body instanceof FormData) {
    // Nunca setar Content-Type manual em multipart: o browser define o boundary.
    init.body = body;
  } else if (body !== undefined) {
    (init.headers as Headers).set("Content-Type", "application/json");
    init.body = JSON.stringify(body);
  }

  return init;
}

async function toApiError(response: Response): Promise<ApiError> {
  let problem: ProblemDetailBody;
  try {
    problem = await response.json();
  } catch {
    problem = {
      status: response.status,
      code: "INTERNAL_ERROR",
      detail: "Não foi possível completar a solicitação. Tente novamente.",
    };
  }
  return new ApiError(problem);
}

/**
 * Faz a requisição a `/api/v1${path}` e, em 401, tenta um refresh (uma vez,
 * com fila única para concorrência) e repete a requisição original; se o
 * refresh falhar, limpa a sessão e manda para `/login`. Usado por `apiFetch`
 * e `apiFetchBlob`, que só diferem em como leem o corpo da resposta.
 */
async function fetchWithRefresh(
  path: string,
  options: ApiFetchOptions,
): Promise<Response> {
  const response = await fetch(`${API_BASE}${path}`, buildRequestInit(options));

  if (response.status === 401 && !path.startsWith("/auth/")) {
    const refreshed = await refreshSession();
    if (refreshed) {
      return fetch(`${API_BASE}${path}`, buildRequestInit(options));
    }
    clearSession();
    redirectToLogin();
    throw await toApiError(response);
  }

  return response;
}

export async function apiFetch<T = void>(
  path: string,
  options: ApiFetchOptions = {},
): Promise<T> {
  const response = await fetchWithRefresh(path, options);
  if (!response.ok) throw await toApiError(response);
  return parseBody<T>(response);
}

async function parseBody<T>(response: Response): Promise<T> {
  if (response.status === 204) return undefined as T;
  const text = await response.text();
  return (text ? JSON.parse(text) : undefined) as T;
}

/** Nome de arquivo entre aspas de `Content-Disposition: attachment; filename="..."`. */
function filenameFromContentDisposition(value: string | null, fallback: string): string {
  const match = value ? /filename="?([^";]+)"?/.exec(value) : null;
  return match ? match[1] : fallback;
}

/**
 * Baixa um arquivo (exportação CSV/XLSX) anexando o token da sessão — nunca
 * `<a href>` direto para `/api/...`, porque o token vive só em memória
 * (CLAUDE.md §7). Devolve o blob e o nome de arquivo sugerido pelo servidor.
 */
export async function apiFetchBlob(
  path: string,
  fallbackFilename: string,
): Promise<{ blob: Blob; filename: string }> {
  const response = await fetchWithRefresh(path, {});
  if (!response.ok) throw await toApiError(response);
  const blob = await response.blob();
  const filename = filenameFromContentDisposition(
    response.headers.get("Content-Disposition"),
    fallbackFilename,
  );
  return { blob, filename };
}
