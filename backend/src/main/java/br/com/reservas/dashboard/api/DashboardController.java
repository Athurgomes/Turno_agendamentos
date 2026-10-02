package br.com.reservas.dashboard.api;

import br.com.reservas.auth.application.CurrentUserProvider;
import br.com.reservas.dashboard.application.DashboardHomeService;
import br.com.reservas.dashboard.application.DashboardMetricsService;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * `/dashboard` (docs/03 "Dashboard e exportação", RF-SIN-01/RF-DAS-01/02,
 * RN-01). Acesso S/A; UNIT recebe 403 FORBIDDEN_RESOURCE (autorização por
 * role, checada aqui via {@link PreAuthorize}). Controller fino: regra em
 * {@link DashboardHomeService} (página inicial) e {@link DashboardMetricsService}
 * (indicadores agregados, F8-1).
 */
@RestController
@RequestMapping("/api/v1/dashboard")
@PreAuthorize("hasAnyRole('SYNDIC', 'ADMIN')")
public class DashboardController {

    private final DashboardHomeService homeService;
    private final DashboardMetricsService metricsService;
    private final CurrentUserProvider currentUserProvider;

    public DashboardController(DashboardHomeService homeService, DashboardMetricsService metricsService,
        CurrentUserProvider currentUserProvider) {
        this.homeService = homeService;
        this.metricsService = metricsService;
        this.currentUserProvider = currentUserProvider;
    }

    /** `GET /dashboard/home` (F7-1, RF-SIN-01). */
    @GetMapping("/home")
    public DashboardHomeDto home() {
        UUID condominiumId = currentUserProvider.current().condominiumId();
        return DashboardHomeDto.of(homeService.home(condominiumId));
    }

    /** `GET /dashboard/summary` (F8-1, RF-DAS-01/02). */
    @GetMapping("/summary")
    public DashboardSummaryDto summary(
        @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
        @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to) {
        UUID condominiumId = currentUserProvider.current().condominiumId();
        return DashboardSummaryDto.of(metricsService.summary(condominiumId, from, to));
    }

    /** `GET /dashboard/reservations-by-month` (F8-1, RF-DAS-02): série de 12 meses até `to`. */
    @GetMapping("/reservations-by-month")
    public List<MonthlyReservationsDto> reservationsByMonth(
        @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to) {
        UUID condominiumId = currentUserProvider.current().condominiumId();
        return metricsService.reservationsByMonth(condominiumId, to).stream().map(MonthlyReservationsDto::of)
            .toList();
    }

    /** `GET /dashboard/areas` (F8-1, RF-DAS-02): reservas, ocupação, reports e custo por área. */
    @GetMapping("/areas")
    public List<AreaMetricDto> areas(
        @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
        @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to) {
        UUID condominiumId = currentUserProvider.current().condominiumId();
        return metricsService.areas(condominiumId, from, to).stream().map(AreaMetricDto::of).toList();
    }

    /** `GET /dashboard/demand-heatmap` (F8-1, RF-DAS-02): dia da semana x hora. */
    @GetMapping("/demand-heatmap")
    public List<HeatmapCellDto> demandHeatmap(
        @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
        @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to) {
        UUID condominiumId = currentUserProvider.current().condominiumId();
        return metricsService.demandHeatmap(condominiumId, from, to).stream().map(HeatmapCellDto::of).toList();
    }

    /** `GET /dashboard/top-units` (F8-1, RF-DAS-02): até 10 unidades que mais reservam. */
    @GetMapping("/top-units")
    public List<TopUnitDto> topUnits(
        @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
        @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to) {
        UUID condominiumId = currentUserProvider.current().condominiumId();
        return metricsService.topUnits(condominiumId, from, to).stream().map(TopUnitDto::of).toList();
    }
}
