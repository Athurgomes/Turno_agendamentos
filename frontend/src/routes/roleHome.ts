import type { Role } from "../shared/api/types";

/** Rota inicial de cada perfil, usada por "/" e pelo guard de acesso indevido. */
export function homeForRole(role: Role): string {
  return role === "UNIT" ? "/areas" : "/painel";
}
