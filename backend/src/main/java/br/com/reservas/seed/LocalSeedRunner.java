package br.com.reservas.seed;

import br.com.reservas.area.application.AreaService;
import br.com.reservas.area.application.CreateAreaCommand;
import br.com.reservas.area.application.OpeningHoursInput;
import br.com.reservas.area.application.UploadedPhoto;
import br.com.reservas.area.domain.Area;
import br.com.reservas.area.domain.AreaCategory;
import br.com.reservas.area.domain.AreaCategoryTemplates;
import br.com.reservas.auth.application.AccountService;
import br.com.reservas.settings.application.SyndicService;
import br.com.reservas.shared.condominium.CondominiumLookup;
import br.com.reservas.unit.application.CreateUnitResult;
import br.com.reservas.unit.application.ResidentInput;
import br.com.reservas.unit.application.UnitService;
import java.awt.Color;
import java.math.BigDecimal;
import java.time.Clock;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;
import java.util.UUID;
import java.util.stream.IntStream;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Profile;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

/**
 * Seed do perfil {@code local} (docs/02 §5, FD-1 do plano; FD-2 fica para o
 * seed `demo` completo). Roda depois do {@code BootstrapRunner} (condomínio +
 * ADMIN, D-42, {@code @Order} garante a ordem) e cria um cenário mínimo e
 * fictício para desenvolvimento: 1 síndico, 2 unidades com moradores e 2
 * áreas (uma gratuita, uma paga) com horário e 1 foto cada.
 *
 * <p>Idempotente: se já existir qualquer unidade, não faz nada (não roda de
 * novo a cada subida). Só ativo no perfil {@code local}; desligável via
 * {@code app.seed.local.enabled=false} (usado pelos testes de integração,
 * que também rodam com o perfil {@code local} mas não querem dados de negócio
 * fixos no contexto compartilhado).
 *
 * <p>Usa apenas os serviços públicos dos módulos (CLAUDE.md seção 5, regra
 * 2): {@link UnitService}, {@link AccountService}, {@link SyndicService} e
 * {@link AreaService}. As ações de auditoria disparadas por esses serviços
 * usam a conta ADMIN criada pelo bootstrap como ator explícito.
 */
@Component
@Profile("local")
@ConditionalOnProperty(prefix = "app.seed.local", name = "enabled", havingValue = "true", matchIfMissing = true)
@Order(Ordered.HIGHEST_PRECEDENCE + 2)
public class LocalSeedRunner implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(LocalSeedRunner.class);

    // RN-07: CPFs fictícios (algoritmo de dígito verificador padrão), gerados uma vez e fixados
    // aqui (CLAUDE.md seção 11: nunca dado pessoal real).
    private static final String CPF_ANA = "12345678143";
    private static final String CPF_BRUNO = "23456789173";
    private static final String CPF_CARLA = "34567891228";

    private final CondominiumLookup condominiums;
    private final AccountService accounts;
    private final UnitService units;
    private final SyndicService syndics;
    private final AreaService areas;
    private final Clock clock;
    private final String seedPassword;
    private final String defaultPaymentWhatsapp;

    public LocalSeedRunner(CondominiumLookup condominiums, AccountService accounts, UnitService units,
        SyndicService syndics, AreaService areas, Clock clock,
        @Value("${app.seed.local.password:LocalDev123!}") String seedPassword,
        @Value("${app.seed.default-payment-whatsapp:}") String defaultPaymentWhatsapp) {
        this.condominiums = condominiums;
        this.accounts = accounts;
        this.units = units;
        this.syndics = syndics;
        this.areas = areas;
        this.clock = clock;
        this.seedPassword = seedPassword;
        this.defaultPaymentWhatsapp = StringUtils.hasText(defaultPaymentWhatsapp) ? defaultPaymentWhatsapp
            : "5511900000000";
    }

    /**
     * Transacional: se qualquer passo falhar (ex.: bucket do S3 ainda não
     * pronto), nada fica gravado — a próxima subida vê o cenário como "nunca
     * rodou" e tenta de novo, em vez de ficar com síndico/unidades criados
     * mas sem as áreas (o guard de idempotência, acima, só olha `unit`).
     */
    @Override
    @Transactional
    public void run(ApplicationArguments args) {
        UUID condominiumId = condominiums.currentId().orElse(null);
        if (condominiumId == null) {
            log.warn("Seed local não executado: nenhum condomínio cadastrado (bootstrap ainda não rodou).");
            return;
        }
        if (units.search(condominiumId, null, null, PageRequest.of(0, 1)).getTotalElements() > 0) {
            return; // idempotente: já existe pelo menos uma unidade.
        }
        UUID adminId = accounts.findAdminId(condominiumId).orElse(null);
        if (adminId == null) {
            log.warn("Seed local não executado: conta ADMIN inicial ausente "
                + "(configure APP_SEED_ADMIN_EMAIL e APP_SEED_ADMIN_PASSWORD).");
            return;
        }

        createSyndic(condominiumId, adminId);
        createUnitA101(condominiumId, adminId);
        createUnitB201(condominiumId, adminId);
        createBarbecueArea(condominiumId, adminId);
        createPartyRoomArea(condominiumId, adminId);

        log.info("Seed local criado: 1 síndico, 2 unidades e 2 áreas (dados fictícios, D-33).");
    }

    private void createSyndic(UUID condominiumId, UUID adminId) {
        SyndicService.Created created = syndics.create(condominiumId, adminId, "Síndico Exemplo",
            "sindico@exemplo.test", "5511900000001");
        accounts.setPassword(created.account().getId(), seedPassword);
    }

    private void createUnitA101(UUID condominiumId, UUID adminId) {
        List<ResidentInput> residents = List.of(
            new ResidentInput(null, "Ana Beatriz Ferreira", "5511900000002", "ana.ferreira@exemplo.test", CPF_ANA,
                true),
            new ResidentInput(null, "Bruno Ferreira", "5511900000003", "bruno.ferreira@exemplo.test", CPF_BRUNO,
                false));
        CreateUnitResult result = units.create(condominiumId, adminId, "A", "101", residents);
        UUID accountId = accounts.findAccountIdByUnit(result.unit().getId()).orElseThrow();
        accounts.setPassword(accountId, seedPassword);
    }

    private void createUnitB201(UUID condominiumId, UUID adminId) {
        List<ResidentInput> residents = List.of(
            new ResidentInput(null, "Carla Souza Lima", "5511900000004", "carla.lima@exemplo.test", CPF_CARLA,
                true));
        CreateUnitResult result = units.create(condominiumId, adminId, "B", "201", residents);
        UUID accountId = accounts.findAccountIdByUnit(result.unit().getId()).orElseThrow();
        accounts.setPassword(accountId, seedPassword);
    }

    private void createBarbecueArea(UUID condominiumId, UUID adminId) {
        CreateAreaCommand command = new CreateAreaCommand("Churrasqueira 1 — Bloco A", AreaCategory.BARBECUE,
            "Churrasqueira coberta com forno de alvenaria, para confraternizações da unidade responsável.",
            AreaCategoryTemplates.rulesTemplate(AreaCategory.BARBECUE),
            AreaCategoryTemplates.conductTemplate(AreaCategory.BARBECUE), 30, true, new BigDecimal("50.00"),
            defaultPaymentWhatsapp, weeklyHours());
        List<UploadedPhoto> photo = List.of(new UploadedPhoto(SeedImage.png("Churrasqueira 1", new Color(0xB5, 0x4A,
            0x1F))));
        Area area = areas.create(condominiumId, adminId, command, photo, LocalDate.now(clock));
        log.debug("Área fictícia criada: {}", area.getName());
    }

    private void createPartyRoomArea(UUID condominiumId, UUID adminId) {
        CreateAreaCommand command = new CreateAreaCommand("Salão de festas", AreaCategory.PARTY_ROOM,
            "Salão de festas com cozinha de apoio, mesas e cadeiras para eventos da unidade responsável.",
            AreaCategoryTemplates.rulesTemplate(AreaCategory.PARTY_ROOM),
            AreaCategoryTemplates.conductTemplate(AreaCategory.PARTY_ROOM), 80, false, null, null, weeklyHours());
        List<UploadedPhoto> photo = List.of(new UploadedPhoto(SeedImage.png("Salão de festas", new Color(0x1F, 0x5C,
            0xB5))));
        Area area = areas.create(condominiumId, adminId, command, photo, LocalDate.now(clock));
        log.debug("Área fictícia criada: {}", area.getName());
    }

    private static List<OpeningHoursInput> weeklyHours() {
        return IntStream.rangeClosed(1, 7)
            .mapToObj(day -> new OpeningHoursInput(day, LocalTime.of(8, 0), LocalTime.of(22, 0)))
            .toList();
    }
}
