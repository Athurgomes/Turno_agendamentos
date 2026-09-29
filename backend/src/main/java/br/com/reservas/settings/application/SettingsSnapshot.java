package br.com.reservas.settings.application;

import java.time.LocalTime;

/** `SettingsDto` (docs/03) por inteiro; `defaultPaymentWhatsapp` some em `/settings/public`. */
public record SettingsSnapshot(String condominiumName, String timezone, String defaultPaymentWhatsapp,
    int minAdvanceDays, LocalTime nextDayWindowStart, LocalTime nextDayWindowEnd, int maxAdvanceDays,
    int maxActiveBookingsPerUnit, int residentCancelDeadlineHours, int slotMinutes, int reportWindowDays) {
}
