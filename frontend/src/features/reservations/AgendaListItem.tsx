/**
 * Card de um item da agenda (RF-RES-07/08, RF-SIN-02): reserva ou bloqueio.
 * Bloqueio tem visual distinto (borda tracejada, sem dados de morador — motivo
 * no lugar) e nunca mostra WhatsApp nem abre detalhe de reserva.
 */
import { ReservationStatusBadge } from "../../shared/components/ReservationStatusBadge";
import { formatDate, formatPhone, formatTime } from "../../shared/utils/format";
import type { AdminReservationDto } from "../../shared/api/types";

export interface AgendaListItemProps {
  item: AdminReservationDto;
  onOpen?: (item: AdminReservationDto) => void;
  onRemoveBlock?: (item: AdminReservationDto) => void;
}

export function AgendaListItem({ item, onOpen, onRemoveBlock }: AgendaListItemProps) {
  const isBlock = item.kind === "BLOCK";

  return (
    <li
      className={[
        "rounded-md border bg-neutral-0 p-4",
        isBlock ? "border-dashed border-neutral-400 bg-neutral-50" : "border-neutral-200",
      ].join(" ")}
    >
      <div className="flex items-start justify-between gap-2">
        <div>
          <p className="text-base font-medium text-neutral-900">
            {isBlock ? `Bloqueio — ${item.areaName}` : item.areaName}
          </p>
          <p className="text-sm text-neutral-600">
            {formatDate(item.date)} · {formatTime(item.startTime)} às {formatTime(item.endTime)}
          </p>
        </div>
        {!isBlock && <ReservationStatusBadge status={item.status} completed={item.completed} />}
      </div>

      {isBlock ? (
        <p className="mt-2 text-sm text-neutral-700">
          <span className="font-medium">Motivo: </span>
          {item.notes ?? "Sem motivo informado."}
        </p>
      ) : (
        <dl className="mt-2 text-sm text-neutral-700">
          <div>
            <dt className="inline font-medium">Unidade: </dt>
            <dd className="inline">{item.unitIdentifier}</dd>
          </div>
          <div>
            <dt className="inline font-medium">Responsável: </dt>
            <dd className="inline">{item.residentName}</dd>
          </div>
          {item.residentPhone && (
            <div>
              <dt className="inline font-medium">Telefone: </dt>
              <dd className="inline">{formatPhone(item.residentPhone)}</dd>
            </div>
          )}
        </dl>
      )}

      <div className="mt-3 flex flex-wrap gap-2">
        {!isBlock && onOpen && (
          <button
            type="button"
            onClick={() => onOpen(item)}
            className="flex h-10 items-center justify-center rounded-md border border-neutral-300 px-3 text-sm font-medium text-neutral-700 hover:bg-neutral-100"
          >
            Ver detalhe
          </button>
        )}
        {!isBlock && item.whatsappContactUrl && (
          <a
            href={item.whatsappContactUrl}
            target="_blank"
            rel="noopener noreferrer"
            className="flex h-10 items-center justify-center rounded-md bg-primary-600 px-3 text-sm font-medium text-white hover:bg-primary-700"
          >
            Falar no WhatsApp
          </a>
        )}
        {isBlock && item.status !== "CANCELLED" && onRemoveBlock && (
          <button
            type="button"
            onClick={() => onRemoveBlock(item)}
            className="flex h-10 items-center justify-center rounded-md border border-danger-700 px-3 text-sm font-medium text-danger-700 hover:bg-danger-50"
          >
            Remover bloqueio
          </button>
        )}
      </div>
    </li>
  );
}
