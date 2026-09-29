package br.com.reservas.report.api;

import br.com.reservas.auth.application.CurrentUserProvider;
import br.com.reservas.report.application.ReportService;
import java.util.List;
import java.util.UUID;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** `GET /me/reports` (docs/03, RF-REP-04, RN-01): só reports da própria unidade. */
@RestController
@RequestMapping("/api/v1/me/reports")
@PreAuthorize("hasRole('UNIT')")
public class MyReportsController {

    private final ReportService reportService;
    private final CurrentUserProvider currentUserProvider;

    public MyReportsController(ReportService reportService, CurrentUserProvider currentUserProvider) {
        this.reportService = reportService;
        this.currentUserProvider = currentUserProvider;
    }

    @GetMapping
    public List<ReportDto> list() {
        UUID unitId = currentUserProvider.current().unitId();
        return reportService.listMine(unitId).stream().map(ReportDto::of).toList();
    }
}
