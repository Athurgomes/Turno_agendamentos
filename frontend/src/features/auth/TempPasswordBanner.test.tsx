import { describe, expect, it, vi } from "vitest";
import { render, screen } from "@testing-library/react";
import { MemoryRouter } from "react-router-dom";
import { TempPasswordBanner } from "./TempPasswordBanner";

const sessionMock = vi.hoisted(() => ({
  user: {
    id: "1",
    role: "UNIT",
    name: "Fernanda Reis",
    unitId: "u1",
    unitIdentifier: "a-1203",
    tempPassword: true,
  },
}));

vi.mock("./useSession", () => ({ useSession: () => sessionMock }));

describe("TempPasswordBanner", () => {
  it("aparece quando a conta está com senha temporária", () => {
    sessionMock.user.tempPassword = true;
    render(
      <MemoryRouter>
        <TempPasswordBanner />
      </MemoryRouter>,
    );

    expect(screen.getByText(/senha temporária/i)).toBeInTheDocument();
    expect(
      screen.getByRole("link", { name: /trocar senha/i }),
    ).toBeInTheDocument();
  });

  it("não aparece quando a conta já trocou a senha temporária", () => {
    sessionMock.user.tempPassword = false;
    render(
      <MemoryRouter>
        <TempPasswordBanner />
      </MemoryRouter>,
    );

    expect(screen.queryByText(/senha temporária/i)).not.toBeInTheDocument();
  });
});
