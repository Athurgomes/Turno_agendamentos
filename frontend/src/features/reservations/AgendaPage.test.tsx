/**
 * Filtros da agenda (RF-RES-07, D-49): garante que os controles montam os
 * parâmetros certos para `GET /reservations` e que "Novo bloqueio" aparece
 * para SYNDIC e ADMIN (D-16 — síndico cria/remove bloqueio).
 */
import { render, screen } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { MemoryRouter } from "react-router-dom";
import { describe, expect, it, vi, beforeEach } from "vitest";
import { AgendaPage } from "./AgendaPage";
import { SessionContext, type SessionContextValue } from "../auth/sessionContext";
import type { AdminReservationDto, Role } from "../../shared/api/types";

const useAdminReservationsQuery = vi.fn();
const useDeleteBlock = vi.fn();
const usePublicSettingsQuery = vi.fn();
const useAreasQuery = vi.fn();

const useAreaAvailabilityQuery = vi.fn();
const useCreateBlock = vi.fn();

vi.mock("./hooks", () => ({
  useAdminReservationsQuery: (...args: unknown[]) => useAdminReservationsQuery(...args),
  useDeleteBlock: () => useDeleteBlock(),
  usePublicSettingsQuery: () => usePublicSettingsQuery(),
  useAreaAvailabilityQuery: (...args: unknown[]) => useAreaAvailabilityQuery(...args),
  useCreateBlock: () => useCreateBlock(),
}));

vi.mock("../areas/hooks", () => ({
  useAreasQuery: (...args: unknown[]) => useAreasQuery(...args),
}));

vi.mock("../../shared/hooks/useServerClock", () => ({
  useServerClock: () => ({ data: undefined }),
  serverNow: () => new Date("2026-09-28T12:00:00Z"),
}));

function sessionFor(role: Role): SessionContextValue {
  return {
    accessToken: "token",
    user: { id: "u1", role, name: role === "ADMIN" ? "Administração" : "Síndico", unitId: null, unitIdentifier: null, tempPassword: false },
    isLoading: false,
    setSession: vi.fn(),
    logout: vi.fn(),
  } as unknown as SessionContextValue;
}

function renderPage(role: Role = "ADMIN") {
  return render(
    <MemoryRouter>
      <SessionContext.Provider value={sessionFor(role)}>
        <AgendaPage />
      </SessionContext.Provider>
    </MemoryRouter>,
  );
}

function emptyPage(): { content: AdminReservationDto[]; page: number; size: number; totalElements: number; totalPages: number } {
  return { content: [], page: 0, size: 20, totalElements: 0, totalPages: 0 };
}

describe("AgendaPage (RF-RES-07, RF-SIN-02)", () => {
  beforeEach(() => {
    useAdminReservationsQuery.mockReturnValue({ data: emptyPage(), isLoading: false });
    useDeleteBlock.mockReturnValue({ mutateAsync: vi.fn(), isPending: false });
    usePublicSettingsQuery.mockReturnValue({ data: { timezone: "America/Sao_Paulo", slotMinutes: 30 } });
    useAreasQuery.mockReturnValue({ data: [{ id: "area-1", name: "Salão de festas" }] });
    useAreaAvailabilityQuery.mockReturnValue({ data: undefined, isLoading: false });
    useCreateBlock.mockReturnValue({ mutateAsync: vi.fn(), isPending: false });
  });

  it("monta a query com os filtros escolhidos (área, status, tipo e unidade)", async () => {
    const user = userEvent.setup();
    renderPage();

    await user.selectOptions(screen.getByLabelText("Área"), "area-1");
    await user.selectOptions(screen.getByLabelText("Status"), "CONFIRMED");
    await user.selectOptions(screen.getByLabelText("Tipo"), "BLOCK");
    await user.type(screen.getByLabelText("Unidade"), "A-101");

    const lastCall = useAdminReservationsQuery.mock.calls.at(-1)?.[0];
    expect(lastCall).toMatchObject({
      areaId: "area-1",
      status: "CONFIRMED",
      kind: "BLOCK",
      unitIdentifier: "A-101",
      page: 0,
    });
    expect(lastCall.unitId).toBeUndefined();
  });

  it("monta a query com o período informado nos filtros De/Até", async () => {
    const user = userEvent.setup();
    renderPage();

    const fromInput = screen.getByLabelText("De");
    const toInput = screen.getByLabelText("Até");
    await user.type(fromInput, "2026-10-01");
    await user.type(toInput, "2026-10-31");

    const lastCall = useAdminReservationsQuery.mock.calls.at(-1)?.[0];
    expect(lastCall).toMatchObject({ from: "2026-10-01", to: "2026-10-31" });
  });

  it.each<Role>(["SYNDIC", "ADMIN"])("mostra 'Novo bloqueio' para %s (D-16)", (role) => {
    renderPage(role);
    expect(screen.getByRole("button", { name: "+ Novo bloqueio" })).toBeInTheDocument();
  });

  it("mostra estado vazio quando não há reservas nem bloqueios", () => {
    renderPage();
    expect(
      screen.getByText("Nenhuma reserva ou bloqueio encontrado com estes filtros."),
    ).toBeInTheDocument();
  });
});
