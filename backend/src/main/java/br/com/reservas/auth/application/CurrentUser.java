package br.com.reservas.auth.application;

import br.com.reservas.auth.domain.Role;
import java.util.UUID;

/**
 * Identidade da conta autenticada na requisicao atual. Interface publica do
 * modulo {@code auth} para os demais modulos (F2+) fazerem autorizacao por
 * recurso (RN-01): uma conta UNIT so enxerga a propria unidade.
 */
public record CurrentUser(UUID id, Role role, UUID unitId, UUID condominiumId) {
}
