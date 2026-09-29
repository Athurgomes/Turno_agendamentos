package br.com.reservas.area.api;

/** `PATCH /areas/{id}/photos/{photoId}` (docs/03). */
public record UpdatePhotoRequest(String caption, Boolean featured) {
}
