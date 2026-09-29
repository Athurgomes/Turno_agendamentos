package br.com.reservas.seed;

import br.com.reservas.area.application.AreaInspectionService;
import br.com.reservas.area.application.AreaService;
import br.com.reservas.area.application.CreateAreaCommand;
import br.com.reservas.area.application.OpeningHoursInput;
import br.com.reservas.area.application.UploadedPhoto;
import br.com.reservas.area.domain.Area;
import br.com.reservas.area.domain.AreaCategory;
import br.com.reservas.area.domain.AreaCategoryTemplates;
import br.com.reservas.area.domain.AreaStatus;
import br.com.reservas.area.domain.InspectionCondition;
import br.com.reservas.auth.application.AccountService;
import br.com.reservas.reservation.application.CreateBlockCommand;
import br.com.reservas.reservation.application.CreateReservationCommand;
import br.com.reservas.reservation.application.ReservationCreationResult;
import br.com.reservas.reservation.application.ReservationService;
import br.com.reservas.settings.application.CondominiumSettingsService;
import br.com.reservas.settings.application.RuleSettingsSnapshot;
import br.com.reservas.settings.application.SyndicService;
import br.com.reservas.shared.condominium.CondominiumLookup;
import br.com.reservas.unit.application.CreateUnitResult;
import br.com.reservas.unit.application.ResidentInput;
import br.com.reservas.unit.application.UnitService;
import br.com.reservas.unit.domain.Resident;
import java.awt.Color;
import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.ZoneId;
import java.time.ZonedDateTime;
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
 * Seed do perfil {@code demo} (FD-2 parcial, docs/08 §2, D-59): contas, áreas,
 * vistorias, reservas das situações pré-montadas (pendente, bloqueio,
 * cancelada, futuras) e reports. Os ~6 meses de histórico do dashboard ficam
 * para a Etapa 3 (D-59).
 *
 * <p>Mesma estrutura do {@link LocalSeedRunner} (idempotente: só roda se
 * ainda não houver unidade; usa só os serviços públicos dos módulos,
 * CLAUDE.md §5), com uma exceção deliberada: dados cuja data já é passada
 * (reservas e reports "de X dias atrás") são gravados por
 * {@link SeedHistoryWriter}, um caminho direto (JdbcTemplate, só neste
 * pacote) porque os serviços públicos de {@code reservation}/{@code report}
 * (a) recusam qualquer data passada (RN-20) e (b) sempre gravam
 * {@code createdAt = Instant.now(clock)} — nenhum dos dois dá para pedir
 * emprestado para o passado. Tudo o que é "hoje em diante" (unidades, contas,
 * áreas, fotos, vistorias, reservas futuras, bloqueio, cancelamento) passa
 * pelos serviços normais e valida as mesmas regras que a API usaria.
 */
@Component
@Profile("demo")
@ConditionalOnProperty(prefix = "app.seed.demo", name = "enabled", havingValue = "true", matchIfMissing = true)
@Order(Ordered.HIGHEST_PRECEDENCE + 2)
public class DemoSeedRunner implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(DemoSeedRunner.class);

    // RN-07: CPFs fictícios (DV válido), gerados uma vez com semente fixa e fixados aqui.
    private static final String CPF_A101_PRINCIPAL = "19827364537";
    private static final String CPF_A101_RESIDENT_2 = "28736451991";
    private static final String CPF_A101_RESIDENT_3 = "37645192828";
    private static final String CPF_A102_PRINCIPAL = "46519283746";
    private static final String CPF_B201_PRINCIPAL = "55492817329";
    private static final String CPF_B201_SPOUSE = "64381726421";
    private static final String CPF_B202_PRINCIPAL = "73264819546";
    private static final String CPF_A103_PRINCIPAL = "82159374628";
    private static final String CPF_B203_PRINCIPAL = "91038472504";

    private final CondominiumLookup condominiums;
    private final AccountService accounts;
    private final UnitService units;
    private final SyndicService syndics;
    private final AreaService areas;
    private final AreaInspectionService inspections;
    private final ReservationService reservations;
    private final CondominiumSettingsService settingsService;
    private final SeedHistoryWriter history;
    private final Clock clock;
    private final String demoPassword;
    private final String demoTempPassword;
    private final String adminWhatsapp;
    private final boolean adminWhatsappMissing;

    public DemoSeedRunner(CondominiumLookup condominiums, AccountService accounts, UnitService units,
        SyndicService syndics, AreaService areas, AreaInspectionService inspections, ReservationService reservations,
        CondominiumSettingsService settingsService, SeedHistoryWriter history, Clock clock,
        @Value("${app.seed.demo.password:}") String demoPassword,
        @Value("${app.seed.demo.temp-password:}") String demoTempPassword,
        @Value("${app.seed.demo.admin-whatsapp:}") String adminWhatsapp) {
        this.condominiums = condominiums;
        this.accounts = accounts;
        this.units = units;
        this.syndics = syndics;
        this.areas = areas;
        this.inspections = inspections;
        this.reservations = reservations;
        this.settingsService = settingsService;
        this.history = history;
        this.clock = clock;
        this.demoPassword = demoPassword;
        this.demoTempPassword = demoTempPassword;
        this.adminWhatsapp = StringUtils.hasText(adminWhatsapp) ? adminWhatsapp : "5511900000099";
        this.adminWhatsappMissing = !StringUtils.hasText(adminWhatsapp);
    }

    @Override
    @Transactional
    public void run(ApplicationArguments args) {
        UUID condominiumId = condominiums.currentId().orElse(null);
        if (condominiumId == null) {
            log.warn("Seed demo não executado: nenhum condomínio cadastrado (bootstrap ainda não rodou).");
            return;
        }
        if (units.search(condominiumId, null, null, PageRequest.of(0, 1)).getTotalElements() > 0) {
            return; // idempotente: já existe pelo menos uma unidade.
        }
        UUID adminId = accounts.findAdminId(condominiumId).orElse(null);
        if (adminId == null) {
            log.warn("Seed demo não executado: conta ADMIN inicial ausente "
                + "(configure APP_SEED_ADMIN_EMAIL e APP_SEED_ADMIN_PASSWORD).");
            return;
        }
        // D-59/D-48: sem essas duas variáveis, metade das contas do roteiro (docs/08 §2) não
        // teria senha conhecida — falha alto e cedo em vez de subir com um cenário incompleto.
        if (!StringUtils.hasText(demoPassword) || !StringUtils.hasText(demoTempPassword)) {
            throw new IllegalStateException("Seed demo não pode rodar: defina APP_DEMO_PASSWORD e "
                + "APP_DEMO_TEMP_PASSWORD (docs/08 §2, D-59).");
        }
        if (adminWhatsappMissing) {
            log.warn("APP_DEMO_ADMIN_WHATSAPP vazio: o botão de pagamento vai abrir um número fictício. "
                + "Veja docs/08 §5.");
        }

        Accounts accountIds = createAccounts(condominiumId, adminId);
        Scenario scenario = new Scenario(condominiumId, adminId, accountIds);
        Areas areaIds = createAreas(scenario);
        createFutureReservations(scenario, areaIds);
        createHistory(scenario, areaIds);
        createInspections(scenario, areaIds);

        log.info("Seed demo criado: 1 síndico, {} unidades, {} áreas (dados fictícios, D-33/D-59).",
            accountIds.units().size(), areaIds.all().size());
    }

    // ---- Contas -----------------------------------------------------------------------------

    private Accounts createAccounts(UUID condominiumId, UUID adminId) {
        SyndicService.Created syndic = syndics.create(condominiumId, adminId, "Ricardo Souza Lima",
            "sindico@exemplo.test", "5561999990000");
        accounts.setPassword(syndic.account().getId(), demoPassword);

        UnitAccount a101 = createUnit(condominiumId, adminId, "A", "101", List.of(
            new ResidentInput(null, "Marina Costa Almeida", "5561999990001", "marina.almeida@exemplo.test",
                CPF_A101_PRINCIPAL, true),
            new ResidentInput(null, "Felipe Costa Almeida", "5561999990002", "felipe.almeida@exemplo.test",
                CPF_A101_RESIDENT_2, false),
            new ResidentInput(null, "Rafael Costa Almeida", "5561999990003", "rafael.almeida@exemplo.test",
                CPF_A101_RESIDENT_3, false)), demoPassword, false);

        UnitAccount a102 = createUnit(condominiumId, adminId, "A", "102", List.of(
            new ResidentInput(null, "Juliana Prado Nunes", "5561999990004", "juliana.nunes@exemplo.test",
                CPF_A102_PRINCIPAL, true)), demoTempPassword, true);

        UnitAccount b201 = createUnit(condominiumId, adminId, "B", "201", List.of(
            new ResidentInput(null, "André Ribeiro Teixeira", "5561999990005", "andre.teixeira@exemplo.test",
                CPF_B201_PRINCIPAL, true),
            new ResidentInput(null, "Camila Ribeiro Teixeira", "5561999990006", "camila.teixeira@exemplo.test",
                CPF_B201_SPOUSE, false)), demoPassword, false);

        UnitAccount b202 = createUnit(condominiumId, adminId, "B", "202", List.of(
            new ResidentInput(null, "Bruno Martins Rocha", "5561999990007", "bruno.rocha@exemplo.test",
                CPF_B202_PRINCIPAL, true)), demoPassword, false);

        UnitAccount a103 = createUnit(condominiumId, adminId, "A", "103", List.of(
            new ResidentInput(null, "Larissa Gomes Duarte", "5561999990008", "larissa.duarte@exemplo.test",
                CPF_A103_PRINCIPAL, true)), demoPassword, false);

        UnitAccount b203 = createUnit(condominiumId, adminId, "B", "203", List.of(
            new ResidentInput(null, "Diego Pereira Vieira", "5561999990009", "diego.vieira@exemplo.test",
                CPF_B203_PRINCIPAL, true)), demoPassword, false);

        return new Accounts(a101, a102, b201, b202, a103, b203);
    }

    private UnitAccount createUnit(UUID condominiumId, UUID adminId, String block, String number,
        List<ResidentInput> residentInputs, String password, boolean temporaryPassword) {
        CreateUnitResult result = units.create(condominiumId, adminId, block, number, residentInputs);
        UUID accountId = accounts.findAccountIdByUnit(result.unit().getId()).orElseThrow();
        if (temporaryPassword) {
            accounts.setTemporaryPassword(accountId, password);
        } else {
            accounts.setPassword(accountId, password);
        }
        UUID principalResidentId = result.residents().stream().filter(Resident::isPrimary).findFirst()
            .orElseThrow().getId();
        return new UnitAccount(result.unit().getId(), accountId, principalResidentId);
    }

    // ---- Áreas --------------------------------------------------------------------------------

    private Areas createAreas(Scenario scenario) {
        UUID partyRoom = createArea(scenario, "Salão de festas", AreaCategory.PARTY_ROOM,
            "Salão de festas com cozinha de apoio, mesas e cadeiras para eventos da unidade responsável.", 80, true,
            new BigDecimal("150.00"), new Color(0x1F, 0x5C, 0xB5));
        UUID barbecue1 = createArea(scenario, "Churrasqueira 1 — Bloco A", AreaCategory.BARBECUE,
            "Churrasqueira coberta com forno de alvenaria, para confraternizações da unidade responsável.", 30, true,
            new BigDecimal("50.00"), new Color(0xB5, 0x4A, 0x1F));
        UUID barbecue2 = createArea(scenario, "Churrasqueira 2 — Cobertura", AreaCategory.BARBECUE,
            "Churrasqueira descoberta na cobertura, com vista, de uso gratuito.", 20, false, null,
            new Color(0xD1, 0x7A, 0x3A));
        UUID gourmet = createArea(scenario, "Espaço gourmet", AreaCategory.GOURMET_SPACE,
            "Espaço gourmet de uso gratuito, com bancada e utensílios básicos.", 25, false, null,
            new Color(0x6B, 0x4F, 0x2A));
        UUID sportsCourt = createArea(scenario, "Quadra poliesportiva", AreaCategory.SPORTS_COURT,
            "Quadra poliesportiva de uso gratuito, para futebol de salão, vôlei e basquete.", 20, false, null,
            new Color(0x2A, 0x8C, 0x3E));
        UUID pool = createArea(scenario, "Piscina", AreaCategory.POOL,
            "Piscina de uso gratuito, com raia e área infantil.", 40, false, null, new Color(0x1E, 0x90, 0xC7));
        areas.changeStatus(pool, scenario.adminId(), AreaStatus.MAINTENANCE,
            "Manutenção preventiva do sistema de filtragem antes da temporada.", false);

        return new Areas(partyRoom, barbecue1, barbecue2, gourmet, sportsCourt, pool);
    }

    private UUID createArea(Scenario scenario, String name, AreaCategory category, String description, int capacity,
        boolean requiresPayment, BigDecimal price, Color photoColor) {
        CreateAreaCommand command = new CreateAreaCommand(name, category, description,
            AreaCategoryTemplates.rulesTemplate(category), AreaCategoryTemplates.conductTemplate(category), capacity,
            requiresPayment, price, requiresPayment ? adminWhatsapp : null, weeklyHours());
        List<UploadedPhoto> photo = List.of(new UploadedPhoto(SeedImage.png(name, photoColor)));
        Area area = areas.create(scenario.condominiumId(), scenario.adminId(), command, photo,
            LocalDate.now(clock));
        return area.getId();
    }

    private static List<OpeningHoursInput> weeklyHours() {
        return IntStream.rangeClosed(1, 7)
            .mapToObj(day -> new OpeningHoursInput(day, LocalTime.of(8, 0), LocalTime.of(22, 0)))
            .toList();
    }

    // ---- Reservas futuras / bloqueio / pendente / cancelada (pelos serviços públicos) --------

    private void createFutureReservations(Scenario scenario, Areas areaIds) {
        UUID adminId = scenario.adminId();
        Accounts a = scenario.accounts();

        // RN-22: b-201 no limite de 3 reservas futuras ativas.
        book(a.b201(), areaIds.gourmet(), 2, LocalTime.of(9, 0), LocalTime.of(10, 0), 2, null);
        book(a.b201(), areaIds.barbecue2(), 3, LocalTime.of(9, 0), LocalTime.of(10, 0), 2, null);
        book(a.b201(), areaIds.gourmet(), 9, LocalTime.of(9, 0), LocalTime.of(10, 0), 2, null);

        // RN-25/RN-26: b-202 pendente de pagamento na Churrasqueira 1.
        book(a.b202(), areaIds.barbecue1(), 5, LocalTime.of(9, 0), LocalTime.of(10, 0), 4, null);

        // Reserva futura na Quadra poliesportiva (roteiro passo 10: afetada pela manutenção ao vivo).
        book(a.a103(), areaIds.sportsCourt(), 3, LocalTime.of(9, 0), LocalTime.of(10, 0), 6, null);

        // RN-27/RN-29: reserva futura cancelada pela administração, com justificativa visível ao morador.
        UUID cancelledId = book(a.b203(), areaIds.gourmet(), 6, LocalTime.of(9, 0), LocalTime.of(10, 0), 3, null)
            .reservation().id();
        reservations.cancelByAdmin(cancelledId, adminId,
            "Espaço reservado para vistoria estrutural do teto; remarque para outra data, por gentileza.");

        // RN-33: bloqueio "Assembleia geral" no Salão daqui a 7 dias.
        reservations.createBlock(scenario.condominiumId(), adminId, "ADMIN",
            new CreateBlockCommand(areaIds.partyRoom(), LocalDate.now(clock).plusDays(7), LocalTime.of(19, 0),
                LocalTime.of(22, 0), "Assembleia geral"));
    }

    private ReservationCreationResult book(UnitAccount unit, UUID areaId, int daysAhead, LocalTime start,
        LocalTime end, int guests, String notes) {
        LocalDate date = LocalDate.now(clock).plusDays(daysAhead);
        CreateReservationCommand command = new CreateReservationCommand(areaId, date, start, end,
            unit.principalResidentId(), guests, notes);
        return reservations.create(unit.unitId(), unit.accountId(), command);
    }

    // ---- Histórico (caminho direto, D-59) ----------------------------------------------------

    private void createHistory(Scenario scenario, Areas areaIds) {
        RuleSettingsSnapshot rules = settingsService.current();
        ZoneId zone = ZoneId.of(rules.timezone());
        LocalDate today = LocalDate.now(clock);
        UUID condominiumId = scenario.condominiumId();
        UUID adminId = scenario.adminId();
        Accounts a = scenario.accounts();

        // a-101: reserva CONFIRMED de ontem (janela de report aberta, RN-34) e outra há 10 dias (janela fechada).
        history.insertConfirmedReservation(condominiumId, areaIds.gourmet(), a.a101().unitId(),
            a.a101().principalResidentId(), "Marina Costa Almeida", "5561999990001",
            startAt(today.minusDays(1), LocalTime.of(19, 0), zone), startAt(today.minusDays(1), LocalTime.of(20, 0),
                zone), 4, false, null, a.a101().accountId(), startAt(today.minusDays(3), LocalTime.of(9, 0), zone));
        history.insertConfirmedReservation(condominiumId, areaIds.sportsCourt(), a.a101().unitId(),
            a.a101().principalResidentId(), "Marina Costa Almeida", "5561999990001",
            startAt(today.minusDays(10), LocalTime.of(9, 0), zone), startAt(today.minusDays(10), LocalTime.of(10, 0),
                zone), 2, false, null, a.a101().accountId(), startAt(today.minusDays(12), LocalTime.of(9, 0), zone));

        // a-103: reserva CONFIRMED da Churrasqueira 1 há 5 dias -> report OPEN aberto há 2 dias.
        UUID reservationForOpenReport = history.insertConfirmedReservation(condominiumId, areaIds.barbecue1(),
            a.a103().unitId(), a.a103().principalResidentId(), "Larissa Gomes Duarte", "5561999990008",
            startAt(today.minusDays(5), LocalTime.of(12, 0), zone), startAt(today.minusDays(5), LocalTime.of(13, 0),
                zone), 6, true, new BigDecimal("50.00"), a.a103().accountId(),
            startAt(today.minusDays(6), LocalTime.of(9, 0), zone));
        history.insertReport(condominiumId, reservationForOpenReport, areaIds.barbecue1(), a.a103().unitId(),
            a.a103().principalResidentId(), "Larissa Gomes Duarte", "MALFUNCTION",
            "A grelha da churrasqueira está com uma das travas quebrada e não fica presa durante o uso.", "OPEN",
            null, null, startAt(today.minusDays(2), LocalTime.of(10, 0), zone));

        // b-203: reserva CONFIRMED da Churrasqueira 1 há 20 dias -> report RESOLVED com custo e comentário.
        UUID reservationForResolvedReport = history.insertConfirmedReservation(condominiumId, areaIds.barbecue1(),
            a.b203().unitId(), a.b203().principalResidentId(), "Diego Pereira Vieira", "5561999990009",
            startAt(today.minusDays(20), LocalTime.of(12, 0), zone), startAt(today.minusDays(20), LocalTime.of(13, 0),
                zone), 5, true, new BigDecimal("50.00"), a.b203().accountId(),
            startAt(today.minusDays(21), LocalTime.of(9, 0), zone));
        Instant resolvedAt = startAt(today.minusDays(15), LocalTime.of(14, 0), zone);
        UUID resolvedReportId = history.insertReport(condominiumId, reservationForResolvedReport,
            areaIds.barbecue1(), a.b203().unitId(), a.b203().principalResidentId(), "Diego Pereira Vieira",
            "DAMAGE", "Uma das bancadas de apoio estava rachada e precisou ser trocada.", "RESOLVED",
            new BigDecimal("120.00"), resolvedAt, startAt(today.minusDays(18), LocalTime.of(9, 0), zone));
        history.insertReportComment(resolvedReportId, adminId,
            "Bancada trocada pela manutenção; custo debitado do rateio extraordinário do mês.", true, resolvedAt);
    }

    private static Instant startAt(LocalDate date, LocalTime time, ZoneId zone) {
        return ZonedDateTime.of(date, time, zone).toInstant();
    }

    // ---- Vistorias ----------------------------------------------------------------------------

    private void createInspections(Scenario scenario, Areas areaIds) {
        UUID adminId = scenario.adminId();
        LocalDate today = LocalDate.now(clock);
        inspections.create(areaIds.barbecue1(), adminId, "ADMIN", today.minusMonths(3), InspectionCondition.FAIR,
            "Grelha com sinais de ferrugem leve; forno de alvenaria em bom estado.",
            List.of(new UploadedPhoto(SeedImage.png("Vistoria -3m", new Color(0x8A, 0x5A, 0x2A)))));
        inspections.create(areaIds.barbecue1(), adminId, "ADMIN", today, InspectionCondition.GOOD,
            "Grelha trocada e forno limpo; sem pendências.",
            List.of(new UploadedPhoto(SeedImage.png("Vistoria atual", new Color(0x2A, 0x8A, 0x4A)))));
    }

    private record UnitAccount(UUID unitId, UUID accountId, UUID principalResidentId) {
    }

    private record Accounts(UnitAccount a101, UnitAccount a102, UnitAccount b201, UnitAccount b202,
        UnitAccount a103, UnitAccount b203) {
        List<UnitAccount> units() {
            return List.of(a101, a102, b201, b202, a103, b203);
        }
    }

    private record Areas(UUID partyRoom, UUID barbecue1, UUID barbecue2, UUID gourmet, UUID sportsCourt, UUID pool) {
        List<UUID> all() {
            return List.of(partyRoom, barbecue1, barbecue2, gourmet, sportsCourt, pool);
        }
    }

    private record Scenario(UUID condominiumId, UUID adminId, Accounts accounts) {
    }
}
