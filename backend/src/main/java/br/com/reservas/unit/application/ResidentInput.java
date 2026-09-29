package br.com.reservas.unit.application;

import java.util.UUID;

/**
 * Comando de entrada para criar/editar um morador. {@code id} nulo = cria;
 * presente = edita (usado por {@code PUT /units/{id}}, D-43). {@code cpf} cru
 * (com ou sem pontuacao) ou {@code null}.
 */
public record ResidentInput(UUID id, String name, String phone, String email, String cpf, boolean primary) {
}
