/**
 * Dashboard de ADMIN/SÍNDICO (RF-DAS-01/02/03, F8-3): cards formatados,
 * troca de período refazendo as consultas e clique em exportar.
 */
import { render, screen, within } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { describe, expect, it, vi, beforeEach } from "vitest";
import { DashboardPage } from "./DashboardPage";
import type { DashboardSummaryDto } from "../../shared/api/types";

const useServerClock = vi.fn();
const usePublicSettingsQuery = vi.fn();
const useSummaryQuery = vi.fn();
const useReservationsByMonthQuery = vi.fn();
const useAreaMetricsQuery = vi.fn();
const useDemandHeatmapQuery = vi.fn();
const useTopUnitsQuery = vi.fn();
const mutateAsync = vi.fn();

vi.mock("../../shared/hooks/useServerClock", () => ({
  useServerClock: () => useServerClock(),
  serverNow: () => new Date("2026-09-29T12:00:00Z"),
}));

vi.mock("../reservations/hooks", () => ({
  usePublicSettingsQuery: () => usePublicSettingsQuery(),
}));

vi.mock("./hooks", () => ({
  useSummaryQuery: (...args: unknown[]) => useSummaryQuery(...args),
  useReservationsByMonthQuery: (...args: unknown[]) => useReservationsByMonthQuery(...args),
  useAreaMetricsQuery: (...args: unknown[]) => useAreaMetricsQuery(...args),
  useDemandHeatmapQuery: (...args: unknown[]) => useDemandHeatmapQuery(...args),
  useTopUnitsQuery: (...args: unknown[]) => useTopUnitsQuery(...args),
  useExportMutation: () => ({ mutateAsync }),
}));

function summary(): DashboardSummaryDto {
  return {
    from: "2026-09-01",
    to: "2026-09-30",
    activeUnits: 42,
    activeResidents: 108,
    reservations: { total: 1234, pendingPayment: 5, confirmed: 1200, cancelled: 29 },
    cancellations: { byResident: 20, byAdmin: 9, bySystem: 0, residentRate: 0.0162, adminRate: 0.0073 },
    amounts: { confirmed: 15750.5, pending: 320 },
    reports: { opened: 8, resolved: 6, open: 3, averageResolutionHours: 26.4, byCategory: [{ category: "DAMAGE", count: 5 }] },
    maintenanceCost: 980.25,
  };
}

function emptyList() {
  return { data: [], isLoading: false, isError: false };
}

function mockAllQueries() {
  usePublicSettingsQuery.mockReturnValue({ data: { timezone: "America/Sao_Paulo" } });
  useServerClock.mockReturnValue({ data: { now: "2026-09-29T12:00:00Z", timezone: "America/Sao_Paulo", simulated: false } });
  useSummaryQuery.mockReturnValue({ data: summary(), isLoading: false, isError: false });
  useReservationsByMonthQuery.mockReturnValue(emptyList());
  useAreaMetricsQuery.mockReturnValue(emptyList());
  useDemandHeatmapQuery.mockReturnValue(emptyList());
  useTopUnitsQuery.mockReturnValue(emptyList());
}

describe("DashboardPage (RF-DAS-01/02/03, RF-SIN-06)", () => {
  beforeEach(() => {
    vi.clearAllMocks();
    mockAllQueries();
  });

  it("renderiza os cards com números formatados em pt-BR", () => {
    render(<DashboardPage />);
    expect(screen.getByText("1.234")).toBeInTheDocument(); // total de reservas
    expect(screen.getByText("R$ 15.750,50")).toBeInTheDocument(); // valor confirmado
    expect(screen.getByText("1,6%")).toBeInTheDocument(); // taxa de cancelamento por morador
    expect(screen.getByText("26,4 h")).toBeInTheDocument(); // tempo médio de resolução
  });

  it("mostra estado de erro do resumo com role=alert", () => {
    useSummaryQuery.mockReturnValue({ data: undefined, isLoading: false, isError: true });
    render(<DashboardPage />);
    const alerts = screen.getAllByRole("alert");
    expect(alerts.length).toBeGreaterThan(0);
    expect(alerts[0]).toHaveTextContent("Não foi possível carregar estes dados.");
  });

  it("troca de período (preset) refaz as consultas com from/to diferentes", async () => {
    const user = userEvent.setup();
    render(<DashboardPage />);

    // primeira chamada, já com o mês corrente calculado a partir do relógio do servidor.
    const firstCall = useSummaryQuery.mock.calls.at(-1)?.[0];
    expect(firstCall).toEqual({ from: "2026-09-01", to: "2026-09-30" });

    await user.click(screen.getByRole("button", { name: "Mês anterior" }));

    const lastCall = useSummaryQuery.mock.calls.at(-1)?.[0];
    expect(lastCall).toEqual({ from: "2026-08-01", to: "2026-08-31" });
    expect(lastCall).not.toEqual(firstCall);
  });

  it("atalho 'Últimos 6 meses' refaz as consultas com from = 1º dia de 5 meses atrás e to = fim do mês atual", async () => {
    const user = userEvent.setup();
    render(<DashboardPage />);

    await user.click(screen.getByRole("button", { name: "Últimos 6 meses" }));

    const lastCall = useSummaryQuery.mock.calls.at(-1)?.[0];
    expect(lastCall).toEqual({ from: "2026-04-01", to: "2026-09-30" });
  });

  it("tabela mensal fica dentro de <details> fechado por padrão", () => {
    useReservationsByMonthQuery.mockReturnValue({
      data: [{ month: "2026-09", total: 10, confirmed: 8, pendingPayment: 1, cancelled: 1 }],
      isLoading: false,
      isError: false,
    });
    render(<DashboardPage />);

    const summaryToggle = screen.getByText("Ver dados do gráfico em tabela");
    const details = summaryToggle.closest("details") as HTMLDetailsElement;
    expect(details).toBeInTheDocument();
    expect(details.open).toBe(false);
  });

  it("mapa de calor: célula com valor > 0 e < máximo tem intensidade própria, e a de 0 não", () => {
    useDemandHeatmapQuery.mockReturnValue({
      data: [
        { dayOfWeek: 1, hour: 10, count: 1 },
        { dayOfWeek: 1, hour: 11, count: 4 },
      ],
      isLoading: false,
      isError: false,
    });
    render(<DashboardPage />);

    const midCell = screen.getByTitle("Seg, 10h: 1 reserva(s)");
    const maxCell = screen.getByTitle("Seg, 11h: 4 reserva(s)");
    const emptyCell = screen.getByTitle("Seg, 12h: 0 reserva(s)");

    expect(midCell.className).toMatch(/bg-primary-/);
    expect(maxCell.className).toMatch(/bg-primary-/);
    expect(midCell.className).not.toBe(maxCell.className);
    expect(emptyCell.className).not.toMatch(/bg-primary-/);
  });

  it("clique em exportar chama a mutação com type/format/period (GET /exports/{type}?from&to&format)", async () => {
    const user = userEvent.setup();
    mutateAsync.mockResolvedValue(undefined);
    render(<DashboardPage />);

    const reservationsRow = screen.getByText("Reservas").closest("li") as HTMLElement;
    await user.click(within(reservationsRow).getByRole("button", { name: "Exportar CSV" }));

    expect(mutateAsync).toHaveBeenCalledWith({
      type: "reservations",
      format: "csv",
      period: { from: "2026-09-01", to: "2026-09-30" },
    });
  });
});
