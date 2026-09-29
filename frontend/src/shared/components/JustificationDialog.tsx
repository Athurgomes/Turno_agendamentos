/**
 * Confirmação que exige justificativa (RN-27, RN-36: mínimo 10 caracteres,
 * texto visível ao morador/unidade). Genérico — usado em cancelamento de
 * reserva pelo ADMIN e em outras ações que citam a mesma regra.
 */
import { useState } from "react";
import { Modal } from "./Modal";
import { textareaClassName } from "./formStyles";

const MIN_JUSTIFICATION_LENGTH = 10;

export interface JustificationDialogProps {
  open: boolean;
  title: string;
  message: string;
  confirmLabel: string;
  helperText?: string;
  error?: string | null;
  isLoading?: boolean;
  onConfirm: (justification: string) => void;
  onCancel: () => void;
}

export function JustificationDialog({
  open,
  title,
  message,
  confirmLabel,
  helperText,
  error,
  isLoading = false,
  onConfirm,
  onCancel,
}: JustificationDialogProps) {
  const [justification, setJustification] = useState("");
  const trimmed = justification.trim();
  const touched = trimmed.length > 0;
  const tooShort = touched && trimmed.length < MIN_JUSTIFICATION_LENGTH;

  function handleCancel() {
    setJustification("");
    onCancel();
  }

  function handleConfirm() {
    if (trimmed.length < MIN_JUSTIFICATION_LENGTH) return;
    onConfirm(trimmed);
  }

  return (
    <Modal open={open} onClose={handleCancel} titleId="justification-dialog-title" title={title}>
      <p className="text-sm text-neutral-700">{message}</p>

      <div className="mt-4 flex flex-col gap-1">
        <label htmlFor="justification-textarea" className="text-sm font-medium text-neutral-700">
          Justificativa (mínimo 10 caracteres)
        </label>
        <textarea
          id="justification-textarea"
          rows={3}
          value={justification}
          onChange={(event) => setJustification(event.target.value)}
          className={textareaClassName(tooShort)}
        />
        {helperText && <p className="text-sm text-neutral-600">{helperText}</p>}
        {tooShort && (
          <p className="text-sm text-danger-700">
            Escreva pelo menos 10 caracteres para que a justificativa fique clara.
          </p>
        )}
      </div>

      {error && (
        <p role="alert" className="mt-4 rounded-sm border border-danger-600 bg-danger-50 px-3 py-2 text-sm text-danger-700">
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
          disabled={isLoading || trimmed.length < MIN_JUSTIFICATION_LENGTH}
          className="h-11 rounded-md bg-danger-700 px-4 text-sm font-medium text-white hover:bg-danger-800 disabled:cursor-not-allowed disabled:opacity-70"
        >
          {isLoading ? "Enviando…" : confirmLabel}
        </button>
      </div>
    </Modal>
  );
}
