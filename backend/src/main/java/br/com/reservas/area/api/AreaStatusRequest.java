package br.com.reservas.area.api;

import br.com.reservas.area.domain.AreaStatus;
import jakarta.validation.constraints.NotNull;

/** `PATCH /areas/{id}/status` e corpo de `DELETE /areas/{id}` (docs/03, RN-14, RN-16). */
public record AreaStatusRequest(@NotNull AreaStatus status, String justification, Boolean confirmCancelAffected) {

    public boolean confirmed() {
        return Boolean.TRUE.equals(confirmCancelAffected);
    }
}
