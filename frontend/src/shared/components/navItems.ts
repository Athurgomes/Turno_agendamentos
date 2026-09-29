import type { Role } from "../api/types";

export interface NavItem {
  to: string;
  label: string;
}

const UNIT_ITEMS: NavItem[] = [
  { to: "/areas", label: "Áreas" },
  { to: "/minhas-reservas", label: "Minhas reservas" },
  { to: "/minha-unidade", label: "Minha unidade" },
  { to: "/meus-reports", label: "Meus reports" },
];

const MANAGEMENT_ITEMS: NavItem[] = [
  { to: "/painel", label: "Painel" },
  { to: "/agenda", label: "Agenda" },
  { to: "/areas", label: "Áreas" },
  { to: "/reports", label: "Reports" },
  { to: "/dashboard", label: "Dashboard" },
];

const ADMIN_ONLY_ITEMS: NavItem[] = [
  { to: "/admin/unidades", label: "Unidades" },
  { to: "/admin/sindicos", label: "Síndicos" },
  { to: "/admin/confirmacoes", label: "Confirmações" },
  { to: "/admin/configuracoes", label: "Configurações" },
];

/** Itens do menu por perfil (RN-01: espelha a matriz de permissões, guarda é só UX). */
export function navItemsForRole(role: Role): NavItem[] {
  switch (role) {
    case "UNIT":
      return UNIT_ITEMS;
    case "SYNDIC":
      return MANAGEMENT_ITEMS;
    case "ADMIN":
      return [...MANAGEMENT_ITEMS, ...ADMIN_ONLY_ITEMS];
  }
}
