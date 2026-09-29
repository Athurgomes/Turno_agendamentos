import { useState } from "react";
import { render, screen, waitFor } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { describe, expect, it, vi } from "vitest";
import { CredentialsModal } from "./CredentialsModal";

const credentials = { username: "a-1203", tempPassword: "K7M4XP" };

function setup(overrides: Partial<React.ComponentProps<typeof CredentialsModal>> = {}) {
  const onClose = vi.fn();
  const utils = render(
    <CredentialsModal
      open
      credentials={credentials}
      residentName="Maria Souza"
      residentPhone="5562999998888"
      onClose={onClose}
      {...overrides}
    />,
  );
  return { onClose, ...utils };
}

describe("CredentialsModal", () => {
  it("mostra usuário e senha temporária uma única vez, com aviso", () => {
    setup();
    expect(screen.getByText("a-1203")).toBeInTheDocument();
    expect(screen.getByText("K7M4XP")).toBeInTheDocument();
    expect(screen.getByText(/não será exibida novamente/i)).toBeInTheDocument();
  });

  it("copia usuário e senha para a área de transferência", async () => {
    const writeText = vi.fn().mockResolvedValue(undefined);
    Object.assign(navigator, { clipboard: { writeText } });
    setup();

    await userEvent.click(screen.getByRole("button", { name: "Copiar" }));

    expect(writeText).toHaveBeenCalledWith(
      "Usuário: a-1203\nSenha temporária: K7M4XP",
    );
    await waitFor(() =>
      expect(screen.getByRole("button", { name: "Copiado!" })).toBeInTheDocument(),
    );
  });

  it("monta o link do WhatsApp com o telefone do principal e a mensagem codificada", () => {
    setup();
    const link = screen.getByRole("link", { name: /whatsapp/i });
    const href = link.getAttribute("href")!;
    expect(href.startsWith("https://wa.me/5562999998888?text=")).toBe(true);

    const expectedMessage =
      "Olá, Maria Souza! Seu acesso ao sistema de reservas do condomínio: " +
      "usuário a-1203, senha temporária K7M4XP. Entre em " +
      window.location.origin +
      " e troque a senha no primeiro acesso.";
    const encodedText = href.split("?text=")[1];
    expect(decodeURIComponent(encodedText)).toBe(expectedMessage);
  });

  it("some do estado do consumidor ao fechar — a senha não fica retida em cache", () => {
    function Wrapper() {
      const [creds, setCreds] = useState<typeof credentials | null>(credentials);
      return (
        <CredentialsModal
          open={!!creds}
          credentials={creds}
          residentName="Maria Souza"
          residentPhone="5562999998888"
          onClose={() => setCreds(null)}
        />
      );
    }
    render(<Wrapper />);
    expect(screen.getByText("K7M4XP")).toBeInTheDocument();
    return userEvent.click(screen.getByRole("button", { name: "Fechar" })).then(() => {
      expect(screen.queryByText("K7M4XP")).not.toBeInTheDocument();
    });
  });
});
