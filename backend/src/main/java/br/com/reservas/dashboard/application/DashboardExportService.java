package br.com.reservas.dashboard.application;

import br.com.reservas.dashboard.domain.DashboardPeriod;
import br.com.reservas.dashboard.domain.ExportFormat;
import br.com.reservas.dashboard.domain.ExportType;
import br.com.reservas.dashboard.infra.DashboardMetricsRepository;
import br.com.reservas.dashboard.infra.PaymentExportRow;
import br.com.reservas.dashboard.infra.ReportExportRow;
import br.com.reservas.dashboard.infra.ReservationExportRow;
import br.com.reservas.dashboard.infra.UnitExportRow;
import br.com.reservas.settings.application.CondominiumSettingsService;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Clock;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * `GET /exports/{type}` (F8-2, RF-DAS-03, D-61/D-62): resolve o mesmo
 * {@link DashboardPeriod} do dashboard (RF-DAS-01), monta uma {@link ExportTable}
 * por tipo (reaproveitando `areaMetrics` para `areas`, igual `/dashboard/areas`,
 * e consultas próprias de {@link DashboardMetricsRepository} para os demais) e
 * delega os bytes ao {@link ExportTableWriter} do formato pedido. Cabeçalhos e
 * rótulos de enum em pt-BR; nunca CPF, telefone ou e-mail (RNF-01).
 */
@Service
public class DashboardExportService {

    private static final Map<String, String> RESERVATION_KIND_LABELS = Map.of("BOOKING", "Reserva", "BLOCK",
        "Bloqueio");
    private static final Map<String, String> RESERVATION_STATUS_LABELS = Map.of("PENDING_PAYMENT",
        "Aguardando pagamento", "CONFIRMED", "Confirmada", "CANCELLED", "Cancelada");
    private static final Map<String, String> CANCELLED_BY_LABELS = Map.of("RESIDENT", "Morador", "ADMIN",
        "Administração", "SYSTEM", "Sistema");
    private static final Map<String, String> REPORT_CATEGORY_LABELS = Map.of("DAMAGE", "Dano", "MALFUNCTION",
        "Mau funcionamento", "CLEANLINESS", "Limpeza", "SAFETY", "Segurança", "MISSING_ITEM", "Item faltando",
        "OTHER", "Outro");
    private static final Map<String, String> REPORT_STATUS_LABELS = Map.of("OPEN", "Aberto", "IN_REVIEW",
        "Em análise", "IN_MAINTENANCE", "Em manutenção", "RESOLVED", "Resolvido", "DISMISSED", "Descartado");
    private static final Map<String, String> AREA_CATEGORY_LABELS = Map.ofEntries(Map.entry("PARTY_ROOM",
            "Salão de festas"), Map.entry("BARBECUE", "Churrasqueira"), Map.entry("GOURMET_SPACE", "Espaço gourmet"),
        Map.entry("POOL", "Piscina"), Map.entry("SPORTS_COURT", "Quadra poliesportiva"), Map.entry("TENNIS_COURT",
            "Quadra de tênis"), Map.entry("GYM", "Academia"), Map.entry("GAME_ROOM", "Salão de jogos"),
        Map.entry("TOY_ROOM", "Brinquedoteca"), Map.entry("PLAYGROUND", "Playground"), Map.entry("SAUNA", "Sauna"),
        Map.entry("CINEMA", "Cinema/Home theater"), Map.entry("COWORKING", "Coworking/Sala de estudos"),
        Map.entry("PET_PLACE", "Pet place"), Map.entry("OTHER", "Outro"));
    private static final Map<String, String> AREA_STATUS_LABELS = Map.of("ACTIVE", "Disponível", "MAINTENANCE",
        "Em manutenção", "RENOVATION", "Em reforma", "INTERDICTED", "Interditada", "INACTIVE", "Desativada");

    private final DashboardMetricsRepository repository;
    private final CondominiumSettingsService settingsService;
    private final Map<ExportFormat, ExportTableWriter> writersByFormat;
    private final Clock clock;

    public DashboardExportService(DashboardMetricsRepository repository, CondominiumSettingsService settingsService,
        List<ExportTableWriter> writers, Clock clock) {
        this.repository = repository;
        this.settingsService = settingsService;
        this.writersByFormat = writers.stream().collect(Collectors.toMap(ExportTableWriter::format,
            Function.identity()));
        this.clock = clock;
    }

    @Transactional(readOnly = true)
    public ExportFile export(UUID condominiumId, String typeParam, String formatParam, LocalDate from,
        LocalDate to) {
        ExportType type = ExportType.fromParam(typeParam);
        ExportFormat format = ExportFormat.fromParam(formatParam);
        String timezone = settingsService.current().timezone();
        LocalDate today = LocalDate.now(clock.withZone(ZoneId.of(timezone)));
        DashboardPeriod period = DashboardPeriod.resolve(from, to, today);

        ExportTable table = tableFor(type, condominiumId, period, timezone);
        byte[] content = writersByFormat.get(format).write(table);
        String filename = "turno-%s-%s-a-%s.%s".formatted(type.param(), period.from(), period.to(),
            format.extension());
        return new ExportFile(content, filename, format.contentType());
    }

    private ExportTable tableFor(ExportType type, UUID condominiumId, DashboardPeriod period, String timezone) {
        return switch (type) {
            case RESERVATIONS -> reservationsTable(condominiumId, period, timezone);
            case AREAS -> areasTable(condominiumId, period, timezone);
            case REPORTS -> reportsTable(condominiumId, period, timezone);
            case PAYMENTS -> paymentsTable(condominiumId, period, timezone);
            case UNITS -> unitsTable(condominiumId, period, timezone);
        };
    }

    private ExportTable reservationsTable(UUID condominiumId, DashboardPeriod period, String timezone) {
        List<ReservationExportRow> found = repository.reservationExportRows(condominiumId, period.from(),
            period.to(), timezone);
        List<List<Object>> rows = new ArrayList<>();
        for (ReservationExportRow r : found) {
            rows.add(Arrays.asList(text(r.code()), label(RESERVATION_KIND_LABELS, r.kind()), text(r.areaName()),
                text(r.unitIdentifier()), text(r.residentName()), r.start().toLocalDate(),
                r.start().toLocalTime(), r.end().toLocalTime(), r.guests() == null ? 0L : r.guests().longValue(),
                label(RESERVATION_STATUS_LABELS, r.status()), label(CANCELLED_BY_LABELS, r.cancelledBy()),
                text(r.reason()), r.amount() == null ? BigDecimal.ZERO.setScale(2) : r.amount()));
        }
        return new ExportTable(List.of("Protocolo", "Tipo", "Área", "Unidade", "Responsável", "Data", "Início",
            "Fim", "Convidados", "Status", "Cancelada por", "Motivo", "Valor"), rows);
    }

    private ExportTable areasTable(UUID condominiumId, DashboardPeriod period, String timezone) {
        List<AreaMetricView> found = repository.areaMetrics(condominiumId, period.from(), period.to(), timezone);
        List<List<Object>> rows = new ArrayList<>();
        for (AreaMetricView a : found) {
            BigDecimal occupancyPercent = a.occupancyRate().multiply(BigDecimal.valueOf(100))
                .setScale(2, RoundingMode.HALF_UP);
            rows.add(Arrays.asList(text(a.areaName()), label(AREA_CATEGORY_LABELS, a.category()),
                label(AREA_STATUS_LABELS, a.status()), a.reservations(), a.reservedHours(), a.availableHours(),
                occupancyPercent, a.reports(), a.maintenanceCost()));
        }
        return new ExportTable(List.of("Área", "Categoria", "Status", "Reservas", "Horas reservadas",
            "Horas disponíveis", "Ocupação (%)", "Reports", "Custo de manutenção"), rows);
    }

    private ExportTable reportsTable(UUID condominiumId, DashboardPeriod period, String timezone) {
        List<ReportExportRow> found = repository.reportExportRows(condominiumId, period.from(), period.to(),
            timezone);
        List<List<Object>> rows = new ArrayList<>();
        for (ReportExportRow r : found) {
            rows.add(Arrays.asList(text(r.code()), text(r.areaName()), text(r.unitIdentifier()),
                label(REPORT_CATEGORY_LABELS, r.category()), label(REPORT_STATUS_LABELS, r.status()), r.openedAt(),
                r.resolvedAt(), r.maintenanceCost() == null ? BigDecimal.ZERO.setScale(2) : r.maintenanceCost()));
        }
        return new ExportTable(List.of("Protocolo", "Área", "Unidade", "Categoria", "Status", "Aberto em",
            "Resolvido em", "Custo de manutenção"), rows);
    }

    private ExportTable paymentsTable(UUID condominiumId, DashboardPeriod period, String timezone) {
        List<PaymentExportRow> found = repository.paymentExportRows(condominiumId, period.from(), period.to(),
            timezone);
        List<List<Object>> rows = new ArrayList<>();
        for (PaymentExportRow r : found) {
            rows.add(Arrays.asList(text(r.code()), text(r.areaName()), text(r.unitIdentifier()), r.date(),
                r.amount() == null ? BigDecimal.ZERO.setScale(2) : r.amount(),
                label(RESERVATION_STATUS_LABELS, r.status()), r.paymentConfirmedAt()));
        }
        return new ExportTable(List.of("Protocolo", "Área", "Unidade", "Data", "Valor", "Status",
            "Pagamento confirmado em"), rows);
    }

    private ExportTable unitsTable(UUID condominiumId, DashboardPeriod period, String timezone) {
        List<UnitExportRow> found = repository.unitExportRows(condominiumId, period.from(), period.to(), timezone);
        List<List<Object>> rows = new ArrayList<>();
        for (UnitExportRow u : found) {
            rows.add(Arrays.asList(text(u.identifier()), text(u.block()), text(u.number()), u.active(),
                u.activeResidents(), u.reservations()));
        }
        return new ExportTable(List.of("Unidade", "Bloco", "Número", "Ativa", "Moradores ativos",
            "Reservas no período"), rows);
    }

    private static String label(Map<String, String> labels, String raw) {
        return raw == null ? "" : labels.getOrDefault(raw, raw);
    }

    private static String text(String value) {
        return value == null ? "" : ExportTable.sanitizeText(value);
    }
}
