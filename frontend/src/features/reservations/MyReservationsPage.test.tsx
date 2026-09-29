import { render, screen, waitFor } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { MemoryRouter } from "react-router-dom";
import { describe, expect, it, vi, beforeEach } from "vitest";
import { MyReservationsPage } from "./MyReservationsPage";
import type { ReservationDto } from "../../shared/api/types";

const useMyReservationsQuery = vi.fn();
const cancelMutateAsync = vi.fn();

vi.mock("./hooks", () => ({
  useMyReservationsQuery: (...args: unknown[]) => useMyReservationsQuery(...args),
  useCancelMyReservation: () => ({ mutateAsync: cancelMutateAsync, isPending: false }),
}));

function reservation(overrides: Partial<ReservationDto> = {}): ReservationDto {
  return {
    id: "res-1",
    code: "RES-2026-000001",
    kind: "BOOKING",
    areaId: "area-1",
    areaName: "Salão de festas",
    date: "2026-09-20",
    startTime: "08:00",
    endTime: "09:00",
    residentId: "res-1",
    residentName: "Maria Souza",
    guests: 10,
    notes: null,
    status: "CONFIRMED",
    completed: false,
    statusReason: null,
    cancelledBy: null,
    requiresPayment: false,
    price: null,
    createdAt: "2026-09-01T12:00:00Z",
    canCancel: true,
    canReport: false,
    whatsappPaymentUrl: null,
    ...overrides,
  };
}

function renderPage() {
  return render(
    <MemoryRouter>
      <MyReservationsPage />
    </MemoryRouter>,
  );
}

describe("MyReservationsPage (RF-RES-05/06)", () => {
  beforeEach(() => {
    cancelMutateAsync.mockReset();
  });

  it("mostra 'Realizada' para reserva CONFIRMED com fim no passado (RN-32)", () => {
    useMyReservationsQuery.mockReturnValue({
      data: { content: [reservation({ completed: true })], page: 0, size: 20, totalElements: 1, totalPages: 1 },
      isLoading: false,
    });
    renderPage();

    expect(screen.getByText("Realizada")).toBeInTheDocument();
  });

  it("mostra o motivo (statusReason) quando presente (RN-29)", () => {
    useMyReservationsQuery.mockReturnValue({
      data: {
        content: [reservation({ status: "PENDING_PAYMENT", statusReason: "Aguardando confirmação de pagamento pela administração." })],
        page: 0,
        size: 20,
        totalElements: 1,
        totalPages: 1,
      },
      isLoading: false,
    });
    renderPage();

    expect(
      screen.getByText("Aguardando confirmação de pagamento pela administração."),
    ).toBeInTheDocument();
  });

  it("cancelar reserva paga já confirmada mostra o aviso da RN-30", async () => {
    useMyReservationsQuery.mockReturnValue({
      data: {
        content: [reservation({ requiresPayment: true, price: 150, status: "CONFIRMED" })],
        page: 0,
        size: 20,
        totalElements: 1,
        totalPages: 1,
      },
      isLoading: false,
    });
    const user = userEvent.setup();
    renderPage();

    await user.click(screen.getByRole("button", { name: "Cancelar" }));

    expect(
      screen.getByText(/Eventual devolução do valor é tratada diretamente com a administração\./),
    ).toBeInTheDocument();
  });

  it("confirma o cancelamento chamando a rota de cancelamento", async () => {
    useMyReservationsQuery.mockReturnValue({
      data: { content: [reservation()], page: 0, size: 20, totalElements: 1, totalPages: 1 },
      isLoading: false,
    });
    cancelMutateAsync.mockResolvedValue(reservation({ status: "CANCELLED" }));
    const user = userEvent.setup();
    renderPage();

    await user.click(screen.getByRole("button", { name: "Cancelar" }));
    await user.click(screen.getByRole("button", { name: "Cancelar reserva" }));

    await waitFor(() => expect(cancelMutateAsync).toHaveBeenCalledWith("res-1"));
  });

  it("troca para a aba Anteriores", async () => {
    useMyReservationsQuery.mockReturnValue({
      data: { content: [], page: 0, size: 20, totalElements: 0, totalPages: 0 },
      isLoading: false,
    });
    const user = userEvent.setup();
    renderPage();

    await user.click(screen.getByRole("tab", { name: "Anteriores" }));

    expect(useMyReservationsQuery).toHaveBeenLastCalledWith("past");
  });
});
