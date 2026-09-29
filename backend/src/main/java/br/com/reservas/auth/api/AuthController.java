package br.com.reservas.auth.api;

import br.com.reservas.auth.application.AuthResult;
import br.com.reservas.auth.application.AuthService;
import br.com.reservas.auth.application.CurrentUserProvider;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import java.time.Duration;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseCookie;
import org.springframework.http.ResponseEntity;
import org.springframework.util.StringUtils;
import org.springframework.web.bind.annotation.CookieValue;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * `Auth` (docs/03-api.md, D-42). Controller fino: toda regra vive em
 * {@link AuthService}; aqui so o mapeamento HTTP (cookie, IP do cliente).
 */
@RestController
@RequestMapping("/api/v1/auth")
public class AuthController {

    private static final String REFRESH_COOKIE_NAME = "refresh_token";
    private static final Duration REFRESH_COOKIE_MAX_AGE = Duration.ofDays(7);

    private final AuthService authService;
    private final CurrentUserProvider currentUserProvider;
    private final CookieProperties cookieProperties;

    public AuthController(AuthService authService, CurrentUserProvider currentUserProvider,
        CookieProperties cookieProperties) {
        this.authService = authService;
        this.currentUserProvider = currentUserProvider;
        this.cookieProperties = cookieProperties;
    }

    @PostMapping("/login")
    public ResponseEntity<LoginResponse> login(@Valid @RequestBody LoginRequest request, HttpServletRequest http) {
        AuthResult result = authService.login(request.login(), request.password(), clientIp(http));
        return withRefreshCookie(result);
    }

    @PostMapping("/refresh")
    public ResponseEntity<LoginResponse> refresh(
        @CookieValue(value = REFRESH_COOKIE_NAME, required = false) String refreshToken) {
        AuthResult result = authService.refresh(refreshToken);
        return withRefreshCookie(result);
    }

    @PostMapping("/logout")
    public ResponseEntity<Void> logout(
        @CookieValue(value = REFRESH_COOKIE_NAME, required = false) String refreshToken) {
        authService.logout(refreshToken);
        return ResponseEntity.noContent()
            .header(HttpHeaders.SET_COOKIE, buildCookie("", Duration.ZERO).toString())
            .build();
    }

    @GetMapping("/me")
    public UserDto me() {
        return UserDto.from(authService.me(currentUserProvider.current().id()));
    }

    @PutMapping("/password")
    public ResponseEntity<Void> changePassword(@Valid @RequestBody ChangePasswordRequest request) {
        authService.changePassword(currentUserProvider.current().id(), request.currentPassword(),
            request.newPassword());
        return ResponseEntity.noContent().build();
    }

    private ResponseEntity<LoginResponse> withRefreshCookie(AuthResult result) {
        return ResponseEntity.ok()
            .header(HttpHeaders.SET_COOKIE, buildCookie(result.refreshToken(), REFRESH_COOKIE_MAX_AGE).toString())
            .body(LoginResponse.from(result));
    }

    private ResponseCookie buildCookie(String value, Duration maxAge) {
        return ResponseCookie.from(REFRESH_COOKIE_NAME, value)
            .httpOnly(true)
            .secure(cookieProperties.secure())
            .sameSite("Strict")
            .path("/api/v1/auth")
            .maxAge(maxAge)
            .build();
    }

    /**
     * O nginx (unico reverse proxy na frente do backend, docker-compose.yml)
     * sempre acrescenta o IP real de quem conectou como o ULTIMO salto de
     * `X-Forwarded-For` (`proxy_set_header X-Forwarded-For $proxy_add_x_forwarded_for`).
     * Qualquer valor anterior nessa lista pode ter sido forjado pelo proprio
     * cliente; por isso so o ultimo salto (o do proxy confiavel) e usado para
     * o rate limit (RNF-02), nunca o primeiro.
     */
    private static String clientIp(HttpServletRequest request) {
        String forwardedFor = request.getHeader("X-Forwarded-For");
        if (!StringUtils.hasText(forwardedFor)) {
            return request.getRemoteAddr();
        }
        String[] hops = forwardedFor.split(",");
        return hops[hops.length - 1].trim();
    }
}
