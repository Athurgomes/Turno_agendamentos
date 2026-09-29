import { fireEvent, render, screen, waitFor } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { describe, expect, it, vi } from "vitest";
import { AreaForm } from "./AreaForm";
import { defaultSchedule, type AreaFormValues } from "./areaFormSchema";
import type { AreaCategoryTemplateDto } from "../../shared/api/types";

const categories: AreaCategoryTemplateDto[] = [
  {
    code: "BARBECUE",
    label: "Churrasqueira",
    rulesTemplate: "Não usar após 22h.",
    conductTemplate: "Deixe o espaço limpo ao sair.",
  },
  {
    code: "POOL",
    label: "Piscina",
    rulesTemplate: "Obrigatório o uso de touca.",
    conductTemplate: "Não é permitido levar vidro.",
  },
];

function emptyValues(): AreaFormValues {
  return {
    name: "",
    category: "",
    description: "",
    rules: "",
    conductGuidelines: "",
    capacity: 1,
    requiresPayment: false,
    price: "",
    paymentWhatsapp: "",
    schedule: defaultSchedule(),
  };
}

function renderForm(overrides: Partial<React.ComponentProps<typeof AreaForm>> = {}) {
  const onSubmit = vi.fn().mockResolvedValue(undefined);
  render(
    <AreaForm
      categories={categories}
      defaultValues={emptyValues()}
      showPhotos
      submitLabel="Cadastrar área"
      submittingLabel="Cadastrando…"
      onSubmit={onSubmit}
      {...overrides}
    />,
  );
  return { onSubmit };
}

describe("AreaForm", () => {
  it("pré-preenche regras e conduta com o template ao escolher a categoria (RF-ARE-03)", async () => {
    const user = userEvent.setup();
    renderForm();

    await user.selectOptions(screen.getByLabelText("Categoria"), "BARBECUE");

    expect(screen.getByLabelText("Regras de uso")).toHaveValue("Não usar após 22h.");
    expect(screen.getByLabelText("Sugestões de conduta")).toHaveValue(
      "Deixe o espaço limpo ao sair.",
    );
  });

  it("pede confirmação antes de substituir regras já editadas manualmente", async () => {
    const user = userEvent.setup();
    renderForm();

    await user.selectOptions(screen.getByLabelText("Categoria"), "BARBECUE");
    await user.clear(screen.getByLabelText("Regras de uso"));
    await user.type(screen.getByLabelText("Regras de uso"), "Regra personalizada.");

    await user.selectOptions(screen.getByLabelText("Categoria"), "POOL");

    expect(screen.getByRole("dialog")).toBeInTheDocument();
    expect(screen.getByLabelText("Regras de uso")).toHaveValue("Regra personalizada.");

    await user.click(screen.getByRole("button", { name: "Substituir" }));
    expect(screen.getByLabelText("Regras de uso")).toHaveValue("Obrigatório o uso de touca.");
  });

  it("exige valor e WhatsApp quando 'exige pagamento' está marcado (RN-12)", async () => {
    const user = userEvent.setup();
    const { onSubmit } = renderForm();

    await user.type(screen.getByLabelText("Nome da área"), "Salão de festas");
    await user.selectOptions(screen.getByLabelText("Categoria"), "BARBECUE");
    await user.type(screen.getByLabelText("Descrição"), "Um salão amplo.");
    await user.clear(screen.getByLabelText("Capacidade máxima (pessoas)"));
    await user.type(screen.getByLabelText("Capacidade máxima (pessoas)"), "50");
    await user.click(screen.getByLabelText("Segunda-feira"));
    await user.click(screen.getByLabelText("Exige pagamento"));

    await user.click(screen.getByRole("button", { name: "Cadastrar área" }));

    expect(await screen.findByText("Informe um valor maior que zero.")).toBeInTheDocument();
    expect(screen.getByText("Informe o WhatsApp da administração.")).toBeInTheDocument();
    expect(onSubmit).not.toHaveBeenCalled();
  });

  it("rejeita arquivo de tipo inválido no upload de fotos", async () => {
    renderForm();

    const file = new File(["conteudo"], "documento.pdf", { type: "application/pdf" });
    const input = screen.getByLabelText("Adicionar fotos");
    fireEvent.change(input, { target: { files: [file] } });

    expect(
      await screen.findByText("Formato não aceito. Envie uma foto em JPG, PNG ou WEBP."),
    ).toBeInTheDocument();
  });

  it("rejeita arquivo maior que 5 MB no upload de fotos", async () => {
    renderForm();

    const bigContent = new Uint8Array(5 * 1024 * 1024 + 1);
    const file = new File([bigContent], "foto.jpg", { type: "image/jpeg" });
    const input = screen.getByLabelText("Adicionar fotos");
    fireEvent.change(input, { target: { files: [file] } });

    expect(
      await screen.findByText("Arquivo maior que 5 MB. Escolha uma foto menor."),
    ).toBeInTheDocument();
  });

  it("bloqueia o envio do cadastro sem nenhuma foto", async () => {
    const user = userEvent.setup();
    const { onSubmit } = renderForm();

    await user.type(screen.getByLabelText("Nome da área"), "Quadra");
    await user.selectOptions(screen.getByLabelText("Categoria"), "BARBECUE");
    await user.type(screen.getByLabelText("Descrição"), "Descrição válida.");
    await user.click(screen.getByLabelText("Segunda-feira"));

    await user.click(screen.getByRole("button", { name: "Cadastrar área" }));

    await waitFor(() => {
      expect(screen.getByText("Adicione ao menos uma foto da área.")).toBeInTheDocument();
    });
    expect(onSubmit).not.toHaveBeenCalled();
  });
});
