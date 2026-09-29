import { render, screen, waitFor, within } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { MemoryRouter } from "react-router-dom";
import { describe, expect, it, vi, beforeEach } from "vitest";
import { UnitsListPage } from "./UnitsListPage";
import { ApiError } from "../../shared/api/client";

const useUnitsQuery = vi.fn();
const deactivateMutateAsync = vi.fn();
const resetMutateAsync = vi.fn();

vi.mock("./hooks", () => ({
  useUnitsQuery: (...args: unknown[]) => useUnitsQuery(...args),
  useDeactivateUnit: () => ({ mutateAsync: deactivateMutateAsync, isPending: false }),
  useResetUnitPassword: () => ({ mutateAsync: resetMutateAsync, isPending: false }),
}));

const sampleUnit = {
  id: "unit-1",
  block: "A",
  number: "1203",
  identifier: "a-1203",
  active: true,
  primaryResident: { name: "Maria Souza", phone: "5562999998888" },
  residentsCount: 2,
};

function renderPage() {
  return render(
    <MemoryRouter>
      <UnitsListPage />
    </MemoryRouter>,
  );
}

describe("UnitsListPage", () => {
  beforeEach(() => {
    useUnitsQuery.mockReturnValue({
      data: { content: [sampleUnit], page: 0, size: 20, totalElements: 1, totalPages: 1 },
      isLoading: false,
    });
    deactivateMutateAsync.mockReset();
    resetMutateAsync.mockReset();
  });

  it("mostra o identificador gerado pelo backend, morador principal e telefone", () => {
    renderPage();
    expect(screen.getAllByText("a-1203").length).toBeGreaterThan(0);
    expect(screen.getAllByText("Maria Souza").length).toBeGreaterThan(0);
    expect(screen.getAllByText("+55 (62) 99999-8888").length).toBeGreaterThan(0);
  });

  it("resetar senha mostra o modal de credenciais uma única vez", async () => {
    resetMutateAsync.mockResolvedValue({ username: "a-1203", tempPassword: "K7M4XP" });
    const user = userEvent.setup();
    renderPage();

    const [resetButton] = screen.getAllByRole("button", { name: "Resetar senha" });
    await user.click(resetButton);

    expect(await screen.findByText("K7M4XP")).toBeInTheDocument();
    expect(resetMutateAsync).toHaveBeenCalledWith("unit-1");
  });

  it("desativar pede confirmação antes de chamar a API", async () => {
    deactivateMutateAsync.mockResolvedValue(undefined);
    const user = userEvent.setup();
    renderPage();

    const [deactivateButton] = screen.getAllByRole("button", { name: "Desativar" });
    await user.click(deactivateButton);
    expect(deactivateMutateAsync).not.toHaveBeenCalled();

    const dialog = screen.getByRole("dialog");
    await user.click(within(dialog).getByRole("button", { name: "Desativar" }));

    await waitFor(() => expect(deactivateMutateAsync).toHaveBeenCalledWith("unit-1"));
  });

  it("mostra o detalhe do 409 UNIT_HAS_FUTURE_RESERVATIONS com a lista de reservas", async () => {
    deactivateMutateAsync.mockRejectedValue(
      new ApiError({
        status: 409,
        code: "UNIT_HAS_FUTURE_RESERVATIONS",
        detail: "Esta unidade tem reservas futuras ativas. Cancele-as antes de desativar.",
        affectedReservations: [
          {
            id: "r1",
            code: "RES-1",
            kind: "BOOKING",
            areaId: "area-1",
            areaName: "Churrasqueira",
            unitIdentifier: "a-1203",
            residentName: "Maria Souza",
            date: "2026-10-10",
            startTime: "12:00",
            endTime: "16:00",
            status: "CONFIRMED",
          },
        ],
      }),
    );
    const user = userEvent.setup();
    renderPage();

    await user.click(screen.getAllByRole("button", { name: "Desativar" })[0]);
    const dialog = screen.getByRole("dialog");
    await user.click(within(dialog).getByRole("button", { name: "Desativar" }));

    expect(
      await screen.findByText(
        "Esta unidade tem reservas futuras ativas. Cancele-as antes de desativar.",
      ),
    ).toBeInTheDocument();
    expect(screen.getByText(/Churrasqueira/)).toBeInTheDocument();
  });
});
