package br.com.reservas.report.domain;

/**
 * RN-36: fluxo de status de um report. A ordem declarada aqui (índice do
 * enum) é a ordem de avanço permitida por {@link Report#changeStatus}: só se
 * avança de {@link #OPEN} a {@link #RESOLVED}, nunca se volta; {@link #DISMISSED}
 * é alcançável de qualquer status não final, com justificativa.
 */
public enum ReportStatus {
    OPEN,
    IN_REVIEW,
    IN_MAINTENANCE,
    RESOLVED,
    DISMISSED;

    public boolean isFinal() {
        return this == RESOLVED || this == DISMISSED;
    }
}
