package br.com.reservas.reservation.application;

import java.time.LocalTime;
import java.util.UUID;

/**
 * Um item ocupado do dia (`GET /areas/{id}/availability`, docs/03). Os campos
 * administrativos ({@code reservationId}, {@code code}, {@code unitIdentifier},
 * {@code status}) só aparecem para S/A na resposta; aqui ficam sempre
 * preenchidos e é a camada `api` quem decide o que serializar por perfil.
 */
public record BusyEntry(LocalTime startTime, LocalTime endTime, String kind, UUID reservationId, String code,
    String unitIdentifier, String status) {
}
