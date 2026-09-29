package br.com.reservas.auth.application;

import br.com.reservas.auth.domain.Role;
import java.util.UUID;

/**
 * F4-5: dados mínimos de uma conta para resolver o ator de um evento
 * auditável (`GET /reservations/{id}/events`) sem expor {@code UserAccount}
 * fora do módulo `auth`. {@code displayName} é {@code null} para conta UNIT
 * (o nome exibido é o identificador da unidade, resolvido por quem chama).
 */
public record AccountSummary(UUID id, Role role, String displayName, UUID unitId) {
}
