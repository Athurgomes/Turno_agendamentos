import { Navigate, Outlet } from "react-router-dom";
import { useSession } from "../features/auth/useSession";
import type { Role } from "../shared/api/types";
import { homeForRole } from "./roleHome";

/**
 * Guarda de rota por perfil (RN-01: UX apenas; o backend reforça por role e
 * por recurso). Acesso a rota de outro perfil → volta para a inicial do
 * perfil autenticado, sem expor a existência da rota.
 */
export function RequireRole({ roles }: { roles: Role[] }) {
  const { user } = useSession();

  if (!user) return <Navigate to="/login" replace />;
  if (!roles.includes(user.role)) {
    return <Navigate to={homeForRole(user.role)} replace />;
  }

  return <Outlet />;
}
