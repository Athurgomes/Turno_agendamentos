import { afterEach, describe, expect, it, vi } from "vitest";
import { render, screen } from "@testing-library/react";
import App from "./App";

describe("App", () => {
  afterEach(() => {
    vi.restoreAllMocks();
  });

  it("sem sessão restaurada, manda o visitante para /login", async () => {
    vi.spyOn(globalThis, "fetch").mockRejectedValue(new Error("sem rede"));

    const { container } = render(<App />);

    expect(
      await screen.findByRole("heading", { name: /turno/i }),
    ).toBeInTheDocument();
    expect(container.querySelector("svg")).toBeInTheDocument();
  });
});
