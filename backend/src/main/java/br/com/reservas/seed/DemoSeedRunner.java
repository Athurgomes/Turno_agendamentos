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
import br.com.reservas.settings.application.SettingsSnapshot;
import br.com.reservas.settings.application.SyndicService;
import br.com.reservas.settings.application.UpdateSettingsCommand;
import br.com.reservas.shared.condominium.CondominiumLookup;
import br.com.reservas.unit.application.CreateUnitResult;
import br.com.reservas.unit.application.ResidentInput;
import br.com.reservas.unit.application.UnitService;
import br.com.reservas.unit.domain.Resident;
import java.awt.Color;
import java.math.BigDecimal;
import java.time.Clock;
import java.time.DayOfWeek;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;
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

    // FD-2 (ajuste): 8 unidades extras, só com histórico (sem reserva futura ativa), para o
    // "top 10 unidades" do dashboard ter linhas suficientes (docs/08 §2).
    private static final String CPF_A201_PRINCIPAL = "42975528507";
    private static final String CPF_A202_PRINCIPAL = "35886825975";
    private static final String CPF_A202_RESIDENT_2 = "62737040400";
    private static final String CPF_A203_PRINCIPAL = "76329783217";
    private static final String CPF_A204_PRINCIPAL = "23411470119";
    private static final String CPF_B101_PRINCIPAL = "26372138760";
    private static final String CPF_B102_PRINCIPAL = "61920145230";
    private static final String CPF_B103_PRINCIPAL = "37421309438";
    private static final String CPF_B103_RESIDENT_2 = "56285000700";
    private static final String CPF_B104_PRINCIPAL = "90950733784";

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

        ensureDefaultPaymentWhatsapp(adminId);
        Accounts accountIds = createAccounts(condominiumId, adminId);
        Scenario scenario = new Scenario(condominiumId, adminId, accountIds);
        Areas areaIds = createAreas(scenario);
        createFutureReservations(scenario, areaIds);
        createHistory(scenario, areaIds);
        createSixMonthHistory(scenario, areaIds);
        createInspections(scenario, areaIds);

        log.info("Seed demo criado: 1 síndico, {} unidades, {} áreas (dados fictícios, D-33/D-59).",
            accountIds.units().size(), areaIds.all().size());
    }

    // ---- Configuracoes ------------------------------------------------------------------------

    /**
     * FD-2/D-43 (docs/12, "Reensaio" Bug 4): sem {@code APP_DEFAULT_PAYMENT_WHATSAPP}, o
     * `BootstrapRunner` deixa {@code condominium.default_payment_whatsapp} nulo e a tela de
     * Configurações trava (roteiro passo 11). No perfil demo, preenche com o mesmo número já
     * usado nas áreas pagas ({@link #adminWhatsapp}); idempotente — não sobrescreve valor já
     * definido (ex.: por `APP_DEFAULT_PAYMENT_WHATSAPP`).
     */
    private void ensureDefaultPaymentWhatsapp(UUID adminId) {
        SettingsSnapshot current = settingsService.get();
        if (StringUtils.hasText(current.defaultPaymentWhatsapp())) {
            return;
        }
        UpdateSettingsCommand command = new UpdateSettingsCommand(current.condominiumName(), adminWhatsapp,
            current.minAdvanceDays(), current.nextDayWindowStart(), current.nextDayWindowEnd(),
            current.maxAdvanceDays(), current.maxActiveBookingsPerUnit(), current.residentCancelDeadlineHours(),
            current.reportWindowDays());
        settingsService.update(command, adminId);
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

        List<UnitAccount> extraUnits = createExtraHistoryUnits(condominiumId, adminId);

        return new Accounts(a101, a102, b201, b202, a103, b203, extraUnits);
    }

    // FD-2 (ajuste): unidades extras só com histórico (nunca aparecem em reservas futuras, blocos
    // ou reports pré-montados), usadas apenas pelo sorteio de {@link #createSixMonthHistory}.
    private List<UnitAccount> createExtraHistoryUnits(UUID condominiumId, UUID adminId) {
        UnitAccount a201 = createUnit(condominiumId, adminId, "A", "201", List.of(
            new ResidentInput(null, "Patrícia Almeida Rezende", "5561999990010", "patricia.rezende@exemplo.test",
                CPF_A201_PRINCIPAL, true)), demoPassword, false);

        UnitAccount a202 = createUnit(condominiumId, adminId, "A", "202", List.of(
            new ResidentInput(null, "Thiago Barbosa Cunha", "5561999990011", "thiago.cunha@exemplo.test",
                CPF_A202_PRINCIPAL, true),
            new ResidentInput(null, "Renata Barbosa Cunha", "5561999990012", "renata.cunha@exemplo.test",
                CPF_A202_RESIDENT_2, false)), demoPassword, false);

        UnitAccount a203 = createUnit(condominiumId, adminId, "A", "203", List.of(
            new ResidentInput(null, "Vinícius Moreira Santana", "5561999990013", "vinicius.santana@exemplo.test",
                CPF_A203_PRINCIPAL, true)), demoPassword, false);

        UnitAccount a204 = createUnit(condominiumId, adminId, "A", "204", List.of(
            new ResidentInput(null, "Beatriz Carvalho Lima", "5561999990014", "beatriz.lima@exemplo.test",
                CPF_A204_PRINCIPAL, true)), demoPassword, false);

        UnitAccount b101 = createUnit(condominiumId, adminId, "B", "101", List.of(
            new ResidentInput(null, "Eduardo Nascimento Farias", "5561999990015", "eduardo.farias@exemplo.test",
                CPF_B101_PRINCIPAL, true)), demoPassword, false);

        UnitAccount b102 = createUnit(condominiumId, adminId, "B", "102", List.of(
            new ResidentInput(null, "Fernanda Azevedo Correia", "5561999990016", "fernanda.correia@exemplo.test",
                CPF_B102_PRINCIPAL, true)), demoPassword, false);

        UnitAccount b103 = createUnit(condominiumId, adminId, "B", "103", List.of(
            new ResidentInput(null, "Gustavo Pinheiro Ramos", "5561999990017", "gustavo.ramos@exemplo.test",
                CPF_B103_PRINCIPAL, true),
            new ResidentInput(null, "Débora Pinheiro Ramos", "5561999990018", "debora.ramos@exemplo.test",
                CPF_B103_RESIDENT_2, false)), demoPassword, false);

        UnitAccount b104 = createUnit(condominiumId, adminId, "B", "104", List.of(
            new ResidentInput(null, "Isabela Monteiro Braga", "5561999990019", "isabela.braga@exemplo.test",
                CPF_B104_PRINCIPAL, true)), demoPassword, false);

        return List.of(a201, a202, a203, a204, b101, b102, b103, b104);
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
            null, null, null, startAt(today.minusDays(2), LocalTime.of(10, 0), zone));

        // b-203: reserva CONFIRMED da Churrasqueira 1 há 20 dias -> report RESOLVED com custo e comentário.
        UUID reservationForResolvedReport = history.insertConfirmedReservation(condominiumId, areaIds.barbecue1(),
            a.b203().unitId(), a.b203().principalResidentId(), "Diego Pereira Vieira", "5561999990009",
            startAt(today.minusDays(20), LocalTime.of(12, 0), zone), startAt(today.minusDays(20), LocalTime.of(13, 0),
                zone), 5, true, new BigDecimal("50.00"), a.b203().accountId(),
            startAt(today.minusDays(21), LocalTime.of(9, 0), zone));
        Instant resolvedAt = startAt(today.minusDays(15), LocalTime.of(14, 0), zone);
        UUID resolvedReportId = history.insertReport(condominiumId, reservationForResolvedReport,
            areaIds.barbecue1(), a.b203().unitId(), a.b203().principalResidentId(), "Diego Pereira Vieira",
            "DAMAGE", "Uma das bancadas de apoio estava rachada e precisou ser trocada.", "RESOLVED", null,
            new BigDecimal("120.00"), resolvedAt, startAt(today.minusDays(18), LocalTime.of(9, 0), zone));
        history.insertReportComment(resolvedReportId, adminId,
            "Bancada trocada pela manutenção; custo debitado do rateio extraordinário do mês.", true, resolvedAt);
    }

    private static Instant startAt(LocalDate date, LocalTime time, ZoneId zone) {
        return ZonedDateTime.of(date, time, zone).toInstant();
    }

    // ---- ~6 meses de historico para o dashboard (FD-2 completo, D-59/D-62) -------------------

    // Semente fixa: mesmo Clock -> mesmo cenario sempre (docs/08 §2).
    private static final long HISTORY_RANDOM_SEED = 20261110L;
    // Mais peso a tarde/noite (RN-24 permite ate as 22:00); repetido = mais chance de sair sorteado.
    private static final int[] HISTORY_HOURS = {9, 10, 11, 14, 14, 15, 15, 16, 16, 17, 17, 18, 18, 19, 19, 20};
    private static final String[] HISTORY_CATEGORIES =
        {"DAMAGE", "MALFUNCTION", "CLEANLINESS", "SAFETY", "MISSING_ITEM", "OTHER"};
    private static final Map<String, String> HISTORY_REPORT_DESCRIPTIONS = Map.of(
        "DAMAGE", "Uma das cadeiras plasticas estava rachada ao final do uso do espaco.",
        "MALFUNCTION", "A torneira da pia de apoio nao fechava direito apos o uso.",
        "CLEANLINESS", "O espaco estava com lixo acumulado do uso anterior, antes da limpeza.",
        "SAFETY", "Uma tomada proxima a bancada estava com o fio parcialmente exposto.",
        "MISSING_ITEM", "Faltava uma das taças do kit de utensilios do espaco.",
        "OTHER", "Barulho de vazamento no encanamento durante o uso do espaco.");

    private void createSixMonthHistory(Scenario scenario, Areas areaIds) {
        RuleSettingsSnapshot rules = settingsService.current();
        ZoneId zone = ZoneId.of(rules.timezone());
        LocalDate today = LocalDate.now(clock);
        UUID condominiumId = scenario.condominiumId();
        Accounts accounts = scenario.accounts();

        List<HistoryArea> pool = List.of(
            new HistoryArea(areaIds.partyRoom(), true, new BigDecimal("150.00"), 3),
            new HistoryArea(areaIds.barbecue1(), true, new BigDecimal("50.00"), 1),
            new HistoryArea(areaIds.barbecue2(), false, null, 1),
            new HistoryArea(areaIds.gourmet(), false, null, 1),
            new HistoryArea(areaIds.sportsCourt(), false, null, 1));
        // RN-16: a Piscina fica de fora do historico (ja nasce em manutencao, docs/08 §2).

        // docs/08 §2: a-103/b-203 sao as unidades "com historico, usadas nos graficos" — as outras 4
        // ja tem contagem exata fixada pelas situacoes pre-montadas (RN-22 em b-201, pendente em
        // b-202, janela de report em a-101, senha temporaria em a-102) e nao podem ganhar reservas
        // extras sem quebrar essas contagens.
        List<UnitProfile> units = List.of(
            new UnitProfile(accounts.a103(), "Larissa Gomes Duarte", "5561999990008"),
            new UnitProfile(accounts.b203(), "Diego Pereira Vieira", "5561999990009"));

        Map<UUID, List<Instant[]>> confirmedSlots = new HashMap<>();
        registerSlot(confirmedSlots, areaIds.gourmet(), today.minusDays(1), LocalTime.of(19, 0), LocalTime.of(20, 0),
            zone);
        registerSlot(confirmedSlots, areaIds.sportsCourt(), today.minusDays(10), LocalTime.of(9, 0),
            LocalTime.of(10, 0), zone);
        registerSlot(confirmedSlots, areaIds.barbecue1(), today.minusDays(5), LocalTime.of(12, 0),
            LocalTime.of(13, 0), zone);
        registerSlot(confirmedSlots, areaIds.barbecue1(), today.minusDays(20), LocalTime.of(12, 0),
            LocalTime.of(13, 0), zone);

        Random random = new Random(HISTORY_RANDOM_SEED);
        int reportCycle = 0;
        int confirmedCount = 0;
        for (LocalDate date = today.minusMonths(6); date.isBefore(today); date = date.plusDays(1)) {
            boolean weekendish = isWeekendish(date.getDayOfWeek());
            for (HistoryArea area : pool) {
                double chance = weekendish ? 0.45 : 0.12;
                if (random.nextDouble() >= chance) {
                    continue;
                }
                UnitProfile unit = units.get(random.nextInt(units.size()));
                int hour = pickHistoryHour(random, area.durationHours());
                Instant startAt = startAt(date, LocalTime.of(hour, 0), zone);
                Instant endAt = startAt.plus(area.durationHours(), ChronoUnit.HOURS);
                String status = pickHistoryStatus(random);
                Instant createdAt = startAt.minus(1 + random.nextInt(5), ChronoUnit.DAYS);
                int guests = 2 + random.nextInt(area.durationHours() == 3 ? 20 : 6);

                if (status.equals("CONFIRMED")) {
                    if (overlaps(confirmedSlots, area.areaId(), startAt, endAt)) {
                        continue;
                    }
                    confirmedSlots.computeIfAbsent(area.areaId(), key -> new ArrayList<>())
                        .add(new Instant[] {startAt, endAt});
                    UUID reservationId = history.insertConfirmedReservation(condominiumId, area.areaId(),
                        unit.unitId(), unit.principalResidentId(), unit.residentName(), unit.residentPhone(),
                        startAt, endAt, guests, area.requiresPayment(), area.price(), unit.accountId(), createdAt);
                    confirmedCount++;
                    // Report historico a cada ~9a reserva confirmada, alternando resolvido/descartado.
                    if (confirmedCount % 9 == 0) {
                        createHistoryReport(condominiumId, reservationId, area.areaId(), unit, reportCycle++,
                            startAt);
                    }
                } else {
                    Instant cancelledAt = createdAt.plus(1 + random.nextInt(2), ChronoUnit.DAYS);
                    if (!cancelledAt.isBefore(startAt)) {
                        cancelledAt = startAt.minus(1, ChronoUnit.HOURS);
                    }
                    history.insertReservation(condominiumId, area.areaId(), unit.unitId(),
                        unit.principalResidentId(), unit.residentName(), unit.residentPhone(), startAt, endAt,
                        guests, area.requiresPayment(), area.price(), "CANCELLED", status,
                        historyCancellationReason(status), null, unit.accountId(), createdAt, cancelledAt);
                }
            }
        }

        // RF-SIN-01/F7: reserva ativa hoje apos as 10:00, gravada como historica (evita a janela RN-20).
        UnitProfile todayUnit = units.get(0); // a-103
        Instant todayStart = startAt(today, LocalTime.of(14, 0), zone);
        Instant todayEnd = todayStart.plus(1, ChronoUnit.HOURS);
        history.insertConfirmedReservation(condominiumId, areaIds.barbecue2(), todayUnit.unitId(),
            todayUnit.principalResidentId(), todayUnit.residentName(), todayUnit.residentPhone(), todayStart,
            todayEnd, 4, false, null, todayUnit.accountId(), todayStart.minus(4, ChronoUnit.DAYS));

        createExtraUnitsGuaranteedHistory(scenario, areaIds, zone, today, confirmedSlots);
    }

    // FD-2 (ajuste): as 3 areas gratuitas usadas aqui bastam para nao mexer em requires_payment.
    // Cada unidade "sorteia" seus proprios dias/horarios (mesmo vies de sex/sab/dom e mesmas
    // HISTORY_HOURS do historico principal, semente fixa), checando overlap em memoria contra
    // confirmedSlots (compartilhado com o sorteio de a-103/b-203) para nunca violar a exclusion
    // constraint. Contagens escalonadas (10..31, sem empate) dao um ranking "top 10 unidades" mais
    // equilibrado que o a-103/b-203 do sorteio principal (docs/08 §2).
    private static final int[] EXTRA_UNIT_HISTORY_COUNTS = {10, 13, 16, 19, 22, 25, 28, 31};

    private void createExtraUnitsGuaranteedHistory(Scenario scenario, Areas areaIds, ZoneId zone, LocalDate today,
        Map<UUID, List<Instant[]>> confirmedSlots) {
        UUID condominiumId = scenario.condominiumId();
        Accounts accounts = scenario.accounts();
        List<UnitProfile> extras = List.of(
            new UnitProfile(accounts.extraUnits().get(0), "Patrícia Almeida Rezende", "5561999990010"),
            new UnitProfile(accounts.extraUnits().get(1), "Thiago Barbosa Cunha", "5561999990011"),
            new UnitProfile(accounts.extraUnits().get(2), "Vinícius Moreira Santana", "5561999990013"),
            new UnitProfile(accounts.extraUnits().get(3), "Beatriz Carvalho Lima", "5561999990014"),
            new UnitProfile(accounts.extraUnits().get(4), "Eduardo Nascimento Farias", "5561999990015"),
            new UnitProfile(accounts.extraUnits().get(5), "Fernanda Azevedo Correia", "5561999990016"),
            new UnitProfile(accounts.extraUnits().get(6), "Gustavo Pinheiro Ramos", "5561999990017"),
            new UnitProfile(accounts.extraUnits().get(7), "Isabela Monteiro Braga", "5561999990019"));
        UUID[] freeAreas = {areaIds.gourmet(), areaIds.sportsCourt(), areaIds.barbecue2()};

        Random random = new Random(HISTORY_RANDOM_SEED + 1);
        for (int i = 0; i < extras.size(); i++) {
            UnitProfile unit = extras.get(i);
            int remaining = EXTRA_UNIT_HISTORY_COUNTS[i];
            LocalDate date = today.minusMonths(6);
            while (remaining > 0 && date.isBefore(today)) {
                double chance = isWeekendish(date.getDayOfWeek()) ? 0.35 : 0.10;
                if (random.nextDouble() < chance) {
                    UUID areaId = freeAreas[random.nextInt(freeAreas.length)];
                    int hour = pickHistoryHour(random, 1);
                    Instant startAt = startAt(date, LocalTime.of(hour, 0), zone);
                    Instant endAt = startAt.plus(1, ChronoUnit.HOURS);
                    if (!overlaps(confirmedSlots, areaId, startAt, endAt)) {
                        confirmedSlots.computeIfAbsent(areaId, key -> new ArrayList<>())
                            .add(new Instant[] {startAt, endAt});
                        history.insertConfirmedReservation(condominiumId, areaId, unit.unitId(),
                            unit.principalResidentId(), unit.residentName(), unit.residentPhone(), startAt, endAt, 4,
                            false, null, unit.accountId(), startAt.minus(3, ChronoUnit.DAYS));
                        remaining--;
                    }
                }
                date = date.plusDays(1);
            }
        }
    }

    private void createHistoryReport(UUID condominiumId, UUID reservationId, UUID areaId, UnitProfile unit,
        int cycle, Instant reservationStartAt) {
        String category = HISTORY_CATEGORIES[cycle % HISTORY_CATEGORIES.length];
        String description = HISTORY_REPORT_DESCRIPTIONS.get(category);
        Instant createdAt = reservationStartAt.plus(1, ChronoUnit.DAYS);
        boolean resolve = cycle % 2 == 0;
        if (resolve) {
            Instant resolvedAt = createdAt.plus(2 + cycle % 5, ChronoUnit.DAYS);
            BigDecimal cost = BigDecimal.valueOf(30 + (cycle % 6) * 15);
            history.insertReport(condominiumId, reservationId, areaId, unit.unitId(), unit.principalResidentId(),
                unit.residentName(), category, description, "RESOLVED", null, cost, resolvedAt, createdAt);
        } else {
            history.insertReport(condominiumId, reservationId, areaId, unit.unitId(), unit.principalResidentId(),
                unit.residentName(), category, description, "DISMISSED",
                "Revisado pela sindicatura; sem evidencia de dano, uso normal do espaco.", null, null, createdAt);
        }
    }

    private static void registerSlot(Map<UUID, List<Instant[]>> slots, UUID areaId, LocalDate date, LocalTime start,
        LocalTime end, ZoneId zone) {
        slots.computeIfAbsent(areaId, key -> new ArrayList<>())
            .add(new Instant[] {startAt(date, start, zone), startAt(date, end, zone)});
    }

    private static boolean overlaps(Map<UUID, List<Instant[]>> slots, UUID areaId, Instant start, Instant end) {
        List<Instant[]> existing = slots.get(areaId);
        if (existing == null) {
            return false;
        }
        for (Instant[] slot : existing) {
            if (start.isBefore(slot[1]) && slot[0].isBefore(end)) {
                return true;
            }
        }
        return false;
    }

    private static boolean isWeekendish(DayOfWeek dayOfWeek) {
        return dayOfWeek == DayOfWeek.FRIDAY || dayOfWeek == DayOfWeek.SATURDAY || dayOfWeek == DayOfWeek.SUNDAY;
    }

    private static int pickHistoryHour(Random random, int durationHours) {
        int maxStart = 22 - durationHours;
        int hour;
        do {
            hour = HISTORY_HOURS[random.nextInt(HISTORY_HOURS.length)];
        } while (hour > maxStart);
        return hour;
    }

    /** `CONFIRMED` (maioria) ou o autor do cancelamento (`RESIDENT`/`ADMIN`/`SYSTEM`, minoria). */
    private static String pickHistoryStatus(Random random) {
        double r = random.nextDouble();
        if (r < 0.78) {
            return "CONFIRMED";
        }
        if (r < 0.90) {
            return "RESIDENT";
        }
        if (r < 0.95) {
            return "ADMIN";
        }
        return "SYSTEM";
    }

    private static String historyCancellationReason(String cancelledBy) {
        return switch (cancelledBy) {
            case "RESIDENT" -> "Cancelado pelo morador antes da data.";
            case "ADMIN" -> "Cancelado pela administração; espaço necessário para outra atividade.";
            default -> "Pagamento não confirmado dentro do prazo (RN-31).";
        };
    }

    private record HistoryArea(UUID areaId, boolean requiresPayment, BigDecimal price, int durationHours) {
    }

    private record UnitProfile(UnitAccount unit, String residentName, String residentPhone) {
        UUID unitId() {
            return unit.unitId();
        }

        UUID accountId() {
            return unit.accountId();
        }

        UUID principalResidentId() {
            return unit.principalResidentId();
        }
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
        UnitAccount a103, UnitAccount b203, List<UnitAccount> extraUnits) {
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
