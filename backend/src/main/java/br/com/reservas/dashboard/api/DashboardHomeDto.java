package br.com.reservas.dashboard.api;

import br.com.reservas.dashboard.application.DashboardHomeView;
import java.util.List;

/** `GET /dashboard/home` (F7-1, RF-SIN-01, docs/03 "Dashboard e exportação"). */
public record DashboardHomeDto(List<ReservationSummaryDto> today, List<ReservationSummaryDto> next7Days,
    OpenReportsDto openReports, List<OverdueInspectionDto> overdueInspections) {

    public static DashboardHomeDto of(DashboardHomeView view) {
        return new DashboardHomeDto(view.today().stream().map(ReservationSummaryDto::from).toList(),
            view.next7Days().stream().map(ReservationSummaryDto::from).toList(),
            OpenReportsDto.of(view.openReports()),
            view.overdueInspections().stream().map(OverdueInspectionDto::of).toList());
    }
}
