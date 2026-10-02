/** Hook TanStack Query sobre `./api.ts` — página inicial do síndico (F7-1). */
import { useQuery } from "@tanstack/react-query";
import * as api from "./api";

export function useDashboardHomeQuery() {
  return useQuery({
    queryKey: ["dashboard-home"] as const,
    queryFn: () => api.getDashboardHome(),
  });
}
