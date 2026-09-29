package br.com.reservas.area.api;

/** Corpo opcional de `DELETE /areas/{id}` (docs/03, RN-16). */
public record DeleteAreaRequest(String justification, Boolean confirmCancelAffected) {

    public boolean confirmed() {
        return Boolean.TRUE.equals(confirmCancelAffected);
    }
}
