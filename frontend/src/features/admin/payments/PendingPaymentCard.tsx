/**
 * Card de um pagamento aguardando confirmação (RF-PAG-02/03). Item com
 * `within48h = true` ganha destaque visual e textual (selo "Acontece em até
 * 48h") — nunca só cor, para não depender de percepção de cor (WCAG 1.4.1).
 */
import { StatusBadge } from "../../../shared/components/StatusBadge";
import { useServerClock } from "../../../shared/hooks/useServerClock";
import { formatCurrency, formatDate, formatDateTimeInstant, formatTime, formatPhone } from "../../../shared/utils/format";
import type { PendingPaymentDto } from "../../../shared/api/types";

export interface PendingPaymentCardProps {
  item: PendingPaymentDto;
  onConfirm: (item: PendingPaymentDto) => void;
  onCancel: (item: PendingPaymentDto) => void;
  isConfirming: boolean;
}

export function PendingPaymentCard({ item, onConfirm, onCancel, isConfirming }: PendingPaymentCardProps) {
  const { data: clock } = useServerClock();
  const timezone = clock?.timezone ?? "America/Sao_Paulo";

  return (
    <li
      className={[
        "rounded-md border bg-neutral-0 p-4",
        item.within48h ? "border-warning-700" : "border-neutral-200",
      ].join(" ")}
    >
      <div className="flex items-start justify-between gap-2">
        <div>
          <p className="text-base font-medium text-neutral-900">{item.areaName}</p>
          <p className="text-sm text-neutral-600">
            {formatDate(item.date)} · {formatTime(item.startTime)} às {formatTime(item.endTime)}
          </p>
        </div>
        {item.within48h && (
          <StatusBadge label="Acontece em até 48h" toneClassName="bg-warning-50 text-warning-800" />
        )}
      </div>

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
        <div>
          <dt className="inline font-medium">Valor: </dt>
          <dd className="inline">{item.price != null ? formatCurrency(item.price) : "—"}</dd>
        </div>
        <div>
          <dt className="inline font-medium">Solicitada em: </dt>
          <dd className="inline">{formatDateTimeInstant(item.createdAt, timezone)}</dd>
        </div>
      </dl>

      <div className="mt-3 flex flex-wrap gap-2">
        <button
          type="button"
          onClick={() => onConfirm(item)}
          disabled={isConfirming}
          className="flex h-10 items-center justify-center rounded-md bg-primary-600 px-3 text-sm font-medium text-white hover:bg-primary-700 disabled:cursor-not-allowed disabled:opacity-70"
        >
          Confirmar pagamento
        </button>
        <button
          type="button"
          onClick={() => onCancel(item)}
          className="flex h-10 items-center justify-center rounded-md border border-danger-700 px-3 text-sm font-medium text-danger-700 hover:bg-danger-50"
        >
          Cancelar reserva
        </button>
        {item.whatsappContactUrl && (
          <a
            href={item.whatsappContactUrl}
            target="_blank"
            rel="noopener noreferrer"
            className="flex h-10 items-center justify-center rounded-md border border-neutral-300 px-3 text-sm font-medium text-neutral-700 hover:bg-neutral-100"
          >
            Falar no WhatsApp
          </a>
        )}
      </div>
    </li>
  );
}
