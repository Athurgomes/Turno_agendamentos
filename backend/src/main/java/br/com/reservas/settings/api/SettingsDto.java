package br.com.reservas.settings.api;

import br.com.reservas.settings.application.SettingsSnapshot;
import java.time.LocalTime;

/** `SettingsDto` (docs/03, `GET/PUT /admin/settings`) — inclui `defaultPaymentWhatsapp`. */
public record SettingsDto(String condominiumName, String timezone, String defaultPaymentWhatsapp,
    int minAdvanceDays, LocalTime nextDayWindowStart, LocalTime nextDayWindowEnd, int maxAdvanceDays,
    int maxActiveBookingsPerUnit, int residentCancelDeadlineHours, int slotMinutes, int reportWindowDays) {

    public static SettingsDto from(SettingsSnapshot s) {
        return new SettingsDto(s.condominiumName(), s.timezone(), s.defaultPaymentWhatsapp(), s.minAdvanceDays(),
            s.nextDayWindowStart(), s.nextDayWindowEnd(), s.maxAdvanceDays(), s.maxActiveBookingsPerUnit(),
            s.residentCancelDeadlineHours(), s.slotMinutes(), s.reportWindowDays());
    }
}
