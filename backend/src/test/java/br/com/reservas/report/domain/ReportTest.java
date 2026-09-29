package br.com.reservas.report.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import br.com.reservas.shared.error.BusinessException;
import java.time.Instant;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/** RN-36: fluxo de status de um report (domínio puro, sem Spring). */
class ReportTest {

    private static final Instant NOW = Instant.parse("2026-11-10T13:00:00Z");

    private Report newOpenReport() {
        return new Report("OCR-2026-000001", UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(),
            UUID.randomUUID(), UUID.randomUUID(), "Ana Souza", ReportCategory.DAMAGE,
            "Churrasqueira com vazamento de gas", NOW);
    }

    @Test
    @DisplayName("RN-36: OPEN pode pular direto para RESOLVED (avanco, sem passar por todos os estagios)")
    void canSkipStagesForward() {
        Report report = newOpenReport();

        report.changeStatus(ReportStatus.RESOLVED, null, NOW.plusSeconds(60));

        assertThat(report.getStatus()).isEqualTo(ReportStatus.RESOLVED);
        assertThat(report.getResolvedAt()).isEqualTo(NOW.plusSeconds(60));
    }

    @Test
    @DisplayName("RN-36: nunca volta (IN_MAINTENANCE -> IN_REVIEW) -> 409 INVALID_STATUS_TRANSITION")
    void cannotGoBackward() {
        Report report = newOpenReport();
        report.changeStatus(ReportStatus.IN_MAINTENANCE, null, NOW);

        assertThatThrownBy(() -> report.changeStatus(ReportStatus.IN_REVIEW, null, NOW))
            .isInstanceOf(BusinessException.class)
            .satisfies(ex -> assertThat(((BusinessException) ex).code()).isEqualTo("INVALID_STATUS_TRANSITION"));
    }

    @Test
    @DisplayName("RN-36: status final (RESOLVED) nao aceita nenhuma nova transicao -> 409")
    void finalStatusRejectsAnyTransition() {
        Report report = newOpenReport();
        report.changeStatus(ReportStatus.RESOLVED, null, NOW);

        assertThatThrownBy(() -> report.changeStatus(ReportStatus.DISMISSED, "Justificativa valida", NOW))
            .isInstanceOf(BusinessException.class)
            .satisfies(ex -> assertThat(((BusinessException) ex).code()).isEqualTo("INVALID_STATUS_TRANSITION"));
    }

    @Test
    @DisplayName("RN-36: DISMISSED sem justificativa (>= 10 caracteres) -> 422 JUSTIFICATION_REQUIRED")
    void dismissWithoutJustificationIsRejected() {
        Report report = newOpenReport();

        assertThatThrownBy(() -> report.changeStatus(ReportStatus.DISMISSED, "curta", NOW))
            .isInstanceOf(BusinessException.class)
            .satisfies(ex -> assertThat(((BusinessException) ex).code()).isEqualTo("JUSTIFICATION_REQUIRED"));
    }

    @Test
    @DisplayName("RN-36: DISMISSED com justificativa valida a partir de qualquer status nao final")
    void dismissWithValidJustificationSucceeds() {
        Report report = newOpenReport();
        report.changeStatus(ReportStatus.IN_REVIEW, null, NOW);

        report.changeStatus(ReportStatus.DISMISSED, "Nao procede, area normal", NOW);

        assertThat(report.getStatus()).isEqualTo(ReportStatus.DISMISSED);
        assertThat(report.getStatusReason()).isEqualTo("Nao procede, area normal");
    }
}
