package br.com.reservas.area.application;

import java.time.LocalTime;

public record OpeningHoursRange(int dayOfWeek, LocalTime openTime, LocalTime closeTime) {
}
