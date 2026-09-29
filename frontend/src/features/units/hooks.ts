/** Hooks TanStack Query sobre `./api.ts` — telas ADMIN de `/admin/unidades`. */
import { useMutation, useQuery, useQueryClient } from "@tanstack/react-query";
import * as api from "./api";
import type { ListUnitsParams } from "./api";
import type {
  AddMyResidentPayload,
  CreateUnitPayload,
  EditMyResidentPayload,
  EditUnitPayload,
  PrimaryAndMembersPayload,
} from "./residentForm";

export const unitsQueryKey = (params: ListUnitsParams) => ["units", params] as const;

export function useUnitsQuery(params: ListUnitsParams) {
  return useQuery({
    queryKey: unitsQueryKey(params),
    queryFn: () => api.listUnits(params),
  });
}

export function useUnitQuery(id: string | undefined) {
  return useQuery({
    queryKey: ["units", "detail", id] as const,
    queryFn: () => api.getUnit(id as string),
    enabled: !!id,
  });
}

export function useCreateUnit() {
  const queryClient = useQueryClient();
  return useMutation({
    mutationFn: (payload: CreateUnitPayload) => api.createUnit(payload),
    onSuccess: () => {
      void queryClient.invalidateQueries({ queryKey: ["units"] });
    },
  });
}

export function useUpdateUnit(id: string) {
  const queryClient = useQueryClient();
  return useMutation({
    mutationFn: (payload: EditUnitPayload) => api.updateUnit(id, payload),
    onSuccess: () => {
      void queryClient.invalidateQueries({ queryKey: ["units"] });
    },
  });
}

export function useDeactivateUnit() {
  const queryClient = useQueryClient();
  return useMutation({
    mutationFn: (id: string) => api.deactivateUnit(id),
    onSuccess: () => {
      void queryClient.invalidateQueries({ queryKey: ["units"] });
    },
  });
}

export function useResetUnitPassword() {
  return useMutation({
    mutationFn: (id: string) => api.resetUnitPassword(id),
  });
}

export function useTransferUnit(id: string) {
  const queryClient = useQueryClient();
  return useMutation({
    mutationFn: (payload: PrimaryAndMembersPayload) => api.transferUnit(id, payload),
    onSuccess: () => {
      void queryClient.invalidateQueries({ queryKey: ["units"] });
    },
  });
}

const myUnitQueryKey = ["myUnit"] as const;

export function useMyUnitQuery() {
  return useQuery({
    queryKey: myUnitQueryKey,
    queryFn: () => api.getMyUnit(),
  });
}

export function useAddMyUnitResident() {
  const queryClient = useQueryClient();
  return useMutation({
    mutationFn: (payload: AddMyResidentPayload) => api.addMyUnitResident(payload),
    onSuccess: () => {
      void queryClient.invalidateQueries({ queryKey: myUnitQueryKey });
    },
  });
}

export function useUpdateMyUnitResident() {
  const queryClient = useQueryClient();
  return useMutation({
    mutationFn: ({ id, payload }: { id: string; payload: EditMyResidentPayload }) =>
      api.updateMyUnitResident(id, payload),
    onSuccess: () => {
      void queryClient.invalidateQueries({ queryKey: myUnitQueryKey });
    },
  });
}

export function useRemoveMyUnitResident() {
  const queryClient = useQueryClient();
  return useMutation({
    mutationFn: (id: string) => api.removeMyUnitResident(id),
    onSuccess: () => {
      void queryClient.invalidateQueries({ queryKey: myUnitQueryKey });
    },
  });
}
