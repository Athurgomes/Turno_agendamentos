import { afterEach, beforeEach, describe, expect, it, vi } from "vitest";
import { apiFetch, apiFetchBlob, ApiError } from "./client";
import {
  clearSession,
  getSession,
  setRedirectToLogin,
  setSession,
} from "./sessionStore";

function jsonResponse(status: number, body: unknown): Response {
  return new Response(JSON.stringify(body), {
    status,
    headers: { "Content-Type": "application/json" },
  });
}

describe("apiFetch", () => {
  beforeEach(() => {
    clearSession();
    setRedirectToLogin(() => {});
  });

  afterEach(() => {
    vi.restoreAllMocks();
  });

  it("anexa o Authorization: Bearer do token em memória", async () => {
    setSession({
      accessToken: "token-123",
      user: {
        id: "1",
        role: "UNIT",
        name: "Unidade 101",
        unitId: "u1",
        unitIdentifier: "101",
        tempPassword: false,
      },
    });
    const fetchMock = vi
      .spyOn(globalThis, "fetch")
      .mockResolvedValue(jsonResponse(200, { ok: true }));

    await apiFetch("/areas");

    const [, init] = fetchMock.mock.calls[0];
    expect((init?.headers as Headers).get("Authorization")).toBe(
      "Bearer token-123",
    );
  });

  it("nunca usa URL absoluta: sempre base relativa /api/v1", async () => {
    const fetchMock = vi
      .spyOn(globalThis, "fetch")
      .mockResolvedValue(jsonResponse(200, {}));

    await apiFetch("/areas");

    expect(fetchMock.mock.calls[0][0]).toBe("/api/v1/areas");
  });

  it("em 401, faz refresh uma vez e repete a requisição original", async () => {
    let call = 0;
    const fetchMock = vi
      .spyOn(globalThis, "fetch")
      .mockImplementation(async (input) => {
        call += 1;
        const url = String(input);
        if (url === "/api/v1/areas" && call === 1) {
          return jsonResponse(401, {
            status: 401,
            code: "UNAUTHENTICATED",
            detail: "Sessão expirada.",
          });
        }
        if (url === "/api/v1/auth/refresh") {
          return jsonResponse(200, {
            accessToken: "novo-token",
            expiresIn: 900,
            user: {
              id: "1",
              role: "UNIT",
              name: "Unidade 101",
              unitId: "u1",
              unitIdentifier: "101",
              tempPassword: false,
            },
          });
        }
        return jsonResponse(200, { content: [] });
      });

    const result = await apiFetch<{ content: unknown[] }>("/areas");

    expect(result).toEqual({ content: [] });
    expect(getSession().accessToken).toBe("novo-token");
    expect(fetchMock).toHaveBeenCalledTimes(3); // 1ª /areas (401) + refresh + retry /areas
  });

  it("duas requisições concorrentes com 401 disparam um só refresh", async () => {
    let refreshCalls = 0;
    // Cada rota falha com 401 na 1ª chamada e responde 200 na 2ª (pós-refresh).
    const routeCalls = new Map<string, number>();
    vi.spyOn(globalThis, "fetch").mockImplementation(async (input) => {
      const url = String(input);
      if (url === "/api/v1/auth/refresh") {
        refreshCalls += 1;
        return jsonResponse(200, {
          accessToken: "novo-token",
          expiresIn: 900,
          user: {
            id: "1",
            role: "UNIT",
            name: "Unidade 101",
            unitId: "u1",
            unitIdentifier: "101",
            tempPassword: false,
          },
        });
      }
      const count = (routeCalls.get(url) ?? 0) + 1;
      routeCalls.set(url, count);
      if (count === 1) {
        return jsonResponse(401, {
          status: 401,
          code: "UNAUTHENTICATED",
          detail: "Sessão expirada.",
        });
      }
      return jsonResponse(200, { ok: url });
    });

    const [a, b] = await Promise.all([
      apiFetch("/areas"),
      apiFetch("/me/reservations"),
    ]);

    expect(a).toEqual({ ok: "/api/v1/areas" });
    expect(b).toEqual({ ok: "/api/v1/me/reservations" });
    expect(refreshCalls).toBe(1);
  });

  it("refresh falho limpa a sessão e redireciona para /login", async () => {
    setSession({
      accessToken: "expirado",
      user: {
        id: "1",
        role: "UNIT",
        name: "Unidade 101",
        unitId: "u1",
        unitIdentifier: "101",
        tempPassword: false,
      },
    });
    const redirect = vi.fn();
    setRedirectToLogin(redirect);

    vi.spyOn(globalThis, "fetch").mockImplementation(async (input) => {
      const url = String(input);
      if (url === "/api/v1/auth/refresh") {
        return jsonResponse(401, {
          status: 401,
          code: "UNAUTHENTICATED",
          detail: "Sessão expirada.",
        });
      }
      return jsonResponse(401, {
        status: 401,
        code: "UNAUTHENTICATED",
        detail: "Sessão expirada.",
      });
    });

    await expect(apiFetch("/areas")).rejects.toBeInstanceOf(ApiError);
    expect(getSession().accessToken).toBeNull();
    expect(redirect).toHaveBeenCalledTimes(1);
  });

  it("erro vira ApiError com detail em pt-BR pronto para exibir", async () => {
    vi.spyOn(globalThis, "fetch").mockResolvedValue(
      jsonResponse(422, {
        status: 422,
        code: "CAPACITY_EXCEEDED",
        detail: "Número de convidados acima da capacidade da área.",
      }),
    );

    const error = await apiFetch("/reservations", { method: "POST" }).catch(
      (e) => e,
    );

    expect(error).toBeInstanceOf(ApiError);
    expect(error.code).toBe("CAPACITY_EXCEEDED");
    expect(error.detail).toBe(
      "Número de convidados acima da capacidade da área.",
    );
  });

  it("suporta FormData sem setar Content-Type manual", async () => {
    const fetchMock = vi
      .spyOn(globalThis, "fetch")
      .mockResolvedValue(jsonResponse(201, {}));
    const form = new FormData();
    form.append("data", "{}");

    await apiFetch("/areas", { method: "POST", body: form });

    const [, init] = fetchMock.mock.calls[0];
    expect((init?.headers as Headers).has("Content-Type")).toBe(false);
    expect(init?.body).toBe(form);
  });
});

describe("apiFetchBlob", () => {
  beforeEach(() => {
    clearSession();
    setRedirectToLogin(() => {});
  });

  afterEach(() => {
    vi.restoreAllMocks();
  });

  it("em 401, faz refresh uma vez e repete a requisição original antes de ler o blob", async () => {
    let call = 0;
    vi.spyOn(globalThis, "fetch").mockImplementation(async (input) => {
      call += 1;
      const url = String(input);
      if (url === "/api/v1/dashboard/export" && call === 1) {
        return jsonResponse(401, {
          status: 401,
          code: "UNAUTHENTICATED",
          detail: "Sessão expirada.",
        });
      }
      if (url === "/api/v1/auth/refresh") {
        return jsonResponse(200, {
          accessToken: "novo-token",
          expiresIn: 900,
          user: {
            id: "1",
            role: "ADMIN",
            name: "Admin",
            unitId: null,
            unitIdentifier: null,
            tempPassword: false,
          },
        });
      }
      return new Response("csv,data", {
        status: 200,
        headers: { "Content-Disposition": 'attachment; filename="reservas.csv"' },
      });
    });

    const result = await apiFetchBlob(
      "/dashboard/export",
      "fallback.csv",
    );

    expect(result.filename).toBe("reservas.csv");
    expect(getSession().accessToken).toBe("novo-token");
    expect(await result.blob.text()).toBe("csv,data");
  });

  it("refresh falho na exportação propaga ApiError e redireciona para /login", async () => {
    const redirect = vi.fn();
    setRedirectToLogin(redirect);

    vi.spyOn(globalThis, "fetch").mockImplementation(async () =>
      jsonResponse(401, {
        status: 401,
        code: "UNAUTHENTICATED",
        detail: "Sessão expirada.",
      }),
    );

    await expect(
      apiFetchBlob("/dashboard/export", "fallback.csv"),
    ).rejects.toBeInstanceOf(ApiError);
    expect(getSession().accessToken).toBeNull();
    expect(redirect).toHaveBeenCalledTimes(1);
  });
});
