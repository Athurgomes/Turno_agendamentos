package br.com.reservas.settings.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.LocalTime;
import java.util.UUID;

/**
 * `condominium_settings` (V1, D-12): "motor de regras" v0. `slot_minutes` nao
 * e editavel pela API (D-15); os demais campos sao os parametros da RF-CFG.
 */
@Entity
@Table(name = "condominium_settings")
public class CondominiumSettings {

    @Id
    @Column(name = "condominium_id")
    private UUID condominiumId;

    @Column(name = "min_advance_days", nullable = false)
    private int minAdvanceDays;

    @Column(name = "next_day_window_start", nullable = false)
    private LocalTime nextDayWindowStart;

    @Column(name = "next_day_window_end", nullable = false)
    private LocalTime nextDayWindowEnd;

    @Column(name = "max_advance_days", nullable = false)
    private int maxAdvanceDays;

    @Column(name = "max_active_bookings_per_unit", nullable = false)
    private int maxActiveBookingsPerUnit;

    @Column(name = "resident_cancel_deadline_hours", nullable = false)
    private int residentCancelDeadlineHours;

    @Column(name = "slot_minutes", nullable = false)
    private int slotMinutes;

    @Column(name = "report_window_days", nullable = false)
    private int reportWindowDays;

    protected CondominiumSettings() {
        // JPA
    }

    public void update(int minAdvanceDays, LocalTime nextDayWindowStart, LocalTime nextDayWindowEnd,
        int maxAdvanceDays, int maxActiveBookingsPerUnit, int residentCancelDeadlineHours, int reportWindowDays) {
        this.minAdvanceDays = minAdvanceDays;
        this.nextDayWindowStart = nextDayWindowStart;
        this.nextDayWindowEnd = nextDayWindowEnd;
        this.maxAdvanceDays = maxAdvanceDays;
        this.maxActiveBookingsPerUnit = maxActiveBookingsPerUnit;
        this.residentCancelDeadlineHours = residentCancelDeadlineHours;
        this.reportWindowDays = reportWindowDays;
    }

    public UUID getCondominiumId() {
        return condominiumId;
    }

    public int getMinAdvanceDays() {
        return minAdvanceDays;
    }

    public LocalTime getNextDayWindowStart() {
        return nextDayWindowStart;
    }

    public LocalTime getNextDayWindowEnd() {
        return nextDayWindowEnd;
    }

    public int getMaxAdvanceDays() {
        return maxAdvanceDays;
    }

    public int getMaxActiveBookingsPerUnit() {
        return maxActiveBookingsPerUnit;
    }

    public int getResidentCancelDeadlineHours() {
        return residentCancelDeadlineHours;
    }

    public int getSlotMinutes() {
        return slotMinutes;
    }

    public int getReportWindowDays() {
        return reportWindowDays;
    }
}
