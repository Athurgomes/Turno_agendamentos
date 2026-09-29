import { render, screen } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { describe, expect, it, vi } from "vitest";
import { SyndicAreaEditForm } from "./SyndicAreaEditForm";

describe("SyndicAreaEditForm", () => {
  it("mostra só descrição, regras, conduta e capacidade (D-20)", () => {
    render(
      <SyndicAreaEditForm
        defaultValues={{
          description: "Descrição atual.",
          rules: "Regras atuais.",
          conductGuidelines: "Conduta atual.",
          capacity: 20,
        }}
        onSubmit={vi.fn()}
      />,
    );

    expect(screen.getByLabelText("Descrição")).toBeInTheDocument();
    expect(screen.getByLabelText("Regras de uso")).toBeInTheDocument();
    expect(screen.getByLabelText("Sugestões de conduta")).toBeInTheDocument();
    expect(screen.getByLabelText("Capacidade máxima (pessoas)")).toBeInTheDocument();

    expect(screen.queryByLabelText(/categoria/i)).not.toBeInTheDocument();
    expect(screen.queryByLabelText(/exige pagamento/i)).not.toBeInTheDocument();
    expect(screen.queryByLabelText(/whatsapp/i)).not.toBeInTheDocument();
    expect(screen.queryByText(/horário de funcionamento/i)).not.toBeInTheDocument();
    expect(screen.queryByLabelText(/status/i)).not.toBeInTheDocument();
  });

  it("envia só os campos permitidos ao salvar", async () => {
    const onSubmit = vi.fn().mockResolvedValue(undefined);
    const user = userEvent.setup();
    render(
      <SyndicAreaEditForm
        defaultValues={{
          description: "Descrição atual.",
          rules: "Regras atuais.",
          conductGuidelines: "Conduta atual.",
          capacity: 20,
        }}
        onSubmit={onSubmit}
      />,
    );

    await user.clear(screen.getByLabelText("Descrição"));
    await user.type(screen.getByLabelText("Descrição"), "Nova descrição.");
    await user.click(screen.getByRole("button", { name: "Salvar alterações" }));

    expect(onSubmit.mock.calls[0][0]).toEqual({
      description: "Nova descrição.",
      rules: "Regras atuais.",
      conductGuidelines: "Conduta atual.",
      capacity: 20,
    });
  });
});
