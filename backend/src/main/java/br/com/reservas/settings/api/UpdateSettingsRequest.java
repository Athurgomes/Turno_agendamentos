package br.com.reservas.settings.api;

import br.com.reservas.settings.application.UpdateSettingsCommand;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import java.time.LocalTime;

/**
 * `PUT /admin/settings` (D-43): `timezone` e `slotMinutes` são só leitura
 * (D-15) e por isso não aparecem aqui.
 */
public record UpdateSettingsRequest(
    @NotBlank(message = "Nome do condomínio é obrigatório.") String condominiumName,
    String defaultPaymentWhatsapp,
    @Min(value = 0, message = "Deve ser maior ou igual a zero.") int minAdvanceDays,
    @NotNull(message = "Horário de início é obrigatório.") LocalTime nextDayWindowStart,
    @NotNull(message = "Horário de fim é obrigatório.") LocalTime nextDayWindowEnd,
    @Min(value = 0, message = "Deve ser maior ou igual a zero.") int maxAdvanceDays,
    @Min(value = 0, message = "Deve ser maior ou igual a zero.") int maxActiveBookingsPerUnit,
    @Min(value = 0, message = "Deve ser maior ou igual a zero.") int residentCancelDeadlineHours,
    @Min(value = 0, message = "Deve ser maior ou igual a zero.") int reportWindowDays) {

    public UpdateSettingsCommand toCommand() {
        return new UpdateSettingsCommand(condominiumName, defaultPaymentWhatsapp, minAdvanceDays,
            nextDayWindowStart, nextDayWindowEnd, maxAdvanceDays, maxActiveBookingsPerUnit,
            residentCancelDeadlineHours, reportWindowDays);
    }
}
