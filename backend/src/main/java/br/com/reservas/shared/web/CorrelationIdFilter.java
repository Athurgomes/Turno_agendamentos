package br.com.reservas.shared.web;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.UUID;
import java.util.regex.Pattern;
import org.slf4j.MDC;
import org.springframework.util.StringUtils;
import org.springframework.web.filter.OncePerRequestFilter;

/**
 * RNF-11: le {@code X-Request-Id} do cliente ou gera um novo, poe no MDC (para
 * todo log da requisicao citar o mesmo id) e devolve no header da resposta.
 * Nunca loga corpo de requisicao aqui.
 */
public class CorrelationIdFilter extends OncePerRequestFilter {

    public static final String HEADER = "X-Request-Id";
    public static final String MDC_KEY = "requestId";

    // Um id de cliente fora desse formato (ex.: com quebra de linha) poderia
    // forjar entradas de log inteiras (log injection) ao ser ecoado no
    // padrao de log via MDC; nesse caso um novo id e gerado no lugar.
    private static final Pattern VALID_REQUEST_ID = Pattern.compile("^[A-Za-z0-9._-]{1,64}$");

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
        throws ServletException, IOException {
        String requestId = request.getHeader(HEADER);
        if (!StringUtils.hasText(requestId) || !VALID_REQUEST_ID.matcher(requestId).matches()) {
            requestId = UUID.randomUUID().toString();
        }
        response.setHeader(HEADER, requestId);
        MDC.put(MDC_KEY, requestId);
        try {
            chain.doFilter(request, response);
        } finally {
            MDC.remove(MDC_KEY);
        }
    }
}
