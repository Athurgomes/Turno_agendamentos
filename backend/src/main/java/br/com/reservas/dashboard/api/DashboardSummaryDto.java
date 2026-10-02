package br.com.reservas.dashboard.api;

import br.com.reservas.dashboard.application.DashboardSummaryView;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

/** `GET /dashboard/summary` (RF-DAS-01/02, docs/03 "Dashboard e exportação"). */
public record DashboardSummaryDto(LocalDate from, LocalDate to, long activeUnits, long activeResidents,
    ReservationsDto reservations, CancellationsDto cancellations, AmountsDto amounts, ReportsDto reports,
    BigDecimal maintenanceCost) {

    public record ReservationsDto(long total, long pendingPayment, long confirmed, long cancelled) {
    }

    public record CancellationsDto(long byResident, long byAdmin, long bySystem, BigDecimal residentRate,
        BigDecimal adminRate) {
    }

    public record AmountsDto(BigDecimal confirmed, BigDecimal pending) {
    }

    public record ReportsDto(long opened, long resolved, long open, BigDecimal averageResolutionHours,
        List<CategoryCountDto> byCategory) {
    }

    public record CategoryCountDto(String category, long count) {
    }

    public static DashboardSummaryDto of(DashboardSummaryView view) {
        return new DashboardSummaryDto(view.from(), view.to(), view.activeUnits(), view.activeResidents(),
            new ReservationsDto(view.reservations().total(), view.reservations().pendingPayment(),
                view.reservations().confirmed(), view.reservations().cancelled()),
            new CancellationsDto(view.cancellations().byResident(), view.cancellations().byAdmin(),
                view.cancellations().bySystem(), view.cancellations().residentRate(),
                view.cancellations().adminRate()),
            new AmountsDto(view.amounts().confirmed(), view.amounts().pending()),
            new ReportsDto(view.reports().opened(), view.reports().resolved(), view.reports().open(),
                view.reports().averageResolutionHours(),
                view.reports().byCategory().stream()
                    .map(c -> new CategoryCountDto(c.category(), c.count()))
                    .toList()),
            view.maintenanceCost());
    }
}
