package br.com.reservas.unit.application;

/** Credenciais exibidas uma unica vez: cadastro, reset de senha e transferencia (RF-UNI-02/03/04). */
public record Credentials(String username, String tempPassword) {
}
