package br.com.reservas.shared.error;

import org.springframework.http.HttpStatus;

/**
 * Erro de regra de negocio (RN-xx) que deve virar {@code ProblemDetail} com um
 * {@code code} estavel para o front (tabela em docs/03-api.md). O {@code detail}
 * e a mensagem pt-BR exibida diretamente ao usuario: nunca inclua dado interno
 * (SQL, stacktrace, nomes de classe) nele.
 */
public class BusinessException extends RuntimeException {

    private final String code;
    private final HttpStatus status;

    public BusinessException(String code, HttpStatus status, String detail) {
        super(detail);
        this.code = code;
        this.status = status;
    }

    public String code() {
        return code;
    }

    public HttpStatus status() {
        return status;
    }
}
