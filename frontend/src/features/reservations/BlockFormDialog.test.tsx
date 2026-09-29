/**
 * "Novo bloqueio" (S/A, RF-RES-10, RN-33): sobreposição com reserva ativa
 * (`409 RESERVATION_OVERLAP`) explica que só a administração cancela a
 * reserva antes de criar o bloqueio.
 */
import { render, screen } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { describe, expect, it, vi, beforeEach } from "vitest";
import { BlockFormDialog } from "./BlockFormDialog";
import { ApiError } from "../../shared/api/client";
import type { AvailabilityDay } from "../../shared/api/types";

const useAreasQuery = vi.fn();
const usePublicSettingsQuery = vi.fn();
const useAreaAvailabilityQuery = vi.fn();
const createBlockMutateAsync = vi.fn();

vi.mock("../areas/hooks", () => ({
  useAreasQuery: (...args: unknown[]) => useAreasQuery(...args),
}));

vi.mock("./hooks", () => ({
  usePublicSettingsQuery: () => usePublicSettingsQuery(),
  useAreaAvailabilityQuery: (...args: unknown[]) => useAreaAvailabilityQuery(...args),
  useCreateBlock: () => ({ mutateAsync: createBlockMutateAsync, isPending: false }),
}));

function day(): AvailabilityDay {
  return {
    date: "2026-10-10",
    open: true,
    openTime: "08:00",
    closeTime: "12:00",
    bookable: true,
    notBookableReason: null,
    busy: [],
  };
}

describe("BlockFormDialog (RF-RES-10, RN-33)", () => {
  beforeEach(() => {
    createBlockMutateAsync.mockReset();
    useAreasQuery.mockReturnValue({ data: [{ id: "area-1", name: "Salão de festas" }] });
    usePublicSettingsQuery.mockReturnValue({ data: { slotMinutes: 30 } });
    useAreaAvailabilityQuery.mockReturnValue({ data: [day()], isLoading: false });
  });

  it("409 RESERVATION_OVERLAP explica que há reserva ativa e que só a administração pode cancelá-la", async () => {
    createBlockMutateAsync.mockRejectedValue(
      new ApiError({
        status: 409,
        code: "RESERVATION_OVERLAP",
        detail: "Já existe uma reserva ativa neste horário.",
      }),
    );
    const user = userEvent.setup();
    render(<BlockFormDialog open today="2026-10-10" onClose={vi.fn()} onCreated={vi.fn()} />);

    await user.selectOptions(screen.getByLabelText("Área"), "area-1");
    await user.click(screen.getByRole("button", { name: "08:00" }));
    await user.type(screen.getByLabelText("Motivo"), "Assembleia geral do condomínio.");
    await user.click(screen.getByRole("button", { name: "Criar bloqueio" }));

    expect(
      await screen.findByText(
        "Já existe uma reserva ativa nesse horário. Só a administração pode cancelá-la antes de criar o bloqueio.",
      ),
    ).toBeInTheDocument();
  });

  it("cria o bloqueio quando não há conflito", async () => {
    createBlockMutateAsync.mockResolvedValue({});
    const onCreated = vi.fn();
    const user = userEvent.setup();
    render(<BlockFormDialog open today="2026-10-10" onClose={vi.fn()} onCreated={onCreated} />);

    await user.selectOptions(screen.getByLabelText("Área"), "area-1");
    await user.click(screen.getByRole("button", { name: "08:00" }));
    await user.type(screen.getByLabelText("Motivo"), "Assembleia geral do condomínio.");
    await user.click(screen.getByRole("button", { name: "Criar bloqueio" }));

    expect(createBlockMutateAsync).toHaveBeenCalledWith({
      areaId: "area-1",
      date: "2026-10-10",
      startTime: "08:00",
      endTime: "08:30",
      reason: "Assembleia geral do condomínio.",
    });
    expect(onCreated).toHaveBeenCalled();
  });
});
