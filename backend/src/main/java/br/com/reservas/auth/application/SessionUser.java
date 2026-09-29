package br.com.reservas.auth.application;

import br.com.reservas.auth.domain.Role;
import java.util.UUID;

/**
 * `user` do contrato de autenticacao (docs/03-api.md, D-42). {@code name} e o
 * {@code display_name} para ADMIN/SYNDIC e {@code "Unidade {identifier}"}
 * para UNIT; {@code unitId}/{@code unitIdentifier} sao {@code null} fora de
 * UNIT.
 */
public record SessionUser(UUID id, Role role, String name, UUID unitId, String unitIdentifier, boolean tempPassword) {
}
