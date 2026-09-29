package br.com.reservas.shared.error;

import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;

/**
 * Monta o {@code ProblemDetail} (RFC 9457) com a propriedade extra {@code code}
 * exigida pelo contrato da API (docs/03, secao "Convencoes transversais").
 * Unico ponto que sabe montar esse formato: usado pelo advice de excecoes e
 * pelos componentes de seguranca que respondem fora do MVC (401/403).
 */
public final class ProblemDetailFactory {

    private ProblemDetailFactory() {
    }

    public static ProblemDetail of(HttpStatus status, String code, String detail) {
        ProblemDetail problem = ProblemDetail.forStatusAndDetail(status, detail);
        problem.setTitle(status.getReasonPhrase());
        problem.setProperty("code", code);
        return problem;
    }
}
