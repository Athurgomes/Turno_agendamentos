import { render, screen, waitFor } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { MemoryRouter, Route, Routes } from "react-router-dom";
import { describe, expect, it, vi, beforeEach } from "vitest";
import { ReservationWizardPage } from "./ReservationWizardPage";
import { ApiError } from "../../shared/api/client";
import type { AreaDetail, AvailabilityDay, MyUnitDto, PublicSettingsDto, ReservationDto } from "../../shared/api/types";

const useAreaQuery = vi.fn();
const useMyUnitQuery = vi.fn();
const useAreaAvailabilityQuery = vi.fn();
const usePublicSettingsQuery = vi.fn();
const createMutateAsync = vi.fn();

vi.mock("../areas/hooks", () => ({
  useAreaQuery: (...args: unknown[]) => useAreaQuery(...args),
}));
vi.mock("../units/hooks", () => ({
  useMyUnitQuery: () => useMyUnitQuery(),
}));
vi.mock("./hooks", () => ({
  useAreaAvailabilityQuery: (...args: unknown[]) => useAreaAvailabilityQuery(...args),
  usePublicSettingsQuery: () => usePublicSettingsQuery(),
  useCreateReservation: () => ({ mutateAsync: createMutateAsync, isPending: false }),
}));
vi.mock("../../shared/hooks/useServerClock", () => ({
  // "Hoje" fixo em 2026-09-15 (America/Sao_Paulo) — nunca `new Date()` puro (D-32).
  serverNow: () => new Date("2026-09-15T12:00:00Z"),
  useServerClock: () => ({ data: undefined }),
}));

function area(overrides: Partial<AreaDetail> = {}): AreaDetail {
  return {
    id: "area-1",
    name: "Salão de festas",
    category: "PARTY_ROOM",
    status: "ACTIVE",
    description: "d",
    rules: "r",
    conductGuidelines: "c",
    capacity: 30,
    requiresPayment: false,
    price: null,
    paymentWhatsapp: null,
    openingHours: [],
    photos: [],
    version: 1,
    ...overrides,
  };
}

function unit(): MyUnitDto {
  return {
    id: "unit-1",
    block: "A",
    number: "101",
    identifier: "A-101",
    username: "a101",
    active: true,
    residents: [
      { id: "res-1", name: "Maria Souza", phone: "5562999998888", email: "maria@exemplo.test", cpf: null, primary: true },
    ],
  };
}

function settings(): PublicSettingsDto {
  return {
    condominiumName: "Condomínio Exemplo",
    timezone: "America/Sao_Paulo",
    minAdvanceDays: 1,
    nextDayWindowStart: "06:00",
    nextDayWindowEnd: "16:00",
    maxAdvanceDays: 60,
    maxActiveBookingsPerUnit: 3,
    residentCancelDeadlineHours: 24,
    slotMinutes: 30,
    reportWindowDays: 7,
  };
}

function day(date: string, overrides: Partial<AvailabilityDay> = {}): AvailabilityDay {
  return {
    date,
    open: true,
    openTime: "08:00",
    closeTime: "12:00",
    bookable: true,
    notBookableReason: null,
    busy: [],
    ...overrides,
  };
}

function reservationDto(overrides: Partial<ReservationDto> = {}): ReservationDto {
  return {
    id: "res-100",
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
    createdAt: "2026-09-15T12:00:00Z",
    canCancel: true,
    canReport: false,
    whatsappPaymentUrl: null,
    ...overrides,
  };
}

function renderWizard() {
  return render(
    <MemoryRouter initialEntries={["/areas/area-1/reservar"]}>
      <Routes>
        <Route path="/areas/:id/reservar" element={<ReservationWizardPage />} />
      </Routes>
    </MemoryRouter>,
  );
}

describe("ReservationWizardPage (RF-RES-02/03, RNF-05)", () => {
  beforeEach(() => {
    useAreaQuery.mockReturnValue({ data: area(), isLoading: false });
    useMyUnitQuery.mockReturnValue({ data: unit(), isLoading: false });
    usePublicSettingsQuery.mockReturnValue({ data: settings() });
    createMutateAsync.mockReset();
  });

  it("dia não reservável mostra o motivo ao tocar (RN-20)", async () => {
    useAreaAvailabilityQuery.mockReturnValue({
      data: [day("2026-09-16", { bookable: false, notBookableReason: "NEXT_DAY_WINDOW_CLOSED" })],
      isLoading: false,
    });
    const user = userEvent.setup();
    renderWizard();

    await user.click(screen.getByRole("button", { name: "16" }));

    expect(
      screen.getByText("Reservas para amanhã só podem ser feitas entre 06:00 e 16:00."),
    ).toBeInTheDocument();
    // Não avança para o passo de horário (dia não ficou selecionado).
    expect(screen.getByText("Passo 1 de 4")).toBeInTheDocument();
  });

  it("slots ocupados não são selecionáveis", async () => {
    useAreaAvailabilityQuery.mockReturnValue({
      data: [
        day("2026-09-20", {
          busy: [{ startTime: "09:00", endTime: "09:30", kind: "BOOKING" }],
        }),
      ],
      isLoading: false,
    });
    const user = userEvent.setup();
    renderWizard();

    await user.click(screen.getByRole("button", { name: "20" }));
    await user.click(screen.getByRole("button", { name: "Continuar" }));

    const busyBlock = screen.getByRole("button", { name: /09:00/ });
    expect(busyBlock).toBeDisabled();
  });

  it("cria a reserva em até 4 passos com o payload do contrato (RF-RES-03)", async () => {
    useAreaAvailabilityQuery.mockReturnValue({ data: [day("2026-09-20")], isLoading: false });
    createMutateAsync.mockResolvedValue({
      reservation: reservationDto(),
      whatsappPaymentUrl: null,
    });
    const user = userEvent.setup();
    renderWizard();

    // Passo 1 — data
    await user.click(screen.getByRole("button", { name: "20" }));
    await user.click(screen.getByRole("button", { name: "Continuar" }));

    // Passo 2 — horário: escolhe o primeiro bloco (08:00–08:30)
    await user.click(screen.getByRole("button", { name: "08:00" }));
    await user.click(screen.getByRole("button", { name: "Continuar" }));

    // Passo 3 — responsável e convidados
    await user.selectOptions(screen.getByLabelText("Morador responsável"), "res-1");
    await user.type(screen.getByLabelText("Número de convidados"), "10");
    await user.click(screen.getByRole("button", { name: "Continuar" }));

    // Passo 4 — revisão e confirmação
    expect(screen.getByText("Passo 4 de 4")).toBeInTheDocument();
    await user.click(screen.getByRole("button", { name: "Confirmar reserva" }));

    await waitFor(() =>
      expect(createMutateAsync).toHaveBeenCalledWith({
        areaId: "area-1",
        date: "2026-09-20",
        startTime: "08:00",
        endTime: "08:30",
        residentId: "res-1",
        guests: 10,
        notes: undefined,
      }),
    );
  });

  it("área paga leva à conclusão com o botão Pagar via WhatsApp (RF-RES-04, RN-26)", async () => {
    useAreaQuery.mockReturnValue({
      data: area({ requiresPayment: true, price: 150, paymentWhatsapp: "5562999990000" }),
      isLoading: false,
    });
    useAreaAvailabilityQuery.mockReturnValue({ data: [day("2026-09-20")], isLoading: false });
    createMutateAsync.mockResolvedValue({
      reservation: reservationDto({
        status: "PENDING_PAYMENT",
        requiresPayment: true,
        price: 150,
        statusReason: "Aguardando confirmação de pagamento pela administração.",
      }),
      whatsappPaymentUrl: "https://wa.me/5562999990000?text=ola",
    });
    const user = userEvent.setup();
    renderWizard();

    await user.click(screen.getByRole("button", { name: "20" }));
    await user.click(screen.getByRole("button", { name: "Continuar" }));
    await user.click(screen.getByRole("button", { name: "08:00" }));
    await user.click(screen.getByRole("button", { name: "Continuar" }));
    await user.selectOptions(screen.getByLabelText("Morador responsável"), "res-1");
    await user.type(screen.getByLabelText("Número de convidados"), "10");
    await user.click(screen.getByRole("button", { name: "Continuar" }));
    await user.click(screen.getByRole("button", { name: "Confirmar reserva" }));

    const link = await screen.findByRole("link", { name: "Pagar via WhatsApp" });
    expect(link).toHaveAttribute("href", "https://wa.me/5562999990000?text=ola");
    expect(link).toHaveAttribute("target", "_blank");
    expect(link).toHaveAttribute("rel", "noopener noreferrer");
  });

  it("erro RESERVATION_OVERLAP (409) volta ao passo de horário com a mensagem", async () => {
    useAreaAvailabilityQuery.mockReturnValue({ data: [day("2026-09-20")], isLoading: false });
    createMutateAsync.mockRejectedValue(
      new ApiError({
        status: 409,
        code: "RESERVATION_OVERLAP",
        detail: "Esse horário acabou de ser reservado por outra pessoa. Escolha outro horário.",
      }),
    );
    const user = userEvent.setup();
    renderWizard();

    await user.click(screen.getByRole("button", { name: "20" }));
    await user.click(screen.getByRole("button", { name: "Continuar" }));
    await user.click(screen.getByRole("button", { name: "08:00" }));
    await user.click(screen.getByRole("button", { name: "Continuar" }));
    await user.selectOptions(screen.getByLabelText("Morador responsável"), "res-1");
    await user.type(screen.getByLabelText("Número de convidados"), "10");
    await user.click(screen.getByRole("button", { name: "Continuar" }));
    await user.click(screen.getByRole("button", { name: "Confirmar reserva" }));

    await waitFor(() => expect(screen.getByText("Passo 2 de 4")).toBeInTheDocument());
    expect(
      screen.getByText("Esse horário acabou de ser reservado por outra pessoa. Escolha outro horário."),
    ).toBeInTheDocument();
  });
});
