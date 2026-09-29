/**
 * Hooks TanStack Query sobre `./api.ts` — telas de reserva do morador
 * (`/areas/:id/reservar`, `/minhas-reservas`) e agenda de S/A (`/agenda`).
 */
import { useMutation, useQuery, useQueryClient } from "@tanstack/react-query";
import * as api from "./api";
import type {
  AdminReservationFilters,
  CreateBlockPayload,
  CreateReservationPayload,
  ReservationScope,
  UpdateReservationPayload,
} from "./api";

export function useAreaAvailabilityQuery(areaId: string | undefined, from: string, to: string) {
  return useQuery({
    queryKey: ["areas", areaId, "availability", from, to] as const,
    queryFn: () => api.getAreaAvailability(areaId as string, from, to),
    enabled: !!areaId,
  });
}

export function usePublicSettingsQuery() {
  return useQuery({
    queryKey: ["settings-public"] as const,
    queryFn: () => api.getPublicSettings(),
    staleTime: 5 * 60 * 1000,
  });
}

export function useCreateReservation() {
  const queryClient = useQueryClient();
  return useMutation({
    mutationFn: (payload: CreateReservationPayload) => api.createReservation(payload),
    onSuccess: () => {
      void queryClient.invalidateQueries({ queryKey: ["me-reservations"] });
    },
  });
}

export function useMyReservationsQuery(scope: ReservationScope) {
  return useQuery({
    queryKey: ["me-reservations", scope] as const,
    queryFn: () => api.listMyReservations(scope),
  });
}

export function useCancelMyReservation() {
  const queryClient = useQueryClient();
  return useMutation({
    mutationFn: (id: string) => api.cancelMyReservation(id),
    onSuccess: () => {
      void queryClient.invalidateQueries({ queryKey: ["me-reservations"] });
    },
  });
}

const AGENDA_QUERY_KEY = ["agenda-reservations"] as const;

export function useAdminReservationsQuery(filters: AdminReservationFilters) {
  return useQuery({
    queryKey: [...AGENDA_QUERY_KEY, filters] as const,
    queryFn: () => api.listAdminReservations(filters),
  });
}

export function useAdminReservationQuery(id: string | undefined) {
  return useQuery({
    queryKey: [...AGENDA_QUERY_KEY, "detail", id] as const,
    queryFn: () => api.getAdminReservation(id as string),
    enabled: !!id,
  });
}

/** Contexto de uma reserva da própria unidade (RF-REP-01) — não usar para agenda de S/A. */
export function useReservationQuery(id: string | undefined) {
  return useQuery({
    queryKey: ["me-reservations", "detail", id] as const,
    queryFn: () => api.getReservation(id as string),
    enabled: !!id,
  });
}

export function useReservationEventsQuery(id: string | undefined) {
  return useQuery({
    queryKey: [...AGENDA_QUERY_KEY, "events", id] as const,
    queryFn: () => api.getReservationEvents(id as string),
    enabled: !!id,
  });
}

export function useUpdateReservation() {
  const queryClient = useQueryClient();
  return useMutation({
    mutationFn: ({ id, payload }: { id: string; payload: UpdateReservationPayload }) =>
      api.updateReservation(id, payload),
    onSuccess: () => {
      void queryClient.invalidateQueries({ queryKey: AGENDA_QUERY_KEY });
    },
  });
}

export function useCancelAdminReservation() {
  const queryClient = useQueryClient();
  return useMutation({
    mutationFn: ({ id, justification }: { id: string; justification: string }) =>
      api.cancelAdminReservation(id, justification),
    onSuccess: () => {
      void queryClient.invalidateQueries({ queryKey: AGENDA_QUERY_KEY });
    },
  });
}

export function useCreateBlock() {
  const queryClient = useQueryClient();
  return useMutation({
    mutationFn: (payload: CreateBlockPayload) => api.createBlock(payload),
    onSuccess: () => {
      void queryClient.invalidateQueries({ queryKey: AGENDA_QUERY_KEY });
    },
  });
}

export function useDeleteBlock() {
  const queryClient = useQueryClient();
  return useMutation({
    mutationFn: (id: string) => api.deleteBlock(id),
    onSuccess: () => {
      void queryClient.invalidateQueries({ queryKey: AGENDA_QUERY_KEY });
    },
  });
}
