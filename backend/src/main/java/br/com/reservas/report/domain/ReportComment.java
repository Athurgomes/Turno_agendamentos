package br.com.reservas.report.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;

/** Comentário de S/A num report (`report_comment`, V7, RF-REP-03/D-55). */
@Entity
@Table(name = "report_comment")
public class ReportComment {

    @Id
    @GeneratedValue
    private UUID id;

    @Column(name = "report_id", nullable = false)
    private UUID reportId;

    @Column(name = "author_id", nullable = false)
    private UUID authorId;

    @Column(nullable = false)
    private String text;

    @Column(name = "visible_to_resident", nullable = false)
    private boolean visibleToResident;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    protected ReportComment() {
        // JPA
    }

    public ReportComment(UUID reportId, UUID authorId, String text, boolean visibleToResident, Instant createdAt) {
        this.reportId = reportId;
        this.authorId = authorId;
        this.text = text;
        this.visibleToResident = visibleToResident;
        this.createdAt = createdAt;
    }

    public UUID getId() {
        return id;
    }

    public UUID getReportId() {
        return reportId;
    }

    public UUID getAuthorId() {
        return authorId;
    }

    public String getText() {
        return text;
    }

    public boolean isVisibleToResident() {
        return visibleToResident;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }
}
