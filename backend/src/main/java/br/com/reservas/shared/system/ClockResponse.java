package br.com.reservas.shared.system;

import java.time.Instant;

/** `GET /api/v1/system/clock` (docs/03, secao "Sistema"). */
public record ClockResponse(Instant now, String timezone, boolean simulated) {
}
