import { QueryClient, QueryClientProvider } from "@tanstack/react-query";
import { renderHook, waitFor } from "@testing-library/react";
import { afterEach, describe, expect, it, vi } from "vitest";
import { serverNow, useServerClock } from "./useServerClock";

function wrapper({ children }: { children: React.ReactNode }) {
  const queryClient = new QueryClient({
    defaultOptions: { queries: { retry: false } },
  });
  return (
    <QueryClientProvider client={queryClient}>{children}</QueryClientProvider>
  );
}

describe("serverNow", () => {
  afterEach(() => {
    vi.restoreAllMocks();
  });

  it("usa o desvio do servidor (`now` da resposta + tempo decorrido no navegador)", async () => {
    // Servidor 2 horas à frente do navegador no instante da resposta.
    vi.spyOn(globalThis, "fetch").mockResolvedValue(
      new Response(
        JSON.stringify({
          now: "1970-01-01T02:00:00.000Z",
          timezone: "America/Sao_Paulo",
          simulated: true,
        }),
        { status: 200, headers: { "Content-Type": "application/json" } },
      ),
    );
    const dateNowSpy = vi.spyOn(Date, "now").mockReturnValue(0);

    const { result } = renderHook(() => useServerClock(), { wrapper });
    await waitFor(() => expect(result.current.isSuccess).toBe(true));

    // O navegador avança 5 minutos sem uma nova resposta do servidor.
    dateNowSpy.mockReturnValue(5 * 60 * 1000);

    expect(serverNow().toISOString()).toBe("1970-01-01T02:05:00.000Z");
  });
});
