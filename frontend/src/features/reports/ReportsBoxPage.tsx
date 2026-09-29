/**
 * Caixa de reports de SÍNDICO/ADMIN (`/reports`, RF-REP-02/03/04): filtros por
 * status, área, categoria e período; abre o detalhe (`ReportDetailPanel`) com
 * mudança de status, comentários e fotos do reparo.
 */
import { useState } from "react";
import { useAreasQuery } from "../areas/hooks";
import { fieldClassName } from "../../shared/components/formStyles";
import { reportCategoryLabels, reportStatusLabels } from "../../shared/utils/labels";
import { ReportStatusBadge } from "../../shared/components/ReportStatusBadge";
import { formatDate } from "../../shared/utils/format";
import { useAdminReportsQuery } from "./hooks";
import { ReportDetailPanel } from "./ReportDetailPanel";
import type { AdminReportDto, ReportCategory, ReportStatus } from "../../shared/api/types";

function ReportListItem({ report, onOpen }: { report: AdminReportDto; onOpen: () => void }) {
  return (
    <li className="rounded-md border border-neutral-200 bg-neutral-0">
      <button type="button" onClick={onOpen} className="flex w-full flex-col gap-2 p-4 text-left">
        <div className="flex items-start justify-between gap-2">
          <div>
            <p className="text-base font-medium text-neutral-900">{report.areaName}</p>
            <p className="text-sm text-neutral-600">
              {report.code} · {report.unitIdentifier} · {formatDate(report.reservationDate)}
            </p>
          </div>
          <ReportStatusBadge status={report.status} />
        </div>
        <p className="text-sm text-neutral-700">{reportCategoryLabels[report.category]}</p>
      </button>
    </li>
  );
}

export function ReportsBoxPage() {
  const [status, setStatus] = useState<ReportStatus | "">("");
  const [areaId, setAreaId] = useState("");
  const [category, setCategory] = useState<ReportCategory | "">("");
  const [fromInput, setFromInput] = useState("");
  const [toInput, setToInput] = useState("");
  const [page, setPage] = useState(0);
  const [selectedId, setSelectedId] = useState<string | null>(null);

  const { data: areas } = useAreasQuery({});

  const filters = {
    status: status || undefined,
    areaId: areaId || undefined,
    category: category || undefined,
    from: fromInput || undefined,
    to: toInput || undefined,
    page,
  };

  const { data, isLoading } = useAdminReportsQuery(filters);
  const items = data?.content ?? [];
  const showingDetail = !!selectedId;

  return (
    <div>
      <div className="mb-4">
        <h1 className="text-xl font-semibold text-neutral-900 sm:text-2xl">Reports</h1>
        <p className="text-sm text-neutral-600">Ocorrências reportadas pelos moradores nas áreas comuns.</p>
      </div>

      <div className="md:flex md:items-start md:gap-6">
        <div className={[showingDetail ? "hidden md:block" : "block", "md:min-w-0 md:flex-1"].join(" ")}>
          <div className="mb-4 flex flex-col gap-3 sm:flex-row sm:flex-wrap">
            <div className="flex flex-col gap-1 sm:w-44">
              <label htmlFor="report-filter-status" className="text-sm font-medium text-neutral-700">
                Status
              </label>
              <select
                id="report-filter-status"
                value={status}
                onChange={(event) => {
                  setStatus(event.target.value as ReportStatus | "");
                  setPage(0);
                }}
                className={fieldClassName(false)}
              >
                <option value="">Todos</option>
                {(Object.keys(reportStatusLabels) as ReportStatus[]).map((option) => (
                  <option key={option} value={option}>
                    {reportStatusLabels[option]}
                  </option>
                ))}
              </select>
            </div>

            <div className="flex flex-col gap-1 sm:w-48">
              <label htmlFor="report-filter-area" className="text-sm font-medium text-neutral-700">
                Área
              </label>
              <select
                id="report-filter-area"
                value={areaId}
                onChange={(event) => {
                  setAreaId(event.target.value);
                  setPage(0);
                }}
                className={fieldClassName(false)}
              >
                <option value="">Todas</option>
                {(areas ?? []).map((area) => (
                  <option key={area.id} value={area.id}>
                    {area.name}
                  </option>
                ))}
              </select>
            </div>

            <div className="flex flex-col gap-1 sm:w-44">
              <label htmlFor="report-filter-category" className="text-sm font-medium text-neutral-700">
                Categoria
              </label>
              <select
                id="report-filter-category"
                value={category}
                onChange={(event) => {
                  setCategory(event.target.value as ReportCategory | "");
                  setPage(0);
                }}
                className={fieldClassName(false)}
              >
                <option value="">Todas</option>
                {(Object.keys(reportCategoryLabels) as ReportCategory[]).map((option) => (
                  <option key={option} value={option}>
                    {reportCategoryLabels[option]}
                  </option>
                ))}
              </select>
            </div>

            <div className="flex flex-col gap-1">
              <label htmlFor="report-filter-from" className="text-sm font-medium text-neutral-700">
                De
              </label>
              <input
                id="report-filter-from"
                type="date"
                value={fromInput}
                onChange={(event) => {
                  setFromInput(event.target.value);
                  setPage(0);
                }}
                className={fieldClassName(false)}
              />
            </div>
            <div className="flex flex-col gap-1">
              <label htmlFor="report-filter-to" className="text-sm font-medium text-neutral-700">
                Até
              </label>
              <input
                id="report-filter-to"
                type="date"
                value={toInput}
                onChange={(event) => {
                  setToInput(event.target.value);
                  setPage(0);
                }}
                className={fieldClassName(false)}
              />
            </div>
          </div>

          {isLoading && <p className="text-sm text-neutral-600">Carregando reports…</p>}

          {!isLoading && items.length === 0 && (
            <p className="text-sm text-neutral-600">Nenhum report encontrado com estes filtros.</p>
          )}

          <ul className="flex flex-col gap-3">
            {items.map((report) => (
              <ReportListItem key={report.id} report={report} onOpen={() => setSelectedId(report.id)} />
            ))}
          </ul>

          {data && data.totalPages > 1 && (
            <div className="mt-4 flex items-center justify-between text-sm text-neutral-700">
              <button
                type="button"
                disabled={page === 0}
                onClick={() => setPage((current) => Math.max(0, current - 1))}
                className="rounded-md border border-neutral-300 px-3 py-1.5 disabled:cursor-not-allowed disabled:opacity-50"
              >
                Anterior
              </button>
              <span>
                Página {page + 1} de {data.totalPages}
              </span>
              <button
                type="button"
                disabled={page + 1 >= data.totalPages}
                onClick={() => setPage((current) => current + 1)}
                className="rounded-md border border-neutral-300 px-3 py-1.5 disabled:cursor-not-allowed disabled:opacity-50"
              >
                Próxima
              </button>
            </div>
          )}
        </div>

        {showingDetail && (
          <div className="mt-4 md:mt-0 md:w-96 md:shrink-0 md:sticky md:top-4">
            <ReportDetailPanel id={selectedId as string} onClose={() => setSelectedId(null)} />
          </div>
        )}
      </div>
    </div>
  );
}
