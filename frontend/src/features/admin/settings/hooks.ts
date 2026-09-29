/** Hooks TanStack Query sobre `./api.ts` — tela ADMIN de `/admin/configuracoes`. */
import { useMutation, useQuery, useQueryClient } from "@tanstack/react-query";
import * as api from "./api";
import type { SettingsDto } from "../../../shared/api/types";

const settingsQueryKey = ["settings"] as const;

export function useSettingsQuery() {
  return useQuery({
    queryKey: settingsQueryKey,
    queryFn: () => api.getSettings(),
  });
}

export function useUpdateSettings() {
  const queryClient = useQueryClient();
  return useMutation({
    mutationFn: (payload: SettingsDto) => api.updateSettings(payload),
    onSuccess: (data) => {
      queryClient.setQueryData(settingsQueryKey, data);
    },
  });
}
