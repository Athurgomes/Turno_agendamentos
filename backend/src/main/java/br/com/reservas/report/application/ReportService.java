package br.com.reservas.report.application;

import br.com.reservas.area.application.AreaBookingInfo;
import br.com.reservas.area.application.AreaQueryService;
import br.com.reservas.auth.application.AccountService;
import br.com.reservas.reservation.application.ReservationService;
import br.com.reservas.reservation.application.ReservationView;
import br.com.reservas.report.domain.Report;
import br.com.reservas.report.domain.ReportComment;
import br.com.reservas.report.domain.ReportPhoto;
import br.com.reservas.report.domain.ReportPhotoStage;
import br.com.reservas.report.domain.ReportStatus;
import br.com.reservas.report.infra.ReportCodeGenerator;
import br.com.reservas.report.infra.ReportCommentRepository;
import br.com.reservas.report.infra.ReportPhotoRepository;
import br.com.reservas.report.infra.ReportRepository;
import br.com.reservas.report.infra.ReportSpecifications;
import br.com.reservas.settings.application.CondominiumSettingsService;
import br.com.reservas.settings.application.RuleSettingsSnapshot;
import br.com.reservas.shared.audit.AuditService;
import br.com.reservas.shared.error.BusinessException;
import br.com.reservas.shared.reservation.ReportWindowPolicy;
import br.com.reservas.shared.reservation.ReservationSummary;
import br.com.reservas.shared.storage.FileStorage;
import br.com.reservas.shared.storage.ImageFileValidator;
import br.com.reservas.unit.application.UnitService;
import br.com.reservas.unit.domain.Resident;
import br.com.reservas.unit.domain.Unit;
import jakarta.persistence.EntityNotFoundException;
import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Interface pública do módulo `report` (RF-REP-01..05, RN-01, RN-17, RN-34..37,
 * D-50, D-55). A janela RN-34 usa {@link ReportWindowPolicy} (`shared`), a
 * mesma regra que {@code reservation.application.ReservationView} usa para
 * `canReport` — as duas nunca divergem. Acesso à reserva é sempre via
 * {@link ReservationService#findMine} (403 FORBIDDEN_RESOURCE embutido, sem
 * este módulo acessar o repositório de `reservation`, CLAUDE.md §5).
 */
@Service
public class ReportService {

    private static final List<ReportStatus> OPEN_STATUSES =
        List.of(ReportStatus.OPEN, ReportStatus.IN_REVIEW, ReportStatus.IN_MAINTENANCE);

    private final ReportRepository reports;
    private final ReportPhotoRepository reportPhotos;
    private final ReportCommentRepository reportComments;
    private final ReportCodeGenerator codeGenerator;
    private final ReservationService reservationService;
    private final AreaQueryService areaQueryService;
    private final UnitService unitService;
    private final CondominiumSettingsService settingsService;
    private final AccountService accountService;
    private final FileStorage storage;
    private final AuditService audit;
    private final Clock clock;

    public ReportService(ReportRepository reports, ReportPhotoRepository reportPhotos,
        ReportCommentRepository reportComments, ReportCodeGenerator codeGenerator,
        ReservationService reservationService, AreaQueryService areaQueryService, UnitService unitService,
        CondominiumSettingsService settingsService, AccountService accountService, FileStorage storage,
        AuditService audit, Clock clock) {
        this.reports = reports;
        this.reportPhotos = reportPhotos;
        this.reportComments = reportComments;
        this.codeGenerator = codeGenerator;
        this.reservationService = reservationService;
        this.areaQueryService = areaQueryService;
        this.unitService = unitService;
        this.settingsService = settingsService;
        this.accountService = accountService;
        this.storage = storage;
        this.audit = audit;
        this.clock = clock;
    }

    /**
     * `POST /me/reservations/{id}/reports` (UNIT, RF-REP-01): reserva de outra
     * unidade -&gt; 403 (via {@link ReservationService#findMine}); não
     * `CONFIRMED`/`BOOKING` -&gt; 422 REPORT_RESERVATION_NOT_CONFIRMED; fora da
     * janela (RN-34) -&gt; 422 REPORT_WINDOW_CLOSED; descrição &lt; 10 ou
     * `residentId` não é morador ativo da própria unidade -&gt; 422
     * VALIDATION_ERROR; mais de 5 fotos -&gt; 422 INVALID_FILE.
     */
    @Transactional
    public ReportView create(UUID unitId, UUID actorId, UUID reservationId, CreateReportCommand command,
        List<UploadedPhoto> photos) {
        ReservationView reservation = reservationService.findMine(unitId, reservationId);
        if (!"BOOKING".equals(reservation.kind()) || !"CONFIRMED".equals(reservation.status())) {
            throw new BusinessException("REPORT_RESERVATION_NOT_CONFIRMED", HttpStatus.UNPROCESSABLE_ENTITY,
                "Só é possível abrir um report sobre uma reserva confirmada.");
        }
        RuleSettingsSnapshot rules = settingsService.current();
        ZoneId zone = ZoneId.of(rules.timezone());
        Instant now = Instant.now(clock);
        LocalDate today = now.atZone(zone).toLocalDate();
        if (!ReportWindowPolicy.isOpen(reservation.date(), today, rules.reportWindowDays())) {
            throw new BusinessException("REPORT_WINDOW_CLOSED", HttpStatus.UNPROCESSABLE_ENTITY,
                "O prazo para reportar essa reserva já passou.");
        }
        requireValidDescription(command.description());
        Resident resident = unitService.findActiveResident(unitId, command.residentId())
            .orElseThrow(() -> new BusinessException("VALIDATION_ERROR", HttpStatus.UNPROCESSABLE_ENTITY,
                "O morador (residentId) precisa ser um morador ativo da sua unidade."));
        if (photos.size() > 5) {
            throw new BusinessException("INVALID_FILE", HttpStatus.UNPROCESSABLE_ENTITY,
                "Envie no máximo 5 fotos.");
        }
        Unit unit = unitService.findActiveUnit(unitId)
            .orElseThrow(() -> new IllegalStateException("Unidade do token não encontrada: " + unitId));

        String code = codeGenerator.next(now.atZone(zone).getYear());
        Report report = new Report(code, unit.getCondominiumId(), reservationId, reservation.areaId(), unitId,
            resident.getId(), resident.getName(), command.category(), command.description(), now);
        reports.save(report);
        photos.forEach(file -> savePhoto(report.getId(), actorId, file, ReportPhotoStage.REPORTED, now));

        return toView(report, false);
    }

    /** `GET /me/reports` (UNIT): por criação decrescente, comentários só `visibleToResident`. */
    @Transactional(readOnly = true)
    public List<ReportView> listMine(UUID unitId) {
        return toViews(reports.findByUnitIdOrderByCreatedAtDesc(unitId), false);
    }

    /** `GET /reports/{id}` (UNIT, própria): 403 FORBIDDEN_RESOURCE se não for da unidade. */
    @Transactional(readOnly = true)
    public ReportView findMine(UUID unitId, UUID reportId) {
        Report r = reports.findByIdAndUnitId(reportId, unitId).orElseThrow(ReportService::forbiddenResource);
        return toView(r, false);
    }

    /** `GET /reports/{id}` (S/A): todos os comentários. */
    @Transactional(readOnly = true)
    public AdminReportView findForAdmin(UUID reportId) {
        return toAdminView(findOrThrow(reportId));
    }

    /** `GET /reports` (S/A): filtros opcionais, por criação decrescente. */
    @Transactional(readOnly = true)
    public Page<AdminReportView> search(ReportFilter filter, Pageable pageable) {
        ZoneId zone = ZoneId.of(settingsService.current().timezone());
        Page<Report> page = reports.findAll(ReportSpecifications.of(filter, zone), pageable);
        List<AdminReportView> views = toAdminViews(page.getContent());
        return new org.springframework.data.domain.PageImpl<>(views, pageable, page.getTotalElements());
    }

    /**
     * `PATCH /reports/{id}/status` (S/A, RN-36/RN-37): `maintenanceCost`
     * (RN-37) validado antes de tocar a entidade; a transição em si (RN-36) é
     * responsabilidade de {@link Report#changeStatus}.
     */
    @Transactional
    public AdminReportView changeStatus(UUID reportId, UUID actorId, String actorRole, ChangeReportStatusCommand command) {
        requireValidMaintenanceCost(command.status(), command.maintenanceCost());
        Report r = findOrThrow(reportId);
        Instant now = Instant.now(clock);
        r.changeStatus(command.status(), command.justification(), now);
        if (command.maintenanceCost() != null) {
            r.setMaintenanceCost(command.maintenanceCost());
        }
        reports.save(r);
        audit.record(actorId, actorRole, "REPORT_STATUS_CHANGE", "REPORT", r.getId(),
            Map.of("status", command.status().name()), command.justification());
        return toAdminView(r);
    }

    /** `POST /reports/{id}/comments` (S/A, D-55): texto (1..2000) validado no DTO de entrada. */
    @Transactional
    public AdminReportView addComment(UUID reportId, UUID actorId, String actorRole, AddCommentCommand command) {
        Report r = findOrThrow(reportId);
        Instant now = Instant.now(clock);
        reportComments.save(new ReportComment(r.getId(), actorId, command.text(), command.visibleToResident(), now));
        r.touch(now);
        reports.save(r);
        audit.record(actorId, actorRole, "REPORT_COMMENT_ADD", "REPORT", r.getId(),
            Map.of("visibleToResident", command.visibleToResident()), null);
        return toAdminView(r);
    }

    /** `POST /reports/{id}/photos` (S/A, D-55): fotos do reparo, `stage = REPAIR`. */
    @Transactional
    public AdminReportView addPhotos(UUID reportId, UUID actorId, String actorRole, List<UploadedPhoto> photos) {
        Report r = findOrThrow(reportId);
        if (photos.isEmpty() || photos.size() > 5) {
            throw new BusinessException("INVALID_FILE", HttpStatus.UNPROCESSABLE_ENTITY,
                "Envie entre 1 e 5 fotos.");
        }
        Instant now = Instant.now(clock);
        photos.forEach(file -> savePhoto(r.getId(), actorId, file, ReportPhotoStage.REPAIR, now));
        r.touch(now);
        reports.save(r);
        audit.record(actorId, actorRole, "REPORT_PHOTO_ADD", "REPORT", r.getId(), Map.of("count", photos.size()),
            null);
        return toAdminView(r);
    }

    /** `GET /reports/summary` (S/A, D-50): reports em status não final. */
    @Transactional(readOnly = true)
    public long openCount() {
        return reports.countByStatusIn(OPEN_STATUSES);
    }

    /** `GET /dashboard/home` (F7-1, RF-SIN-01): os `limit` reports abertos mais antigos. */
    @Transactional(readOnly = true)
    public List<AdminReportView> oldestOpen(int limit) {
        List<Report> found = reports.findByStatusInOrderByCreatedAtAsc(OPEN_STATUSES, PageRequest.of(0, limit));
        return toAdminViews(found);
    }

    private void savePhoto(UUID reportId, UUID actorId, UploadedPhoto file, ReportPhotoStage stage, Instant now) {
        ImageFileValidator.Validated validated = ImageFileValidator.validate(file.content());
        // RN-17: chave nunca reutilizada, nunca baseada em nome enviado pelo cliente.
        String key = "reports/" + reportId + "/" + UUID.randomUUID() + "." + validated.extension();
        storage.put(key, file.content(), validated.contentType());
        reportPhotos.save(new ReportPhoto(reportId, key, validated.contentType(), stage, actorId, now));
    }

    // RN-35 (descrição da ocorrência).
    private void requireValidDescription(String description) {
        if (description == null || description.trim().length() < 10) {
            throw new BusinessException("VALIDATION_ERROR", HttpStatus.UNPROCESSABLE_ENTITY,
                "A descrição precisa ter pelo menos 10 caracteres.");
        }
    }

    // RN-37: custo de manutenção só junto com RESOLVED (não negativo já é 400 via @DecimalMin no DTO de entrada).
    private void requireValidMaintenanceCost(ReportStatus target, BigDecimal maintenanceCost) {
        if (maintenanceCost != null && target != ReportStatus.RESOLVED) {
            throw new BusinessException("VALIDATION_ERROR", HttpStatus.UNPROCESSABLE_ENTITY,
                "O custo de manutenção só pode ser informado ao resolver o report.");
        }
    }

    private Report findOrThrow(UUID reportId) {
        return reports.findById(reportId)
            .orElseThrow(() -> new EntityNotFoundException("Report não encontrado: " + reportId));
    }

    private static BusinessException forbiddenResource() {
        return new BusinessException("FORBIDDEN_RESOURCE", HttpStatus.FORBIDDEN,
            "Você não tem permissão para acessar esse report.");
    }

    private ReportView toView(Report r, boolean includeAllComments) {
        return toViews(List.of(r), includeAllComments).get(0);
    }

    private AdminReportView toAdminView(Report r) {
        return toAdminViews(List.of(r)).get(0);
    }

    // Evita N+1 em listagens (RF-REP-04/05): resolve reserva, área, fotos e comentários da página inteira de uma vez.
    private List<ReportView> toViews(List<Report> found, boolean includeAllComments) {
        if (found.isEmpty()) {
            return List.of();
        }
        Map<UUID, ReservationSummary> reservationSummaries = reservationService.summariesByIds(
            found.stream().map(Report::getReservationId).distinct().toList());
        Map<UUID, AreaBookingInfo> areasById = areaQueryService.findBookableByIds(
            found.stream().map(Report::getAreaId).distinct().toList());
        List<UUID> reportIds = found.stream().map(Report::getId).toList();
        Map<UUID, List<ReportPhoto>> photosByReport = reportPhotos.groupByReportId(reportIds);
        Map<UUID, List<ReportComment>> commentsByReport = reportComments.groupByReportId(reportIds);

        List<ReportView> views = new ArrayList<>(found.size());
        for (Report r : found) {
            ReservationSummary reservation = reservationSummaries.get(r.getReservationId());
            AreaBookingInfo area = areasById.get(r.getAreaId());
            views.add(buildView(r, reservation, area == null ? "" : area.name(),
                photosByReport.getOrDefault(r.getId(), List.of()), commentsByReport.getOrDefault(r.getId(),
                    List.of()), includeAllComments));
        }
        return views;
    }

    private List<AdminReportView> toAdminViews(List<Report> found) {
        if (found.isEmpty()) {
            return List.of();
        }
        List<ReportView> baseViews = toViews(found, true);
        Map<UUID, String> unitIdentifiers = unitService.identifiersByIds(
            found.stream().map(Report::getUnitId).distinct().toList());
        // Evita N+1 (revisão F7): busca todos os moradores da página de uma vez.
        Map<UUID, Resident> residentsById = unitService.findResidentsByIds(
            found.stream().map(Report::getResidentId).distinct().toList());

        List<AdminReportView> views = new ArrayList<>(found.size());
        for (int i = 0; i < found.size(); i++) {
            Report r = found.get(i);
            Resident resident = residentsById.get(r.getResidentId());
            String phone = resident == null ? null : resident.getPhone();
            String whatsappContactUrl = phone == null ? null : "https://wa.me/" + phone;
            views.add(new AdminReportView(baseViews.get(i), r.getUnitId(), unitIdentifiers.get(r.getUnitId()), phone,
                whatsappContactUrl, r.getMaintenanceCost()));
        }
        return views;
    }

    private ReportView buildView(Report r, ReservationSummary reservation, String areaName, List<ReportPhoto> photos,
        List<ReportComment> comments, boolean includeAllComments) {
        List<ReportPhotoView> photoViews = photos.stream()
            .map(p -> new ReportPhotoView(p.getId(), storage.presignedGetUrl(p.getStorageKey()), p.getStage().name(),
                p.getCreatedAt()))
            .toList();
        List<ReportCommentView> commentViews = comments.stream()
            .filter(c -> includeAllComments || c.isVisibleToResident())
            .map(c -> new ReportCommentView(c.getId(), c.getText(), accountService.displayNameFor(c.getAuthorId()),
                c.getCreatedAt(), c.isVisibleToResident()))
            .toList();
        String reservationCode = reservation == null ? "" : reservation.code();
        LocalDate reservationDate = reservation == null ? null : reservation.date();
        return new ReportView(r.getId(), r.getCode(), r.getReservationId(), reservationCode, r.getAreaId(), areaName,
            reservationDate, r.getCategory().name(), r.getDescription(), r.getResidentNameSnapshot(),
            r.getStatus().name(), r.getStatusReason(), r.getCreatedAt(), r.getResolvedAt(), photoViews,
            commentViews);
    }
}
