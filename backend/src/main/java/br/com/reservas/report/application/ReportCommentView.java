package br.com.reservas.report.application;

import java.time.Instant;
import java.util.UUID;

public record ReportCommentView(UUID id, String text, String authorName, Instant createdAt,
    boolean visibleToResident) {
}
