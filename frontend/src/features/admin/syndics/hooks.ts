/** Hooks TanStack Query sobre `./api.ts` — tela ADMIN de `/admin/sindicos`. */
import { useMutation, useQuery, useQueryClient } from "@tanstack/react-query";
import * as api from "./api";
import type { CreateSyndicPayload } from "./api";

export function useSyndicsQuery() {
  return useQuery({
    queryKey: ["syndics"],
    queryFn: () => api.listSyndics(),
  });
}

export function useCreateSyndic() {
  const queryClient = useQueryClient();
  return useMutation({
    mutationFn: (payload: CreateSyndicPayload) => api.createSyndic(payload),
    onSuccess: () => {
      void queryClient.invalidateQueries({ queryKey: ["syndics"] });
    },
  });
}

export function useDeactivateSyndic() {
  const queryClient = useQueryClient();
  return useMutation({
    mutationFn: (id: string) => api.deactivateSyndic(id),
    onSuccess: () => {
      void queryClient.invalidateQueries({ queryKey: ["syndics"] });
    },
  });
}
