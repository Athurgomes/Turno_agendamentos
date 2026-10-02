/**
 * Página inicial de SÍNDICO/ADMIN (`/painel`, RF-SIN-01, F7-1): reservas e
 * bloqueios de hoje e dos próximos 7 dias, reports abertos e vistorias
 * atrasadas — tudo em uma única chamada (`GET /dashboard/home`). Datas de
 * agenda (`date`) já vêm no fuso do condomínio (RN-31); `createdAt` é
 * instante e usa `formatDateTimeInstant` com o fuso de `/settings/public`.
 */
import { Link } from "react-router-dom";
import { ReservationStatusBadge } from "../../shared/components/ReservationStatusBadge";
import { ReportStatusBadge } from "../../shared/components/ReportStatusBadge";
import { StatusBadge } from "../../shared/components/StatusBadge";
import { formatDate, formatDateTimeInstant, formatTime } from "../../shared/utils/format";
import { reportCategoryLabels } from "../../shared/utils/labels";
import { usePublicSettingsQuery } from "../reservations/hooks";
import { useDashboardHomeQuery } from "./hooks";
import type {
  DashboardHomeDto,
  OpenReportItemDto,
  OverdueInspectionDto,
  ReservationSummary,
} from "../../shared/api/types";

const BLOCK_TONE = "bg-neutral-100 text-neutral-700";

function ReservationRow({ item }: { item: ReservationSummary }) {
  return (
    <li>
      <Link
        to="/agenda"
        className="flex items-center justify-between gap-3 rounded-md border border-neutral-200 bg-neutral-0 p-3 hover:bg-neutral-50"
      >
        <div className="min-w-0">
          <p className="truncate text-sm font-medium text-neutral-900">{item.areaName}</p>
          <p className="text-sm text-neutral-600">
            {formatDate(item.date)} · {formatTime(item.startTime)}–{formatTime(item.endTime)}
            {item.kind === "BOOKING" ? ` · ${item.unitIdentifier}` : null}
          </p>
        </div>
        {item.kind === "BOOKING" ? (
          <ReservationStatusBadge status={item.status} completed={false} />
        ) : (
          <StatusBadge label="Bloqueio" toneClassName={BLOCK_TONE} />
        )}
      </Link>
    </li>
  );
}

function ReservationSection({
  title,
  items,
  emptyText,
}: {
  title: string;
  items: ReservationSummary[];
  emptyText: string;
}) {
  return (
    <section>
      <div className="mb-2 flex items-center justify-between gap-2">
        <h2 className="text-lg font-semibold text-neutral-900">{title}</h2>
        <Link to="/agenda" className="text-sm font-medium text-primary-600 hover:text-primary-700">
          Ver agenda
        </Link>
      </div>
      {items.length === 0 ? (
        <p className="text-sm text-neutral-600">{emptyText}</p>
      ) : (
        <ul className="flex flex-col gap-2">
          {items.map((item) => (
            <ReservationRow key={item.id} item={item} />
          ))}
        </ul>
      )}
    </section>
  );
}

function ReportRow({ item, timezone }: { item: OpenReportItemDto; timezone: string }) {
  return (
    <li>
      <Link
        to="/reports"
        className="flex items-center justify-between gap-3 rounded-md border border-neutral-200 bg-neutral-0 p-3 hover:bg-neutral-50"
      >
        <div className="min-w-0">
          <p className="truncate text-sm font-medium text-neutral-900">{item.areaName}</p>
          <p className="text-sm text-neutral-600">
            {item.code} · {item.unitIdentifier} · {reportCategoryLabels[item.category]} ·{" "}
            {formatDateTimeInstant(item.createdAt, timezone)}
          </p>
        </div>
        <ReportStatusBadge status={item.status} />
      </Link>
    </li>
  );
}

function ReportsSection({
  openReports,
  timezone,
}: {
  openReports: DashboardHomeDto["openReports"];
  timezone: string;
}) {
  return (
    <section>
      <div className="mb-2 flex items-center justify-between gap-2">
        <h2 className="text-lg font-semibold text-neutral-900">
          Reports abertos {openReports.count > 0 ? `(${openReports.count})` : null}
        </h2>
        <Link to="/reports" className="text-sm font-medium text-primary-600 hover:text-primary-700">
          Ver caixa de reports
        </Link>
      </div>
      {openReports.items.length === 0 ? (
        <p className="text-sm text-neutral-600">Nenhum report aberto.</p>
      ) : (
        <ul className="flex flex-col gap-2">
          {openReports.items.map((item) => (
            <ReportRow key={item.id} item={item} timezone={timezone} />
          ))}
        </ul>
      )}
    </section>
  );
}

function InspectionRow({ item }: { item: OverdueInspectionDto }) {
  return (
    <li>
      <Link
        to={`/areas/${item.areaId}/vistorias`}
        className="flex items-center justify-between gap-3 rounded-md border border-neutral-200 bg-neutral-0 p-3 hover:bg-neutral-50"
      >
        <p className="truncate text-sm font-medium text-neutral-900">{item.areaName}</p>
        <p className="shrink-0 text-sm text-neutral-600">
          {item.daysSinceInspection == null
            ? "Nunca vistoriada"
            : `${item.daysSinceInspection} dias sem vistoria`}
        </p>
      </Link>
    </li>
  );
}

function InspectionsSection({ items }: { items: OverdueInspectionDto[] }) {
  return (
    <section>
      <h2 className="mb-2 text-lg font-semibold text-neutral-900">Vistorias atrasadas</h2>
      {items.length === 0 ? (
        <p className="text-sm text-neutral-600">
          Nenhuma vistoria atrasada — todas as áreas foram vistoriadas nos últimos 30 dias.
        </p>
      ) : (
        <ul className="flex flex-col gap-2">
          {items.map((item) => (
            <InspectionRow key={item.areaId} item={item} />
          ))}
        </ul>
      )}
    </section>
  );
}

export function SyndicHomePage() {
  const { data, isLoading, isError, refetch, isFetching } = useDashboardHomeQuery();
  const { data: settings } = usePublicSettingsQuery();
  const timezone = settings?.timezone ?? "America/Sao_Paulo";

  return (
    <div className="mx-auto max-w-3xl">
      <h1 className="mb-6 text-xl font-semibold text-neutral-900 sm:text-2xl">Início</h1>

      {isLoading && <p className="text-sm text-neutral-600">Carregando página inicial…</p>}

      {isError && (
        <div role="alert" className="rounded-md border border-danger-600 bg-neutral-0 p-4">
          <p className="text-sm text-danger-700">
            Não foi possível carregar a página inicial. Tente novamente.
          </p>
          <button
            type="button"
            onClick={() => void refetch()}
            disabled={isFetching}
            className="mt-3 flex h-10 items-center justify-center rounded-md border border-neutral-300 px-4 text-sm font-medium text-neutral-700 hover:bg-neutral-100 disabled:cursor-not-allowed disabled:opacity-70"
          >
            {isFetching ? "Tentando…" : "Tentar novamente"}
          </button>
        </div>
      )}

      {data && (
        <div className="flex flex-col gap-8">
          <ReservationSection
            title="Hoje"
            items={data.today}
            emptyText="Nenhuma reserva ou bloqueio hoje."
          />
          <ReservationSection
            title="Próximos 7 dias"
            items={data.next7Days}
            emptyText="Nenhuma reserva ou bloqueio nos próximos 7 dias."
          />
          <ReportsSection openReports={data.openReports} timezone={timezone} />
          <InspectionsSection items={data.overdueInspections} />
        </div>
      )}
    </div>
  );
}
