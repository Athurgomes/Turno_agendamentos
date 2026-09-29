/**
 * "Meus reports" (`/meus-reports`, RF-REP-05): código, área, data da reserva,
 * categoria, status com motivo (RN-36) e comentários públicos — o backend já
 * devolve só os comentários com `visibleToResident = true` (D-50), então esta
 * tela nunca precisa (nem deve) marcar comentário como "Só equipe".
 */
import { ReportStatusBadge } from "../../shared/components/ReportStatusBadge";
import { useServerClock } from "../../shared/hooks/useServerClock";
import { formatDate, formatDateTimeInstant } from "../../shared/utils/format";
import { reportCategoryLabels } from "../../shared/utils/labels";
import { useMyReportsQuery } from "./hooks";

export function MyReportsPage() {
  const { data: reports, isLoading } = useMyReportsQuery();
  const { data: clock } = useServerClock();
  const timezone = clock?.timezone ?? "America/Sao_Paulo";
  const items = reports ?? [];

  return (
    <div className="mx-auto max-w-2xl">
      <h1 className="mb-4 text-xl font-semibold text-neutral-900 sm:text-2xl">Meus reports</h1>

      {isLoading && <p className="text-sm text-neutral-600">Carregando reports…</p>}

      {!isLoading && items.length === 0 && (
        <p className="text-sm text-neutral-600">
          Nenhum report ainda — em "Minhas reservas" você pode reportar um problema de uma reserva
          confirmada.
        </p>
      )}

      <ul className="flex flex-col gap-3">
        {items.map((report) => (
          <li key={report.id} className="rounded-md border border-neutral-200 bg-neutral-0 p-4">
            <div className="flex items-start justify-between gap-2">
              <div>
                <p className="text-base font-medium text-neutral-900">{report.areaName}</p>
                <p className="text-sm text-neutral-600">
                  {report.code} · {formatDate(report.reservationDate)}
                </p>
              </div>
              <ReportStatusBadge status={report.status} />
            </div>

            <p className="mt-2 text-sm text-neutral-700">{reportCategoryLabels[report.category]}</p>
            <p className="mt-1 text-sm text-neutral-700">{report.description}</p>

            {report.statusReason && (
              <p className="mt-2 text-sm text-neutral-600">{report.statusReason}</p>
            )}

            <p className="mt-2 text-xs text-neutral-500">
              Aberto em {formatDateTimeInstant(report.createdAt, timezone)}
              {report.resolvedAt && ` · Resolvido em ${formatDateTimeInstant(report.resolvedAt, timezone)}`}
            </p>

            {report.photos.length > 0 && (
              <ul className="mt-3 grid grid-cols-3 gap-2 sm:grid-cols-5">
                {report.photos.map((photo) => (
                  <li key={photo.id}>
                    <img src={photo.url} alt="" className="aspect-square w-full rounded-sm object-cover" />
                  </li>
                ))}
              </ul>
            )}

            {report.comments.length > 0 && (
              <div className="mt-3 border-t border-neutral-200 pt-3">
                <h2 className="text-sm font-semibold text-neutral-900">Comentários</h2>
                <ul className="mt-2 flex flex-col gap-2">
                  {report.comments.map((comment) => (
                    <li key={comment.id} className="text-sm text-neutral-700">
                      <span className="font-medium text-neutral-900">{comment.authorName}: </span>
                      {comment.text}
                    </li>
                  ))}
                </ul>
              </div>
            )}
          </li>
        ))}
      </ul>
    </div>
  );
}
