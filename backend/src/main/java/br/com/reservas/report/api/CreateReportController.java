package br.com.reservas.report.api;

import br.com.reservas.auth.application.CurrentUser;
import br.com.reservas.auth.application.CurrentUserProvider;
import br.com.reservas.report.application.ReportService;
import br.com.reservas.report.application.ReportView;
import br.com.reservas.report.application.UploadedPhoto;
import br.com.reservas.shared.error.BusinessException;
import jakarta.validation.Valid;
import java.io.IOException;
import java.util.List;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

/**
 * `POST /me/reservations/{reservationId}/reports` (docs/03, RF-REP-01, RN-01,
 * RN-34, RN-35). Controller fino: toda regra em {@link ReportService}.
 */
@RestController
@RequestMapping("/api/v1/me/reservations/{reservationId}/reports")
@PreAuthorize("hasRole('UNIT')")
public class CreateReportController {

    private final ReportService reportService;
    private final CurrentUserProvider currentUserProvider;

    public CreateReportController(ReportService reportService, CurrentUserProvider currentUserProvider) {
        this.reportService = reportService;
        this.currentUserProvider = currentUserProvider;
    }

    @PostMapping(consumes = "multipart/form-data")
    @ResponseStatus(HttpStatus.CREATED)
    public ReportDto create(@PathVariable UUID reservationId, @Valid @RequestPart("data") CreateReportRequest data,
        @RequestPart(value = "photos", required = false) List<MultipartFile> photos) {
        CurrentUser user = currentUserProvider.current();
        ReportView view = reportService.create(user.unitId(), user.id(), reservationId, data.toCommand(),
            toUploadedPhotos(photos));
        return ReportDto.of(view);
    }

    private static List<UploadedPhoto> toUploadedPhotos(List<MultipartFile> files) {
        if (files == null) {
            return List.of();
        }
        return files.stream().map(CreateReportController::readBytes).toList();
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
