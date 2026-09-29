import { fireEvent, render, screen, waitFor } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { describe, expect, it, vi, beforeEach } from "vitest";
import { SettingsPage } from "./SettingsPage";
import { ApiError } from "../../../shared/api/client";

const useSettingsQuery = vi.fn();
const updateMutateAsync = vi.fn();

vi.mock("./hooks", () => ({
  useSettingsQuery: () => useSettingsQuery(),
  useUpdateSettings: () => ({ mutateAsync: updateMutateAsync, isPending: false }),
}));

const baseSettings = {
  condominiumName: "Condomínio Jardim das Flores",
  timezone: "America/Sao_Paulo",
  defaultPaymentWhatsapp: "5562999998888",
  minAdvanceDays: 1,
  nextDayWindowStart: "08:00",
  nextDayWindowEnd: "20:00",
  maxAdvanceDays: 30,
  maxActiveBookingsPerUnit: 2,
  residentCancelDeadlineHours: 24,
  slotMinutes: 30,
  reportWindowDays: 3,
};

describe("SettingsPage", () => {
  beforeEach(() => {
    useSettingsQuery.mockReturnValue({ data: baseSettings, isLoading: false });
    updateMutateAsync.mockReset();
  });

  it("mostra timezone e slotMinutes como somente leitura, sem campo editável", () => {
    render(<SettingsPage />);

    expect(screen.getByText("America/Sao_Paulo")).toBeInTheDocument();
    expect(screen.getByText("30")).toBeInTheDocument();
    expect(screen.queryByLabelText("Fuso horário")).not.toBeInTheDocument();
  });

  it("valida nextDayWindowStart < nextDayWindowEnd", async () => {
    const user = userEvent.setup();
    render(<SettingsPage />);

    const endField = screen.getByLabelText("Fim da janela para o dia seguinte");
    fireEvent.change(endField, { target: { value: "07:00" } });
    await user.click(screen.getByRole("button", { name: "Salvar configurações" }));

    expect(
      await screen.findByText("O fim da janela precisa ser depois do início."),
    ).toBeInTheDocument();
    expect(updateMutateAsync).not.toHaveBeenCalled();
  });

  it("valida maxAdvanceDays >= minAdvanceDays", async () => {
    const user = userEvent.setup();
    render(<SettingsPage />);

    const minField = screen.getByLabelText("Antecedência mínima (dias)");
    await user.clear(minField);
    await user.type(minField, "60");
    await user.click(screen.getByRole("button", { name: "Salvar configurações" }));

    expect(
      await screen.findByText("A antecedência máxima não pode ser menor que a mínima."),
    ).toBeInTheDocument();
    expect(updateMutateAsync).not.toHaveBeenCalled();
  });

  it("envia o payload do contrato, reaproveitando timezone e slotMinutes", async () => {
    updateMutateAsync.mockResolvedValue(baseSettings);
    const user = userEvent.setup();
    render(<SettingsPage />);

    const maxBookingsField = screen.getByLabelText("Reservas ativas por unidade");
    await user.clear(maxBookingsField);
    await user.type(maxBookingsField, "3");
    await user.click(screen.getByRole("button", { name: "Salvar configurações" }));

    await waitFor(() =>
      expect(updateMutateAsync).toHaveBeenCalledWith({
        ...baseSettings,
        maxActiveBookingsPerUnit: 3,
      }),
    );
    expect(await screen.findByText("Configurações salvas.")).toBeInTheDocument();
  });

  it("mostra o erro do backend sem quebrar o formulário", async () => {
    updateMutateAsync.mockRejectedValue(
      new ApiError({
        status: 422,
        code: "VALIDATION_ERROR",
        detail: "Não foi possível salvar as configurações.",
      }),
    );
    const user = userEvent.setup();
    render(<SettingsPage />);

    await user.click(screen.getByRole("button", { name: "Salvar configurações" }));

    expect(await screen.findByText("Não foi possível salvar as configurações.")).toBeInTheDocument();
  });
});
