import { describe, expect, it, vi } from "vitest";
import { render, screen } from "@testing-library/react";
import { MemoryRouter, Route, Routes } from "react-router-dom";
import { RequireRole } from "./RequireRole";

const sessionMock = vi.hoisted(() => ({ user: { role: "UNIT" } }));

vi.mock("../features/auth/useSession", () => ({
  useSession: () => sessionMock,
}));

function renderAt(initialPath: string) {
  return render(
    <MemoryRouter initialEntries={[initialPath]}>
      <Routes>
        <Route element={<RequireRole roles={["ADMIN"]} />}>
          <Route path="/admin/unidades" element={<div>Unidades (ADMIN)</div>} />
        </Route>
        <Route element={<RequireRole roles={["UNIT"]} />}>
          <Route path="/areas" element={<div>Áreas (UNIT)</div>} />
        </Route>
      </Routes>
    </MemoryRouter>,
  );
}

describe("RequireRole", () => {
  it("UNIT não entra em /admin/unidades: é redirecionado para a rota inicial do próprio perfil", () => {
    renderAt("/admin/unidades");

    expect(screen.queryByText("Unidades (ADMIN)")).not.toBeInTheDocument();
    expect(screen.getByText("Áreas (UNIT)")).toBeInTheDocument();
  });

  it("UNIT acessa normalmente a própria rota (/areas)", () => {
    renderAt("/areas");

    expect(screen.getByText("Áreas (UNIT)")).toBeInTheDocument();
  });
});
