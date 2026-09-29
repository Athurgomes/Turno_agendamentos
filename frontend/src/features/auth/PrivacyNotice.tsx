/**
 * Aviso de privacidade no primeiro acesso (RNF-01). Informa, em pt-BR
 * simples, que nome, unidade e telefone ficam visíveis ao síndico e à
 * administração. A confirmação ("Entendi") é só uma preferência de UI salva
 * no navegador por conta (`localStorage`, não é token nem dado de negócio —
 * ver D-45) e faz o aviso sumir só para esse dispositivo/conta.
 */
import { useState } from "react";
import { useSession } from "./useSession";

function storageKey(userId: string) {
  return `privacy-notice-ack:${userId}`;
}

function alreadyAcknowledged(userId: string): boolean {
  try {
    return localStorage.getItem(storageKey(userId)) === "1";
  } catch {
    // Sem acesso ao storage (ex.: navegação privada restrita): não impede o uso.
    return true;
  }
}

export function PrivacyNotice() {
  const { user } = useSession();
  const [dismissed, setDismissed] = useState(
    () => !user || alreadyAcknowledged(user.id),
  );

  if (!user || dismissed) return null;

  function handleAcknowledge() {
    try {
      localStorage.setItem(storageKey(user!.id), "1");
    } catch {
      // Ignora falha de storage: o aviso reaparece na próxima visita, sem travar o uso.
    }
    setDismissed(true);
  }

  return (
    <div className="flex flex-col gap-2 border-b border-primary-200 bg-primary-50 px-4 py-3 text-sm text-primary-800 sm:flex-row sm:items-center sm:justify-between sm:px-6">
      <p>
        Seu nome, sua unidade e seu telefone ficam visíveis ao síndico e à
        administração do condomínio, para que eles possam gerenciar as
        reservas.
      </p>
      <button
        type="button"
        onClick={handleAcknowledge}
        className="inline-flex shrink-0 items-center justify-center rounded-md border border-primary-800 px-3 py-1.5 text-sm font-medium text-primary-800 hover:bg-primary-100"
      >
        Entendi
      </button>
    </div>
  );
}
