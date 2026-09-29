/**
 * Aba Confirmações (`/admin/confirmacoes`, RF-PAG-01..03, D-49): reservas
 * `PENDING_PAYMENT` aguardando o ADMIN confirmar ou cancelar o pagamento
 * combinado fora do sistema (WhatsApp, D-06). Lista já vem ordenada pelo
 * backend (início crescente); itens em até 48h ganham destaque (RF-PAG-02).
 */
import { useState } from "react";
import { ConfirmDialog } from "../../../shared/components/ConfirmDialog";
import { JustificationDialog } from "../../../shared/components/JustificationDialog";
import { ApiError } from "../../../shared/api/client";
import { useConfirmPayment, useCancelPendingPayment, usePendingPaymentsQuery } from "./hooks";
import { PendingPaymentCard } from "./PendingPaymentCard";
import type { PendingPaymentDto } from "../../../shared/api/types";

export function ConfirmationsPage() {
  const { data: items, isLoading } = usePendingPaymentsQuery();
  const confirmPayment = useConfirmPayment();
  const cancelPayment = useCancelPendingPayment();

  const [toConfirm, setToConfirm] = useState<PendingPaymentDto | null>(null);
  const [toCancel, setToCancel] = useState<PendingPaymentDto | null>(null);
  const [confirmError, setConfirmError] = useState<string | null>(null);
  const [cancelError, setCancelError] = useState<string | null>(null);

  async function handleConfirm() {
    if (!toConfirm) return;
    setConfirmError(null);
    try {
      await confirmPayment.mutateAsync(toConfirm.id);
      setToConfirm(null);
    } catch (err) {
      setConfirmError(
        err instanceof ApiError ? err.detail : "Não foi possível confirmar este pagamento. Tente novamente.",
      );
    }
  }

  async function handleCancel(justification: string) {
    if (!toCancel) return;
    setCancelError(null);
    try {
      await cancelPayment.mutateAsync({ id: toCancel.id, justification });
      setToCancel(null);
    } catch (err) {
      setCancelError(
        err instanceof ApiError ? err.detail : "Não foi possível cancelar esta reserva. Tente novamente.",
      );
    }
  }

  const list = items ?? [];

  return (
    <div>
      <div className="mb-4">
        <h1 className="text-xl font-semibold text-neutral-900 sm:text-2xl">Confirmações</h1>
        <p className="text-sm text-neutral-600">
          Reservas de áreas pagas aguardando a confirmação do pagamento combinado pelo WhatsApp.
        </p>
      </div>

      {isLoading && <p className="text-sm text-neutral-600">Carregando confirmações…</p>}

      {!isLoading && list.length === 0 && (
        <p className="text-sm text-neutral-600">Nenhum pagamento aguardando confirmação.</p>
      )}

      <ul className="flex flex-col gap-3">
        {list.map((item) => (
          <PendingPaymentCard
            key={item.id}
            item={item}
            isConfirming={confirmPayment.isPending && toConfirm?.id === item.id}
            onConfirm={(selected) => {
              setConfirmError(null);
              setToConfirm(selected);
            }}
            onCancel={(selected) => {
              setCancelError(null);
              setToCancel(selected);
            }}
          />
        ))}
      </ul>

      <ConfirmDialog
        open={!!toConfirm}
        title="Confirmar pagamento"
        message={
          confirmError ??
          `Confirmar o pagamento da reserva de ${toConfirm?.areaName ?? ""} da unidade ${toConfirm?.unitIdentifier ?? ""}? A reserva passa para "Confirmada".`
        }
        confirmLabel="Confirmar pagamento"
        isLoading={confirmPayment.isPending}
        onConfirm={() => void handleConfirm()}
        onCancel={() => {
          setToConfirm(null);
          setConfirmError(null);
        }}
      />

      <JustificationDialog
        open={!!toCancel}
        title="Cancelar reserva"
        message={`Cancelar a reserva de ${toCancel?.areaName ?? ""} da unidade ${toCancel?.unitIdentifier ?? ""}? Explique o motivo. O morador vai ver esta justificativa.`}
        confirmLabel="Cancelar reserva"
        error={cancelError}
        isLoading={cancelPayment.isPending}
        onConfirm={(justification) => void handleCancel(justification)}
        onCancel={() => {
          setToCancel(null);
          setCancelError(null);
        }}
      />
    </div>
  );
}
