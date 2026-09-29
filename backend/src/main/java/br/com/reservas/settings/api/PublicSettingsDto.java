package br.com.reservas.settings.api;

import br.com.reservas.settings.application.SettingsSnapshot;
import java.time.LocalTime;

/** `GET /settings/public` (docs/03): mesmos campos do `SettingsDto`, sem `defaultPaymentWhatsapp`. */
public record PublicSettingsDto(String condominiumName, String timezone, int minAdvanceDays,
    LocalTime nextDayWindowStart, LocalTime nextDayWindowEnd, int maxAdvanceDays, int maxActiveBookingsPerUnit,
    int residentCancelDeadlineHours, int slotMinutes, int reportWindowDays) {

    public static PublicSettingsDto from(SettingsSnapshot s) {
        return new PublicSettingsDto(s.condominiumName(), s.timezone(), s.minAdvanceDays(), s.nextDayWindowStart(),
            s.nextDayWindowEnd(), s.maxAdvanceDays(), s.maxActiveBookingsPerUnit(), s.residentCancelDeadlineHours(),
            s.slotMinutes(), s.reportWindowDays());
    }
}
