package br.com.reservas.report.api;

import br.com.reservas.auth.application.CurrentUser;
import br.com.reservas.auth.application.CurrentUserProvider;
import br.com.reservas.auth.domain.Role;
import br.com.reservas.report.application.AdminReportView;
import br.com.reservas.report.application.ReportFilter;
import br.com.reservas.report.application.ReportService;
import br.com.reservas.report.application.UploadedPhoto;
import br.com.reservas.report.domain.ReportCategory;
import br.com.reservas.report.domain.ReportStatus;
import br.com.reservas.shared.error.BusinessException;
import br.com.reservas.shared.web.PageDto;
import jakarta.validation.Valid;
import java.io.IOException;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.web.PageableDefault;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

/** `/reports` (docs/03 "Reports", D-50). Controller fino: regra em {@link ReportService}. */
@RestController
@RequestMapping("/api/v1/reports")
@PreAuthorize("hasAnyRole('SYNDIC', 'ADMIN')")
public class ReportController {

    private final ReportService reportService;
    private final CurrentUserProvider currentUserProvider;

    public ReportController(ReportService reportService, CurrentUserProvider currentUserProvider) {
        this.reportService = reportService;
        this.currentUserProvider = currentUserProvider;
    }

    @GetMapping
    public PageDto<AdminReportDto> search(@RequestParam(required = false) ReportStatus status,
        @RequestParam(required = false) UUID areaId, @RequestParam(required = false) ReportCategory category,
        @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
        @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to,
        @PageableDefault(sort = "createdAt", direction = Sort.Direction.DESC) Pageable pageable) {
        Page<AdminReportView> page = reportService.search(new ReportFilter(status, areaId, category, from, to),
            pageable);
        return PageDto.of(page, AdminReportDto::of);
    }

    // RN-01: método sobrescreve a classe (`hasAnyRole('SYNDIC', 'ADMIN')`) porque a mesma rota
    // também atende UNIT (docs/03); a checagem de dono (morador só o próprio report) é feita no
    // serviço, não aqui.
    @GetMapping("/{id}")
    @PreAuthorize("hasAnyRole('UNIT', 'SYNDIC', 'ADMIN')")
    public Object detail(@PathVariable UUID id) {
        CurrentUser user = currentUserProvider.current();
        if (user.role() == Role.UNIT) {
            return ReportDto.of(reportService.findMine(user.unitId(), id));
        }
        return AdminReportDto.of(reportService.findForAdmin(id));
    }

    @PatchMapping("/{id}/status")
    public AdminReportDto changeStatus(@PathVariable UUID id, @Valid @RequestBody ChangeReportStatusRequest request) {
        CurrentUser user = currentUserProvider.current();
        AdminReportView view = reportService.changeStatus(id, user.id(), user.role().name(), request.toCommand());
        return AdminReportDto.of(view);
    }

    @PostMapping("/{id}/comments")
    @ResponseStatus(HttpStatus.CREATED)
    public AdminReportDto addComment(@PathVariable UUID id, @Valid @RequestBody AddCommentRequest request) {
        CurrentUser user = currentUserProvider.current();
        AdminReportView view = reportService.addComment(id, user.id(), user.role().name(), request.toCommand());
        return AdminReportDto.of(view);
    }

    @PostMapping(value = "/{id}/photos", consumes = "multipart/form-data")
    @ResponseStatus(HttpStatus.CREATED)
    public AdminReportDto addPhotos(@PathVariable UUID id, @RequestPart("photos") List<MultipartFile> photos) {
        CurrentUser user = currentUserProvider.current();
        AdminReportView view = reportService.addPhotos(id, user.id(), user.role().name(), toUploadedPhotos(photos));
        return AdminReportDto.of(view);
    }

    @GetMapping("/summary")
    public ReportSummaryDto summary() {
        return new ReportSummaryDto(reportService.openCount());
    }

    private static List<UploadedPhoto> toUploadedPhotos(List<MultipartFile> files) {
        return files.stream().map(ReportController::readBytes).toList();
    }

    private static UploadedPhoto readBytes(MultipartFile file) {
        try {
            return new UploadedPhoto(file.getBytes());
        } catch (IOException e) {
            throw new BusinessException("INVALID_FILE", HttpStatus.UNPROCESSABLE_ENTITY,
                "Não foi possível ler o arquivo enviado.");
        }
    }
}
