package br.com.reservas.settings.application;

import java.time.LocalTime;

public record UpdateSettingsCommand(String condominiumName, String defaultPaymentWhatsapp, int minAdvanceDays,
    LocalTime nextDayWindowStart, LocalTime nextDayWindowEnd, int maxAdvanceDays, int maxActiveBookingsPerUnit,
    int residentCancelDeadlineHours, int reportWindowDays) {
}
