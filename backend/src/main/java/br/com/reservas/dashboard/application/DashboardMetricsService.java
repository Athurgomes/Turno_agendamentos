package br.com.reservas.dashboard.application;

import br.com.reservas.dashboard.domain.DashboardPeriod;
import br.com.reservas.dashboard.infra.DashboardMetricsRepository;
import br.com.reservas.dashboard.infra.ReportTotalsRow;
import br.com.reservas.dashboard.infra.ReservationTotalsRow;
import br.com.reservas.settings.application.CondominiumSettingsService;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Clock;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * `GET /dashboard/summary|reservations-by-month|areas|demand-heatmap|top-units`
 * (F8-1, RF-DAS-01/02, D-61/D-62): um método por indicador, cada um resolvendo
 * o {@link DashboardPeriod} (RF-DAS-01) e delegando a agregação para
 * {@link DashboardMetricsRepository} (SQL nativo somente leitura).
 */
@Service
public class DashboardMetricsService {

    private static final DateTimeFormatter MONTH_FORMAT = DateTimeFormatter.ofPattern("yyyy-MM");

    private final DashboardMetricsRepository repository;
    private final CondominiumSettingsService settingsService;
    private final Clock clock;

    public DashboardMetricsService(DashboardMetricsRepository repository, CondominiumSettingsService settingsService,
        Clock clock) {
        this.repository = repository;
        this.settingsService = settingsService;
        this.clock = clock;
    }

    @Transactional(readOnly = true)
    public DashboardSummaryView summary(UUID condominiumId, LocalDate from, LocalDate to) {
        String timezone = timezone();
        DashboardPeriod period = resolvePeriod(from, to, timezone);

        long activeUnits = repository.activeUnitsCount(condominiumId);
        long activeResidents = repository.activeResidentsCount(condominiumId);
        ReservationTotalsRow r = repository.reservationTotals(condominiumId, period.from(), period.to(), timezone);
        ReportTotalsRow rep = repository.reportTotals(condominiumId, period.from(), period.to(), timezone);
        List<DashboardSummaryView.CategoryCount> byCategory =
            repository.reportCountsByCategory(condominiumId, period.from(), period.to(), timezone);

        var reservations = new DashboardSummaryView.Reservations(r.total(), r.pendingPayment(), r.confirmed(),
            r.cancelled());
        var cancellations = new DashboardSummaryView.Cancellations(r.cancelledByResident(), r.cancelledByAdmin(),
            r.cancelledBySystem(), rate(r.cancelledByResident(), r.total()), rate(r.cancelledByAdmin(), r.total()));
        var amounts = new DashboardSummaryView.Amounts(money(r.amountConfirmed()), money(r.amountPending()));
        var reports = new DashboardSummaryView.Reports(rep.opened(), rep.resolved(), rep.open(),
            rep.averageResolutionHours(), byCategory);

        return new DashboardSummaryView(period.from(), period.to(), activeUnits, activeResidents, reservations,
            cancellations, amounts, reports, money(rep.maintenanceCost()));
    }

    /** `to` opcional (default mês corrente); série sempre com 12 meses, meses sem reserva com zeros. */
    @Transactional(readOnly = true)
    public List<MonthlyReservationsView> reservationsByMonth(UUID condominiumId, LocalDate to) {
        String timezone = timezone();
        LocalDate anchor = to != null ? to : LocalDate.now(clock.withZone(ZoneId.of(timezone)));
        LocalDate rangeEnd = anchor.withDayOfMonth(anchor.lengthOfMonth());
        LocalDate rangeStart = rangeEnd.withDayOfMonth(1).minusMonths(11);

        Map<String, MonthlyReservationsView> byMonth = repository
            .reservationsByMonth(condominiumId, rangeStart, rangeEnd, timezone).stream()
            .collect(java.util.stream.Collectors.toMap(MonthlyReservationsView::month, v -> v));

        List<MonthlyReservationsView> series = new ArrayList<>();
        LocalDate month = rangeStart;
        for (int i = 0; i < 12; i++) {
            String key = month.format(MONTH_FORMAT);
            series.add(byMonth.getOrDefault(key, new MonthlyReservationsView(key, 0, 0, 0, 0)));
            month = month.plusMonths(1);
        }
        return series;
    }

    @Transactional(readOnly = true)
    public List<AreaMetricView> areas(UUID condominiumId, LocalDate from, LocalDate to) {
        String timezone = timezone();
        DashboardPeriod period = resolvePeriod(from, to, timezone);
        return repository.areaMetrics(condominiumId, period.from(), period.to(), timezone);
    }

    @Transactional(readOnly = true)
    public List<HeatmapCellView> demandHeatmap(UUID condominiumId, LocalDate from, LocalDate to) {
        String timezone = timezone();
        DashboardPeriod period = resolvePeriod(from, to, timezone);
        return repository.demandHeatmap(condominiumId, period.from(), period.to(), timezone);
    }

    @Transactional(readOnly = true)
    public List<TopUnitView> topUnits(UUID condominiumId, LocalDate from, LocalDate to) {
        String timezone = timezone();
        DashboardPeriod period = resolvePeriod(from, to, timezone);
        return repository.topUnits(condominiumId, period.from(), period.to(), timezone);
    }

    private String timezone() {
        return settingsService.current().timezone();
    }

    private DashboardPeriod resolvePeriod(LocalDate from, LocalDate to, String timezone) {
        LocalDate today = LocalDate.now(clock.withZone(ZoneId.of(timezone)));
        return DashboardPeriod.resolve(from, to, today);
    }

    private static BigDecimal rate(long count, long total) {
        if (total == 0) {
            return BigDecimal.ZERO.setScale(4, RoundingMode.HALF_UP);
        }
        return BigDecimal.valueOf(count).divide(BigDecimal.valueOf(total), 4, RoundingMode.HALF_UP);
    }

    private static BigDecimal money(BigDecimal value) {
        return (value == null ? BigDecimal.ZERO : value).setScale(2, RoundingMode.HALF_UP);
    }
}
