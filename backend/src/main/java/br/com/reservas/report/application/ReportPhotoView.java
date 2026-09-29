package br.com.reservas.report.application;

import java.net.URI;
import java.time.Instant;
import java.util.UUID;

public record ReportPhotoView(UUID id, URI url, String stage, Instant createdAt) {
}
