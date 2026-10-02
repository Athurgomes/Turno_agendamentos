/** Hooks TanStack Query sobre `./api.ts` — dashboard (F8-3). Uma query por endpoint. */
import { useMutation, useQuery } from "@tanstack/react-query";
import * as api from "./api";
import type { Period } from "./api";
import type { ExportFormat, ExportType } from "../../shared/api/types";

const DASHBOARD_KEY = "dashboard";

export function useSummaryQuery(period: Period | null) {
  return useQuery({
    queryKey: [DASHBOARD_KEY, "summary", period] as const,
    queryFn: () => api.getSummary(period as Period),
    enabled: !!period,
  });
}

export function useReservationsByMonthQuery(to: string | null) {
  return useQuery({
    queryKey: [DASHBOARD_KEY, "reservations-by-month", to] as const,
    queryFn: () => api.getReservationsByMonth(to as string),
    enabled: !!to,
  });
}

export function useAreaMetricsQuery(period: Period | null) {
  return useQuery({
    queryKey: [DASHBOARD_KEY, "areas", period] as const,
    queryFn: () => api.getAreaMetrics(period as Period),
    enabled: !!period,
  });
}

export function useDemandHeatmapQuery(period: Period | null) {
  return useQuery({
    queryKey: [DASHBOARD_KEY, "demand-heatmap", period] as const,
    queryFn: () => api.getDemandHeatmap(period as Period),
    enabled: !!period,
  });
}

export function useTopUnitsQuery(period: Period | null) {
  return useQuery({
    queryKey: [DASHBOARD_KEY, "top-units", period] as const,
    queryFn: () => api.getTopUnits(period as Period),
    enabled: !!period,
  });
}

/** Baixa o arquivo e dispara o "Salvar como" do navegador a partir do blob (token já foi anexado no fetch). */
export function useExportMutation() {
  return useMutation({
    mutationFn: ({ type, format, period }: { type: ExportType; format: ExportFormat; period: Period }) =>
      api.exportFile(type, format, period),
    onSuccess: ({ blob, filename }) => {
      const url = URL.createObjectURL(blob);
      const link = document.createElement("a");
      link.href = url;
      link.download = filename;
      document.body.appendChild(link);
      link.click();
      link.remove();
      URL.revokeObjectURL(url);
    },
  });
}
