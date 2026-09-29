/**
 * Hooks TanStack Query sobre `./api.ts` — "Meus reports" (UNIT), caixa de
 * reports (S/A) e o contador do item de menu "Reports" (RF-REP-02).
 */
import { useMutation, useQuery, useQueryClient } from "@tanstack/react-query";
import { useSession } from "../auth/useSession";
import * as api from "./api";
import type {
  AddReportCommentPayload,
  AdminReportFilters,
  CreateReportPayload,
  UpdateReportStatusPayload,
} from "./api";

const MY_REPORTS_KEY = ["me-reports"] as const;
const ADMIN_REPORTS_KEY = ["admin-reports"] as const;
const REPORTS_SUMMARY_KEY = ["reports-summary"] as const;

export function useMyReportsQuery() {
  return useQuery({
    queryKey: MY_REPORTS_KEY,
    queryFn: () => api.listMyReports(),
  });
}

export function useCreateReport() {
  const queryClient = useQueryClient();
  return useMutation({
    mutationFn: ({
      reservationId,
      payload,
      photos,
    }: {
      reservationId: string;
      payload: CreateReportPayload;
      photos: File[];
    }) => api.createReport(reservationId, payload, photos),
    onSuccess: () => {
      void queryClient.invalidateQueries({ queryKey: MY_REPORTS_KEY });
      void queryClient.invalidateQueries({ queryKey: ["me-reservations"] });
    },
  });
}

export function useAdminReportsQuery(filters: AdminReportFilters) {
  return useQuery({
    queryKey: [...ADMIN_REPORTS_KEY, filters] as const,
    queryFn: () => api.listAdminReports(filters),
  });
}

export function useAdminReportQuery(id: string | undefined) {
  return useQuery({
    queryKey: [...ADMIN_REPORTS_KEY, "detail", id] as const,
    queryFn: () => api.getReport(id as string),
    enabled: !!id,
  });
}

export function useUpdateReportStatus() {
  const queryClient = useQueryClient();
  return useMutation({
    mutationFn: ({ id, payload }: { id: string; payload: UpdateReportStatusPayload }) =>
      api.updateReportStatus(id, payload),
    onSuccess: () => {
      void queryClient.invalidateQueries({ queryKey: ADMIN_REPORTS_KEY });
      void queryClient.invalidateQueries({ queryKey: REPORTS_SUMMARY_KEY });
    },
  });
}

export function useAddReportComment() {
  const queryClient = useQueryClient();
  return useMutation({
    mutationFn: ({ id, payload }: { id: string; payload: AddReportCommentPayload }) =>
      api.addReportComment(id, payload),
    onSuccess: () => {
      void queryClient.invalidateQueries({ queryKey: ADMIN_REPORTS_KEY });
    },
  });
}

export function useUploadReportPhotos() {
  const queryClient = useQueryClient();
  return useMutation({
    mutationFn: ({ id, photos }: { id: string; photos: File[] }) => api.uploadReportPhotos(id, photos),
    onSuccess: () => {
      void queryClient.invalidateQueries({ queryKey: ADMIN_REPORTS_KEY });
    },
  });
}

/**
 * Contador do item de menu "Reports" (RF-REP-02): só dispara a consulta para
 * SYNDIC/ADMIN — a conta UNIT nunca chama `/reports/summary`.
 */
export function useReportsNavInfo() {
  const { user } = useSession();
  const isStaff = user?.role === "SYNDIC" || user?.role === "ADMIN";

  const { data } = useQuery({
    queryKey: REPORTS_SUMMARY_KEY,
    queryFn: () => api.getReportsSummary(),
    enabled: isStaff,
  });

  return { count: data?.open ?? 0 };
}
