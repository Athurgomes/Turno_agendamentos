/**
 * Detalhe do report (RF-REP-03/04, RN-36, RN-37): só oferece status seguintes
 * válidos, exige justificativa para descartar, aceita custo só ao resolver e
 * marca comentários internos.
 */
import { render, screen } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { describe, expect, it, vi, beforeEach } from "vitest";
import { ReportDetailPanel } from "./ReportDetailPanel";
import type { AdminReportDto } from "../../shared/api/types";

const useAdminReportQuery = vi.fn();
const updateStatusMutateAsync = vi.fn();
const addCommentMutateAsync = vi.fn();
const uploadPhotosMutateAsync = vi.fn();
const refetch = vi.fn();

vi.mock("./hooks", () => ({
  useAdminReportQuery: (...args: unknown[]) => useAdminReportQuery(...args),
  useUpdateReportStatus: () => ({ mutateAsync: updateStatusMutateAsync, isPending: false }),
  useAddReportComment: () => ({ mutateAsync: addCommentMutateAsync, isPending: false }),
  useUploadReportPhotos: () => ({ mutateAsync: uploadPhotosMutateAsync, isPending: false }),
}));

vi.mock("../../shared/hooks/useServerClock", () => ({
  useServerClock: () => ({ data: { timezone: "America/Sao_Paulo", simulated: false } }),
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

function renderPanel(overrides: Partial<AdminReportDto> = {}) {
  useAdminReportQuery.mockReturnValue({ data: report(overrides), isLoading: false, refetch });
  return render(<ReportDetailPanel id="report-1" onClose={vi.fn()} />);
}

describe("ReportDetailPanel (RF-REP-03/04, RN-36, RN-37)", () => {
  beforeEach(() => {
    updateStatusMutateAsync.mockReset();
    addCommentMutateAsync.mockReset();
    uploadPhotosMutateAsync.mockReset();
    refetch.mockReset();
  });

  it("de OPEN oferece Em análise, Em manutenção, Resolvido e Descartado", () => {
    renderPanel({ status: "OPEN" });

    expect(screen.getByRole("button", { name: "Em análise" })).toBeInTheDocument();
    expect(screen.getByRole("button", { name: "Em manutenção" })).toBeInTheDocument();
    expect(screen.getByRole("button", { name: "Resolvido" })).toBeInTheDocument();
    expect(screen.getByRole("button", { name: "Descartado" })).toBeInTheDocument();
  });

  it("status final (RESOLVED) não mostra nenhuma opção de transição", () => {
    renderPanel({ status: "RESOLVED", maintenanceCost: 120 });

    expect(screen.queryByRole("button", { name: "Descartado" })).not.toBeInTheDocument();
    expect(screen.queryByRole("button", { name: "Em análise" })).not.toBeInTheDocument();
  });

  it("descartar sem justificativa (< 10 caracteres) não confirma (RN-36)", async () => {
    const user = userEvent.setup();
    renderPanel({ status: "OPEN" });

    await user.click(screen.getByRole("button", { name: "Descartado" }));
    await user.type(screen.getByLabelText(/Justificativa/), "curto");

    expect(screen.getByRole("button", { name: "Confirmar" })).toBeDisabled();
    expect(updateStatusMutateAsync).not.toHaveBeenCalled();
  });

  it("descartar com justificativa válida chama a rota com DISMISSED", async () => {
    updateStatusMutateAsync.mockResolvedValue(report({ status: "DISMISSED" }));
    const user = userEvent.setup();
    renderPanel({ status: "OPEN" });

    await user.click(screen.getByRole("button", { name: "Descartado" }));
    await user.type(
      screen.getByLabelText(/Justificativa/),
      "Item já substituído pela manutenção preventiva.",
    );
    await user.click(screen.getByRole("button", { name: "Confirmar" }));

    expect(updateStatusMutateAsync).toHaveBeenCalledWith({
      id: "report-1",
      payload: {
        status: "DISMISSED",
        justification: "Item já substituído pela manutenção preventiva.",
        maintenanceCost: undefined,
      },
    });
  });

  it("resolver sem custo é permitido; o campo de custo só aparece para RESOLVED", async () => {
    updateStatusMutateAsync.mockResolvedValue(report({ status: "RESOLVED" }));
    const user = userEvent.setup();
    renderPanel({ status: "IN_MAINTENANCE" });

    await user.click(screen.getByRole("button", { name: "Resolvido" }));
    expect(screen.getByLabelText(/Custo da manutenção/)).toBeInTheDocument();

    await user.type(screen.getByLabelText(/Custo da manutenção/), "150");
    await user.click(screen.getByRole("button", { name: "Confirmar" }));

    expect(updateStatusMutateAsync).toHaveBeenCalledWith({
      id: "report-1",
      payload: { status: "RESOLVED", justification: undefined, maintenanceCost: 150 },
    });
  });

  it("mudar para um status que não é RESOLVED não mostra campo de custo", async () => {
    const user = userEvent.setup();
    renderPanel({ status: "OPEN" });

    await user.click(screen.getByRole("button", { name: "Em análise" }));

    expect(screen.queryByLabelText(/Custo da manutenção/)).not.toBeInTheDocument();
  });

  it("marca comentário interno como 'Só equipe'", () => {
    renderPanel({
      comments: [
        {
          id: "c1",
          text: "Aviso interno da equipe de manutenção.",
          authorName: "Síndico",
          createdAt: "2026-09-22T10:00:00Z",
          visibleToResident: false,
        },
        {
          id: "c2",
          text: "Já estamos cuidando disso.",
          authorName: "Síndico",
          createdAt: "2026-09-22T11:00:00Z",
          visibleToResident: true,
        },
      ],
    });

    expect(screen.getByText("Só equipe")).toBeInTheDocument();
    expect(screen.getAllByText("Só equipe")).toHaveLength(1);
  });

  it("o link do WhatsApp abre em nova aba com rel seguro", () => {
    renderPanel();
    const link = screen.getByRole("link", { name: "Falar no WhatsApp" });
    expect(link).toHaveAttribute("href", "https://wa.me/5562999998888");
    expect(link).toHaveAttribute("target", "_blank");
    expect(link).toHaveAttribute("rel", "noopener noreferrer");
  });

  it("mostra a data de abertura e, quando houver, de resolução no fuso do condomínio", () => {
    renderPanel({ createdAt: "2026-09-21T10:00:00Z", resolvedAt: "2026-09-23T18:00:00Z" });

    expect(screen.getByText("21/09/2026 07:00")).toBeInTheDocument();
    expect(screen.getByText("23/09/2026 15:00")).toBeInTheDocument();
  });

  it("não mostra 'Resolvido em' quando o report ainda não foi resolvido", () => {
    renderPanel({ resolvedAt: null });

    expect(screen.queryByText(/Resolvido em/)).not.toBeInTheDocument();
  });

  it("em 409 INVALID_STATUS_TRANSITION mostra o detail e recarrega", async () => {
    const { ApiError } = await import("../../shared/api/client");
    updateStatusMutateAsync.mockRejectedValue(
      new ApiError({
        status: 409,
        code: "INVALID_STATUS_TRANSITION",
        detail: "Este report já foi finalizado.",
      }),
    );
    const user = userEvent.setup();
    renderPanel({ status: "OPEN" });

    await user.click(screen.getByRole("button", { name: "Em análise" }));
    await user.click(screen.getByRole("button", { name: "Confirmar" }));

    expect(await screen.findByText("Este report já foi finalizado.")).toBeInTheDocument();
    expect(refetch).toHaveBeenCalled();
  });
});
