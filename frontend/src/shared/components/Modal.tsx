/**
 * Modal genérico sobre `<dialog>` nativo (DESIGN.md — confirmações e diálogos
 * usam o elemento nativo, nunca `alert()`/`confirm()`). `Esc` fecha sozinho
 * (comportamento nativo do `showModal`); clique fora chama `onClose`.
 *
 * `jsdom` não implementa `showModal`/`close` (ambiente de teste): o efeito
 * cai para `dialog.open = true/false`, que ainda deixa o conteúdo verificável
 * nos testes, embora sem o comportamento modal real do navegador.
 */
import { useEffect, useRef, type ReactNode } from "react";

export interface ModalProps {
  open: boolean;
  onClose: () => void;
  titleId: string;
  title: string;
  children: ReactNode;
}

export function Modal({ open, onClose, titleId, title, children }: ModalProps) {
  const ref = useRef<HTMLDialogElement>(null);

  useEffect(() => {
    const dialog = ref.current;
    if (!dialog) return;
    if (open && !dialog.open) {
      if (typeof dialog.showModal === "function") dialog.showModal();
      else dialog.open = true;
    } else if (!open && dialog.open) {
      if (typeof dialog.close === "function") dialog.close();
      else dialog.open = false;
    }
  }, [open]);

  useEffect(() => {
    const dialog = ref.current;
    if (!dialog) return;
    const handleClose = () => onClose();
    dialog.addEventListener("close", handleClose);
    return () => dialog.removeEventListener("close", handleClose);
  }, [onClose]);

  function handleBackdropClick(event: React.MouseEvent<HTMLDialogElement>) {
    if (event.target === ref.current) onClose();
  }

  if (!open) return null;

  return (
    <dialog
      ref={ref}
      aria-labelledby={titleId}
      onClick={handleBackdropClick}
      onCancel={(event) => {
        event.preventDefault();
        onClose();
      }}
      className="w-[calc(100%-2rem)] max-w-md rounded-lg border border-neutral-200 bg-neutral-0 p-0 shadow-lg backdrop:bg-neutral-900/50"
    >
      <div className="p-6">
        <h2 id={titleId} className="text-lg font-semibold text-neutral-900">
          {title}
        </h2>
        <div className="mt-4">{children}</div>
      </div>
    </dialog>
  );
}
