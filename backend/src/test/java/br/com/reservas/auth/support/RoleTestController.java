package br.com.reservas.auth.support;

import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Controller so de teste (test sources) para exercitar autorizacao por role
 * (RN-01) de ponta a ponta via MockMvc, sem depender de rotas de outras fases.
 */
@RestController
@RequestMapping("/api/v1/test-roles")
public class RoleTestController {

    @GetMapping("/admin-only")
    @PreAuthorize("hasRole('ADMIN')")
    public String adminOnly() {
        return "ok";
    }

    @GetMapping("/any-authenticated")
    public String anyAuthenticated() {
        return "ok";
    }
}
