package br.com.reservas.report.api;

import br.com.reservas.report.application.AddCommentCommand;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/** `POST /reports/{id}/comments` (S/A, docs/03, D-55). */
public record AddCommentRequest(@NotBlank @Size(min = 1, max = 2000) String text, boolean visibleToResident) {

    public AddCommentCommand toCommand() {
        return new AddCommentCommand(text, visibleToResident);
    }
}
