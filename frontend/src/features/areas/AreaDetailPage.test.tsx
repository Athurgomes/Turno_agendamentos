import { render, screen } from "@testing-library/react";
import { MemoryRouter, Route, Routes } from "react-router-dom";
import { describe, expect, it, vi } from "vitest";
import { AreaDetailPage } from "./AreaDetailPage";
import type { AreaDetail } from "../../shared/api/types";

const useAreaQuery = vi.fn();
const mutateAsync = vi.fn();

vi.mock("./hooks", () => ({
  useAreaQuery: (...args: unknown[]) => useAreaQuery(...args),
  useUpdateAreaStatus: () => ({ mutateAsync, isPending: false }),
  useDeleteArea: () => ({ mutateAsync, isPending: false }),
}));

const sessionMock = vi.hoisted(() => ({
  user: { id: "1", role: "UNIT", name: "Fernanda Reis", unitId: "u1", unitIdentifier: "a-1203", tempPassword: false },
}));
vi.mock("../auth/useSession", () => ({ useSession: () => sessionMock }));

function area(overrides: Partial<AreaDetail> = {}): AreaDetail {
  return {
    id: "area-1",
    name: "Churrasqueira 1",
    category: "BARBECUE",
    status: "ACTIVE",
    description: "Área com churrasqueira e mesas.",
    rules: "Uso até as 22h.",
    conductGuidelines: "Deixe limpo ao sair.",
    capacity: 30,
    requiresPayment: false,
    price: null,
    paymentWhatsapp: null,
    openingHours: [{ dayOfWeek: 1, openTime: "08:00", closeTime: "22:00" }],
    photos: [],
    version: 1,
    ...overrides,
  };
}

function renderPage() {
  return render(
    <MemoryRouter initialEntries={["/areas/area-1"]}>
      <Routes>
        <Route path="/areas/:id" element={<AreaDetailPage />} />
      </Routes>
    </MemoryRouter>,
  );
}

describe("AreaDetailPage (RF-ARE-04)", () => {
  it("mostra Reservar para UNIT quando a área está ACTIVE (RF-ARE-04, RN-14)", () => {
    sessionMock.user.role = "UNIT";
    useAreaQuery.mockReturnValue({ data: area(), isLoading: false });
    renderPage();
    expect(screen.getByRole("link", { name: "Reservar" })).toBeInTheDocument();
  });

  it("esconde Reservar para UNIT quando a área não está ACTIVE (RF-ARE-04, RN-14)", () => {
    sessionMock.user.role = "UNIT";
    useAreaQuery.mockReturnValue({ data: area({ status: "INTERDICTED" }), isLoading: false });
    renderPage();
    expect(screen.queryByRole("link", { name: "Reservar" })).not.toBeInTheDocument();
  });

  it("mostra Alterar status e Excluir só para ADMIN (RF-ARE-04, RN-15, RN-16)", () => {
    sessionMock.user.role = "SYNDIC";
    useAreaQuery.mockReturnValue({ data: area(), isLoading: false });
    const { rerender } = render(
      <MemoryRouter initialEntries={["/areas/area-1"]}>
        <Routes>
          <Route path="/areas/:id" element={<AreaDetailPage />} />
        </Routes>
      </MemoryRouter>,
    );
    expect(screen.queryByRole("button", { name: "Alterar status" })).not.toBeInTheDocument();
    expect(screen.queryByRole("button", { name: "Excluir" })).not.toBeInTheDocument();
    expect(screen.getByRole("link", { name: "Editar" })).toBeInTheDocument();

    sessionMock.user.role = "ADMIN";
    rerender(
      <MemoryRouter initialEntries={["/areas/area-1"]}>
        <Routes>
          <Route path="/areas/:id" element={<AreaDetailPage />} />
        </Routes>
      </MemoryRouter>,
    );
    expect(screen.getByRole("button", { name: "Alterar status" })).toBeInTheDocument();
    expect(screen.getByRole("button", { name: "Excluir" })).toBeInTheDocument();
  });
});
