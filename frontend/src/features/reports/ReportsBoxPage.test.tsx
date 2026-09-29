/**
 * Caixa de reports de SÍNDICO/ADMIN (RF-REP-02/03): filtros e abertura do
 * detalhe.
 */
import { render, screen } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { describe, expect, it, vi, beforeEach } from "vitest";
import { ReportsBoxPage } from "./ReportsBoxPage";
import type { AdminReportDto } from "../../shared/api/types";

const useAreasQuery = vi.fn();
const useAdminReportsQuery = vi.fn();

vi.mock("../areas/hooks", () => ({
  useAreasQuery: () => useAreasQuery(),
}));
vi.mock("./hooks", () => ({
  useAdminReportsQuery: (...args: unknown[]) => useAdminReportsQuery(...args),
}));
vi.mock("./ReportDetailPanel", () => ({
  ReportDetailPanel: ({ id }: { id: string }) => <p>Detalhe do report {id}</p>,
}));

function report(overrides: Partial<AdminReportDto> = {}): AdminReportDto {
  return {
    id: "report-1",
    code: "OCR-2026-000001",
    reservationId: "res-1",
    reservationCode: "RES-2026-000001",
    areaId: "area-1",
    areaName: "Churrasqueira",
    reservationDate: "2026-09-20",
    category: "DAMAGE",
    description: "A grelha está quebrada.",
    residentName: "Maria Souza",
    status: "OPEN",
    statusReason: null,
    createdAt: "2026-09-21T10:00:00Z",
    resolvedAt: null,
    photos: [],
    comments: [],
    unitId: "unit-1",
    unitIdentifier: "A-101",
    residentPhone: "5562999998888",
    whatsappContactUrl: "https://wa.me/5562999998888",
    maintenanceCost: null,
    ...overrides,
  };
}

describe("ReportsBoxPage (RF-REP-02/03)", () => {
  beforeEach(() => {
    useAreasQuery.mockReturnValue({ data: [] });
  });

  it("lista os reports e abre o detalhe ao clicar", async () => {
    useAdminReportsQuery.mockReturnValue({
      data: { content: [report()], page: 0, size: 20, totalElements: 1, totalPages: 1 },
      isLoading: false,
    });
    const user = userEvent.setup();
    render(<ReportsBoxPage />);

    expect(screen.getByText("Churrasqueira")).toBeInTheDocument();
    await user.click(screen.getByText("Churrasqueira"));

    expect(screen.getByText("Detalhe do report report-1")).toBeInTheDocument();
  });

  it("mostra estado vazio quando não há reports com os filtros", () => {
    useAdminReportsQuery.mockReturnValue({
      data: { content: [], page: 0, size: 20, totalElements: 0, totalPages: 0 },
      isLoading: false,
    });
    render(<ReportsBoxPage />);

    expect(screen.getByText("Nenhum report encontrado com estes filtros.")).toBeInTheDocument();
  });
});
