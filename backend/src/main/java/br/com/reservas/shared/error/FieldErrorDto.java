package br.com.reservas.shared.error;

/** Item de {@code errors} no {@code ProblemDetail} de {@code VALIDATION_ERROR} (docs/03). */
public record FieldErrorDto(String field, String message) {
}
