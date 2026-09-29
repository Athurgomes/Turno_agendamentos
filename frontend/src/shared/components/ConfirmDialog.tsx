/** Confirmação destrutiva reutilizável (desativar unidade, desativar síndico...). */
import { Modal } from "./Modal";

export interface ConfirmDialogProps {
  open: boolean;
  title: string;
  message: string;
  confirmLabel: string;
  isLoading?: boolean;
  onConfirm: () => void;
  onCancel: () => void;
}

export function ConfirmDialog({
  open,
  title,
  message,
  confirmLabel,
  isLoading = false,
  onConfirm,
  onCancel,
}: ConfirmDialogProps) {
  return (
    <Modal open={open} onClose={onCancel} titleId="confirm-dialog-title" title={title}>
      <p className="text-sm text-neutral-700">{message}</p>
      <div className="mt-6 flex flex-col-reverse gap-3 sm:flex-row sm:justify-end">
        <button
          type="button"
          onClick={onCancel}
          className="h-11 rounded-md border border-neutral-300 px-4 text-sm font-medium text-neutral-700 hover:bg-neutral-100"
        >
          Cancelar
        </button>
        <button
          type="button"
          onClick={onConfirm}
          disabled={isLoading}
          className="h-11 rounded-md bg-danger-700 px-4 text-sm font-medium text-white hover:bg-danger-800 disabled:cursor-not-allowed disabled:opacity-70"
        >
          {isLoading ? "Enviando…" : confirmLabel}
        </button>
      </div>
    </Modal>
  );
}
