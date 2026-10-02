/**
 * Página inicial do síndico/administração (RF-SIN-01, F7-1): dados, vazio e erro.
 */
import { render, screen } from "@testing-library/react";
import { MemoryRouter } from "react-router-dom";
import { describe, expect, it, vi, beforeEach } from "vitest";
import { SyndicHomePage } from "./SyndicHomePage";
import type { DashboardHomeDto } from "../../shared/api/types";

const useDashboardHomeQuery = vi.fn();
const usePublicSettingsQuery = vi.fn();

vi.mock("./hooks", () => ({
  useDashboardHomeQuery: () => useDashboardHomeQuery(),
}));

vi.mock("../reservations/hooks", () => ({
  usePublicSettingsQuery: () => usePublicSettingsQuery(),
}));

function emptyHome(): DashboardHomeDto {
  return {
    today: [],
    next7Days: [],
    openReports: { count: 0, items: [] },
    overdueInspections: [],
  };
}

function renderPage() {
  return render(
    <MemoryRouter>
      <SyndicHomePage />
    </MemoryRouter>,
  );
}

describe("SyndicHomePage (RF-SIN-01)", () => {
  beforeEach(() => {
    usePublicSettingsQuery.mockReturnValue({ data: { timezone: "America/Sao_Paulo" } });
  });

  it("mostra o estado de carregamento", () => {
    useDashboardHomeQuery.mockReturnValue({
      data: undefined,
      isLoading: true,
      isError: false,
      isFetching: true,
      refetch: vi.fn(),
    });
    renderPage();
    expect(screen.getByText("Carregando página inicial…")).toBeInTheDocument();
  });

  it("mostra o estado de erro com ação de tentar novamente", () => {
    const refetch = vi.fn();
    useDashboardHomeQuery.mockReturnValue({
      data: undefined,
      isLoading: false,
      isError: true,
      isFetching: false,
      refetch,
    });
    renderPage();
    expect(screen.getByText(/Não foi possível carregar a página inicial/)).toBeInTheDocument();
    screen.getByRole("button", { name: "Tentar novamente" }).click();
    expect(refetch).toHaveBeenCalled();
  });

  it("mostra os estados vazios de cada bloco", () => {
    useDashboardHomeQuery.mockReturnValue({
      data: emptyHome(),
      isLoading: false,
      isError: false,
      isFetching: false,
      refetch: vi.fn(),
    });
    renderPage();
    expect(screen.getByText("Nenhuma reserva ou bloqueio hoje.")).toBeInTheDocument();
    expect(screen.getByText("Nenhuma reserva ou bloqueio nos próximos 7 dias.")).toBeInTheDocument();
    expect(screen.getByText("Nenhum report aberto.")).toBeInTheDocument();
    expect(
      screen.getByText("Nenhuma vistoria atrasada — todas as áreas foram vistoriadas nos últimos 30 dias."),
    ).toBeInTheDocument();
  });

  it("lista reserva de hoje, bloqueio, report aberto e vistoria atrasada, cada um com link", () => {
    const home: DashboardHomeDto = {
      today: [
        {
          id: "r1",
          code: "RES-1",
          kind: "BOOKING",
          areaId: "area-1",
          areaName: "Salão de festas",
          unitIdentifier: "A-101",
          residentName: "Fernanda Reis",
          date: "2026-09-29",
          startTime: "18:00",
          endTime: "22:00",
          status: "CONFIRMED",
        },
        {
          id: "b1",
          code: "BLK-1",
          kind: "BLOCK",
          areaId: "area-2",
          areaName: "Quadra",
          unitIdentifier: "",
          residentName: "",
          date: "2026-09-29",
          startTime: "08:00",
          endTime: "12:00",
          status: "CONFIRMED",
        },
      ],
      next7Days: [],
      openReports: {
        count: 1,
        items: [
          {
            id: "rep-1",
            code: "REP-1",
            areaName: "Churrasqueira",
            unitIdentifier: "B-202",
            category: "DAMAGE",
            status: "OPEN",
            createdAt: "2026-09-20T10:00:00Z",
          },
        ],
      },
      overdueInspections: [
        { areaId: "area-3", areaName: "Piscina", status: "ACTIVE", lastInspectionAt: null, daysSinceInspection: null },
        {
          areaId: "area-4",
          areaName: "Academia",
          status: "ACTIVE",
          lastInspectionAt: "2026-08-01",
          daysSinceInspection: 59,
        },
      ],
    };
    useDashboardHomeQuery.mockReturnValue({
      data: home,
      isLoading: false,
      isError: false,
      isFetching: false,
      refetch: vi.fn(),
    });
    renderPage();

    expect(screen.getByText("Salão de festas")).toBeInTheDocument();
    expect(screen.getByText("Bloqueio")).toBeInTheDocument();
    expect(screen.getByText("Reports abertos (1)")).toBeInTheDocument();
    expect(screen.getByText("Nunca vistoriada")).toBeInTheDocument();
    expect(screen.getByText("59 dias sem vistoria")).toBeInTheDocument();

    expect(screen.getAllByRole("link", { name: /Salão de festas/ })[0]).toHaveAttribute("href", "/agenda");
    expect(screen.getAllByRole("link", { name: /Churrasqueira/ })[0]).toHaveAttribute("href", "/reports");
    expect(screen.getByRole("link", { name: /Piscina/ })).toHaveAttribute("href", "/areas/area-3/vistorias");
  });
});
