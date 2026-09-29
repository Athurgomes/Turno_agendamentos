import { render, screen } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { describe, expect, it, vi } from "vitest";
import { AreaLifecycleDialog } from "./AreaLifecycleDialog";
import { ApiError } from "../../shared/api/client";

const affectedReservations = [
  {
    id: "r1",
    code: "RES-1",
    kind: "BOOKING" as const,
    areaId: "area-1",
    areaName: "Churrasqueira",
    unitIdentifier: "a-1203",
    residentName: "Maria Souza",
    date: "2026-10-10",
    startTime: "12:00",
    endTime: "16:00",
    status: "CONFIRMED" as const,
  },
];

describe("AreaLifecycleDialog", () => {
  it("mostra a lista de reservas afetadas e exige justificativa no 409 AREA_HAS_FUTURE_RESERVATIONS (RN-16)", async () => {
    const onSubmit = vi
      .fn()
      .mockRejectedValueOnce(
        new ApiError({
          status: 409,
          code: "AREA_HAS_FUTURE_RESERVATIONS",
          detail: "Esta área tem reservas futuras ativas. Confirme o cancelamento para continuar.",
          affectedReservations,
        }),
      )
      .mockResolvedValueOnce({ area: {}, cancelledReservations: 1 });

    const user = userEvent.setup();
    render(
      <AreaLifecycleDialog
        open
        title="Alterar status"
        description="Escolha o novo status da área."
        confirmLabel="Confirmar"
        statusOptions={["MAINTENANCE"]}
        onCancel={vi.fn()}
        onSubmit={onSubmit}
      />,
    );

    await user.click(screen.getByRole("button", { name: "Confirmar" }));

    expect(
      await screen.findByText(
        "Esta área tem reservas futuras ativas. Confirme o cancelamento para continuar.",
      ),
    ).toBeInTheDocument();
    expect(screen.getByText(/RES-1/)).toBeInTheDocument();
    expect(screen.getByText(/a-1203/)).toBeInTheDocument();

    const confirmButton = screen.getByRole("button", { name: "Confirmar cancelamento" });
    expect(confirmButton).toBeDisabled();

    await user.type(screen.getByLabelText(/justificativa/i), "curta");
    expect(confirmButton).toBeDisabled();

    await user.clear(screen.getByLabelText(/justificativa/i));
    await user.type(
      screen.getByLabelText(/justificativa/i),
      "Reforma estrutural programada pela administração.",
    );
    expect(confirmButton).toBeEnabled();

    await user.click(confirmButton);

    expect(onSubmit).toHaveBeenLastCalledWith({
      status: "MAINTENANCE",
      justification: "Reforma estrutural programada pela administração.",
      confirmCancelAffected: true,
    });
  });
});
