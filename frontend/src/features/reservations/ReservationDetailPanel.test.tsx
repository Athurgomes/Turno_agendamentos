/**
 * Detalhe da reserva/bloqueio (RF-RES-08/09, RN-27, RN-28, D-16): ações de
 * ADMIN, ausência dessas ações para SYNDIC, WhatsApp e histórico
 * (`GET /reservations/{id}/events`).
 */
import { render, screen } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { describe, expect, it, vi, beforeEach } from "vitest";
import { ReservationDetailPanel } from "./ReservationDetailPanel";
import type { AdminReservationDto, ReservationEventDto, Role } from "../../shared/api/types";

const useAdminReservationQuery = vi.fn();
const useReservationEventsQuery = vi.fn();
const cancelMutateAsync = vi.fn();
const deleteBlockMutateAsync = vi.fn();

vi.mock("./hooks", () => ({
  useAdminReservationQuery: (...args: unknown[]) => useAdminReservationQuery(...args),
  useReservationEventsQuery: (...args: unknown[]) => useReservationEventsQuery(...args),
  useCancelAdminReservation: () => ({ mutateAsync: cancelMutateAsync, isPending: false }),
  useDeleteBlock: () => ({ mutateAsync: deleteBlockMutateAsync, isPending: false }),
}));

vi.mock("../../shared/hooks/useServerClock", () => ({
  useServerClock: () => ({ data: { timezone: "America/Sao_Paulo", simulated: false } }),
}));

// ReservationEditDialog puxa área/disponibilidade; fora do escopo deste teste.
vi.mock("./ReservationEditDialog", () => ({
  ReservationEditDialog: () => null,
}));

function reservation(overrides: Partial<AdminReservationDto> = {}): AdminReservationDto {
  return {
    id: "res-1",
    code: "RES-2026-000001",
    kind: "BOOKING",
    areaId: "area-1",
    areaName: "Salão de festas",
    date: "2026-10-10",
    startTime: "08:00",
    endTime: "10:00",
    residentId: "resident-1",
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
    unitId: "unit-1",
    unitIdentifier: "A-101",
    residentPhone: "5562999998888",
    whatsappContactUrl: "https://wa.me/5562999998888",
    cancelledAt: null,
    paymentConfirmedAt: null,
    ...overrides,
  };
}

function events(): ReservationEventDto[] {
  return [
    {
      type: "CREATED",
      occurredAt: "2026-09-01T12:00:00Z",
      actor: { id: "resident-1", name: "Maria Souza", role: "UNIT" },
      justification: null,
      changes: null,
    },
    {
      type: "UPDATED",
      occurredAt: "2026-09-15T09:00:00Z",
      actor: { id: "admin-1", name: "Administração", role: "ADMIN" },
      justification: "Área em manutenção emergencial, remanejada para o salão principal.",
      changes: null,
    },
  ];
}

function renderPanel(role: Role, overrides: Partial<AdminReservationDto> = {}) {
  useAdminReservationQuery.mockReturnValue({ data: reservation(overrides), isLoading: false });
  useReservationEventsQuery.mockReturnValue({ data: events() });
  return render(<ReservationDetailPanel id="res-1" role={role} onClose={vi.fn()} />);
}

describe("ReservationDetailPanel (RF-RES-08/09, D-16)", () => {
  beforeEach(() => {
    cancelMutateAsync.mockReset();
    deleteBlockMutateAsync.mockReset();
  });

  it("ADMIN vê os botões Alterar e Cancelar", () => {
    renderPanel("ADMIN");
    expect(screen.getByRole("button", { name: "Alterar" })).toBeInTheDocument();
    expect(screen.getByRole("button", { name: "Cancelar" })).toBeInTheDocument();
  });

  it("SYNDIC não vê Alterar nem Cancelar, mas vê o botão de WhatsApp (D-16)", () => {
    renderPanel("SYNDIC");
    expect(screen.queryByRole("button", { name: "Alterar" })).not.toBeInTheDocument();
    expect(screen.queryByRole("button", { name: "Cancelar" })).not.toBeInTheDocument();
    expect(screen.getByRole("link", { name: "Falar no WhatsApp" })).toHaveAttribute(
      "href",
      "https://wa.me/5562999998888",
    );
  });

  it("o link do WhatsApp abre em nova aba com rel seguro", () => {
    renderPanel("SYNDIC");
    const link = screen.getByRole("link", { name: "Falar no WhatsApp" });
    expect(link).toHaveAttribute("target", "_blank");
    expect(link).toHaveAttribute("rel", "noopener noreferrer");
  });

  it("renderiza o histórico com os eventos e a justificativa registrada", () => {
    renderPanel("ADMIN");
    expect(screen.getByText(/Criada/)).toBeInTheDocument();
    expect(screen.getByText(/Alterada/)).toBeInTheDocument();
    expect(
      screen.getByText(/Justificativa: Área em manutenção emergencial/),
    ).toBeInTheDocument();
  });

  it("cancelar com justificativa menor que 10 caracteres não chama a rota (RN-27)", async () => {
    const user = userEvent.setup();
    renderPanel("ADMIN");

    await user.click(screen.getByRole("button", { name: "Cancelar" }));
    await user.type(screen.getByLabelText("Justificativa (mínimo 10 caracteres)"), "curto");
    expect(screen.getByRole("button", { name: "Cancelar reserva" })).toBeDisabled();
    expect(cancelMutateAsync).not.toHaveBeenCalled();
  });

  it("cancelar com justificativa válida chama a rota de cancelamento (RN-27)", async () => {
    const user = userEvent.setup();
    cancelMutateAsync.mockResolvedValue(reservation({ status: "CANCELLED" }));
    renderPanel("ADMIN");

    await user.click(screen.getByRole("button", { name: "Cancelar" }));
    await user.type(
      screen.getByLabelText("Justificativa (mínimo 10 caracteres)"),
      "Área precisou ser interditada por manutenção urgente.",
    );
    await user.click(screen.getByRole("button", { name: "Cancelar reserva" }));

    expect(cancelMutateAsync).toHaveBeenCalledWith({
      id: "res-1",
      justification: "Área precisou ser interditada por manutenção urgente.",
    });
  });

  it("bloqueio mostra o motivo e o botão de remover para SYNDIC (D-16)", () => {
    renderPanel("SYNDIC", {
      kind: "BLOCK",
      notes: "Assembleia geral do condomínio.",
      unitId: null,
      unitIdentifier: null,
      residentPhone: null,
      whatsappContactUrl: null,
    });
    expect(screen.getByText("Assembleia geral do condomínio.")).toBeInTheDocument();
    expect(screen.getByRole("button", { name: "Remover bloqueio" })).toBeInTheDocument();
  });
});
