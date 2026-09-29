import { Navigate, Outlet, useLocation } from "react-router-dom";
import { useSession } from "../features/auth/useSession";

/**
 * Guarda de rota por autenticação (RN-01: só UX — o backend decide de verdade).
 * Sem sessão restaurada → `/login`, preservando a rota pretendida.
 */
export function RequireAuth() {
  const { user, isLoading } = useSession();
  const location = useLocation();

  if (isLoading) {
    return (
      <div className="flex min-h-screen items-center justify-center">
        <p className="text-sm text-neutral-600">Carregando sessão…</p>
      </div>
    );
  }

  if (!user) {
    return <Navigate to="/login" replace state={{ from: location }} />;
  }

  return <Outlet />;
}
