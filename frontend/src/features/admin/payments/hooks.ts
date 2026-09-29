/**
 * Hooks TanStack Query da aba Confirmações (RF-PAG-01..03) e do item de menu
 * correspondente (visibilidade e contador, RF-PAG-01).
 */
import { useQuery, useMutation, useQueryClient } from "@tanstack/react-query";
import { useSession } from "../../auth/useSession";
import { listAreas } from "../../areas/api";
import { cancelAdminReservation } from "../../reservations/api";
import * as api from "./api";

const PAYMENTS_QUERY_KEY = ["payments-pending"] as const;

export function usePendingPaymentsQuery() {
  return useQuery({
    queryKey: PAYMENTS_QUERY_KEY,
    queryFn: () => api.listPendingPayments(),
  });
}

export function useConfirmPayment() {
  const queryClient = useQueryClient();
  return useMutation({
    mutationFn: (id: string) => api.confirmPayment(id),
    onSuccess: () => {
      void queryClient.invalidateQueries({ queryKey: PAYMENTS_QUERY_KEY });
      void queryClient.invalidateQueries({ queryKey: ["agenda-reservations"] });
    },
  });
}

export function useCancelPendingPayment() {
  const queryClient = useQueryClient();
  return useMutation({
    mutationFn: ({ id, justification }: { id: string; justification: string }) =>
      cancelAdminReservation(id, justification),
    onSuccess: () => {
      void queryClient.invalidateQueries({ queryKey: PAYMENTS_QUERY_KEY });
      void queryClient.invalidateQueries({ queryKey: ["agenda-reservations"] });
    },
  });
}

/**
 * Visibilidade do item de menu "Confirmações" (RF-PAG-01): aparece para ADMIN
 * quando o catálogo tem alguma área `requiresPayment` ou já existe pendência.
 * Desabilitada para os demais perfis (nada de negócio exposto a S/U).
 */
export function useConfirmationsNavInfo() {
  const { user } = useSession();
  const isAdmin = user?.role === "ADMIN";

  const { data: areas } = useQuery({
    queryKey: ["areas", {}] as const,
    queryFn: () => listAreas({}),
    enabled: isAdmin,
  });

  const { data: pending } = useQuery({
    queryKey: PAYMENTS_QUERY_KEY,
    queryFn: () => api.listPendingPayments(),
    enabled: isAdmin,
  });

  const count = pending?.length ?? 0;
  const hasPayableArea = (areas ?? []).some((area) => area.requiresPayment);
  const visible = isAdmin && (hasPayableArea || count > 0);

  return { visible, count };
}
