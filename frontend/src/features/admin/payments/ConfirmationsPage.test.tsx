/**
 * Aba Confirmações (RF-PAG-01..03, RN-27, RN-29, RN-32): lista pendências,
 * destaca as que acontecem em até 48h, confirma pagamento e cancela reserva
 * com justificativa obrigatória (mínimo 10 caracteres, RN-27).
 */
import { render, screen, waitFor } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { describe, expect, it, vi, beforeEach } from "vitest";
import { ConfirmationsPage } from "./ConfirmationsPage";
import { ApiError } from "../../../shared/api/client";
import type { PendingPaymentDto } from "../../../shared/api/types";

const usePendingPaymentsQuery = vi.fn();
const confirmMutateAsync = vi.fn();
const cancelMutateAsync = vi.fn();

vi.mock("./hooks", () => ({
  usePendingPaymentsQuery: () => usePendingPaymentsQuery(),
  useConfirmPayment: () => ({ mutateAsync: confirmMutateAsync, isPending: false }),
  useCancelPendingPayment: () => ({ mutateAsync: cancelMutateAsync, isPending: false }),
}));

vi.mock("../../../shared/hooks/useServerClock", () => ({
  useServerClock: () => ({ data: { timezone: "America/Sao_Paulo", simulated: false } }),
}));

function pendingPayment(overrides: Partial<PendingPaymentDto> = {}): PendingPaymentDto {
  return {
    id: "res-1",
    code: "RES-2026-000001",
    kind: "BOOKING",
    areaId: "area-1",
    areaName: "Salão de festas",
    date: "2026-10-10",
    startTime: "18:00",
    endTime: "22:00",
    residentId: "resident-1",
    residentName: "Maria Souza",
    guests: 20,
    notes: null,
    status: "PENDING_PAYMENT",
    completed: false,
    statusReason: "Aguardando confirmação de pagamento pela administração.",
    cancelledBy: null,
    requiresPayment: true,
    price: 150,
    createdAt: "2026-09-27T12:00:00Z",
    canCancel: false,
    canReport: false,
    whatsappPaymentUrl: "https://wa.me/5562999998888",
    unitId: "unit-1",
    unitIdentifier: "A-1203",
    residentPhone: "5562999998888",
    whatsappContactUrl: "https://wa.me/5562999998888",
    cancelledAt: null,
    paymentConfirmedAt: null,
    within48h: false,
    ...overrides,
  };
}

function renderPage() {
  return render(<ConfirmationsPage />);
}

describe("ConfirmationsPage (RF-PAG-01..03)", () => {
  beforeEach(() => {
    confirmMutateAsync.mockReset();
    cancelMutateAsync.mockReset();
  });

  it("mostra o estado vazio quando não há pendências", () => {
    usePendingPaymentsQuery.mockReturnValue({ data: [], isLoading: false });
    renderPage();

    expect(screen.getByText("Nenhum pagamento aguardando confirmação.")).toBeInTheDocument();
  });

  it("destaca com selo textual o item que acontece em até 48h", () => {
    usePendingPaymentsQuery.mockReturnValue({
      data: [pendingPayment({ within48h: true })],
      isLoading: false,
    });
    renderPage();

    expect(screen.getByText("Acontece em até 48h")).toBeInTheDocument();
  });

  it("não mostra o selo de 48h quando within48h é falso", () => {
    usePendingPaymentsQuery.mockReturnValue({
      data: [pendingPayment({ within48h: false })],
      isLoading: false,
    });
    renderPage();

    expect(screen.queryByText("Acontece em até 48h")).not.toBeInTheDocument();
  });

  it("mostra área, unidade, morador, telefone, valor e data da solicitação", () => {
    usePendingPaymentsQuery.mockReturnValue({ data: [pendingPayment()], isLoading: false });
    renderPage();

    expect(screen.getByText("Salão de festas")).toBeInTheDocument();
    expect(screen.getByText("A-1203")).toBeInTheDocument();
    expect(screen.getByText("Maria Souza")).toBeInTheDocument();
    expect(screen.getByText("+55 (62) 99999-8888")).toBeInTheDocument();
    expect(screen.getByText("R$ 150,00")).toBeInTheDocument();
    expect(screen.getByRole("link", { name: "Falar no WhatsApp" })).toHaveAttribute(
      "target",
      "_blank",
    );
  });

  it("confirma o pagamento chamando a rota de confirmação", async () => {
    usePendingPaymentsQuery.mockReturnValue({ data: [pendingPayment()], isLoading: false });
    confirmMutateAsync.mockResolvedValue(pendingPayment({ status: "CONFIRMED" }));
    const user = userEvent.setup();
    renderPage();

    await user.click(screen.getByRole("button", { name: "Confirmar pagamento" }));
    await user.click(screen.getAllByRole("button", { name: "Confirmar pagamento" })[1]);

    await waitFor(() => expect(confirmMutateAsync).toHaveBeenCalledWith("res-1"));
  });

  it("mostra o detail do 409 INVALID_STATUS_TRANSITION ao confirmar (RN-32)", async () => {
    usePendingPaymentsQuery.mockReturnValue({ data: [pendingPayment()], isLoading: false });
    confirmMutateAsync.mockRejectedValue(
      new ApiError({
        status: 409,
        code: "INVALID_STATUS_TRANSITION",
        detail: "Esta reserva já não está mais aguardando confirmação de pagamento.",
      }),
    );
    const user = userEvent.setup();
    renderPage();

    await user.click(screen.getByRole("button", { name: "Confirmar pagamento" }));
    await user.click(screen.getAllByRole("button", { name: "Confirmar pagamento" })[1]);

    expect(
      await screen.findByText("Esta reserva já não está mais aguardando confirmação de pagamento."),
    ).toBeInTheDocument();
  });

  it("cancelar sem justificativa de pelo menos 10 caracteres não chama a API (RN-27)", async () => {
    usePendingPaymentsQuery.mockReturnValue({ data: [pendingPayment()], isLoading: false });
    const user = userEvent.setup();
    renderPage();

    await user.click(screen.getByRole("button", { name: "Cancelar reserva" }));
    await user.type(screen.getByLabelText(/Justificativa/), "curta");
    await user.click(screen.getAllByRole("button", { name: "Cancelar reserva" })[1]);

    expect(cancelMutateAsync).not.toHaveBeenCalled();
  });

  it("cancela a reserva com justificativa válida", async () => {
    usePendingPaymentsQuery.mockReturnValue({ data: [pendingPayment()], isLoading: false });
    cancelMutateAsync.mockResolvedValue(pendingPayment({ status: "CANCELLED" }));
    const user = userEvent.setup();
    renderPage();

    await user.click(screen.getByRole("button", { name: "Cancelar reserva" }));
    await user.type(screen.getByLabelText(/Justificativa/), "Pagamento não foi identificado a tempo.");
    await user.click(screen.getAllByRole("button", { name: "Cancelar reserva" })[1]);

    await waitFor(() =>
      expect(cancelMutateAsync).toHaveBeenCalledWith({
        id: "res-1",
        justification: "Pagamento não foi identificado a tempo.",
      }),
    );
  });

  it("mostra o detail do erro ao cancelar", async () => {
    usePendingPaymentsQuery.mockReturnValue({ data: [pendingPayment()], isLoading: false });
    cancelMutateAsync.mockRejectedValue(
      new ApiError({
        status: 409,
        code: "INVALID_STATUS_TRANSITION",
        detail: "Esta reserva já foi cancelada.",
      }),
    );
    const user = userEvent.setup();
    renderPage();

    await user.click(screen.getByRole("button", { name: "Cancelar reserva" }));
    await user.type(screen.getByLabelText(/Justificativa/), "Pagamento não confirmado pelo síndico.");
    await user.click(screen.getAllByRole("button", { name: "Cancelar reserva" })[1]);

    expect(await screen.findByText("Esta reserva já foi cancelada.")).toBeInTheDocument();
  });
});
