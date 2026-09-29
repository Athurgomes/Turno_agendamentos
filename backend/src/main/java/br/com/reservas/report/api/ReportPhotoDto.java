package br.com.reservas.report.api;

import br.com.reservas.report.application.ReportPhotoView;
import java.time.Instant;
import java.util.UUID;

public record ReportPhotoDto(UUID id, String url, String stage, Instant createdAt) {

    public static ReportPhotoDto of(ReportPhotoView v) {
        return new ReportPhotoDto(v.id(), v.url().toString(), v.stage(), v.createdAt());
    }
}
