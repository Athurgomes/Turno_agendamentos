package br.com.reservas.area.domain;

import br.com.reservas.shared.error.BusinessException;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.LocalTime;
import java.util.UUID;
import org.springframework.http.HttpStatus;

/**
 * Horário de funcionamento por dia da semana (`area_opening_hours`, V5,
 * D-44). `dayOfWeek`: 1 = segunda ... 7 = domingo. No máximo um registro por
 * dia (unicidade no banco); dia sem registro = área fechada nesse dia.
 */
@Entity
@Table(name = "area_opening_hours")
public class OpeningHours {

    @Id
    @GeneratedValue
    private UUID id;

    @Column(name = "area_id", nullable = false)
    private UUID areaId;

    @Column(name = "day_of_week", nullable = false)
    private short dayOfWeek;

    @Column(name = "open_time", nullable = false)
    private LocalTime openTime;

    @Column(name = "close_time", nullable = false)
    private LocalTime closeTime;

    protected OpeningHours() {
        // JPA
    }

    public OpeningHours(UUID areaId, int dayOfWeek, LocalTime openTime, LocalTime closeTime) {
        if (dayOfWeek < 1 || dayOfWeek > 7) {
            throw validationError("Dia da semana precisa ser entre 1 (segunda) e 7 (domingo).");
        }
        if (!isStep(openTime) || !isStep(closeTime)) {
            throw validationError("Horário precisa ser em múltiplos de 30 minutos.");
        }
        if (!closeTime.isAfter(openTime)) {
            throw validationError("O horário de fechamento precisa ser depois do de abertura.");
        }
        this.areaId = areaId;
        this.dayOfWeek = (short) dayOfWeek;
        this.openTime = openTime;
        this.closeTime = closeTime;
    }

    // D-44: múltiplos de 30 minutos, sem segundos.
    private static boolean isStep(LocalTime time) {
        return time.getSecond() == 0 && (time.getMinute() == 0 || time.getMinute() == 30);
    }

    private static BusinessException validationError(String detail) {
        return new BusinessException("VALIDATION_ERROR", HttpStatus.UNPROCESSABLE_ENTITY, detail);
    }

    public UUID getId() {
        return id;
    }

    public UUID getAreaId() {
        return areaId;
    }

    public int getDayOfWeek() {
        return dayOfWeek;
    }

    public LocalTime getOpenTime() {
        return openTime;
    }

    public LocalTime getCloseTime() {
        return closeTime;
    }
}
