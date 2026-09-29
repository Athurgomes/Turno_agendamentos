package br.com.reservas.auth.infra.bootstrap;

import br.com.reservas.auth.domain.Role;
import br.com.reservas.auth.domain.UserAccount;
import br.com.reservas.auth.infra.UserAccountRepository;
import br.com.reservas.shared.condominium.CondominiumLookup;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

/**
 * D-42: cria, na subida (todos os perfis), o condomínio único do MVP (D-13) e
 * a conta ADMIN inicial, se ainda não existirem. Idempotente: roda em toda
 * subida, mas só grava o que faltar.
 *
 * <p>{@code @Order} explícito (menor valor = executa primeiro) para garantir,
 * de forma determinística, que este runner roda antes do seed do perfil
 * {@code local} (`br.com.reservas.seed.LocalSeedRunner`, FD-1), que depende do
 * condomínio e do ADMIN já existirem.
 */
@Component
@Order(Ordered.HIGHEST_PRECEDENCE)
public class BootstrapRunner implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(BootstrapRunner.class);
    private static final String DEFAULT_TIMEZONE = "America/Sao_Paulo";

    private final JdbcTemplate jdbcTemplate;
    private final CondominiumLookup condominiums;
    private final UserAccountRepository accounts;
    private final PasswordEncoder passwordEncoder;
    private final SeedProperties seed;

    public BootstrapRunner(JdbcTemplate jdbcTemplate, CondominiumLookup condominiums, UserAccountRepository accounts,
        PasswordEncoder passwordEncoder, SeedProperties seed) {
        this.jdbcTemplate = jdbcTemplate;
        this.condominiums = condominiums;
        this.accounts = accounts;
        this.passwordEncoder = passwordEncoder;
        this.seed = seed;
    }

    @Override
    public void run(ApplicationArguments args) {
        UUID condominiumId = condominiums.currentId().orElseGet(this::createCondominium);
        createAdminIfMissing(condominiumId);
    }

    private UUID createCondominium() {
        UUID id = UUID.randomUUID();
        String name = StringUtils.hasText(seed.condominiumName()) ? seed.condominiumName() : "Residencial Exemplo";
        String whatsapp = StringUtils.hasText(seed.defaultPaymentWhatsapp()) ? seed.defaultPaymentWhatsapp() : null;

        jdbcTemplate.update(
            "insert into condominium (id, name, timezone, default_payment_whatsapp) values (?, ?, ?, ?)",
            id, name, DEFAULT_TIMEZONE, whatsapp);
        jdbcTemplate.update("insert into condominium_settings (condominium_id) values (?)", id);

        log.info("Condomínio inicial criado (D-42).");
        return id;
    }

    private void createAdminIfMissing(UUID condominiumId) {
        if (accounts.existsByCondominiumIdAndRole(condominiumId, Role.ADMIN)) {
            return;
        }
        if (!StringUtils.hasText(seed.adminEmail()) || !StringUtils.hasText(seed.adminPassword())) {
            log.warn("Conta ADMIN inicial não criada: configure APP_SEED_ADMIN_EMAIL e APP_SEED_ADMIN_PASSWORD.");
            return;
        }

        UserAccount admin = new UserAccount(condominiumId, Role.ADMIN, null, seed.adminEmail().toLowerCase(),
            "Administração", null, null, passwordEncoder.encode(seed.adminPassword()));
        admin.markPasswordAsPermanent();
        accounts.save(admin);
        log.info("Conta ADMIN inicial criada (D-42).");
    }
}
