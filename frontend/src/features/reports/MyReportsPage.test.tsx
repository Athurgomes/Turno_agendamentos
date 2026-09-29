/**
 * "Meus reports" (RF-REP-05): status, motivo e comentários públicos. A
 * conta UNIT nunca vê comentário marcado "Só equipe" — o backend já filtra
 * antes de chegar aqui (D-50), e esta tela nem sequer sabe renderizar a
 * marca interna.
 */
import { render, screen } from "@testing-library/react";
import { describe, expect, it, vi } from "vitest";
import { MyReportsPage } from "./MyReportsPage";
import type { ReportDto } from "../../shared/api/types";

const useMyReportsQuery = vi.fn();

vi.mock("./hooks", () => ({
  useMyReportsQuery: () => useMyReportsQuery(),
}));

vi.mock("../../shared/hooks/useServerClock", () => ({
  useServerClock: () => ({ data: { timezone: "America/Sao_Paulo", simulated: false } }),
}));

function report(overrides: Partial<ReportDto> = {}): ReportDto {
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
    status: "IN_REVIEW",
    statusReason: null,
    createdAt: "2026-09-21T10:00:00Z",
    resolvedAt: null,
    photos: [],
    comments: [],
    ...overrides,
  };
}

describe("MyReportsPage (RF-REP-05)", () => {
  it("mostra o status e o motivo do status (RN-36)", () => {
    useMyReportsQuery.mockReturnValue({
      data: [report({ status: "DISMISSED", statusReason: "Fora da garantia do condomínio." })],
      isLoading: false,
    });
    render(<MyReportsPage />);

    expect(screen.getByText("Descartado")).toBeInTheDocument();
    expect(screen.getByText("Fora da garantia do condomínio.")).toBeInTheDocument();
  });

  it("mostra os comentários públicos sem nenhuma marca de 'Só equipe'", () => {
    useMyReportsQuery.mockReturnValue({
      data: [
        report({
          comments: [
            {
              id: "c1",
              text: "Já encaminhamos para a manutenção.",
              authorName: "Síndico",
              createdAt: "2026-09-22T10:00:00Z",
              visibleToResident: true,
            },
          ],
        }),
      ],
      isLoading: false,
    });
    render(<MyReportsPage />);

    expect(screen.getByText(/Já encaminhamos para a manutenção\./)).toBeInTheDocument();
    expect(screen.queryByText("Só equipe")).not.toBeInTheDocument();
  });

  it("mostra estado vazio quando não há reports", () => {
    useMyReportsQuery.mockReturnValue({ data: [], isLoading: false });
    render(<MyReportsPage />);

    expect(screen.getByText(/Nenhum report ainda/)).toBeInTheDocument();
  });

  it("mostra a data de abertura e, quando houver, de resolução (RN-34/35) no fuso do condomínio", () => {
    useMyReportsQuery.mockReturnValue({
      data: [
        report({
          createdAt: "2026-09-21T10:00:00Z",
          resolvedAt: "2026-09-23T18:00:00Z",
        }),
      ],
      isLoading: false,
    });
    render(<MyReportsPage />);

    expect(screen.getByText(/Aberto em 21\/09\/2026 07:00/)).toBeInTheDocument();
    expect(screen.getByText(/Resolvido em 23\/09\/2026 15:00/)).toBeInTheDocument();
  });

  it("não mostra data de resolução quando o report ainda não foi resolvido", () => {
    useMyReportsQuery.mockReturnValue({
      data: [report({ resolvedAt: null })],
      isLoading: false,
    });
    render(<MyReportsPage />);

    expect(screen.queryByText(/Resolvido em/)).not.toBeInTheDocument();
  });
});
