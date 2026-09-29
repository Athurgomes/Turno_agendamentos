package br.com.reservas.settings.api;

import br.com.reservas.auth.domain.UserAccount;
import java.util.UUID;

public record SyndicDto(UUID id, String name, String email, String phone, boolean active) {

    public static SyndicDto from(UserAccount account) {
        return new SyndicDto(account.getId(), account.getDisplayName(), account.getEmail(), account.getPhone(),
            account.isActive());
    }
}
