/**
 * Diálogo de mudança de status do report (RN-36, RN-37): `DISMISSED` exige
 * justificativa (mínimo 10 caracteres); `RESOLVED` aceita custo de manutenção
 * (opcional, ≥ 0). Demais transições aceitam justificativa opcional (vira
 * `statusReason`).
 */
import { useState } from "react";
import { Modal } from "../../shared/components/Modal";
import { fieldClassName, textareaClassName } from "../../shared/components/formStyles";
import { reportStatusLabels } from "../../shared/utils/labels";
import type { ReportStatus } from "../../shared/api/types";

const MIN_JUSTIFICATION_LENGTH = 10;

export interface ReportStatusDialogPayload {
  justification?: string;
  maintenanceCost?: number;
}

export interface ReportStatusDialogProps {
  open: boolean;
  targetStatus: ReportStatus | null;
  error?: string | null;
  isLoading?: boolean;
  onConfirm: (payload: ReportStatusDialogPayload) => void;
  onCancel: () => void;
}

export function ReportStatusDialog({
  open,
  targetStatus,
  error,
  isLoading = false,
  onConfirm,
  onCancel,
}: ReportStatusDialogProps) {
  const [justification, setJustification] = useState("");
  const [maintenanceCost, setMaintenanceCost] = useState("");

  const isDismiss = targetStatus === "DISMISSED";
  const isResolve = targetStatus === "RESOLVED";
  const trimmedJustification = justification.trim();
  const justificationTouched = trimmedJustification.length > 0;
  const justificationTooShort = isDismiss && trimmedJustification.length < MIN_JUSTIFICATION_LENGTH;
  const costNumber = maintenanceCost === "" ? undefined : Number(maintenanceCost);
  const costInvalid = costNumber !== undefined && (Number.isNaN(costNumber) || costNumber < 0);
  const canConfirm = !justificationTooShort && !costInvalid;

  function reset() {
    setJustification("");
    setMaintenanceCost("");
  }

  function handleCancel() {
    reset();
    onCancel();
  }

  function handleConfirm() {
    if (!canConfirm) return;
    onConfirm({
      justification: trimmedJustification || undefined,
      maintenanceCost: isResolve ? costNumber : undefined,
    });
    reset();
  }

  if (!targetStatus) return null;

  return (
    <Modal
      open={open}
      onClose={handleCancel}
      titleId="report-status-dialog-title"
      title={`Mudar status para "${reportStatusLabels[targetStatus]}"`}
    >
      <div className="flex flex-col gap-4">
        <div className="flex flex-col gap-1">
          <label htmlFor="report-status-justification" className="text-sm font-medium text-neutral-700">
            Justificativa {isDismiss ? "(mínimo 10 caracteres)" : "(opcional)"}
          </label>
          <textarea
            id="report-status-justification"
            rows={3}
            value={justification}
            onChange={(event) => setJustification(event.target.value)}
            className={textareaClassName(justificationTouched && justificationTooShort)}
          />
          {justificationTouched && justificationTooShort && (
            <p className="text-sm text-danger-700">
              Escreva pelo menos 10 caracteres explicando por que o report foi descartado.
            </p>
          )}
        </div>

        {isResolve && (
          <div className="flex flex-col gap-1">
            <label htmlFor="report-status-cost" className="text-sm font-medium text-neutral-700">
              Custo da manutenção (opcional)
            </label>
            <input
              id="report-status-cost"
              type="number"
              inputMode="decimal"
              min={0}
              step="0.01"
              value={maintenanceCost}
              onChange={(event) => setMaintenanceCost(event.target.value)}
              className={fieldClassName(costInvalid)}
            />
            {costInvalid && <p className="text-sm text-danger-700">Informe um valor maior ou igual a zero.</p>}
          </div>
        )}
      </div>

      {error && (
        <p
          role="alert"
          className="mt-4 rounded-sm border border-danger-600 bg-danger-50 px-3 py-2 text-sm text-danger-700"
        >
          {error}
        </p>
      )}

      <div className="mt-6 flex flex-col-reverse gap-3 sm:flex-row sm:justify-end">
        <button
          type="button"
          onClick={handleCancel}
          className="h-11 rounded-md border border-neutral-300 px-4 text-sm font-medium text-neutral-700 hover:bg-neutral-100"
        >
          Voltar
        </button>
        <button
          type="button"
          onClick={handleConfirm}
          disabled={isLoading || !canConfirm}
          className="h-11 rounded-md bg-primary-600 px-4 text-sm font-medium text-white hover:bg-primary-700 disabled:cursor-not-allowed disabled:opacity-70"
        >
          {isLoading ? "Enviando…" : "Confirmar"}
        </button>
      </div>
    </Modal>
  );
}
