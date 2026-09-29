import { render, screen, within } from "@testing-library/react";
import { MemoryRouter } from "react-router-dom";
import { describe, expect, it, vi } from "vitest";
import { AreasCatalogPage } from "./AreasCatalogPage";
import type { AreaSummary } from "../../shared/api/types";

const useAreasQuery = vi.fn();

vi.mock("./hooks", () => ({
  useAreasQuery: (...args: unknown[]) => useAreasQuery(...args),
}));

const sessionMock = vi.hoisted(() => ({
  user: { id: "1", role: "UNIT", name: "Fernanda Reis", unitId: "u1", unitIdentifier: "a-1203", tempPassword: false },
}));
vi.mock("../auth/useSession", () => ({ useSession: () => sessionMock }));

function area(overrides: Partial<AreaSummary> = {}): AreaSummary {
  return {
    id: "area-1",
    name: "Churrasqueira 1",
    category: "BARBECUE",
    status: "ACTIVE",
    capacity: 30,
    requiresPayment: false,
    price: null,
    coverPhotoUrl: null,
    ...overrides,
  };
}

function renderPage() {
  return render(
    <MemoryRouter>
      <AreasCatalogPage />
    </MemoryRouter>,
  );
}

describe("AreasCatalogPage", () => {
  it("RF-ARE-04: mostra o botão Reservar para uma área ACTIVE", () => {
    useAreasQuery.mockReturnValue({ data: [area()], isLoading: false });
    renderPage();
    expect(screen.getByRole("link", { name: /reservar/i })).toBeInTheDocument();
  });

  it("RF-ARE-04/RN-14: esconde o botão Reservar quando a área não está ACTIVE", () => {
    sessionMock.user.role = "UNIT";
    useAreasQuery.mockReturnValue({
      data: [area({ status: "MAINTENANCE" })],
      isLoading: false,
    });
    renderPage();
    expect(screen.queryByRole("link", { name: /reservar/i })).not.toBeInTheDocument();
    expect(within(screen.getByRole("list")).getByText("Em manutenção")).toBeInTheDocument();
  });

  it("RF-ARE-04: mostra o valor formatado quando a área é paga e 'Gratuita' quando não é", () => {
    useAreasQuery.mockReturnValue({
      data: [area({ requiresPayment: true, price: 150 }), area({ id: "area-2", name: "Salão" })],
      isLoading: false,
    });
    renderPage();
    expect(screen.getByText("R$ 150,00")).toBeInTheDocument();
    expect(screen.getByText("Gratuita")).toBeInTheDocument();
  });

  it("RF-ARE-01: mostra o botão 'Nova área' só para ADMIN", () => {
    sessionMock.user.role = "ADMIN";
    useAreasQuery.mockReturnValue({ data: [], isLoading: false });
    renderPage();
    expect(screen.getByRole("link", { name: /nova área/i })).toBeInTheDocument();
    sessionMock.user.role = "UNIT";
  });
});
