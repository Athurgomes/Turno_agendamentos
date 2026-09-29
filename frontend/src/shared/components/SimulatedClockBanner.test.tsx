import { QueryClient, QueryClientProvider } from "@tanstack/react-query";
import { render, screen, waitFor } from "@testing-library/react";
import { afterEach, describe, expect, it, vi } from "vitest";
import { SimulatedClockBanner } from "./SimulatedClockBanner";

function renderBanner() {
  const queryClient = new QueryClient({
    defaultOptions: { queries: { retry: false } },
  });
  return render(
    <QueryClientProvider client={queryClient}>
      <SimulatedClockBanner />
    </QueryClientProvider>,
  );
}

function mockClock(body: unknown) {
  vi.spyOn(globalThis, "fetch").mockResolvedValue(
    new Response(JSON.stringify(body), {
      status: 200,
      headers: { "Content-Type": "application/json" },
    }),
  );
}

describe("SimulatedClockBanner", () => {
  afterEach(() => {
    vi.restoreAllMocks();
  });

  it("D-32: mostra a faixa formatada no fuso do condomínio quando simulated=true", async () => {
    mockClock({
      now: "2026-11-10T13:00:00Z",
      timezone: "America/Sao_Paulo",
      simulated: true,
    });

    renderBanner();

    expect(
      await screen.findByText("Horário simulado: 10/11/2026 10:00"),
    ).toBeInTheDocument();
  });

  it("D-32: não mostra nada quando simulated=false", async () => {
    mockClock({
      now: "2026-11-10T13:00:00Z",
      timezone: "America/Sao_Paulo",
      simulated: false,
    });

    renderBanner();

    await waitFor(() => expect(globalThis.fetch).toHaveBeenCalled());
    expect(screen.queryByText(/horário simulado/i)).not.toBeInTheDocument();
  });
});
