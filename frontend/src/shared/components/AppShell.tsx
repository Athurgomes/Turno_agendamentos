/**
 * App shell mobile-first (>= 360px, RNF-05): menu compacto (botão + lista) no
 * celular, barra lateral fixa no desktop. Mesmos itens de menu por perfil
 * (`navItemsForRole`), nome da conta e "Sair" (`POST /auth/logout`).
 */
import { useState } from "react";
import { NavLink, Outlet } from "react-router-dom";
import { useSession } from "../../features/auth/useSession";
import { PrivacyNotice } from "../../features/auth/PrivacyNotice";
import { TempPasswordBanner } from "../../features/auth/TempPasswordBanner";
import { useConfirmationsNavInfo } from "../../features/admin/payments/hooks";
import { useReportsNavInfo } from "../../features/reports/hooks";
import { AccountMenu } from "./AccountMenu";
import { navItemsForRole, type NavItem } from "./navItems";

const CONFIRMATIONS_PATH = "/admin/confirmacoes";
const REPORTS_PATH = "/reports";

/**
 * Aplica a visibilidade condicional e o contador do item "Confirmações"
 * (RF-PAG-01): só aparece para ADMIN com área paga no catálogo ou pendência.
 */
function applyConfirmationsNavInfo(
  items: NavItem[],
  info: { visible: boolean; count: number },
): NavItem[] {
  return items
    .filter((item) => item.to !== CONFIRMATIONS_PATH || info.visible)
    .map((item) =>
      item.to === CONFIRMATIONS_PATH && info.count > 0
        ? { ...item, label: `${item.label} (${info.count})` }
        : item,
    );
}

/** Contador de reports abertos no item "Reports" (RF-REP-02), só para SYNDIC/ADMIN. */
function applyReportsNavInfo(items: NavItem[], count: number): NavItem[] {
  return items.map((item) =>
    item.to === REPORTS_PATH && count > 0 ? { ...item, label: `${item.label} (${count})` } : item,
  );
}

function linkClassName({ isActive }: { isActive: boolean }) {
  return [
    "block rounded-md px-3 py-2 text-sm font-medium",
    isActive
      ? "bg-primary-100 text-primary-800"
      : "text-neutral-700 hover:bg-neutral-200",
  ].join(" ");
}

function MenuLinks({
  items,
  onNavigate,
}: {
  items: NavItem[];
  onNavigate: () => void;
}) {
  return (
    <ul className="space-y-1 p-4">
      {items.map((item) => (
        <li key={item.to}>
          <NavLink to={item.to} className={linkClassName} onClick={onNavigate}>
            {item.label}
          </NavLink>
        </li>
      ))}
    </ul>
  );
}

export function AppShell() {
  const { user } = useSession();
  const [menuOpen, setMenuOpen] = useState(false);
  const confirmationsNavInfo = useConfirmationsNavInfo();
  const reportsNavInfo = useReportsNavInfo();

  // RequireAuth garante sessão antes de renderizar o shell.
  if (!user) return null;

  const items = applyReportsNavInfo(
    applyConfirmationsNavInfo(navItemsForRole(user.role), confirmationsNavInfo),
    reportsNavInfo.count,
  );

  return (
    <div className="min-h-screen bg-neutral-50 lg:flex">
      <a
        href="#conteudo-principal"
        className="sr-only focus:not-sr-only focus:fixed focus:left-2 focus:top-2 focus:z-50 focus:rounded-md focus:bg-primary-600 focus:px-4 focus:py-2 focus:text-white"
      >
        Pular para o conteúdo
      </a>

      {/* Barra lateral — desktop (>= 1024px) */}
      <aside className="hidden w-64 shrink-0 flex-col border-r border-neutral-200 bg-neutral-100 lg:flex">
        <div className="p-4">
          <p className="text-lg font-semibold text-neutral-900">Reservas</p>
        </div>
        <nav aria-label="Navegação principal" className="flex-1">
          <MenuLinks items={items} onNavigate={() => {}} />
        </nav>
        <AccountMenu />
      </aside>

      <div className="flex min-h-screen flex-1 flex-col">
        {/* Barra superior — mobile/tablet, menu compacto */}
        <header className="flex items-center justify-between border-b border-neutral-200 bg-neutral-0 p-4 lg:hidden">
          <span className="text-lg font-semibold text-neutral-900">
            Reservas
          </span>
          <button
            type="button"
            aria-expanded={menuOpen}
            aria-controls="menu-mobile"
            onClick={() => setMenuOpen((open) => !open)}
            className="rounded-md border border-neutral-300 px-3 py-2 text-sm font-medium text-neutral-700 hover:bg-neutral-100"
          >
            {menuOpen ? "Fechar menu" : "Abrir menu"}
          </button>
        </header>

        {menuOpen && (
          <nav
            id="menu-mobile"
            aria-label="Navegação principal"
            className="border-b border-neutral-200 bg-neutral-0 lg:hidden"
          >
            <MenuLinks items={items} onNavigate={() => setMenuOpen(false)} />
            <AccountMenu />
          </nav>
        )}

        {/*
          Banners globais de largura total: aviso de privacidade no primeiro
          acesso (RNF-01) e senha temporária (RF-AUT-03). A faixa "Horário
          simulado" (docs/08 §3, D-32) fica em `App.tsx`, acima do roteador,
          para cobrir também o `/login`, que não usa este shell.
        */}
        <div id="banner-slot">
          <PrivacyNotice />
          <TempPasswordBanner />
        </div>

        <main id="conteudo-principal" className="flex-1 p-4 sm:p-6">
          <Outlet />
        </main>
      </div>
    </div>
  );
}
