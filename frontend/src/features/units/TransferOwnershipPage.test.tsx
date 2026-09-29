import { render, screen, waitFor } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { MemoryRouter, Route, Routes } from "react-router-dom";
import { describe, expect, it, vi } from "vitest";
import { TransferOwnershipPage } from "./TransferOwnershipPage";

const mutateAsync = vi.fn();

vi.mock("./hooks", () => ({
  useUnitQuery: () => ({
    data: {
      id: "unit-1",
      block: "A",
      number: "1203",
      identifier: "a-1203",
      username: "a-1203",
      active: true,
      residents: [],
    },
    isLoading: false,
  }),
  useTransferUnit: () => ({ mutateAsync, isPending: false }),
}));

function renderPage() {
  return render(
    <MemoryRouter initialEntries={["/admin/unidades/unit-1/transferir"]}>
      <Routes>
        <Route path="/admin/unidades/:id/transferir" element={<TransferOwnershipPage />} />
      </Routes>
    </MemoryRouter>,
  );
}

async function fillPrimary(user: ReturnType<typeof userEvent.setup>) {
  await user.type(screen.getByLabelText("Nome"), "Carlos Lima");
  await user.type(
    screen.getByLabelText("Telefone (com DDI, só dígitos)"),
    "5562988887777",
  );
  await user.type(screen.getByLabelText("E-mail"), "carlos@exemplo.test");
  await user.type(screen.getByLabelText("CPF"), "52998224725");
}

describe("TransferOwnershipPage", () => {
  it("depois do sucesso, mostra credenciais e lista de reservas afetadas (podendo ser vazia)", async () => {
    mutateAsync.mockResolvedValue({
      credentials: { username: "a-1203", tempPassword: "K7M4XP" },
      affectedReservations: [
        {
          id: "r1",
          code: "RES-1",
          kind: "BOOKING",
          areaId: "area-1",
          areaName: "Salão de festas",
          unitIdentifier: "a-1203",
          residentName: "Maria Souza",
          date: "2026-10-05",
          startTime: "19:00",
          endTime: "23:00",
          status: "CONFIRMED",
        },
      ],
    });

    const user = userEvent.setup();
    renderPage();

    await fillPrimary(user);
    await user.click(screen.getByRole("button", { name: "Trocar titularidade" }));

    expect(await screen.findByText("K7M4XP")).toBeInTheDocument();
    expect(mutateAsync).toHaveBeenCalledWith({
      primary: {
        name: "Carlos Lima",
        phone: "5562988887777",
        email: "carlos@exemplo.test",
        cpf: "52998224725",
      },
      members: [],
    });

    await user.click(screen.getByRole("button", { name: "Fechar" }));

    expect(
      await screen.findByText("Reservas futuras da titularidade anterior"),
    ).toBeInTheDocument();
    expect(screen.getByText(/Salão de festas/)).toBeInTheDocument();
  });

  it("mostra erro do backend sem quebrar o formulário", async () => {
    const { ApiError } = await import("../../shared/api/client");
    mutateAsync.mockRejectedValue(
      new ApiError({
        status: 422,
        code: "CPF_INVALID",
        detail: "CPF inválido. Confira os dígitos informados.",
      }),
    );

    const user = userEvent.setup();
    renderPage();
    await fillPrimary(user);
    await user.click(screen.getByRole("button", { name: "Trocar titularidade" }));

    await waitFor(() =>
      expect(
        screen.getByText("CPF inválido. Confira os dígitos informados."),
      ).toBeInTheDocument(),
    );
  });
});
