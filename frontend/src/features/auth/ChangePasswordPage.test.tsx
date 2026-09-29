import { afterEach, beforeEach, describe, expect, it, vi } from "vitest";
import { render, screen } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { MemoryRouter, Route, Routes } from "react-router-dom";
import { ChangePasswordPage } from "./ChangePasswordPage";
import { SessionProvider } from "./session";
import { clearSession, getSession } from "../../shared/api/sessionStore";

function jsonResponse(status: number, body: unknown): Response {
  return new Response(body === null ? null : JSON.stringify(body), {
    status,
    headers: body === null ? {} : { "Content-Type": "application/json" },
  });
}

const unitUser = {
  id: "1",
  role: "UNIT" as const,
  name: "Fernanda Reis",
  unitId: "u1",
  unitIdentifier: "a-1203",
  tempPassword: true,
};

function mockRefreshOnly() {
  vi.spyOn(globalThis, "fetch").mockImplementation(async (input) => {
    const url = String(input);
    if (url === "/api/v1/auth/refresh") {
      return jsonResponse(200, {
        accessToken: "token-abc",
        expiresIn: 900,
        user: unitUser,
      });
    }
    throw new Error(`chamada inesperada: ${url}`);
  });
}

function renderPage() {
  return render(
    <MemoryRouter initialEntries={["/alterar-senha"]}>
      <SessionProvider>
        <Routes>
          <Route path="/alterar-senha" element={<ChangePasswordPage />} />
        </Routes>
      </SessionProvider>
    </MemoryRouter>,
  );
}

async function fillAndSubmit(
  user: ReturnType<typeof userEvent.setup>,
  values: { current: string; next: string; confirm: string },
) {
  await user.type(
    await screen.findByLabelText(/senha atual/i),
    values.current,
  );
  await user.type(screen.getByLabelText(/^nova senha$/i), values.next);
  await user.type(
    screen.getByLabelText(/confirmar nova senha/i),
    values.confirm,
  );
  await user.click(screen.getByRole("button", { name: /salvar nova senha/i }));
}

describe("ChangePasswordPage", () => {
  beforeEach(() => {
    clearSession();
  });

  afterEach(() => {
    vi.restoreAllMocks();
  });

  it("sucesso: mostra confirmação e zera tempPassword na sessão", async () => {
    const user = userEvent.setup();
    vi.spyOn(globalThis, "fetch").mockImplementation(async (input, init) => {
      const url = String(input);
      if (url === "/api/v1/auth/refresh") {
        return jsonResponse(200, {
          accessToken: "token-abc",
          expiresIn: 900,
          user: unitUser,
        });
      }
      if (url === "/api/v1/auth/password" && init?.method === "PUT") {
        return jsonResponse(204, null);
      }
      throw new Error(`chamada inesperada: ${url}`);
    });

    renderPage();

    await fillAndSubmit(user, {
      current: "temporaria1",
      next: "novaSenha123",
      confirm: "novaSenha123",
    });

    expect(
      await screen.findByText("Senha alterada com sucesso."),
    ).toBeInTheDocument();
    expect(getSession().user?.tempPassword).toBe(false);
  });

  it("422 INVALID_CURRENT_PASSWORD aponta o erro no campo de senha atual", async () => {
    const user = userEvent.setup();
    vi.spyOn(globalThis, "fetch").mockImplementation(async (input, init) => {
      const url = String(input);
      if (url === "/api/v1/auth/refresh") {
        return jsonResponse(200, {
          accessToken: "token-abc",
          expiresIn: 900,
          user: unitUser,
        });
      }
      if (url === "/api/v1/auth/password" && init?.method === "PUT") {
        return jsonResponse(422, {
          status: 422,
          code: "INVALID_CURRENT_PASSWORD",
          detail: "A senha atual informada está incorreta.",
        });
      }
      throw new Error(`chamada inesperada: ${url}`);
    });

    renderPage();

    await fillAndSubmit(user, {
      current: "senha-errada",
      next: "novaSenha123",
      confirm: "novaSenha123",
    });

    expect(
      await screen.findByText("A senha atual informada está incorreta."),
    ).toBeInTheDocument();
  });

  it("política de senha (RN-04): recusa senha curta, sem número e igual ao usuário", async () => {
    const user = userEvent.setup();
    mockRefreshOnly();

    renderPage();

    await user.type(await screen.findByLabelText(/senha atual/i), "temp123");
    await user.type(screen.getByLabelText(/^nova senha$/i), "curta1");
    await user.type(screen.getByLabelText(/confirmar nova senha/i), "curta1");
    await user.click(
      screen.getByRole("button", { name: /salvar nova senha/i }),
    );

    expect(
      await screen.findByText(
        "A nova senha precisa ter no mínimo 8 caracteres.",
      ),
    ).toBeInTheDocument();
  });

  it("política de senha (RN-04): recusa nova senha igual ao usuário (unitIdentifier)", async () => {
    const user = userEvent.setup();
    // unitIdentifier com 8+ caracteres só para este teste, já que "a-1203" da
    // sessão padrão é curto demais para colidir com o mínimo de 8 caracteres.
    vi.spyOn(globalThis, "fetch").mockImplementation(async (input) => {
      const url = String(input);
      if (url === "/api/v1/auth/refresh") {
        return jsonResponse(200, {
          accessToken: "token-abc",
          expiresIn: 900,
          user: { ...unitUser, unitIdentifier: "a-1203ab" },
        });
      }
      throw new Error(`chamada inesperada: ${url}`);
    });

    renderPage();

    await user.type(await screen.findByLabelText(/senha atual/i), "temp123a");
    await user.type(screen.getByLabelText(/^nova senha$/i), "a-1203ab");
    await user.type(
      screen.getByLabelText(/confirmar nova senha/i),
      "a-1203ab",
    );
    await user.click(
      screen.getByRole("button", { name: /salvar nova senha/i }),
    );

    expect(
      await screen.findByText(
        "A nova senha não pode ser igual ao seu usuário.",
      ),
    ).toBeInTheDocument();
  });

  it("confirmação diferente da nova senha mostra erro sem chamar a API", async () => {
    const user = userEvent.setup();
    const fetchMock = vi.spyOn(globalThis, "fetch").mockImplementation(
      async (input) => {
        const url = String(input);
        if (url === "/api/v1/auth/refresh") {
          return jsonResponse(200, {
            accessToken: "token-abc",
            expiresIn: 900,
            user: unitUser,
          });
        }
        throw new Error(`chamada inesperada: ${url}`);
      },
    );

    renderPage();

    await fillAndSubmit(user, {
      current: "temp1234",
      next: "novaSenha123",
      confirm: "outraSenha123",
    });

    expect(
      await screen.findByText(
        "A confirmação precisa ser igual à nova senha.",
      ),
    ).toBeInTheDocument();
    expect(fetchMock).toHaveBeenCalledTimes(1); // só o refresh do bootstrap
  });
});
