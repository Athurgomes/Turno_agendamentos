/**
 * Bug 3 (docs/12-ensaio-roteiro.md): o AppShell (e com ele
 * `useConfirmationsNavInfo`) remonta a cada login/logout. Sem `staleTime`,
 * cada remontagem refazia `GET /areas` mesmo com o catálogo já em cache,
 * competindo por conexão com as chamadas concorrentes de `/painel` logo
 * após o login e atrasando o item "Confirmações" na barra lateral (reproduz
 * só sob contenção real de rede, por isso o teste de unidade aqui trava o
 * sintoma acessível em Vitest: número de chamadas de rede por remontagem,
 * não o tempo de espera em si).
 */
import { render, renderHook, waitFor } from "@testing-library/react";
import { QueryClient, QueryClientProvider } from "@tanstack/react-query";
import { describe, expect, it, vi } from "vitest";
import type { ReactNode } from "react";
import { useConfirmationsNavInfo } from "./hooks";
import { SessionContext, type SessionContextValue } from "../../auth/sessionContext";
import type { AreaSummary } from "../../../shared/api/types";

const listAreas = vi.fn();
const listPendingPayments = vi.fn();

vi.mock("../../areas/api", () => ({
  listAreas: (...args: unknown[]) => listAreas(...args),
}));

vi.mock("./api", () => ({
  listPendingPayments: () => listPendingPayments(),
}));

function payableArea(): AreaSummary {
  return {
    id: "area-1",
    name: "Salão de festas",
    category: "PARTY_ROOM",
    status: "ACTIVE",
    capacity: 50,
    requiresPayment: true,
    price: 150,
    coverPhotoUrl: null,
  };
}

function adminSession(): SessionContextValue {
  return {
    accessToken: "token",
    user: { id: "1", role: "ADMIN", name: "Administração", unitId: null, unitIdentifier: null, tempPassword: false },
    isLoading: false,
    setSession: vi.fn(),
    logout: vi.fn(),
  } as unknown as SessionContextValue;
}

describe("useConfirmationsNavInfo (Bug 3)", () => {
  it("fica visível assim que GET /areas resolve, mesmo com GET /payments/pending nunca resolvendo", async () => {
    listAreas.mockResolvedValue([payableArea()]);
    listPendingPayments.mockReturnValue(new Promise(() => {})); // nunca resolve nesta rodada

    const queryClient = new QueryClient({ defaultOptions: { queries: { retry: false } } });
    function wrapper({ children }: { children: ReactNode }) {
      return (
        <SessionContext.Provider value={adminSession()}>
          <QueryClientProvider client={queryClient}>{children}</QueryClientProvider>
        </SessionContext.Provider>
      );
    }

    const { result } = renderHook(() => useConfirmationsNavInfo(), { wrapper });

    await waitFor(() => expect(result.current.visible).toBe(true), { timeout: 2000 });
    expect(result.current.count).toBe(0);
  });

  it("não refaz GET /areas a cada login (remontagem do AppShell) dentro da janela de cache", async () => {
    listAreas.mockResolvedValue([payableArea()]);
    listPendingPayments.mockResolvedValue([]);

    const queryClient = new QueryClient({ defaultOptions: { queries: { retry: false } } });
    function Wrapped() {
      return (
        <SessionContext.Provider value={adminSession()}>
          <QueryClientProvider client={queryClient}>
            <Probe />
          </QueryClientProvider>
        </SessionContext.Provider>
      );
    }
    function Probe() {
      const { visible } = useConfirmationsNavInfo();
      return <span data-testid="visible">{String(visible)}</span>;
    }

    const first = render(<Wrapped />);
    await waitFor(() => expect(listAreas).toHaveBeenCalledTimes(1));
    first.unmount(); // logout: AppShell some da árvore, cache do QueryClient sobrevive

    render(<Wrapped />); // login de novo: AppShell remonta

    // Dá tempo de um possível refetch acontecer antes de afirmar que não aconteceu.
    await new Promise((resolve) => setTimeout(resolve, 50));

    expect(listAreas).toHaveBeenCalledTimes(1);
  });
});
