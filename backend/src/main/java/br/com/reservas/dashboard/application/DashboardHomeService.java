package br.com.reservas.dashboard.application;

import br.com.reservas.area.application.AreaInspectionService;
import br.com.reservas.area.application.AreaService;
import br.com.reservas.area.domain.Area;
import br.com.reservas.report.application.AdminReportView;
import br.com.reservas.report.application.ReportService;
import br.com.reservas.reservation.application.ReservationService;
import br.com.reservas.settings.application.CondominiumSettingsService;
import br.com.reservas.settings.application.RuleSettingsSnapshot;
import java.time.Clock;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.temporal.ChronoUnit;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * `GET /dashboard/home` (F7-1, RF-SIN-01, D-61): não é agregação — compõe os
 * serviços públicos de `reservation`, `report` e `area` (nunca os
 * repositórios deles), diferente do resto do módulo `dashboard` (F8, D-61),
 * que vai concentrar as consultas agregadas em SQL nativo.
 */
@Service
public class DashboardHomeService {

    // RF-SIN-01: limiar fixo da própria página ("mais antiga que 30 dias"), não é parâmetro configurável.
    private static final int OVERDUE_INSPECTION_DAYS = 30;
    private static final int OPEN_REPORTS_ITEMS_LIMIT = 5;

    // "sem vistoria" (null) no topo, depois a mais antiga primeiro.
    private static final Comparator<OverdueInspectionView> OVERDUE_ORDER = Comparator
        .comparing((OverdueInspectionView v) -> v.lastInspectionAt() != null)
        .thenComparing(v -> v.lastInspectionAt() == null ? LocalDate.MIN : v.lastInspectionAt());

    private final ReservationService reservationService;
    private final ReportService reportService;
    private final AreaService areaService;
    private final AreaInspectionService inspectionService;
    private final CondominiumSettingsService settingsService;
    private final Clock clock;

    public DashboardHomeService(ReservationService reservationService, ReportService reportService,
        AreaService areaService, AreaInspectionService inspectionService, CondominiumSettingsService settingsService,
        Clock clock) {
        this.reservationService = reservationService;
        this.reportService = reportService;
        this.areaService = areaService;
        this.inspectionService = inspectionService;
        this.settingsService = settingsService;
        this.clock = clock;
    }

    // F9-3: NAO readOnly. `activeSummariesBetween` (RN-31, expirePendingIfNeeded) faz UPDATE; como
    // propagation e REQUIRED (default), ele roda dentro DESTA transacao, entao o readOnly daqui e
    // quem manda no `SET TRANSACTION READ ONLY` do Postgres - "cannot execute UPDATE in a read-only
    // transaction" (bug do ensaio, docs/12).
    @Transactional
    public DashboardHomeView home(UUID condominiumId) {
        RuleSettingsSnapshot rules = settingsService.current();
        ZoneId zone = ZoneId.of(rules.timezone());
        LocalDate today = LocalDate.now(clock.withZone(zone));

        var todayReservations = reservationService.activeSummariesBetween(today, today.plusDays(1));
        var next7Days = reservationService.activeSummariesBetween(today.plusDays(1), today.plusDays(8));

        OpenReportsView openReports = openReports();
        List<OverdueInspectionView> overdueInspections = overdueInspections(condominiumId, today);

        return new DashboardHomeView(todayReservations, next7Days, openReports, overdueInspections);
    }

    private OpenReportsView openReports() {
        long count = reportService.openCount();
        List<OpenReportItemView> items = reportService.oldestOpen(OPEN_REPORTS_ITEMS_LIMIT).stream()
            .map(DashboardHomeService::toOpenReportItem)
            .toList();
        return new OpenReportsView(count, items);
    }

    private static OpenReportItemView toOpenReportItem(AdminReportView v) {
        return new OpenReportItemView(v.base().id(), v.base().code(), v.base().areaName(), v.unitIdentifier(),
            v.base().category(), v.base().status(), v.base().createdAt());
    }

    private List<OverdueInspectionView> overdueInspections(UUID condominiumId, LocalDate today) {
        List<Area> areas = areaService.catalog(condominiumId, null, null);
        Map<UUID, LocalDate> lastInspectionByAreaId = inspectionService
            .lastInspectionByAreaIds(areas.stream().map(Area::getId).toList());
        return areas.stream()
            .map(area -> toOverdueView(area, lastInspectionByAreaId.get(area.getId()), today))
            .filter(v -> v != null)
            .sorted(OVERDUE_ORDER)
            .toList();
    }

    // RF-SIN-01: sem vistoria -> sempre no topo; com vistoria -> só entra se mais antiga que 30 dias.
    private static OverdueInspectionView toOverdueView(Area area, LocalDate lastInspectionAt, LocalDate today) {
        if (lastInspectionAt == null) {
            return new OverdueInspectionView(area.getId(), area.getName(), area.getStatus().name(), null, null);
        }
        long days = ChronoUnit.DAYS.between(lastInspectionAt, today);
        if (days <= OVERDUE_INSPECTION_DAYS) {
            return null;
        }
        return new OverdueInspectionView(area.getId(), area.getName(), area.getStatus().name(), lastInspectionAt,
            days);
    }
}
