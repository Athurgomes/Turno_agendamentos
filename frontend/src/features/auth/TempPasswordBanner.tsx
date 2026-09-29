/**
 * Banner persistente de senha temporária (RF-AUT-03, RN-04). Não bloqueia o
 * uso do sistema — só recomenda a troca — e some sozinho quando
 * `user.tempPassword` vira `false` (após `PUT /auth/password` com sucesso).
 */
import { Link } from "react-router-dom";
import { useSession } from "./useSession";

export function TempPasswordBanner() {
  const { user } = useSession();
  if (!user?.tempPassword) return null;

  return (
    <div
      role="status"
      className="flex flex-col gap-2 border-b border-warning-800 bg-warning-50 px-4 py-3 text-sm text-warning-800 sm:flex-row sm:items-center sm:justify-between sm:px-6"
    >
      <p>
        Você está com uma senha temporária. Troque para uma senha só sua
        assim que possível.
      </p>
      <Link
        to="/alterar-senha"
        className="inline-flex shrink-0 items-center justify-center rounded-md border border-warning-800 px-3 py-1.5 text-sm font-medium text-warning-800 hover:bg-warning-100"
      >
        Trocar senha
      </Link>
    </div>
  );
}
