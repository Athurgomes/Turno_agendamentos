import { afterEach, beforeEach, describe, expect, it, vi } from "vitest";
import { render, screen } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { MemoryRouter, Route, Routes } from "react-router-dom";
import { LoginPage } from "./LoginPage";
import { SessionProvider } from "./session";
import { clearSession } from "../../shared/api/sessionStore";

function jsonResponse(status: number, body: unknown): Response {
  return new Response(body === null ? null : JSON.stringify(body), {
    status,
    headers: body === null ? {} : { "Content-Type": "application/json" },
  });
}

function renderLogin(initialEntry: string | { pathname: string; state?: unknown }) {
  return render(
    <MemoryRouter initialEntries={[initialEntry]}>
      <SessionProvider>
        <Routes>
          <Route path="/login" element={<LoginPage />} />
          <Route path="/painel" element={<div>Painel (síndico/admin)</div>} />
          <Route path="/areas" element={<div>Áreas (morador)</div>} />
          <Route
            path="/minhas-reservas"
            element={<div>Minhas reservas</div>}
          />
        </Routes>
      </SessionProvider>
    </MemoryRouter>,
  );
}

const unitUser = {
  id: "1",
  role: "UNIT" as const,
  name: "Fernanda Reis",
  unitId: "u1",
  unitIdentifier: "a-1203",
  tempPassword: false,
};

describe("LoginPage", () => {
  beforeEach(() => {
    clearSession();
  });

  afterEach(() => {
    vi.restoreAllMocks();
  });

  it("login bem-sucedido de conta UNIT redireciona para a home do perfil", async () => {
    const user = userEvent.setup();
    vi.spyOn(globalThis, "fetch").mockImplementation(async (input) => {
      const url = String(input);
      if (url === "/api/v1/auth/refresh") {
        return jsonResponse(401, {
          status: 401,
          code: "UNAUTHENTICATED",
          detail: "Sem sessão.",
        });
      }
      if (url === "/api/v1/auth/login") {
        return jsonResponse(200, {
          accessToken: "token-abc",
          expiresIn: 900,
          user: unitUser,
        });
      }
      throw new Error(`chamada inesperada: ${url}`);
    });

    renderLogin("/login");

    await user.type(
      await screen.findByLabelText(/usuário ou e-mail/i),
      "a-1203",
    );
    await user.type(screen.getByLabelText(/^senha$/i), "senha123");
    await user.click(screen.getByRole("button", { name: /^entrar$/i }));

    expect(await screen.findByText("Áreas (morador)")).toBeInTheDocument();
  });

  it("login bem-sucedido preserva a rota que o usuário tentou abrir antes de ser mandado para /login", async () => {
    const user = userEvent.setup();
    vi.spyOn(globalThis, "fetch").mockImplementation(async (input) => {
      const url = String(input);
      if (url === "/api/v1/auth/refresh") {
        return jsonResponse(401, {
          status: 401,
          code: "UNAUTHENTICATED",
          detail: "Sem sessão.",
        });
      }
      if (url === "/api/v1/auth/login") {
        return jsonResponse(200, {
          accessToken: "token-abc",
          expiresIn: 900,
          user: unitUser,
        });
      }
      throw new Error(`chamada inesperada: ${url}`);
    });

    renderLogin({
      pathname: "/login",
      state: { from: { pathname: "/minhas-reservas" } },
    });

    await user.type(
      await screen.findByLabelText(/usuário ou e-mail/i),
      "a-1203",
    );
    await user.type(screen.getByLabelText(/^senha$/i), "senha123");
    await user.click(screen.getByRole("button", { name: /^entrar$/i }));

    expect(await screen.findByText("Minhas reservas")).toBeInTheDocument();
  });

  it("401 mostra a mensagem genérica da RN-05, sem detalhar o motivo", async () => {
    const user = userEvent.setup();
    vi.spyOn(globalThis, "fetch").mockImplementation(async (input) => {
      const url = String(input);
      if (url === "/api/v1/auth/refresh") {
        return jsonResponse(401, {
          status: 401,
          code: "UNAUTHENTICATED",
          detail: "Sem sessão.",
        });
      }
      if (url === "/api/v1/auth/login") {
        return jsonResponse(401, {
          status: 401,
          code: "INVALID_CREDENTIALS",
          detail: "A senha informada não corresponde a este usuário.",
        });
      }
      throw new Error(`chamada inesperada: ${url}`);
    });

    renderLogin("/login");

    await user.type(
      await screen.findByLabelText(/usuário ou e-mail/i),
      "a-1203",
    );
    await user.type(screen.getByLabelText(/^senha$/i), "senha-errada");
    await user.click(screen.getByRole("button", { name: /^entrar$/i }));

    expect(
      await screen.findByText("Usuário ou senha inválidos."),
    ).toBeInTheDocument();
    expect(
      screen.queryByText(/não corresponde a este usuário/i),
    ).not.toBeInTheDocument();
  });

  it("423 (conta bloqueada) mostra o detail vindo do backend", async () => {
    const user = userEvent.setup();
    vi.spyOn(globalThis, "fetch").mockImplementation(async (input) => {
      const url = String(input);
      if (url === "/api/v1/auth/refresh") {
        return jsonResponse(401, {
          status: 401,
          code: "UNAUTHENTICATED",
          detail: "Sem sessão.",
        });
      }
      if (url === "/api/v1/auth/login") {
        return jsonResponse(423, {
          status: 423,
          code: "ACCOUNT_LOCKED",
          detail: "Conta bloqueada por 15 minutos após tentativas inválidas.",
        });
      }
      throw new Error(`chamada inesperada: ${url}`);
    });

    renderLogin("/login");

    await user.type(
      await screen.findByLabelText(/usuário ou e-mail/i),
      "a-1203",
    );
    await user.type(screen.getByLabelText(/^senha$/i), "senha123");
    await user.click(screen.getByRole("button", { name: /^entrar$/i }));

    expect(
      await screen.findByText(
        "Conta bloqueada por 15 minutos após tentativas inválidas.",
      ),
    ).toBeInTheDocument();
  });

  it("usuário já autenticado que acessa /login vai direto para a home do perfil", async () => {
    vi.spyOn(globalThis, "fetch").mockImplementation(async (input) => {
      const url = String(input);
      if (url === "/api/v1/auth/refresh") {
        return jsonResponse(200, {
          accessToken: "token-restaurado",
          expiresIn: 900,
          user: {
            id: "2",
            role: "ADMIN",
            name: "Ana Souza",
            unitId: null,
            unitIdentifier: null,
            tempPassword: false,
          },
        });
      }
      throw new Error(`chamada inesperada: ${url}`);
    });

    renderLogin("/login");

    expect(
      await screen.findByText("Painel (síndico/admin)"),
    ).toBeInTheDocument();
  });
});
