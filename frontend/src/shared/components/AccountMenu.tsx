/**
 * Bloco nome/perfil/"Sair" da conta autenticada, usado tanto na barra
 * lateral (desktop) quanto no menu compacto (mobile) do `AppShell` — extraído
 * para não duplicar o mesmo markup nos dois lugares (revisão F0-6).
 */
import { useSession } from "../../features/auth/useSession";
import { roleLabels } from "../utils/labels";

export function AccountMenu() {
  const { user, logout } = useSession();
  if (!user) return null;

  return (
    <div className="border-t border-neutral-200 p-4">
      <p className="text-sm font-medium text-neutral-900">{user.name}</p>
      <p className="text-xs text-neutral-600">{roleLabels[user.role]}</p>
      <button
        type="button"
        onClick={() => void logout()}
        className="mt-3 text-sm font-medium text-primary-700 hover:underline"
      >
        Sair
      </button>
    </div>
  );
}
