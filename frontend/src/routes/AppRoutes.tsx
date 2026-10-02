import { Navigate, Route, Routes } from "react-router-dom";
import { AppShell } from "../shared/components/AppShell";
import { useSession } from "../features/auth/useSession";
import { LoginPage } from "../features/auth/LoginPage";
import { ChangePasswordPage } from "../features/auth/ChangePasswordPage";
import { UnitsListPage } from "../features/units/UnitsListPage";
import { CreateUnitPage, EditUnitPage } from "../features/units/UnitFormPage";
import { TransferOwnershipPage } from "../features/units/TransferOwnershipPage";
import { MyUnitPage } from "../features/units/MyUnitPage";
import { SyndicsPage } from "../features/admin/syndics/SyndicsPage";
import { SettingsPage } from "../features/admin/settings/SettingsPage";
import { AreasCatalogPage } from "../features/areas/AreasCatalogPage";
import { AreaDetailPage } from "../features/areas/AreaDetailPage";
import { CreateAreaPage, EditAreaPage } from "../features/areas/AreaFormPage";
import { AreaPhotosPage } from "../features/areas/AreaPhotosPage";
import { AreaInspectionsPage } from "../features/areas/AreaInspectionsPage";
import { AreaComparePage } from "../features/areas/AreaComparePage";
import { ReservationWizardPage } from "../features/reservations/ReservationWizardPage";
import { MyReservationsPage } from "../features/reservations/MyReservationsPage";
import { AgendaPage } from "../features/reservations/AgendaPage";
import { ConfirmationsPage } from "../features/admin/payments/ConfirmationsPage";
import { ReportFormPage } from "../features/reports/ReportFormPage";
import { MyReportsPage } from "../features/reports/MyReportsPage";
import { ReportsBoxPage } from "../features/reports/ReportsBoxPage";
import { SyndicHomePage } from "../features/syndic/SyndicHomePage";
import { DashboardPage } from "../features/dashboard/DashboardPage";
import { NotFound } from "./NotFound";
import { RequireAuth } from "./RequireAuth";
import { RequireRole } from "./RequireRole";
import { homeForRole } from "./roleHome";
import { SessionExpiredRedirect } from "./SessionExpiredRedirect";

function HomeRedirect() {
  const { user } = useSession();
  // RequireAuth garante sessão antes desta rota renderizar; guarda só para o narrowing de tipos.
  if (!user) return null;
  return <Navigate to={homeForRole(user.role)} replace />;
}

/**
 * Mapa de rotas (F0-6). Login e alteração de senha reais chegaram na F1-5
 * (RF-AUT); as demais telas seguem como placeholder até a fase que as
 * implementa (RF correspondente).
 */
export function AppRoutes() {
  return (
    <>
      <SessionExpiredRedirect />
      <Routes>
        <Route path="/login" element={<LoginPage />} />

        <Route element={<RequireAuth />}>
          <Route element={<AppShell />}>
            <Route path="/" element={<HomeRedirect />} />
            <Route path="/alterar-senha" element={<ChangePasswordPage />} />
            <Route path="/areas" element={<AreasCatalogPage />} />
            <Route path="/areas/:id" element={<AreaDetailPage />} />

            <Route element={<RequireRole roles={["UNIT"]} />}>
              <Route path="/areas/:id/reservar" element={<ReservationWizardPage />} />
              <Route path="/minhas-reservas" element={<MyReservationsPage />} />
              <Route path="/minhas-reservas/:id/reportar" element={<ReportFormPage />} />
              <Route path="/minha-unidade" element={<MyUnitPage />} />
              <Route path="/meus-reports" element={<MyReportsPage />} />
            </Route>

            <Route element={<RequireRole roles={["SYNDIC", "ADMIN"]} />}>
              <Route path="/painel" element={<SyndicHomePage />} />
              <Route path="/agenda" element={<AgendaPage />} />
              <Route path="/reports" element={<ReportsBoxPage />} />
              <Route path="/dashboard" element={<DashboardPage />} />
              <Route path="/areas/:id/editar" element={<EditAreaPage />} />
              <Route path="/areas/:id/fotos" element={<AreaPhotosPage />} />
              <Route path="/areas/:id/vistorias" element={<AreaInspectionsPage />} />
              <Route path="/areas/:id/comparar" element={<AreaComparePage />} />
            </Route>

            <Route element={<RequireRole roles={["ADMIN"]} />}>
              <Route path="/areas/nova" element={<CreateAreaPage />} />
              <Route path="/admin/unidades" element={<UnitsListPage />} />
              <Route path="/admin/unidades/nova" element={<CreateUnitPage />} />
              <Route path="/admin/unidades/:id/editar" element={<EditUnitPage />} />
              <Route
                path="/admin/unidades/:id/transferir"
                element={<TransferOwnershipPage />}
              />
              <Route path="/admin/sindicos" element={<SyndicsPage />} />
              <Route path="/admin/confirmacoes" element={<ConfirmationsPage />} />
              <Route path="/admin/configuracoes" element={<SettingsPage />} />
            </Route>

            <Route path="*" element={<NotFound />} />
          </Route>
        </Route>
      </Routes>
    </>
  );
}
