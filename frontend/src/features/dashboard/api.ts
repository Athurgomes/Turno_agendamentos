/**
 * Chamadas HTTP do dashboard (`docs/03-api.md` "Dashboard e exportação",
 * RF-DAS-01/02/03, F8-3). Camada fina sobre `apiFetch`/`apiFetchBlob`: nenhum
 * cálculo de indicador aqui, só o contrato da API.
 */
import { apiFetch, apiFetchBlob } from "../../shared/api/client";
import type {
  AreaMetricDto,
  DashboardSummaryDto,
  ExportFormat,
  ExportType,
  HeatmapCellDto,
  MonthlyReservationsDto,
  TopUnitDto,
} from "../../shared/api/types";

export interface Period {
  from: string;
  to: string;
}

function periodQuery({ from, to }: Period): string {
  return `?from=${from}&to=${to}`;
}

export function getSummary(period: Period): Promise<DashboardSummaryDto> {
  return apiFetch(`/dashboard/summary${periodQuery(period)}`);
}

/** `to` fixa a janela de 12 meses (docs/03: sem `from`, sem validação de intervalo). */
export function getReservationsByMonth(to: string): Promise<MonthlyReservationsDto[]> {
  return apiFetch(`/dashboard/reservations-by-month?to=${to}`);
}

export function getAreaMetrics(period: Period): Promise<AreaMetricDto[]> {
  return apiFetch(`/dashboard/areas${periodQuery(period)}`);
}

export function getDemandHeatmap(period: Period): Promise<HeatmapCellDto[]> {
  return apiFetch(`/dashboard/demand-heatmap${periodQuery(period)}`);
}

export function getTopUnits(period: Period): Promise<TopUnitDto[]> {
  return apiFetch(`/dashboard/top-units${periodQuery(period)}`);
}

/** `GET /exports/{type}?from&to&format` — baixa o arquivo (blob) já com o token anexado. */
export function exportFile(
  type: ExportType,
  format: ExportFormat,
  period: Period,
): Promise<{ blob: Blob; filename: string }> {
  return apiFetchBlob(
    `/exports/${type}${periodQuery(period)}&format=${format}`,
    `turno-${type}-${period.from}-a-${period.to}.${format}`,
  );
}
