package br.com.reservas.settings.application;

import java.time.LocalTime;

/**
 * F4 (`reservation`): "motor de regras" (D-12) num unico snapshot imutavel,
 * usado por RN-18..24, RN-30 e RN-34. Fuso do condominio incluso para nao
 * obrigar outro lookup so por isso.
 */
public record RuleSettingsSnapshot(String timezone, int minAdvanceDays, LocalTime nextDayWindowStart,
    LocalTime nextDayWindowEnd, int maxAdvanceDays, int maxActiveBookingsPerUnit, int residentCancelDeadlineHours,
    int slotMinutes, int reportWindowDays) {
}
