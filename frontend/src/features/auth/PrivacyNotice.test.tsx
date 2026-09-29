import { beforeEach, describe, expect, it, vi } from "vitest";
import { render, screen } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { PrivacyNotice } from "./PrivacyNotice";

const sessionMock = vi.hoisted(() => ({
  user: {
    id: "42",
    role: "UNIT",
    name: "Fernanda Reis",
    unitId: "u1",
    unitIdentifier: "a-1203",
    tempPassword: false,
  },
}));

vi.mock("./useSession", () => ({ useSession: () => sessionMock }));

describe("PrivacyNotice", () => {
  beforeEach(() => {
    localStorage.clear();
  });

  it("aparece no primeiro acesso da conta", () => {
    render(<PrivacyNotice />);

    expect(
      screen.getByText(/síndico e à administração do condomínio/i),
    ).toBeInTheDocument();
  });

  it("some ao clicar em Entendi e grava a confirmação por conta no navegador", async () => {
    const user = userEvent.setup();
    render(<PrivacyNotice />);

    await user.click(screen.getByRole("button", { name: /entendi/i }));

    expect(
      screen.queryByText(/síndico e à administração do condomínio/i),
    ).not.toBeInTheDocument();
    expect(localStorage.getItem("privacy-notice-ack:42")).toBe("1");
  });

  it("não reaparece em uma nova sessão da mesma conta", () => {
    localStorage.setItem("privacy-notice-ack:42", "1");
    render(<PrivacyNotice />);

    expect(
      screen.queryByText(/síndico e à administração do condomínio/i),
    ).not.toBeInTheDocument();
  });
});
