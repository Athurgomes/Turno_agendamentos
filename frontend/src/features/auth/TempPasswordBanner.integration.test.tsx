/**
 * Integração AppShell + troca de senha: valida o critério de aceite "banner
 * some após trocar a senha" (docs/04 F1) com a sessão real (sem mockar
 * `useSession`), diferente de `TempPasswordBanner.test.tsx`.
 */
import { afterEach, beforeEach, describe, expect, it, vi } from "vitest";
import { render, screen, waitFor } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { MemoryRouter, Route, Routes } from "react-router-dom";
import { QueryClient, QueryClientProvider } from "@tanstack/react-query";
import { ChangePasswordPage } from "./ChangePasswordPage";
import { SessionProvider } from "./session";
import { AppShell } from "../../shared/components/AppShell";
import { clearSession } from "../../shared/api/sessionStore";

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

describe("Banner de senha temporária no AppShell", () => {
  beforeEach(() => {
    clearSession();
  });

  afterEach(() => {
    vi.restoreAllMocks();
  });

  it("aparece com tempPassword=true e some depois de uma troca de senha bem-sucedida", async () => {
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

    const queryClient = new QueryClient({ defaultOptions: { queries: { retry: false } } });
    render(
      <QueryClientProvider client={queryClient}>
        <MemoryRouter initialEntries={["/alterar-senha"]}>
          <SessionProvider>
            <Routes>
              <Route element={<AppShell />}>
                <Route path="/alterar-senha" element={<ChangePasswordPage />} />
              </Route>
            </Routes>
          </SessionProvider>
        </MemoryRouter>
      </QueryClientProvider>,
    );

    expect(await screen.findByText(/senha temporária/i)).toBeInTheDocument();

    await user.type(screen.getByLabelText(/senha atual/i), "temporaria1");
    await user.type(screen.getByLabelText(/^nova senha$/i), "novaSenha123");
    await user.type(
      screen.getByLabelText(/confirmar nova senha/i),
      "novaSenha123",
    );
    await user.click(
      screen.getByRole("button", { name: /salvar nova senha/i }),
    );

    await waitFor(() => {
      expect(screen.queryByText(/senha temporária/i)).not.toBeInTheDocument();
    });
  });
});
