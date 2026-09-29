package br.com.reservas.reservation.application;

import java.time.LocalDate;
import java.time.LocalTime;
import java.util.UUID;

/** `POST /blocks` (S/A, RF-RES-10/RN-33): motivo obrigatório (1..500 caracteres). */
public record CreateBlockCommand(UUID areaId, LocalDate date, LocalTime startTime, LocalTime endTime,
    String reason) {
}
