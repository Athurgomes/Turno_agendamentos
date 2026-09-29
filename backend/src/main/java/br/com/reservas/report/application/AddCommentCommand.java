package br.com.reservas.report.application;

/** `POST /reports/{id}/comments` (S/A, D-55). */
public record AddCommentCommand(String text, boolean visibleToResident) {
}
