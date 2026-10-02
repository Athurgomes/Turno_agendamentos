package br.com.reservas.dashboard.api;

import br.com.reservas.auth.application.CurrentUserProvider;
import br.com.reservas.dashboard.application.DashboardExportService;
import br.com.reservas.dashboard.application.ExportFile;
import java.time.LocalDate;
import java.util.UUID;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * `GET /exports/{type}` (F8-2, RF-DAS-03, docs/03 "Dashboard e exportação").
 * Acesso S/A (mesmo `@PreAuthorize` de {@link DashboardController}); UNIT
 * recebe 403 FORBIDDEN_RESOURCE. Controller fino: toda a regra (período,
 * colunas, rótulos pt-BR, formato) fica em {@link DashboardExportService}.
 */
@RestController
@RequestMapping("/api/v1/exports")
@PreAuthorize("hasAnyRole('SYNDIC', 'ADMIN')")
public class ExportController {

    private final DashboardExportService exportService;
    private final CurrentUserProvider currentUserProvider;

    public ExportController(DashboardExportService exportService, CurrentUserProvider currentUserProvider) {
        this.exportService = exportService;
        this.currentUserProvider = currentUserProvider;
    }

    @GetMapping("/{type}")
    public ResponseEntity<byte[]> export(@PathVariable String type, @RequestParam String format,
        @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
        @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to) {
        UUID condominiumId = currentUserProvider.current().condominiumId();
        ExportFile file = exportService.export(condominiumId, type, format, from, to);
        return ResponseEntity.ok()
            .contentType(MediaType.parseMediaType(file.contentType()))
            .header(HttpHeaders.CONTENT_DISPOSITION,
                ContentDisposition.attachment().filename(file.filename()).build().toString())
            .body(file.content());
    }
}
