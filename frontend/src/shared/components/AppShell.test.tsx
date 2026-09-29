/**
 * Visibilidade condicional do item "Confirmações" no menu (RF-PAG-01):
 * aparece só para ADMIN quando há área paga no catálogo ou pendência, com o
 * número de pendentes no rótulo.
 */
import { render, screen } from "@testing-library/react";
import { MemoryRouter, Route, Routes } from "react-router-dom";
import { beforeEach, describe, expect, it, vi } from "vitest";
import { AppShell } from "./AppShell";
import { SessionContext, type SessionContextValue } from "../../features/auth/sessionContext";
import type { Role } from "../api/types";

const useConfirmationsNavInfo = vi.fn();
const useReportsNavInfo = vi.fn();

vi.mock("../../features/admin/payments/hooks", () => ({
  useConfirmationsNavInfo: () => useConfirmationsNavInfo(),
}));

vi.mock("../../features/reports/hooks", () => ({
  useReportsNavInfo: () => useReportsNavInfo(),
}));

function sessionFor(role: Role): SessionContextValue {
  return {
    accessToken: "token",
    user: { id: "1", role, name: "Administração", unitId: null, unitIdentifier: null, tempPassword: false },
    isLoading: false,
    setSession: vi.fn(),
    logout: vi.fn(),
  } as unknown as SessionContextValue;
}

function renderShell(role: Role) {
  return render(
    <MemoryRouter>
      <SessionContext.Provider value={sessionFor(role)}>
        <Routes>
          <Route element={<AppShell />}>
            <Route path="/" element={<p>conteúdo</p>} />
          </Route>
        </Routes>
      </SessionContext.Provider>
    </MemoryRouter>,
  );
}

describe("AppShell — item de menu Confirmações (RF-PAG-01)", () => {
  beforeEach(() => {
    useReportsNavInfo.mockReturnValue({ count: 0 });
  });

  it("não aparece para ADMIN quando não há área paga nem pendência", () => {
    useConfirmationsNavInfo.mockReturnValue({ visible: false, count: 0 });
    renderShell("ADMIN");

    expect(screen.queryByText(/Confirmações/)).not.toBeInTheDocument();
  });

  it("aparece para ADMIN com o número de pendentes no rótulo", () => {
    useConfirmationsNavInfo.mockReturnValue({ visible: true, count: 3 });
    renderShell("ADMIN");

    expect(screen.getAllByText("Confirmações (3)").length).toBeGreaterThan(0);
  });
});

describe("AppShell — contador de reports abertos no menu (RF-REP-02)", () => {
  beforeEach(() => {
    useConfirmationsNavInfo.mockReturnValue({ visible: false, count: 0 });
  });

  it("aparece com o número de reports abertos para SYNDIC", () => {
    useReportsNavInfo.mockReturnValue({ count: 4 });
    renderShell("SYNDIC");

    expect(screen.getAllByText("Reports (4)").length).toBeGreaterThan(0);
  });

  it("não mostra contador quando não há reports abertos", () => {
    useReportsNavInfo.mockReturnValue({ count: 0 });
    renderShell("ADMIN");

    expect(screen.getAllByText("Reports").length).toBeGreaterThan(0);
    expect(screen.queryByText(/Reports \(/)).not.toBeInTheDocument();
  });

  it("conta UNIT nem sequer tem o item 'Reports' no menu", () => {
    useReportsNavInfo.mockReturnValue({ count: 9 });
    renderShell("UNIT");

    expect(screen.queryByText(/Reports/)).not.toBeInTheDocument();
  });
});
