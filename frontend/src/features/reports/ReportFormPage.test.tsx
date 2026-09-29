/**
 * Formulário de report (RF-REP-01, RN-34, RN-35): botão "Reportar" só existe
 * porque `MyReservationsPage` já checou `canReport` vindo do backend — este
 * formulário não recalcula a janela da RN-34, só valida descrição e fotos
 * para UX.
 */
import { render, screen, waitFor } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { MemoryRouter, Route, Routes } from "react-router-dom";
import { describe, expect, it, vi, beforeEach } from "vitest";
import { ReportFormPage } from "./ReportFormPage";
import { ApiError } from "../../shared/api/client";
import type { MyUnitDto, ReservationDto } from "../../shared/api/types";

const useReservationQuery = vi.fn();
const useMyUnitQuery = vi.fn();
const createMutateAsync = vi.fn();
const navigate = vi.fn();

vi.mock("../reservations/hooks", () => ({
  useReservationQuery: (...args: unknown[]) => useReservationQuery(...args),
}));
vi.mock("../units/hooks", () => ({
  useMyUnitQuery: () => useMyUnitQuery(),
}));
vi.mock("./hooks", () => ({
  useCreateReport: () => ({ mutateAsync: createMutateAsync, isPending: false }),
}));
vi.mock("react-router-dom", async () => {
  const actual = await vi.importActual<typeof import("react-router-dom")>("react-router-dom");
  return { ...actual, useNavigate: () => navigate };
});

function reservation(): ReservationDto {
  return {
    id: "res-1",
    code: "RES-2026-000001",
    kind: "BOOKING",
    areaId: "area-1",
    areaName: "Salão de festas",
    date: "2026-09-20",
    startTime: "08:00",
    endTime: "09:00",
    residentId: "resident-1",
    residentName: "Maria Souza",
    guests: 10,
    notes: null,
    status: "CONFIRMED",
    completed: true,
    statusReason: null,
    cancelledBy: null,
    requiresPayment: false,
    price: null,
    createdAt: "2026-09-01T12:00:00Z",
    canCancel: false,
    canReport: true,
    whatsappPaymentUrl: null,
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
      { id: "resident-1", name: "Maria Souza", phone: "5562999998888", email: null, cpf: null, primary: true },
    ],
  };
}

function renderPage() {
  return render(
    <MemoryRouter initialEntries={["/minhas-reservas/res-1/reportar"]}>
      <Routes>
        <Route path="/minhas-reservas/:id/reportar" element={<ReportFormPage />} />
      </Routes>
    </MemoryRouter>,
  );
}

describe("ReportFormPage (RF-REP-01, RN-35)", () => {
  beforeEach(() => {
    createMutateAsync.mockReset();
    navigate.mockReset();
    useReservationQuery.mockReturnValue({ data: reservation() });
    useMyUnitQuery.mockReturnValue({ data: unit() });
  });

  it("não envia com descrição menor que 10 caracteres", async () => {
    const user = userEvent.setup();
    renderPage();

    await user.selectOptions(screen.getByLabelText("Categoria"), "DAMAGE");
    await user.selectOptions(screen.getByLabelText("Morador que reporta"), "resident-1");
    await user.type(screen.getByLabelText("Descrição (mínimo 10 caracteres)"), "curto");
    await user.click(screen.getByRole("button", { name: "Enviar report" }));

    expect(createMutateAsync).not.toHaveBeenCalled();
    expect(
      screen.getByText("Escreva pelo menos 10 caracteres para descrever o problema."),
    ).toBeInTheDocument();
  });

  it("envia o report com categoria, morador, descrição válida e navega para 'Meus reports'", async () => {
    createMutateAsync.mockResolvedValue({});
    const user = userEvent.setup();
    renderPage();

    await user.selectOptions(screen.getByLabelText("Categoria"), "DAMAGE");
    await user.selectOptions(screen.getByLabelText("Morador que reporta"), "resident-1");
    await user.type(
      screen.getByLabelText("Descrição (mínimo 10 caracteres)"),
      "A churrasqueira está com a grelha quebrada.",
    );
    await user.click(screen.getByRole("button", { name: "Enviar report" }));

    await waitFor(() =>
      expect(createMutateAsync).toHaveBeenCalledWith({
        reservationId: "res-1",
        payload: {
          category: "DAMAGE",
          description: "A churrasqueira está com a grelha quebrada.",
          residentId: "resident-1",
        },
        photos: [],
      }),
    );
    await waitFor(() => expect(navigate).toHaveBeenCalledWith("/meus-reports"));
  });

  it("recusa selecionar mais de 5 fotos de uma vez", async () => {
    const user = userEvent.setup();
    renderPage();

    const files = Array.from({ length: 6 }, (_, index) =>
      new File(["a"], `foto-${index}.jpg`, { type: "image/jpeg" }),
    );
    await user.upload(screen.getByLabelText("Selecionar fotos"), files);

    expect(screen.getByText("Selecione no máximo 5 fotos por vez.")).toBeInTheDocument();
  });

  it("mostra o detail do erro da API quando o envio falha", async () => {
    createMutateAsync.mockRejectedValue(
      new ApiError({
        status: 422,
        code: "REPORT_WINDOW_CLOSED",
        detail: "A janela para reportar esta reserva já foi encerrada.",
      }),
    );
    const user = userEvent.setup();
    renderPage();

    await user.selectOptions(screen.getByLabelText("Categoria"), "DAMAGE");
    await user.selectOptions(screen.getByLabelText("Morador que reporta"), "resident-1");
    await user.type(
      screen.getByLabelText("Descrição (mínimo 10 caracteres)"),
      "A churrasqueira está com a grelha quebrada.",
    );
    await user.click(screen.getByRole("button", { name: "Enviar report" }));

    expect(
      await screen.findByText("A janela para reportar esta reserva já foi encerrada."),
    ).toBeInTheDocument();
  });
});
