import { render, screen, waitFor, within } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { describe, expect, it, vi, beforeEach } from "vitest";
import { SyndicsPage } from "./SyndicsPage";
import { ApiError } from "../../../shared/api/client";

const useSyndicsQuery = vi.fn();
const createMutateAsync = vi.fn();
const deactivateMutateAsync = vi.fn();

vi.mock("./hooks", () => ({
  useSyndicsQuery: () => useSyndicsQuery(),
  useCreateSyndic: () => ({ mutateAsync: createMutateAsync, isPending: false }),
  useDeactivateSyndic: () => ({ mutateAsync: deactivateMutateAsync, isPending: false }),
}));

describe("SyndicsPage", () => {
  beforeEach(() => {
    useSyndicsQuery.mockReturnValue({
      data: [
        { id: "syn-1", name: "Ana Ribeiro", email: "ana@exemplo.test", phone: "5562988887777", active: true },
      ],
      isLoading: false,
    });
    createMutateAsync.mockReset();
    deactivateMutateAsync.mockReset();
  });

  it("cadastra síndico e mostra o modal de credenciais com usuário = e-mail", async () => {
    createMutateAsync.mockResolvedValue({
      syndic: { id: "syn-2", name: "Bruno Alves", email: "bruno@exemplo.test", phone: "5562977776666", active: true },
      credentials: { username: "bruno@exemplo.test", tempPassword: "K7M4XP" },
    });
    const user = userEvent.setup();
    render(<SyndicsPage />);

    await user.type(screen.getByLabelText("Nome"), "Bruno Alves");
    await user.type(screen.getByLabelText("E-mail (será o usuário)"), "bruno@exemplo.test");
    await user.type(
      screen.getByLabelText("Telefone (com DDI, só dígitos)"),
      "5562977776666",
    );
    await user.click(screen.getByRole("button", { name: "Cadastrar síndico" }));

    expect(await screen.findByText("bruno@exemplo.test")).toBeInTheDocument();
    expect(screen.getByText("K7M4XP")).toBeInTheDocument();
    expect(createMutateAsync).toHaveBeenCalledWith({
      name: "Bruno Alves",
      email: "bruno@exemplo.test",
      phone: "5562977776666",
    });
  });

  it("mostra EMAIL_TAKEN sem quebrar o formulário", async () => {
    createMutateAsync.mockRejectedValue(
      new ApiError({
        status: 409,
        code: "EMAIL_TAKEN",
        detail: "Este e-mail já está em uso por outra conta.",
      }),
    );
    const user = userEvent.setup();
    render(<SyndicsPage />);

    await user.type(screen.getByLabelText("Nome"), "Bruno Alves");
    await user.type(screen.getByLabelText("E-mail (será o usuário)"), "ana@exemplo.test");
    await user.type(
      screen.getByLabelText("Telefone (com DDI, só dígitos)"),
      "5562977776666",
    );
    await user.click(screen.getByRole("button", { name: "Cadastrar síndico" }));

    expect(
      await screen.findByText("Este e-mail já está em uso por outra conta."),
    ).toBeInTheDocument();
  });

  it("desativar pede confirmação antes de chamar a API", async () => {
    deactivateMutateAsync.mockResolvedValue(undefined);
    const user = userEvent.setup();
    render(<SyndicsPage />);

    await user.click(screen.getByRole("button", { name: "Desativar" }));
    expect(deactivateMutateAsync).not.toHaveBeenCalled();

    const dialog = screen.getByRole("dialog");
    await user.click(within(dialog).getByRole("button", { name: "Desativar" }));

    await waitFor(() => expect(deactivateMutateAsync).toHaveBeenCalledWith("syn-1"));
  });
});
