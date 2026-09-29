package br.com.reservas.report.api;

import br.com.reservas.report.application.ReportCommentView;
import java.time.Instant;
import java.util.UUID;

public record ReportCommentDto(UUID id, String text, String authorName, Instant createdAt,
    boolean visibleToResident) {

    public static ReportCommentDto of(ReportCommentView v) {
        return new ReportCommentDto(v.id(), v.text(), v.authorName(), v.createdAt(), v.visibleToResident());
    }
}
