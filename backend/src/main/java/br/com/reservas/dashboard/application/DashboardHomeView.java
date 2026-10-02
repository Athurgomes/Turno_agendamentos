package br.com.reservas.dashboard.application;

import br.com.reservas.shared.reservation.ReservationSummary;
import java.util.List;

/** `GET /dashboard/home` (F7-1, RF-SIN-01): página inicial do síndico. */
public record DashboardHomeView(List<ReservationSummary> today, List<ReservationSummary> next7Days,
    OpenReportsView openReports, List<OverdueInspectionView> overdueInspections) {
}
