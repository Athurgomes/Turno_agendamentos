import { render, screen, waitFor, within } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { describe, expect, it, vi } from "vitest";
import { UnitForm } from "./UnitForm";
import { buildCreatePayload, emptyResident } from "./residentForm";

function renderForm(onSubmit = vi.fn().mockResolvedValue(undefined)) {
  render(
    <UnitForm
      defaultValues={{ block: "", number: "", residents: [emptyResident(true)] }}
      showUnitFields
      submitLabel="Cadastrar unidade"
      submittingLabel="Cadastrando…"
      onSubmit={onSubmit}
    />,
  );
  return { onSubmit };
}

async function fillPrimary(user: ReturnType<typeof userEvent.setup>) {
  await user.type(screen.getByLabelText("Número da unidade"), "1203");
  await user.type(screen.getByLabelText("Nome"), "Maria Souza");
  await user.type(
    screen.getByLabelText("Telefone (com DDI, só dígitos)"),
    "5562999998888",
  );
  await user.type(screen.getByLabelText("E-mail"), "maria@exemplo.test");
  await user.type(screen.getByLabelText("CPF"), "52998224725");
}

describe("UnitForm", () => {
  it("bloqueia o envio e mostra erros quando faltam dados do morador principal", async () => {
    const user = userEvent.setup();
    const { onSubmit } = renderForm();

    await user.type(screen.getByLabelText("Número da unidade"), "1203");
    await user.click(screen.getByRole("button", { name: "Cadastrar unidade" }));

    expect(await screen.findByText("Informe o nome do morador.")).toBeInTheDocument();
    expect(onSubmit).not.toHaveBeenCalled();
  });

  it("acusa CPF inválido do morador principal", async () => {
    const user = userEvent.setup();
    renderForm();

    await fillPrimary(user);
    await user.clear(screen.getByLabelText("CPF"));
    await user.type(screen.getByLabelText("CPF"), "11111111111");
    await user.click(screen.getByRole("button", { name: "Cadastrar unidade" }));

    expect(await screen.findByText("CPF inválido. Confira os dígitos.")).toBeInTheDocument();
  });

  it("adiciona e remove morador adicional dinamicamente", async () => {
    const user = userEvent.setup();
    renderForm();

    expect(screen.queryByText("Morador adicional 1")).not.toBeInTheDocument();

    await user.click(screen.getByRole("button", { name: "+ Adicionar morador" }));
    expect(screen.getByText("Morador adicional 1")).toBeInTheDocument();

    const additionalFieldset = screen.getByText("Morador adicional 1").closest("fieldset")!;
    await user.click(within(additionalFieldset).getByRole("button", { name: "Remover morador" }));
    expect(screen.queryByText("Morador adicional 1")).not.toBeInTheDocument();
  });

  it("envia valores que, montados, batem com o contrato de POST /units", async () => {
    const user = userEvent.setup();
    const { onSubmit } = renderForm();

    await user.type(screen.getByLabelText("Bloco/torre (opcional)"), "A");
    await fillPrimary(user);
    await user.click(screen.getByRole("button", { name: "Cadastrar unidade" }));

    await waitFor(() => expect(onSubmit).toHaveBeenCalledTimes(1));
    const values = onSubmit.mock.calls[0][0];
    expect(buildCreatePayload(values)).toEqual({
      block: "A",
      number: "1203",
      primary: {
        name: "Maria Souza",
        phone: "5562999998888",
        email: "maria@exemplo.test",
        cpf: "52998224725",
      },
      members: [],
    });
  });
});
