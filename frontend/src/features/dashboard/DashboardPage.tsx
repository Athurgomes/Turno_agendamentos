/**
 * Dashboard de ADMIN/SÍNDICO (`/dashboard`, RF-DAS-01/02/03, RF-SIN-06, F8-3):
 * cards de indicador, série de 12 meses, métricas por área, mapa de calor de
 * demanda, reports por categoria, top 10 unidades e exportação. Uma consulta
 * por endpoint (TanStack Query); nenhum cálculo de indicador aqui — só
 * formatação (CLAUDE.md §5 regra 3). SÍNDICO tem o mesmo acesso de leitura
 * que ADMIN (RF-SIN-06); não há ação de escrita nesta tela.
 */
import { useState, type ReactNode } from "react";
import {
  Bar,
  BarChart,
  CartesianGrid,
  Legend,
  ResponsiveContainer,
  Tooltip,
  XAxis,
  YAxis,
} from "recharts";
import { serverNow, useServerClock } from "../../shared/hooks/useServerClock";
import { usePublicSettingsQuery } from "../reservations/hooks";
import { dateKeyInTimezone } from "../reservations/dateUtils";
import { periodPresets, monthRange } from "./periodUtils";
import type { Period } from "./api";
import {
  useAreaMetricsQuery,
  useDemandHeatmapQuery,
  useExportMutation,
  useReservationsByMonthQuery,
  useSummaryQuery,
  useTopUnitsQuery,
} from "./hooks";
import { AreaStatusBadge } from "../../shared/components/AreaStatusBadge";
import { fieldClassName } from "../../shared/components/formStyles";
import { ApiError } from "../../shared/api/client";
import {
  formatCurrency,
  formatDate,
  formatHours,
  formatPercent,
} from "../../shared/utils/format";
import { areaCategoryLabels, reportCategoryLabels, weekdayShortLabels } from "../../shared/utils/labels";
import type { ExportFormat, ExportType, HeatmapCellDto, IsoWeekday } from "../../shared/api/types";

const EXPORT_TYPES: { type: ExportType; label: string }[] = [
  { type: "reservations", label: "Reservas" },
  { type: "areas", label: "Áreas" },
  { type: "reports", label: "Reports" },
  { type: "payments", label: "Pagamentos" },
  { type: "units", label: "Unidades" },
];

const MONTHLY_SERIES = [
  { key: "confirmed", label: "Confirmada", color: "#065f46" },
  { key: "pendingPayment", label: "Pendente", color: "#92400e" },
  { key: "cancelled", label: "Cancelada", color: "#991b1b" },
] as const;

function formatMonthLabel(month: string): string {
  const [year, monthNumber] = month.split("-").map(Number);
  const date = new Date(Date.UTC(year, monthNumber - 1, 1));
  return new Intl.DateTimeFormat("pt-BR", { month: "short", year: "2-digit", timeZone: "UTC" }).format(date);
}

/** Estados padrão (carregando/erro/vazio) de uma seção com uma query própria. */
function QuerySection({
  title,
  isLoading,
  isError,
  isEmpty,
  emptyText,
  children,
}: {
  title: string;
  isLoading: boolean;
  isError: boolean;
  isEmpty: boolean;
  emptyText: string;
  children: ReactNode;
}) {
  return (
    <section>
      <h2 className="mb-3 text-lg font-semibold text-neutral-900">{title}</h2>
      {isLoading && <p className="text-sm text-neutral-600">Carregando {title.toLowerCase()}…</p>}
      {isError && (
        <p role="alert" className="text-sm text-danger-700">
          Não foi possível carregar estes dados. Tente novamente.
        </p>
      )}
      {!isLoading && !isError && isEmpty && <p className="text-sm text-neutral-600">{emptyText}</p>}
      {!isLoading && !isError && !isEmpty && children}
    </section>
  );
}

function Kpi({ label, value, toneClassName }: { label: string; value: string; toneClassName?: string }) {
  return (
    <div>
      <p className={`text-2xl font-semibold ${toneClassName ?? "text-neutral-900"}`}>{value}</p>
      <p className="text-sm text-neutral-600">{label}</p>
    </div>
  );
}

function Panel({ title, children }: { title: string; children: ReactNode }) {
  return (
    <div className="rounded-md border border-neutral-200 bg-neutral-0 p-4">
      <h2 className="mb-3 text-sm font-medium text-neutral-600">{title}</h2>
      <div className="flex flex-wrap gap-x-6 gap-y-4">{children}</div>
    </div>
  );
}

/**
 * Escala sequencial de uma cor só (tokens `primary-*` do DESIGN.md), 6 níveis com contraste
 * AA confirmado (texto escuro até `primary-500`, branco a partir de `primary-600`). Célula sem
 * reserva fica neutra, não entra na escala.
 */
const HEATMAP_SCALE = [
  { bg: "bg-primary-100", text: "text-neutral-900" },
  { bg: "bg-primary-200", text: "text-neutral-900" },
  { bg: "bg-primary-500", text: "text-neutral-900" },
  { bg: "bg-primary-600", text: "text-white" },
  { bg: "bg-primary-700", text: "text-white" },
  { bg: "bg-primary-800", text: "text-white" },
] as const;

function heatmapColor(count: number, max: number): string {
  if (count <= 0) return "bg-neutral-50 text-neutral-400";
  const ratio = count / max;
  const level = Math.min(HEATMAP_SCALE.length - 1, Math.ceil(ratio * HEATMAP_SCALE.length) - 1);
  const { bg, text } = HEATMAP_SCALE[level];
  return `${bg} ${text}`;
}

function DemandHeatmap({ cells }: { cells: HeatmapCellDto[] }) {
  const byKey = new Map(cells.map((cell) => [`${cell.dayOfWeek}-${cell.hour}`, cell.count]));
  const max = Math.max(1, ...cells.map((cell) => cell.count));
  const days: IsoWeekday[] = [1, 2, 3, 4, 5, 6, 7];
  const hours = Array.from({ length: 24 }, (_, hour) => hour);

  return (
    <div>
      <div className="overflow-x-auto rounded-sm [&::-webkit-scrollbar]:h-2 [&::-webkit-scrollbar-thumb]:rounded-pill [&::-webkit-scrollbar-thumb]:bg-neutral-300 [&::-webkit-scrollbar-track]:bg-neutral-100">
        <table className="border-collapse text-center text-xs">
          <caption className="mb-2 text-left text-sm text-neutral-600">
            Quantidade de reservas por dia da semana e hora de início, no período selecionado.
          </caption>
          <thead>
            <tr>
              <th scope="col" className="p-1" />
              {hours.map((hour) => (
                <th key={hour} scope="col" className="p-1 font-normal text-neutral-500">
                  {hour}
                </th>
              ))}
            </tr>
          </thead>
          <tbody>
            {days.map((day) => (
              <tr key={day}>
                <th scope="row" className="p-1 text-right font-medium text-neutral-700">
                  {weekdayShortLabels[day]}
                </th>
                {hours.map((hour) => {
                  const count = byKey.get(`${day}-${hour}`) ?? 0;
                  return (
                    <td
                      key={hour}
                      title={`${weekdayShortLabels[day]}, ${hour}h: ${count} reserva(s)`}
                      className={`h-6 w-6 rounded-sm ${heatmapColor(count, max)}`}
                    >
                      {count > 0 ? count : ""}
                    </td>
                  );
                })}
              </tr>
            ))}
          </tbody>
        </table>
      </div>
      <div className="mt-3 flex items-center gap-2 text-xs text-neutral-600" aria-hidden="true">
        <span>0</span>
        <span
          className="h-3 w-32 rounded-pill border border-neutral-200"
          style={{ background: "linear-gradient(to right, var(--color-primary-100), var(--color-primary-800))" }}
        />
        <span>{max} reserva{max === 1 ? "" : "s"}</span>
      </div>
      <p className="mt-2 text-sm text-neutral-600">
        Quanto mais escura a célula, maior a demanda naquele dia e horário. Células em branco não
        tiveram reserva no período.
      </p>
    </div>
  );
}

export function DashboardPage() {
  const { data: clock } = useServerClock();
  const { data: settings } = usePublicSettingsQuery();
  const timezone = settings?.timezone ?? "America/Sao_Paulo";

  // Sem escolha explícita do usuário, o período é o mês corrente pelo relógio do
  // servidor (derivado no render, nunca `new Date()` — RNF-10); vira estado só
  // quando um preset ou uma data é escolhida.
  const [customPeriod, setCustomPeriod] = useState<Period | null>(null);
  const today = clock ? dateKeyInTimezone(serverNow(), timezone) : null;
  const presets = today ? periodPresets(today) : [];
  const period = customPeriod ?? (today ? monthRange(today) : null);

  const summary = useSummaryQuery(period);
  const monthly = useReservationsByMonthQuery(period?.to ?? null);
  const areas = useAreaMetricsQuery(period);
  const heatmap = useDemandHeatmapQuery(period);
  const topUnits = useTopUnitsQuery(period);
  const exportMutation = useExportMutation();
  const [exporting, setExporting] = useState<`${ExportType}-${ExportFormat}` | null>(null);
  const [exportError, setExportError] = useState<string | null>(null);

  async function handleExport(type: ExportType, format: ExportFormat) {
    if (!period) return;
    setExportError(null);
    setExporting(`${type}-${format}`);
    try {
      await exportMutation.mutateAsync({ type, format, period });
    } catch (err) {
      setExportError(err instanceof ApiError ? err.detail : "Não foi possível gerar o arquivo. Tente novamente.");
    } finally {
      setExporting(null);
    }
  }

  return (
    <div className="mx-auto flex max-w-5xl flex-col gap-8">
      <div>
        <h1 className="text-xl font-semibold text-neutral-900 sm:text-2xl">Dashboard</h1>
        <p className="text-sm text-neutral-600">Indicadores do condomínio no período selecionado.</p>
      </div>

      <section aria-label="Período">
        <div className="flex flex-wrap gap-2">
          {presets.map((preset) => {
            const active = period?.from === preset.period.from && period?.to === preset.period.to;
            return (
              <button
                key={preset.label}
                type="button"
                onClick={() => setCustomPeriod(preset.period)}
                aria-pressed={active}
                className={[
                  "h-10 rounded-pill border px-4 text-sm font-medium",
                  active
                    ? "border-primary-600 bg-primary-50 text-primary-800"
                    : "border-neutral-300 text-neutral-700 hover:bg-neutral-100",
                ].join(" ")}
              >
                {preset.label}
              </button>
            );
          })}
        </div>
        <div className="mt-3 flex flex-wrap items-end gap-3">
          <label className="flex flex-col gap-1 text-sm text-neutral-700">
            De
            <input
              type="date"
              value={period?.from ?? ""}
              onChange={(event) => setCustomPeriod({ from: event.target.value, to: period?.to ?? event.target.value })}
              className={fieldClassName(false)}
            />
          </label>
          <label className="flex flex-col gap-1 text-sm text-neutral-700">
            Até
            <input
              type="date"
              value={period?.to ?? ""}
              onChange={(event) => setCustomPeriod({ from: period?.from ?? event.target.value, to: event.target.value })}
              className={fieldClassName(false)}
            />
          </label>
        </div>
      </section>

      <QuerySection
        title="Resumo"
        isLoading={summary.isLoading}
        isError={summary.isError}
        isEmpty={false}
        emptyText=""
      >
        {summary.data && (
          <div className="grid grid-cols-1 gap-4 sm:grid-cols-2 lg:grid-cols-3">
            <Panel title="Unidades e moradores">
              <Kpi label="Unidades ativas" value={summary.data.activeUnits.toLocaleString("pt-BR")} />
              <Kpi label="Moradores ativos" value={summary.data.activeResidents.toLocaleString("pt-BR")} />
            </Panel>
            <Panel title="Reservas no período">
              <Kpi label="Total" value={summary.data.reservations.total.toLocaleString("pt-BR")} />
              <Kpi
                label="Confirmadas"
                value={summary.data.reservations.confirmed.toLocaleString("pt-BR")}
                toneClassName="text-status-confirmed-text"
              />
              <Kpi
                label="Pendentes"
                value={summary.data.reservations.pendingPayment.toLocaleString("pt-BR")}
                toneClassName="text-status-pending-text"
              />
              <Kpi
                label="Canceladas"
                value={summary.data.reservations.cancelled.toLocaleString("pt-BR")}
                toneClassName="text-status-cancelled-text"
              />
            </Panel>
            <Panel title="Valores">
              <Kpi label="Confirmado" value={formatCurrency(summary.data.amounts.confirmed)} />
              <Kpi label="Pendente" value={formatCurrency(summary.data.amounts.pending)} />
            </Panel>
            <Panel title="Cancelamentos">
              <Kpi label="Taxa pelo morador" value={formatPercent(summary.data.cancellations.residentRate)} />
              <Kpi label="Taxa pela administração" value={formatPercent(summary.data.cancellations.adminRate)} />
            </Panel>
            <Panel title="Reports">
              <Kpi label="Abertos no período" value={summary.data.reports.opened.toLocaleString("pt-BR")} />
              <Kpi label="Resolvidos no período" value={summary.data.reports.resolved.toLocaleString("pt-BR")} />
              <Kpi label="Em aberto agora" value={summary.data.reports.open.toLocaleString("pt-BR")} />
              <Kpi
                label="Tempo médio de resolução"
                value={
                  summary.data.reports.averageResolutionHours == null
                    ? "—"
                    : formatHours(summary.data.reports.averageResolutionHours)
                }
              />
            </Panel>
            <Panel title="Manutenção">
              <Kpi label="Custo no período" value={formatCurrency(summary.data.maintenanceCost)} />
            </Panel>
          </div>
        )}
      </QuerySection>

      <QuerySection
        title="Reservas por mês"
        isLoading={monthly.isLoading}
        isError={monthly.isError}
        isEmpty={(monthly.data?.length ?? 0) === 0}
        emptyText="Nenhuma reserva nos últimos 12 meses."
      >
        {monthly.data && (
          <div>
            <div className="h-64 w-full">
              <ResponsiveContainer width="100%" height="100%">
                <BarChart data={monthly.data} margin={{ left: -20 }}>
                  <CartesianGrid vertical={false} stroke="#e2e8f0" />
                  <XAxis dataKey="month" tickFormatter={formatMonthLabel} tick={{ fontSize: 12 }} />
                  <YAxis allowDecimals={false} tick={{ fontSize: 12 }} />
                  <Tooltip labelFormatter={(value) => formatMonthLabel(String(value))} />
                  <Legend />
                  {MONTHLY_SERIES.map((series) => (
                    <Bar key={series.key} dataKey={series.key} name={series.label} stackId="status" fill={series.color} />
                  ))}
                </BarChart>
              </ResponsiveContainer>
            </div>
            <details className="mt-4">
              <summary className="cursor-pointer text-sm font-medium text-primary-700 hover:text-primary-800">
                Ver dados do gráfico em tabela
              </summary>
              <div className="mt-3 overflow-x-auto">
                <table className="w-full min-w-[480px] border-collapse text-left text-sm">
                  <caption className="sr-only">Reservas por mês, resumo em tabela do gráfico acima.</caption>
                  <thead>
                    <tr className="border-b border-neutral-200 text-neutral-600">
                      <th scope="col" className="py-2 pr-4">Mês</th>
                      <th scope="col" className="py-2 pr-4">Total</th>
                      <th scope="col" className="py-2 pr-4">Confirmadas</th>
                      <th scope="col" className="py-2 pr-4">Pendentes</th>
                      <th scope="col" className="py-2 pr-4">Canceladas</th>
                    </tr>
                  </thead>
                  <tbody>
                    {monthly.data.map((item) => (
                      <tr key={item.month} className="border-b border-neutral-200">
                        <td className="py-2 pr-4 font-medium text-neutral-900">{formatMonthLabel(item.month)}</td>
                        <td className="py-2 pr-4">{item.total}</td>
                        <td className="py-2 pr-4">{item.confirmed}</td>
                        <td className="py-2 pr-4">{item.pendingPayment}</td>
                        <td className="py-2 pr-4">{item.cancelled}</td>
                      </tr>
                    ))}
                  </tbody>
                </table>
              </div>
            </details>
          </div>
        )}
      </QuerySection>

      <QuerySection
        title="Áreas: reservas, ocupação, reports e custo"
        isLoading={areas.isLoading}
        isError={areas.isError}
        isEmpty={(areas.data?.length ?? 0) === 0}
        emptyText="Nenhuma área cadastrada."
      >
        {areas.data && (
          <>
            <div className="hidden overflow-x-auto md:block">
              <table className="w-full border-collapse text-left text-sm">
                <thead>
                  <tr className="border-b border-neutral-200 text-neutral-600">
                    <th scope="col" className="py-2 pr-4">Área</th>
                    <th scope="col" className="py-2 pr-4">Categoria</th>
                    <th scope="col" className="py-2 pr-4">Status</th>
                    <th scope="col" className="py-2 pr-4">Reservas</th>
                    <th scope="col" className="py-2 pr-4">Ocupação</th>
                    <th scope="col" className="py-2 pr-4">Reports</th>
                    <th scope="col" className="py-2 pr-4">Custo de manutenção</th>
                  </tr>
                </thead>
                <tbody>
                  {areas.data.map((area) => (
                    <tr key={area.areaId} className="border-b border-neutral-200">
                      <td className="py-3 pr-4 font-medium text-neutral-900">{area.areaName}</td>
                      <td className="py-3 pr-4">{areaCategoryLabels[area.category]}</td>
                      <td className="py-3 pr-4"><AreaStatusBadge status={area.status} /></td>
                      <td className="py-3 pr-4">{area.reservations}</td>
                      <td className="py-3 pr-4">{formatPercent(area.occupancyRate)}</td>
                      <td className="py-3 pr-4">{area.reports}</td>
                      <td className="py-3 pr-4">{formatCurrency(area.maintenanceCost)}</td>
                    </tr>
                  ))}
                </tbody>
              </table>
            </div>
            <ul className="flex flex-col gap-3 md:hidden">
              {areas.data.map((area) => (
                <li key={area.areaId} className="rounded-md border border-neutral-200 bg-neutral-0 p-4">
                  <div className="flex items-center justify-between gap-2">
                    <span className="font-medium text-neutral-900">{area.areaName}</span>
                    <AreaStatusBadge status={area.status} />
                  </div>
                  <dl className="mt-2 space-y-1 text-sm text-neutral-700">
                    <div><dt className="inline font-medium">Categoria: </dt><dd className="inline">{areaCategoryLabels[area.category]}</dd></div>
                    <div><dt className="inline font-medium">Reservas: </dt><dd className="inline">{area.reservations}</dd></div>
                    <div><dt className="inline font-medium">Ocupação: </dt><dd className="inline">{formatPercent(area.occupancyRate)}</dd></div>
                    <div><dt className="inline font-medium">Reports: </dt><dd className="inline">{area.reports}</dd></div>
                    <div><dt className="inline font-medium">Custo de manutenção: </dt><dd className="inline">{formatCurrency(area.maintenanceCost)}</dd></div>
                  </dl>
                </li>
              ))}
            </ul>
          </>
        )}
      </QuerySection>

      <QuerySection
        title="Demanda por dia e horário"
        isLoading={heatmap.isLoading}
        isError={heatmap.isError}
        isEmpty={(heatmap.data?.length ?? 0) === 0}
        emptyText="Nenhuma reserva no período."
      >
        {heatmap.data && <DemandHeatmap cells={heatmap.data} />}
      </QuerySection>

      <QuerySection
        title="Reports por categoria"
        isLoading={summary.isLoading}
        isError={summary.isError}
        isEmpty={(summary.data?.reports.byCategory.length ?? 0) === 0}
        emptyText="Nenhum report aberto no período."
      >
        {summary.data && (
          <table className="w-full max-w-md border-collapse text-left text-sm">
            <thead>
              <tr className="border-b border-neutral-200 text-neutral-600">
                <th scope="col" className="py-2 pr-4">Categoria</th>
                <th scope="col" className="py-2 pr-4">Quantidade</th>
              </tr>
            </thead>
            <tbody>
              {summary.data.reports.byCategory.map((item) => (
                <tr key={item.category} className="border-b border-neutral-200">
                  <td className="py-2 pr-4">{reportCategoryLabels[item.category]}</td>
                  <td className="py-2 pr-4">{item.count}</td>
                </tr>
              ))}
            </tbody>
          </table>
        )}
      </QuerySection>

      <QuerySection
        title="Top 10 unidades"
        isLoading={topUnits.isLoading}
        isError={topUnits.isError}
        isEmpty={(topUnits.data?.length ?? 0) === 0}
        emptyText="Nenhuma reserva no período."
      >
        {topUnits.data && (
          <table className="w-full max-w-md border-collapse text-left text-sm">
            <thead>
              <tr className="border-b border-neutral-200 text-neutral-600">
                <th scope="col" className="py-2 pr-4">Unidade</th>
                <th scope="col" className="py-2 pr-4">Reservas</th>
                <th scope="col" className="py-2 pr-4">Horas reservadas</th>
              </tr>
            </thead>
            <tbody>
              {topUnits.data.map((item) => (
                <tr key={item.unitId} className="border-b border-neutral-200">
                  <td className="py-2 pr-4 font-medium text-neutral-900">{item.unitIdentifier}</td>
                  <td className="py-2 pr-4">{item.reservations}</td>
                  <td className="py-2 pr-4">{formatHours(item.reservedHours)}</td>
                </tr>
              ))}
            </tbody>
          </table>
        )}
      </QuerySection>

      <section>
        <h2 className="mb-3 text-lg font-semibold text-neutral-900">Exportar relatórios</h2>
        <p className="text-sm text-neutral-600">
          Gera um arquivo com os dados do período selecionado{period ? ` (${formatDate(period.from)} a ${formatDate(period.to)})` : ""}.
        </p>
        {exportError && (
          <p role="alert" className="mt-2 text-sm text-danger-700">
            {exportError}
          </p>
        )}
        <ul className="mt-3 flex flex-col gap-2">
          {EXPORT_TYPES.map(({ type, label }) => (
            <li key={type} className="flex flex-wrap items-center gap-3 rounded-md border border-neutral-200 bg-neutral-0 p-3">
              <span className="min-w-24 font-medium text-neutral-900">{label}</span>
              {(["csv", "xlsx"] as const).map((format) => {
                const key: `${ExportType}-${ExportFormat}` = `${type}-${format}`;
                return (
                  <button
                    key={format}
                    type="button"
                    disabled={!period || exporting === key}
                    onClick={() => void handleExport(type, format)}
                    className="h-10 rounded-md border border-neutral-300 px-3 text-sm font-medium text-neutral-700 hover:bg-neutral-100 disabled:cursor-not-allowed disabled:opacity-70"
                  >
                    {exporting === key ? "Gerando…" : `Exportar ${format.toUpperCase()}`}
                  </button>
                );
              })}
            </li>
          ))}
        </ul>
      </section>
    </div>
  );
}
