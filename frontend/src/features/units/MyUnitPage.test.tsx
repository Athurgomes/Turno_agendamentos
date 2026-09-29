import { render, screen, waitFor, within } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { describe, expect, it, vi, beforeEach } from "vitest";
import { MyUnitPage } from "./MyUnitPage";
import { ApiError } from "../../shared/api/client";

const useMyUnitQuery = vi.fn();
const addMutateAsync = vi.fn();
const updateMutateAsync = vi.fn();
const removeMutateAsync = vi.fn();

vi.mock("./hooks", () => ({
  useMyUnitQuery: () => useMyUnitQuery(),
  useAddMyUnitResident: () => ({ mutateAsync: addMutateAsync, isPending: false }),
  useUpdateMyUnitResident: () => ({ mutateAsync: updateMutateAsync, isPending: false }),
  useRemoveMyUnitResident: () => ({ mutateAsync: removeMutateAsync, isPending: false }),
}));

const baseUnit = {
  id: "unit-1",
  block: "A",
  number: "101",
  identifier: "A-101",
  username: "a101",
  residents: [
    {
      id: "res-1",
      name: "Maria Souza",
      phone: "5562999998888",
      email: "maria@exemplo.test",
      cpf: "***.456.789-**",
      primary: true,
    },
    {
      id: "res-2",
      name: "João Souza",
      phone: "5562988887777",
      email: null,
      cpf: null,
      primary: false,
    },
  ],
};

describe("MyUnitPage", () => {
  beforeEach(() => {
    useMyUnitQuery.mockReturnValue({ data: baseUnit, isLoading: false });
    addMutateAsync.mockReset();
    updateMutateAsync.mockReset();
    removeMutateAsync.mockReset();
  });

  it("mostra o identificador, o usuário e o selo Principal, sem CPF editável nem botão de remover o principal", () => {
    render(<MyUnitPage />);

    expect(screen.getByText("Unidade A-101")).toBeInTheDocument();
    expect(screen.getByText("Usuário: a101")).toBeInTheDocument();
    expect(screen.getByText("Principal")).toBeInTheDocument();
    expect(screen.getByText("***.456.789-**")).toBeInTheDocument();
    expect(screen.queryByLabelText(/^CPF$/)).not.toBeInTheDocument();

    const primaryCard = screen.getByText("Maria Souza").closest("li") as HTMLElement;
    expect(within(primaryCard).queryByRole("button", { name: "Remover" })).not.toBeInTheDocument();

    const additionalCard = screen.getByText("João Souza").closest("li") as HTMLElement;
    expect(within(additionalCard).getByRole("button", { name: "Remover" })).toBeInTheDocument();
  });

  it("edita nome/telefone/e-mail de um morador sem enviar CPF", async () => {
    updateMutateAsync.mockResolvedValue({});
    const user = userEvent.setup();
    render(<MyUnitPage />);

    const additionalCard = screen.getByText("João Souza").closest("li") as HTMLElement;
    await user.click(within(additionalCard).getByRole("button", { name: "Editar" }));

    const nameField = within(additionalCard).getByLabelText("Nome");
    await user.clear(nameField);
    await user.type(nameField, "João Pereira");
    await user.click(within(additionalCard).getByRole("button", { name: "Salvar" }));

    await waitFor(() =>
      expect(updateMutateAsync).toHaveBeenCalledWith({
        id: "res-2",
        payload: { name: "João Pereira", phone: "5562988887777", email: undefined },
      }),
    );
  });

  it("adiciona um morador adicional", async () => {
    addMutateAsync.mockResolvedValue({});
    const user = userEvent.setup();
    render(<MyUnitPage />);

    await user.type(screen.getByLabelText("Nome"), "Ana Lima");
    await user.type(screen.getByLabelText("Telefone (com DDI, só dígitos)"), "5562977776666");
    await user.click(screen.getByRole("button", { name: "Adicionar morador" }));

    await waitFor(() =>
      expect(addMutateAsync).toHaveBeenCalledWith({
        name: "Ana Lima",
        phone: "5562977776666",
        email: undefined,
        cpf: undefined,
      }),
    );
  });

  it("remove um adicional só após confirmação", async () => {
    removeMutateAsync.mockResolvedValue(undefined);
    const user = userEvent.setup();
    render(<MyUnitPage />);

    const additionalCard = screen.getByText("João Souza").closest("li") as HTMLElement;
    await user.click(within(additionalCard).getByRole("button", { name: "Remover" }));
    expect(removeMutateAsync).not.toHaveBeenCalled();

    const dialog = screen.getByRole("dialog");
    await user.click(within(dialog).getByRole("button", { name: "Remover" }));

    await waitFor(() => expect(removeMutateAsync).toHaveBeenCalledWith("res-2"));
  });

  it("mostra o erro do backend quando a remoção falha com CANNOT_REMOVE_PRIMARY", async () => {
    removeMutateAsync.mockRejectedValue(
      new ApiError({
        status: 422,
        code: "CANNOT_REMOVE_PRIMARY",
        detail: "O morador principal não pode ser removido por aqui.",
      }),
    );
    const user = userEvent.setup();
    render(<MyUnitPage />);

    const additionalCard = screen.getByText("João Souza").closest("li") as HTMLElement;
    await user.click(within(additionalCard).getByRole("button", { name: "Remover" }));
    const dialog = screen.getByRole("dialog");
    await user.click(within(dialog).getByRole("button", { name: "Remover" }));

    expect(
      await screen.findByText("O morador principal não pode ser removido por aqui."),
    ).toBeInTheDocument();
  });
});
