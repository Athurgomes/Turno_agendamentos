package br.com.reservas.area.application;

import java.time.LocalTime;

public record OpeningHoursInput(int dayOfWeek, LocalTime openTime, LocalTime closeTime) {
}
