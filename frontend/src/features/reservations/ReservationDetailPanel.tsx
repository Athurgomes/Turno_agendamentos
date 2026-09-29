/**
 * Detalhe de uma reserva/bloqueio (RF-RES-07, RF-SIN-02) + histórico
 * (`GET /reservations/{id}/events`): quem, quando, o quê, justificativa.
 * ADMIN tem "Alterar" e "Cancelar" (RN-27, RN-28); SÍNDICO só vê e contata por
 * WhatsApp — nunca altera nem cancela reserva de morador (D-16). Bloqueio tem
 * "Remover bloqueio" para S/A.
 */
import { useState } from "react";
import { ConfirmDialog } from "../../shared/components/ConfirmDialog";
import { JustificationDialog } from "../../shared/components/JustificationDialog";
import { ReservationStatusBadge } from "../../shared/components/ReservationStatusBadge";
import { ApiError } from "../../shared/api/client";
import { useServerClock } from "../../shared/hooks/useServerClock";
import { formatCurrency, formatDate, formatDateTimeInstant, formatPhone, formatTime } from "../../shared/utils/format";
import { reservationEventTypeLabels, roleLabels } from "../../shared/utils/labels";
import {
  useAdminReservationQuery,
  useCancelAdminReservation,
  useDeleteBlock,
  useReservationEventsQuery,
} from "./hooks";
import { ReservationEditDialog } from "./ReservationEditDialog";
import type { Role } from "../../shared/api/types";

export interface ReservationDetailPanelProps {
  id: string;
  role: Role;
  onClose: () => void;
}

export function ReservationDetailPanel({ id, role, onClose }: ReservationDetailPanelProps) {
  const { data: item, isLoading } = useAdminReservationQuery(id);
  const { data: events } = useReservationEventsQuery(id);
  const { data: clock } = useServerClock();
  const timezone = clock?.timezone ?? "America/Sao_Paulo";
  const cancelReservation = useCancelAdminReservation();
  const deleteBlock = useDeleteBlock();

  const [showCancel, setShowCancel] = useState(false);
  const [showEdit, setShowEdit] = useState(false);
  const [showRemoveBlock, setShowRemoveBlock] = useState(false);
  const [cancelError, setCancelError] = useState<string | null>(null);
  const [removeError, setRemoveError] = useState<string | null>(null);

  if (isLoading || !item) {
    return <p className="text-sm text-neutral-600">Carregando detalhe…</p>;
  }

  const isBlock = item.kind === "BLOCK";
  const canManage = role === "ADMIN" && !isBlock && item.status !== "CANCELLED";
  const canRemoveBlock = isBlock && item.status !== "CANCELLED";

  async function handleConfirmCancel(justification: string) {
    setCancelError(null);
    try {
      await cancelReservation.mutateAsync({ id, justification });
      setShowCancel(false);
    } catch (err) {
      setCancelError(
        err instanceof ApiError ? err.detail : "Não foi possível cancelar esta reserva. Tente novamente.",
      );
    }
  }

  async function handleConfirmRemoveBlock() {
    setRemoveError(null);
    try {
      await deleteBlock.mutateAsync(id);
      setShowRemoveBlock(false);
      onClose();
    } catch (err) {
      setRemoveError(
        err instanceof ApiError ? err.detail : "Não foi possível remover este bloqueio. Tente novamente.",
      );
    }
  }

  return (
    <div className="rounded-md border border-neutral-200 bg-neutral-0 p-4">
      <div className="flex items-start justify-between gap-2">
        <div>
          <p className="text-lg font-semibold text-neutral-900">
            {isBlock ? `Bloqueio — ${item.areaName}` : item.areaName}
          </p>
          <p className="text-sm text-neutral-600">{item.code}</p>
        </div>
        <button
          type="button"
          onClick={onClose}
          className="h-10 rounded-md border border-neutral-300 px-3 text-sm font-medium text-neutral-700 hover:bg-neutral-100 md:hidden"
        >
          Voltar
        </button>
      </div>

      {!isBlock && (
        <div className="mt-2">
          <ReservationStatusBadge status={item.status} completed={item.completed} />
        </div>
      )}

      <dl className="mt-4 flex flex-col gap-2 text-sm text-neutral-700">
        <div className="flex justify-between gap-2">
          <dt className="text-neutral-600">Data</dt>
          <dd className="font-medium text-neutral-900">{formatDate(item.date)}</dd>
        </div>
        <div className="flex justify-between gap-2">
          <dt className="text-neutral-600">Horário</dt>
          <dd className="font-medium text-neutral-900">
            {formatTime(item.startTime)} às {formatTime(item.endTime)}
          </dd>
        </div>
        {isBlock ? (
          <div className="flex justify-between gap-2">
            <dt className="text-neutral-600">Motivo</dt>
            <dd className="font-medium text-neutral-900">{item.notes ?? "—"}</dd>
          </div>
        ) : (
          <>
            <div className="flex justify-between gap-2">
              <dt className="text-neutral-600">Unidade</dt>
              <dd className="font-medium text-neutral-900">{item.unitIdentifier}</dd>
            </div>
            <div className="flex justify-between gap-2">
              <dt className="text-neutral-600">Responsável</dt>
              <dd className="font-medium text-neutral-900">{item.residentName}</dd>
            </div>
            {item.residentPhone && (
              <div className="flex justify-between gap-2">
                <dt className="text-neutral-600">Telefone</dt>
                <dd className="font-medium text-neutral-900">{formatPhone(item.residentPhone)}</dd>
              </div>
            )}
            <div className="flex justify-between gap-2">
              <dt className="text-neutral-600">Convidados</dt>
              <dd className="font-medium text-neutral-900">{item.guests}</dd>
            </div>
            {item.requiresPayment && item.price != null && (
              <div className="flex justify-between gap-2">
                <dt className="text-neutral-600">Valor</dt>
                <dd className="font-medium text-neutral-900">{formatCurrency(item.price)}</dd>
              </div>
            )}
            {item.statusReason && (
              <div className="flex justify-between gap-2">
                <dt className="text-neutral-600">Motivo do status</dt>
                <dd className="font-medium text-neutral-900">{item.statusReason}</dd>
              </div>
            )}
          </>
        )}
      </dl>

      <div className="mt-4 flex flex-wrap gap-2">
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
        {canManage && (
          <button
            type="button"
            onClick={() => setShowEdit(true)}
            className="flex h-10 items-center justify-center rounded-md border border-neutral-300 px-3 text-sm font-medium text-neutral-700 hover:bg-neutral-100"
          >
            Alterar
          </button>
        )}
        {canManage && (
          <button
            type="button"
            onClick={() => setShowCancel(true)}
            className="flex h-10 items-center justify-center rounded-md border border-danger-700 px-3 text-sm font-medium text-danger-700 hover:bg-danger-50"
          >
            Cancelar
          </button>
        )}
        {canRemoveBlock && (
          <button
            type="button"
            onClick={() => setShowRemoveBlock(true)}
            className="flex h-10 items-center justify-center rounded-md border border-danger-700 px-3 text-sm font-medium text-danger-700 hover:bg-danger-50"
          >
            Remover bloqueio
          </button>
        )}
      </div>

      <div className="mt-6">
        <h2 className="text-sm font-semibold text-neutral-900">Histórico</h2>
        {!events || events.length === 0 ? (
          <p className="mt-2 text-sm text-neutral-600">Nenhum evento registrado ainda.</p>
        ) : (
          <ul className="mt-2 flex flex-col gap-2">
            {events.map((event, index) => (
              <li key={index} className="rounded-sm border border-neutral-200 p-3 text-sm">
                <p className="font-medium text-neutral-900">
                  {reservationEventTypeLabels[event.type]} ·{" "}
                  {formatDateTimeInstant(event.occurredAt, timezone)}
                </p>
                <p className="text-neutral-600">
                  {event.actor ? `${event.actor.name} (${roleLabels[event.actor.role]})` : "Sistema"}
                </p>
                {event.justification && (
                  <p className="mt-1 text-neutral-700">Justificativa: {event.justification}</p>
                )}
              </li>
            ))}
          </ul>
        )}
      </div>

      {canManage && (
        <>
          <JustificationDialog
            open={showCancel}
            title="Cancelar reserva"
            message="Esta reserva será cancelada e a justificativa fica visível para o morador."
            confirmLabel="Cancelar reserva"
            error={cancelError}
            isLoading={cancelReservation.isPending}
            onConfirm={(justification) => void handleConfirmCancel(justification)}
            onCancel={() => {
              setShowCancel(false);
              setCancelError(null);
            }}
          />
          <ReservationEditDialog
            open={showEdit}
            reservation={item}
            onClose={() => setShowEdit(false)}
            onUpdated={() => setShowEdit(false)}
          />
        </>
      )}

      {canRemoveBlock && (
        <ConfirmDialog
          open={showRemoveBlock}
          title="Remover bloqueio"
          message={removeError ?? "Tem certeza que deseja remover este bloqueio? A área volta a ficar disponível nesse horário."}
          confirmLabel="Remover bloqueio"
          isLoading={deleteBlock.isPending}
          onConfirm={() => void handleConfirmRemoveBlock()}
          onCancel={() => {
            setShowRemoveBlock(false);
            setRemoveError(null);
          }}
        />
      )}
    </div>
  );
}
