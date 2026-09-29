package br.com.reservas.shared.time;

/**
 * D-32/FD-3: se o {@link java.time.Clock} injetado na aplicacao e o relogio
 * simulado do perfil {@code demo} ({@code APP_DEMO_NOW}). Consumido por
 * {@code SystemClockService} para o campo {@code simulated} de
 * {@code GET /api/v1/system/clock}.
 */
public record SimulatedClockState(boolean active) {
}
