package br.com.reservas.auth.api;

import br.com.reservas.auth.application.SessionUser;
import br.com.reservas.auth.domain.Role;
import java.util.UUID;

/** `user` do contrato de autenticacao (docs/03-api.md, D-42). */
public record UserDto(UUID id, Role role, String name, UUID unitId, String unitIdentifier, boolean tempPassword) {

    public static UserDto from(SessionUser user) {
        return new UserDto(user.id(), user.role(), user.name(), user.unitId(), user.unitIdentifier(),
            user.tempPassword());
    }
}
