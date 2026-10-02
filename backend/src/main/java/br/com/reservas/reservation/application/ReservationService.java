package br.com.reservas.reservation.application;

import br.com.reservas.area.application.AreaBookingInfo;
import br.com.reservas.area.application.AreaQueryService;
import br.com.reservas.auth.application.AccountService;
import br.com.reservas.auth.application.AccountSummary;
import br.com.reservas.auth.domain.Role;
import br.com.reservas.reservation.domain.Reservation;
import br.com.reservas.reservation.domain.ReservationEvent;
import br.com.reservas.reservation.domain.ReservationKind;
import br.com.reservas.reservation.domain.ReservationPolicy;
import br.com.reservas.reservation.domain.ReservationStatus;
import br.com.reservas.reservation.domain.WhatsAppPaymentLink;
import br.com.reservas.reservation.infra.ReservationCodeGenerator;
import br.com.reservas.reservation.infra.ReservationEventRepository;
import br.com.reservas.reservation.infra.ReservationRepository;
import br.com.reservas.reservation.infra.ReservationSpecifications;
import br.com.reservas.settings.application.CondominiumSettingsService;
import br.com.reservas.settings.application.RuleSettingsSnapshot;
import br.com.reservas.shared.audit.AuditService;
import br.com.reservas.shared.error.BusinessException;
import br.com.reservas.unit.application.UnitService;
import br.com.reservas.unit.domain.Resident;
import br.com.reservas.unit.domain.Unit;
import jakarta.persistence.EntityManager;
import jakarta.persistence.EntityNotFoundException;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.time.temporal.ChronoUnit;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;
import org.springframework.dao.PessimisticLockingFailureException;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Interface pública do módulo `reservation` para criação (RF-RES-01..04,
 * RN-18..26), listagem de "Minhas reservas" (RF-RES-05) e confirmações de
 * pagamento (RF-PAG-02..04). {@link #expirePendingIfNeeded} (RN-31) é o ponto
 * único de expiração em lote, chamado por toda consulta/ação deste serviço,
 * por {@link AvailabilityService} (mesmo pacote) e pelo job agendado
 * (F5-2, {@code ReservationExpirationJob}, `reservation.infra`).
 */
@Service
public class ReservationService {

    private static final List<ReservationStatus> ACTIVE_STATUSES =
        List.of(ReservationStatus.PENDING_PAYMENT, ReservationStatus.CONFIRMED);

    // RN-29/RN-31: mesma mensagem gravada em `status_reason` e mostrada ao morador em `statusReason`.
    static final String EXPIRED_REASON = "Pagamento não confirmado até o início da reserva.";

    // RF-PAG-02: janela usada em "within48h" de `GET /payments/pending`.
    private static final Duration WITHIN_48H = Duration.ofHours(48);

    private final ReservationRepository reservations;
    private final ReservationEventRepository events;
    private final ReservationCodeGenerator codeGenerator;
    private final AreaQueryService areaQueryService;
    private final UnitService unitService;
    private final CondominiumSettingsService settingsService;
    private final AccountService accountService;
    private final AuditService audit;
    private final Clock clock;
    private final EntityManager entityManager;

    public ReservationService(ReservationRepository reservations, ReservationEventRepository events,
        ReservationCodeGenerator codeGenerator, AreaQueryService areaQueryService, UnitService unitService,
        CondominiumSettingsService settingsService, AccountService accountService, AuditService audit, Clock clock,
        EntityManager entityManager) {
        this.reservations = reservations;
        this.events = events;
        this.codeGenerator = codeGenerator;
        this.areaQueryService = areaQueryService;
        this.unitService = unitService;
        this.settingsService = settingsService;
        this.accountService = accountService;
        this.audit = audit;
        this.clock = clock;
        this.entityManager = entityManager;
    }

    /** RF-RES-01..04: valida (RN-18..24), define o status inicial (RN-25) e grava o evento CREATED. */
    @Transactional
    public ReservationCreationResult create(UUID unitId, UUID actorId, CreateReservationCommand command) {
        AreaBookingInfo area = areaQueryService.findBookable(command.areaId())
            .orElseThrow(() -> new EntityNotFoundException("Área não encontrada: " + command.areaId()));
        Resident resident = unitService.findActiveResident(unitId, command.residentId())
            .orElseThrow(() -> new BusinessException("VALIDATION_ERROR", HttpStatus.UNPROCESSABLE_ENTITY,
                "O morador (residentId) precisa ser um morador ativo da sua unidade."));
        Unit unit = unitService.findActiveUnit(unitId)
            .orElseThrow(() -> new IllegalStateException("Unidade do token não encontrada: " + unitId));

        RuleSettingsSnapshot rules = settingsService.current();
        ZoneId zone = ZoneId.of(rules.timezone());
        ZonedDateTime now = ZonedDateTime.now(clock.withZone(zone));
        long activeUnitBookings = reservations.countByUnitIdAndStatusInAndStartAtGreaterThanEqual(unitId,
            ACTIVE_STATUSES, now.toInstant());

        ReservationPolicy.validate(
            new ReservationPolicy.Request(command.date(), command.startTime(), command.endTime(), command.guests()),
            new ReservationPolicy.Context(now, rules, area, activeUnitBookings));

        Instant startAt = ZonedDateTime.of(command.date(), command.startTime(), zone).toInstant();
        Instant endAt = ZonedDateTime.of(command.date(), command.endTime(), zone).toInstant();
        ReservationStatus status = area.requiresPayment() ? ReservationStatus.PENDING_PAYMENT
            : ReservationStatus.CONFIRMED;
        String code = codeGenerator.next(now.getYear());
        Instant createdAt = now.toInstant();

        Reservation reservation = new Reservation(code, unit.getCondominiumId(), area.id(), unitId, resident.getId(),
            resident.getName(), resident.getPhone(), startAt, endAt, command.guests(), command.notes(), status,
            area.requiresPayment(), area.price(), actorId, createdAt);
        // RN-24 (sobreposição) é a exclusion constraint do banco (23P01 -> 409 RESERVATION_OVERLAP
        // no GlobalExceptionHandler); saveNewOrBlock precisa do flush dentro da transação para a
        // violação estourar aqui, não só no commit (D-57: deadlock/serialização também viram 409
        // aqui). RNF-03: lockArea serializa a área antes do insert (ver javadoc do repositório).
        reservations.lockArea(area.id());
        saveAndFlushOrOverlap(reservation);
        events.save(new ReservationEvent(reservation.getId(), "CREATED", null, actorId, createdAt));

        String whatsappPaymentUrl = whatsappUrlFor(reservation, area, unit.getIdentifier(), zone);
        ReservationView view = ReservationView.of(reservation, area.name(), zone, createdAt,
            rules.residentCancelDeadlineHours(), rules.reportWindowDays(), whatsappPaymentUrl);
        return new ReservationCreationResult(view, whatsappPaymentUrl);
    }

    /** RF-RES-05: `scope=upcoming` (início &gt;= agora, crescente) ou `past` (início &lt; agora, decrescente). */
    @Transactional
    public Page<ReservationView> listMine(UUID unitId, String scope, Pageable pageable) {
        Context ctx = currentContext();
        expirePendingIfNeeded(ctx.now());
        String unitIdentifier = unitService.findActiveUnit(unitId).map(Unit::getIdentifier).orElse("");

        Page<Reservation> page = "past".equalsIgnoreCase(scope)
            ? reservations.findByUnitIdAndStartAtLessThanOrderByStartAtDesc(unitId, ctx.now(), pageable)
            : reservations.findByUnitIdAndStartAtGreaterThanEqualOrderByStartAtAsc(unitId, ctx.now(), pageable);

        // Evita N+1: resolve os dados das áreas das reservas da página inteira em uma única consulta.
        Map<UUID, AreaBookingInfo> areasById = areaQueryService.findBookableByIds(
            page.getContent().stream().map(Reservation::getAreaId).distinct().toList());
        return page.map(r -> toDto(r, ctx.zone(), ctx.now(), ctx.rules(), unitIdentifier, areasById));
    }

    /** RN-01/RN-30: 403 FORBIDDEN_RESOURCE se a reserva não existir ou não for da unidade (nunca 404). */
    @Transactional
    public ReservationView findMine(UUID unitId, UUID reservationId) {
        Context ctx = currentContext();
        expirePendingIfNeeded(ctx.now());
        Reservation r = reservations.findById(reservationId)
            .filter(x -> unitId.equals(x.getUnitId()))
            .orElseThrow(ReservationService::forbiddenResource);
        Optional<AreaBookingInfo> area = areaQueryService.findBookable(r.getAreaId());
        String areaName = area.map(AreaBookingInfo::name).orElse("");
        String unitIdentifier = unitService.findActiveUnit(unitId).map(Unit::getIdentifier).orElse("");
        String whatsappPaymentUrl = area.map(a -> whatsappUrlFor(r, a, unitIdentifier, ctx.zone())).orElse(null);
        return ReservationView.of(r, areaName, ctx.zone(), ctx.now(), ctx.rules().residentCancelDeadlineHours(),
            ctx.rules().reportWindowDays(), whatsappPaymentUrl);
    }

    /** `GET /reservations/{id}` (S/A): não encontrada -&gt; 404 (não é IDOR, é role, checado pelo `@PreAuthorize`). */
    @Transactional
    public AdminReservationView findForAdmin(UUID reservationId) {
        Context ctx = currentContext();
        expirePendingIfNeeded(ctx.now());
        Reservation r = findReservationOrThrow(reservationId);
        return toAdminView(r, ctx.zone(), ctx.now(), ctx.rules());
    }

    /** `GET /reservations` (S/A, D-53): sem correspondência de `unitIdentifier` -&gt; página vazia. */
    @Transactional
    public Page<AdminReservationView> search(ReservationFilter filter, Pageable pageable) {
        Context ctx = currentContext();
        expirePendingIfNeeded(ctx.now());
        ZoneId zone = ctx.zone();
        Instant now = ctx.now();
        RuleSettingsSnapshot rules = ctx.rules();

        UUID unitId = filter.unitId();
        if (filter.unitIdentifier() != null && !filter.unitIdentifier().isBlank()) {
            Optional<UUID> found = unitService.findIdByIdentifier(filter.unitIdentifier());
            if (found.isEmpty()) {
                return Page.empty(pageable);
            }
            unitId = found.get();
        }
        var spec = ReservationSpecifications.of(filter, unitId, zone);
        Page<Reservation> page = reservations.findAll(spec, pageable);
        List<Reservation> content = page.getContent();
        Map<UUID, AreaBookingInfo> areasById = areaQueryService.findBookableByIds(
            content.stream().map(Reservation::getAreaId).distinct().toList());
        Map<UUID, String> unitIdentifiersById = unitService.identifiersByIds(
            content.stream().map(Reservation::getUnitId).filter(Objects::nonNull).distinct().toList());
        return page.map(r -> {
            AreaBookingInfo area = areasById.get(r.getAreaId());
            String areaName = area == null ? "" : area.name();
            String unitIdentifier = r.getUnitId() == null ? null : unitIdentifiersById.get(r.getUnitId());
            String whatsappPaymentUrl = area == null ? null
                : whatsappUrlFor(r, area, unitIdentifier == null ? "" : unitIdentifier, zone);
            return AdminReservationView.of(r, areaName, zone, now, rules.residentCancelDeadlineHours(),
                rules.reportWindowDays(), whatsappPaymentUrl, unitIdentifier);
        });
    }

    /** `GET /payments/pending` (ADMIN, RF-PAG-02): `PENDING_PAYMENT` por início crescente, com `within48h`. */
    @Transactional
    public List<PendingPaymentView> pendingPayments() {
        Context ctx = currentContext();
        expirePendingIfNeeded(ctx.now());
        List<Reservation> pending = reservations.findByStatusAndKindOrderByStartAtAsc(
            ReservationStatus.PENDING_PAYMENT, ReservationKind.BOOKING);
        return pending.stream()
            .map(r -> new PendingPaymentView(toAdminView(r, ctx.zone(), ctx.now(), ctx.rules()),
                !r.getStartAt().isAfter(ctx.now().plus(WITHIN_48H))))
            .toList();
    }

    /**
     * `POST /reservations/{id}/confirm-payment` (ADMIN, RF-PAG-03/RN-32): pendente cujo início já
     * passou expira (RN-31) antes de tentar confirmar -&gt; `409 INVALID_STATUS_TRANSITION`.
     */
    @Transactional
    public AdminReservationView confirmPayment(UUID reservationId, UUID actorId) {
        Context ctx = currentContext();
        // RN-31 precisa rodar ANTES de carregar a reserva: o UPDATE em lote não passa pelo
        // primeiro-nivel de cache do Hibernate, entao uma entidade ja carregada nao veria o
        // status novo e um `save` mais tarde reescreveria por cima da expiracao (D-57-like).
        expirePendingIfNeeded(ctx.now());
        Reservation r = findReservationOrThrow(reservationId);

        r.confirmPayment(actorId, ctx.now());
        reservations.save(r);
        events.save(new ReservationEvent(r.getId(), "PAYMENT_CONFIRMED", null, actorId, ctx.now()));
        audit.record(actorId, Role.ADMIN.name(), "RESERVATION_CONFIRM_PAYMENT", "RESERVATION", r.getId(),
            Map.of("code", r.getCode()), null);

        return toAdminView(r, ctx.zone(), ctx.now(), ctx.rules());
    }

    /**
     * `GET /dashboard/home` (F7-1, RF-SIN-01): reservas e bloqueios ativos
     * (`PENDING_PAYMENT`/`CONFIRMED`) com início em {@code [from, toExclusive)},
     * por início crescente. Expira pendentes vencidas (RN-31) antes de montar a
     * lista, como toda leitura deste serviço.
     */
    @Transactional
    public List<br.com.reservas.shared.reservation.ReservationSummary> activeSummariesBetween(LocalDate from,
        LocalDate toExclusive) {
        Context ctx = currentContext();
        expirePendingIfNeeded(ctx.now());
        ZoneId zone = ctx.zone();
        Instant fromInstant = from.atStartOfDay(zone).toInstant();
        Instant toInstant = toExclusive.atStartOfDay(zone).toInstant();
        List<Reservation> found = reservations
            .findByStatusInAndStartAtGreaterThanEqualAndStartAtLessThanOrderByStartAtAsc(ACTIVE_STATUSES,
                fromInstant, toInstant);
        Map<UUID, AreaBookingInfo> areasById = areaQueryService.findBookableByIds(
            found.stream().map(Reservation::getAreaId).distinct().toList());
        Map<UUID, String> unitIdentifiersById = unitService.identifiersByIds(
            found.stream().map(Reservation::getUnitId).filter(Objects::nonNull).distinct().toList());
        return found.stream().map(r -> toSummary(r, zone, areasById, unitIdentifiersById)).toList();
    }

    /**
     * F6 (`report`): resumo em lote de reservas por id (mesmo tipo usado em
     * `affectedReservations`, `shared.reservation`), para o módulo `report`
     * montar listagens sem N+1 nem acessar o repositório deste módulo
     * (CLAUDE.md §5).
     */
    @Transactional(readOnly = true)
    public Map<UUID, br.com.reservas.shared.reservation.ReservationSummary> summariesByIds(
        Collection<UUID> reservationIds) {
        if (reservationIds.isEmpty()) {
            return Map.of();
        }
        List<Reservation> found = reservations.findAllById(reservationIds);
        Map<UUID, AreaBookingInfo> areasById = areaQueryService.findBookableByIds(
            found.stream().map(Reservation::getAreaId).distinct().toList());
        Map<UUID, String> unitIdentifiersById = unitService.identifiersByIds(
            found.stream().map(Reservation::getUnitId).filter(Objects::nonNull).distinct().toList());
        ZoneId zone = ZoneId.of(settingsService.current().timezone());
        return found.stream().collect(Collectors.toMap(Reservation::getId,
            r -> toSummary(r, zone, areasById, unitIdentifiersById)));
    }

    private br.com.reservas.shared.reservation.ReservationSummary toSummary(Reservation r, ZoneId zone,
        Map<UUID, AreaBookingInfo> areasById, Map<UUID, String> unitIdentifiersById) {
        AreaBookingInfo area = areasById.get(r.getAreaId());
        String areaName = area == null ? "" : area.name();
        String unitIdentifier = r.getUnitId() == null ? null : unitIdentifiersById.get(r.getUnitId());
        ZonedDateTime start = r.getStartAt().atZone(zone);
        ZonedDateTime end = r.getEndAt().atZone(zone);
        return new br.com.reservas.shared.reservation.ReservationSummary(r.getId(), r.getCode(), r.getKind().name(),
            r.getAreaId(), areaName, unitIdentifier, r.getResidentNameSnapshot(), start.toLocalDate(),
            start.toLocalTime(), end.toLocalTime(), r.getStatus().name());
    }

    /** `GET /reservations/{id}/events` (S/A, RF-RES-09): em ordem cronológica. */
    @Transactional(readOnly = true)
    public List<ReservationEventView> events(UUID reservationId) {
        findReservationOrThrow(reservationId);
        List<ReservationEvent> found = events.findByReservationIdOrderByOccurredAtAsc(reservationId);
        Set<UUID> actorIds = found.stream().map(ReservationEvent::getActorId).filter(Objects::nonNull)
            .collect(Collectors.toSet());
        Map<UUID, AccountSummary> summaries = accountService.summaries(actorIds);
        Map<UUID, String> unitIdentifiersById = unitService.identifiersByIds(summaries.values().stream()
            .filter(s -> s.role() == Role.UNIT && s.unitId() != null).map(AccountSummary::unitId).distinct().toList());
        return found.stream().map(e -> new ReservationEventView(e.getType(), e.getOccurredAt(),
            actorOf(e.getActorId(), summaries, unitIdentifiersById), e.getJustification(), e.getChanges())).toList();
    }

    private ReservationEventView.ActorView actorOf(UUID actorId, Map<UUID, AccountSummary> summaries,
        Map<UUID, String> unitIdentifiersById) {
        if (actorId == null) {
            return null;
        }
        AccountSummary summary = summaries.get(actorId);
        if (summary == null) {
            return null;
        }
        String name = switch (summary.role()) {
            case ADMIN -> "Administração";
            case SYNDIC -> summary.displayName();
            case UNIT -> unitIdentifiersById.get(summary.unitId());
        };
        return new ReservationEventView.ActorView(summary.id(), name, summary.role().name());
    }

    /** `POST /me/reservations/{id}/cancel` (RN-30): dentro do prazo, ativa e da própria unidade. */
    @Transactional
    public ReservationView cancelByResident(UUID unitId, UUID reservationId, UUID actorId) {
        Context ctx = currentContext();
        Reservation r = reservations.findById(reservationId)
            .filter(x -> unitId.equals(x.getUnitId()))
            .orElseThrow(ReservationService::forbiddenResource);
        requireWithinCancelDeadline(r, ctx.now(), ctx.rules().residentCancelDeadlineHours());

        r.cancel("RESIDENT", "Cancelada pelo morador", ctx.now());
        reservations.save(r);
        events.save(new ReservationEvent(r.getId(), "CANCELLED", "Cancelada pelo morador", actorId, ctx.now()));

        String areaName = areaQueryService.findBookable(r.getAreaId()).map(AreaBookingInfo::name).orElse("");
        return ReservationView.of(r, areaName, ctx.zone(), ctx.now(), ctx.rules().residentCancelDeadlineHours(),
            ctx.rules().reportWindowDays(), null);
    }

    /** `POST /reservations/{id}/cancel` (ADMIN, RF-RES-08/RN-27): justificativa exibida ao morador (RN-29). */
    @Transactional
    public AdminReservationView cancelByAdmin(UUID reservationId, UUID actorId, String justification) {
        requireJustification(justification);
        Context ctx = currentContext();
        Reservation r = findReservationOrThrow(reservationId);

        r.cancel("ADMIN", justification, ctx.now());
        reservations.save(r);
        events.save(new ReservationEvent(r.getId(), "CANCELLED", justification, actorId, ctx.now()));
        audit.record(actorId, Role.ADMIN.name(), "RESERVATION_CANCEL", "RESERVATION", r.getId(),
            Map.of("code", r.getCode()), justification);

        return toAdminView(r, ctx.zone(), ctx.now(), ctx.rules());
    }

    /** `PUT /reservations/{id}` (ADMIN, RF-RES-08/RN-27/RN-28): só reserva (`kind = BOOKING`) ativa. */
    @Transactional
    public AdminReservationView updateByAdmin(UUID reservationId, UUID actorId, UpdateReservationCommand command) {
        Reservation r = findReservationOrThrow(reservationId);
        if (r.getKind() != ReservationKind.BOOKING || r.getStatus() == ReservationStatus.CANCELLED) {
            throw new BusinessException("INVALID_STATUS_TRANSITION", HttpStatus.CONFLICT,
                "Só é possível alterar reservas ativas.");
        }
        requireJustification(command.justification());

        Context ctx = currentContext();
        ZoneId zone = ctx.zone();
        RuleSettingsSnapshot rules = ctx.rules();
        Map<String, Object> before = snapshot(r, zone);

        UUID newAreaId = command.areaId() != null ? command.areaId() : r.getAreaId();
        AreaBookingInfo area = areaQueryService.findBookable(newAreaId)
            .orElseThrow(() -> new EntityNotFoundException("Área não encontrada: " + newAreaId));
        LocalDate date = command.date() != null ? command.date() : r.getStartAt().atZone(zone).toLocalDate();
        LocalTime startTime = command.startTime() != null ? command.startTime()
            : r.getStartAt().atZone(zone).toLocalTime();
        LocalTime endTime = command.endTime() != null ? command.endTime() : r.getEndAt().atZone(zone).toLocalTime();

        ReservationPolicy.validateForAdminUpdate(new ReservationPolicy.Request(date, startTime, endTime,
            r.getGuests() == null ? 0 : r.getGuests()), rules.slotMinutes(), area);

        Instant newStartAt = ZonedDateTime.of(date, startTime, zone).toInstant();
        Instant newEndAt = ZonedDateTime.of(date, endTime, zone).toInstant();
        r.reschedule(newAreaId, newStartAt, newEndAt, command.justification());
        // RN-24 (sobreposição) precisa estourar aqui dentro da transação (mesmo motivo do
        // create); RNF-03: lockArea na área de destino antes do insert.
        reservations.lockArea(newAreaId);
        saveAndFlushOrOverlap(r);

        Map<String, Object> after = snapshot(r, zone);
        events.save(new ReservationEvent(r.getId(), "UPDATED", command.justification(), actorId, ctx.now(),
            Map.of("before", before, "after", after)));
        audit.record(actorId, Role.ADMIN.name(), "RESERVATION_UPDATE", "RESERVATION", r.getId(),
            Map.of("before", before, "after", after), command.justification());

        return toAdminView(r, zone, ctx.now(), rules);
    }

    /** `POST /blocks` (S/A, RF-RES-10/RN-33): sem morador, `status = CONFIRMED` direto. */
    @Transactional
    public AdminReservationView createBlock(UUID condominiumId, UUID actorId, String actorRole,
        CreateBlockCommand command) {
        AreaBookingInfo area = areaQueryService.findBookable(command.areaId())
            .orElseThrow(() -> new EntityNotFoundException("Área não encontrada: " + command.areaId()));
        RuleSettingsSnapshot rules = settingsService.current();
        ZoneId zone = ZoneId.of(rules.timezone());
        ZonedDateTime now = ZonedDateTime.now(clock.withZone(zone));

        ReservationPolicy.validateForBlock(
            new ReservationPolicy.Request(command.date(), command.startTime(), command.endTime(), 0),
            rules.slotMinutes(), area);

        Instant startAt = ZonedDateTime.of(command.date(), command.startTime(), zone).toInstant();
        Instant endAt = ZonedDateTime.of(command.date(), command.endTime(), zone).toInstant();
        String code = codeGenerator.next(now.getYear());
        Instant createdAt = now.toInstant();

        Reservation block = Reservation.block(code, condominiumId, area.id(), startAt, endAt, command.reason(),
            actorId, createdAt);
        // RN-24 (sobreposição com reserva/bloqueio ativo) é a exclusion constraint do banco;
        // D-57: deadlock/serialização também viram 409 RESERVATION_OVERLAP aqui. RNF-03: lockArea
        // antes do insert.
        reservations.lockArea(area.id());
        saveAndFlushOrOverlap(block);
        events.save(new ReservationEvent(block.getId(), "CREATED", null, actorId, createdAt));
        audit.record(actorId, actorRole, "BLOCK_CREATE", "RESERVATION", block.getId(),
            Map.of("code", block.getCode(), "areaId", area.id()), command.reason());

        return toAdminView(block, zone, createdAt, rules);
    }

    /**
     * `DELETE /blocks/{id}` (S/A, RN-33/D-49): id que não é `BLOCK` -&gt; 404 (mesmo tratamento de
     * {@link #findReservationOrThrow}); já cancelado -&gt; 409 INVALID_STATUS_TRANSITION (via
     * {@link Reservation#cancel}); `cancelledBy = ADMIN` mesmo quando o síndico remove (o ator real
     * fica no evento e na auditoria).
     */
    @Transactional
    public void cancelBlock(UUID blockId, UUID actorId, String actorRole) {
        Reservation r = reservations.findById(blockId)
            .filter(x -> x.getKind() == ReservationKind.BLOCK)
            .orElseThrow(() -> new EntityNotFoundException("Bloqueio não encontrado: " + blockId));
        Instant now = Instant.now(clock);

        r.cancel("ADMIN", null, now);
        reservations.save(r);
        events.save(new ReservationEvent(r.getId(), "CANCELLED", null, actorId, now));
        audit.record(actorId, actorRole, "BLOCK_CANCEL", "RESERVATION", r.getId(), Map.of("code", r.getCode()), null);
    }

    /**
     * D-57: sob concorrência real (RNF-03), o Postgres às vezes aborta a transação que perde a
     * disputa pela exclusion constraint por deadlock (`40P01`) ou falha de serialização (`40001`)
     * em vez de simplesmente rejeitar o insert (`23P01`, `DataIntegrityViolationException`, já
     * tratada globalmente). Sem isso, essas duas viravam `500 INTERNAL_ERROR`: aqui, no ponto de
     * escrita (criar reserva, alterar reserva, criar bloqueio), viram o mesmo `409
     * RESERVATION_OVERLAP` — a transação abortada é a que perdeu o horário.
     */
    private void saveAndFlushOrOverlap(Reservation reservation) {
        try {
            reservations.saveAndFlush(reservation);
        } catch (PessimisticLockingFailureException e) {
            // 40P01 (deadlock) -> DeadlockLoserDataAccessException; 40001 (falha de serialização)
            // -> CannotSerializeTransactionException; 55P03/lock -> CannotAcquireLockException.
            // As três estendem PessimisticLockingFailureException (D-57).
            throw new BusinessException("RESERVATION_OVERLAP", HttpStatus.CONFLICT,
                "Já existe uma reserva ou bloqueio nesse horário.");
        }
    }

    private AdminReservationView toAdminView(Reservation r, ZoneId zone, Instant now, RuleSettingsSnapshot rules) {
        String unitIdentifier = r.getUnitId() == null ? null
            : unitService.identifiersByIds(List.of(r.getUnitId())).get(r.getUnitId());
        Optional<AreaBookingInfo> area = areaQueryService.findBookable(r.getAreaId());
        String areaName = area.map(AreaBookingInfo::name).orElse("");
        String whatsappPaymentUrl = area.map(a -> whatsappUrlFor(r, a, unitIdentifier == null ? "" : unitIdentifier,
            zone)).orElse(null);
        return AdminReservationView.of(r, areaName, zone, now, rules.residentCancelDeadlineHours(),
            rules.reportWindowDays(), whatsappPaymentUrl, unitIdentifier);
    }

    // RN-27/RN-36: justificativa de ação administrativa precisa de pelo menos 10 caracteres.
    private void requireJustification(String justification) {
        if (justification == null || justification.trim().length() < 10) {
            throw new BusinessException("JUSTIFICATION_REQUIRED", HttpStatus.UNPROCESSABLE_ENTITY,
                "Justificativa precisa ter pelo menos 10 caracteres.");
        }
    }

    // RN-30: mesma fórmula do `canCancel` de ReservationView, para as duas nunca divergirem.
    private void requireWithinCancelDeadline(Reservation r, Instant now, int residentCancelDeadlineHours) {
        if (!now.isBefore(r.getStartAt().minus(residentCancelDeadlineHours, ChronoUnit.HOURS))) {
            throw new BusinessException("CANCEL_DEADLINE_PASSED", HttpStatus.UNPROCESSABLE_ENTITY,
                "O prazo para cancelar essa reserva pelo aplicativo já passou. Fale com a administração.");
        }
    }

    private Reservation findReservationOrThrow(UUID reservationId) {
        return reservations.findById(reservationId)
            .orElseThrow(() -> new EntityNotFoundException("Reserva não encontrada: " + reservationId));
    }

    private static BusinessException forbiddenResource() {
        return new BusinessException("FORBIDDEN_RESOURCE", HttpStatus.FORBIDDEN,
            "Você não tem permissão para acessar essa reserva.");
    }

    // Snapshot (antes/depois) do evento UPDATED e da auditoria (RNF-07): sempre em texto (JSON do banco).
    private Map<String, Object> snapshot(Reservation r, ZoneId zone) {
        ZonedDateTime start = r.getStartAt().atZone(zone);
        ZonedDateTime end = r.getEndAt().atZone(zone);
        Map<String, Object> map = new LinkedHashMap<>();
        map.put("areaId", r.getAreaId().toString());
        map.put("date", start.toLocalDate().toString());
        map.put("startTime", start.toLocalTime().toString());
        map.put("endTime", end.toLocalTime().toString());
        return map;
    }

    /**
     * RN-31: expira em lote toda `PENDING_PAYMENT` cujo início já passou, antes
     * de qualquer consulta/ação enxergar essas reservas (`/me/reservations`,
     * `GET /reservations`, `GET /reservations/{id}`, `/payments/pending`,
     * disponibilidade, confirmar pagamento) — e também pelo job agendado
     * (F5-2, {@code ReservationExpirationJob}, `reservation.infra`).
     */
    @Transactional
    public void expirePendingIfNeeded(Instant now) {
        List<UUID> expiredIds = reservations.expireOverduePending(now, EXPIRED_REASON);
        if (expiredIds.isEmpty()) {
            return;
        }
        for (UUID id : expiredIds) {
            // RN-31: sem ator humano (evento automático) -> actor = null (docs/03).
            events.save(new ReservationEvent(id, "EXPIRED", null, null, now));
        }
        // O UPDATE acima é SQL puro (fora do Hibernate): uma Reservation dessas ids que já estivesse
        // gerenciada na sessão corrente (ex.: carregada por uma chamada anterior na mesma transação)
        // ficaria com o status antigo em memória. `flush()` primeiro grava os eventos EXPIRED (senão
        // `clear()` os descartaria, ainda só `persist()`ados); `clear()` então descarta o cache de
        // 1º nível para a consulta seguinte, na mesma transação, ler a linha já expirada do banco.
        entityManager.flush();
        entityManager.clear();
    }

    // RN-18..26/RN-30..32: trio "settings atuais + fuso + agora" repetido em quase toda leitura/ação
    // (sugestão do revisor na F4-6); um único ponto para nunca deixar as três coisas dessincronizarem.
    // Sem efeito colateral (RN-31 é opt-in, ver expirePendingIfNeeded): quem já carregou uma
    // Reservation antes de chamar isto (ex.: updateByAdmin) não corre o risco de ficar com uma
    // cópia em memória defasada por um UPDATE em lote feito por baixo.
    private Context currentContext() {
        RuleSettingsSnapshot rules = settingsService.current();
        ZoneId zone = ZoneId.of(rules.timezone());
        Instant now = Instant.now(clock);
        return new Context(rules, zone, now);
    }

    private record Context(RuleSettingsSnapshot rules, ZoneId zone, Instant now) {
    }

    private ReservationView toDto(Reservation r, ZoneId zone, Instant now, RuleSettingsSnapshot rules,
        String unitIdentifier, Map<UUID, AreaBookingInfo> areasById) {
        AreaBookingInfo area = areasById.get(r.getAreaId());
        String areaName = area == null ? "" : area.name();
        String whatsappPaymentUrl = area == null ? null : whatsappUrlFor(r, area, unitIdentifier, zone);
        return ReservationView.of(r, areaName, zone, now, rules.residentCancelDeadlineHours(),
            rules.reportWindowDays(), whatsappPaymentUrl);
    }

    // RN-26: só em PENDING_PAYMENT.
    private String whatsappUrlFor(Reservation r, AreaBookingInfo area, String unitIdentifier, ZoneId zone) {
        if (r.getStatus() != ReservationStatus.PENDING_PAYMENT) {
            return null;
        }
        ZonedDateTime start = r.getStartAt().atZone(zone);
        ZonedDateTime end = r.getEndAt().atZone(zone);
        return WhatsAppPaymentLink.build(area.paymentWhatsapp(), r.getResidentNameSnapshot(), unitIdentifier,
            area.name(), start.toLocalDate(), start.toLocalTime(), end.toLocalTime(), r.getCode(),
            r.getPriceSnapshot());
    }
}
