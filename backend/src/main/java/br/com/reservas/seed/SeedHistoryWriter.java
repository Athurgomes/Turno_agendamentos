package br.com.reservas.seed;

import java.math.BigDecimal;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.UUID;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

/**
 * Caminho direto (D-59) só para o dado histórico do seed `demo`: uma reserva
 * ou um report cuja data já é passada não pode nascer pelos serviços públicos
 * de {@code reservation}/{@code report} porque (a) {@code ReservationPolicy}
 * recusa qualquer data anterior a "hoje" (RN-20) e (b) esses serviços gravam
 * {@code createdAt = Instant.now(clock)}, sempre "agora" — não dá para pedir
 * uma reserva ou um report "de 10 dias atrás" por eles.
 *
 * <p>Escopo deliberadamente mínimo: só os inserts SQL dessas duas tabelas
 * (mais o evento `CREATED` e o comentário do report), sem nenhuma regra de
 * negócio — a validação de quem chama ({@link DemoSeedRunner}) é o que
 * garante que os dados aqui gravados são consistentes (datas coerentes,
 * status finais corretos etc.). Não é usado por nenhum outro seed nem por
 * código de produção; existe só para não reescrever regras de RN-18..26/
 * RN-34..37 num "modo sem validação" dentro dos módulos `reservation`/
 * `report` (CLAUDE.md §5: um módulo não acessa o repositório de outro — aqui
 * quem grava é o próprio pacote `seed`, direto no schema, não um repositório
 * De outro módulo).
 */
@Component
class SeedHistoryWriter {

    private final JdbcTemplate jdbcTemplate;

    SeedHistoryWriter(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    /** Reserva histórica (`kind = BOOKING`) já `CONFIRMED`; devolve o id gerado. */
    UUID insertConfirmedReservation(UUID condominiumId, UUID areaId, UUID unitId, UUID residentId,
        String residentName, String residentPhone, Instant startAt, Instant endAt, int guests,
        boolean requiresPayment, BigDecimal price, UUID createdBy, Instant createdAt) {
        return insertReservation(condominiumId, areaId, unitId, residentId, residentName, residentPhone, startAt,
            endAt, guests, requiresPayment, price, "CONFIRMED", null, null, requiresPayment ? createdAt : null,
            createdBy, createdAt, null);
    }

    /**
     * Reserva histórica (`kind = BOOKING`) já com o status final desejado (FD-2 completo: mistura de
     * `CONFIRMED`/`CANCELLED` do histórico de ~6 meses); devolve o id gerado. {@code cancelledBy}/
     * {@code cancelledAt} nulos para `CONFIRMED`; {@code paymentConfirmedAt} só para `CONFIRMED` em
     * área paga.
     */
    UUID insertReservation(UUID condominiumId, UUID areaId, UUID unitId, UUID residentId, String residentName,
        String residentPhone, Instant startAt, Instant endAt, int guests, boolean requiresPayment, BigDecimal price,
        String status, String cancelledBy, String statusReason, Instant paymentConfirmedAt, UUID createdBy,
        Instant createdAt, Instant cancelledAt) {
        UUID id = UUID.randomUUID();
        long sequence = nextReservationSequence();
        String code = "RES-%d-%06d".formatted(yearOf(startAt), sequence);
        Instant updatedAt = cancelledAt != null ? cancelledAt : createdAt;
        jdbcTemplate.update("""
            insert into reservation (id, code, condominium_id, area_id, kind, unit_id, resident_id,
                resident_name_snapshot, resident_phone_snapshot, start_at, end_at, guests, status, status_reason,
                requires_payment_snapshot, price_snapshot, payment_confirmed_at, payment_confirmed_by,
                cancelled_by, cancelled_at, created_by, created_at, updated_at)
            values (?, ?, ?, ?, 'BOOKING', ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
            """, id, code, condominiumId, areaId, unitId, residentId, residentName, residentPhone, ts(startAt),
            ts(endAt), guests, status, statusReason, requiresPayment, price, ts(paymentConfirmedAt),
            paymentConfirmedAt != null ? createdBy : null, cancelledBy, ts(cancelledAt), createdBy, ts(createdAt),
            ts(updatedAt));
        insertReservationEvent(id, "CREATED", createdBy, createdAt);
        if (cancelledBy != null) {
            insertReservationEvent(id, "CANCELLED", createdBy, cancelledAt);
        }
        return id;
    }

    private void insertReservationEvent(UUID reservationId, String type, UUID actorId, Instant occurredAt) {
        jdbcTemplate.update(
            "insert into reservation_event (id, reservation_id, type, actor_id, occurred_at) values (?, ?, ?, ?, ?)",
            UUID.randomUUID(), reservationId, type, actorId, ts(occurredAt));
    }

    /**
     * Report histórico; devolve o id gerado. {@code resolvedAt}/{@code maintenanceCost} nulos se ainda
     * aberto ou descartado; {@code statusReason} obrigatório para `DISMISSED` (RN-36).
     */
    UUID insertReport(UUID condominiumId, UUID reservationId, UUID areaId, UUID unitId, UUID residentId,
        String residentName, String category, String description, String status, String statusReason,
        BigDecimal maintenanceCost, Instant resolvedAt, Instant createdAt) {
        UUID id = UUID.randomUUID();
        long sequence = nextReportSequence();
        String code = "OCR-%d-%06d".formatted(yearOf(createdAt), sequence);
        jdbcTemplate.update("""
            insert into report (id, code, condominium_id, reservation_id, area_id, unit_id, resident_id,
                resident_name_snapshot, category, description, status, status_reason, maintenance_cost, resolved_at,
                created_at, updated_at)
            values (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
            """, id, code, condominiumId, reservationId, areaId, unitId, residentId, residentName, category,
            description, status, statusReason, maintenanceCost, ts(resolvedAt), ts(createdAt),
            ts(resolvedAt != null ? resolvedAt : createdAt));
        return id;
    }

    void insertReportComment(UUID reportId, UUID authorId, String text, boolean visibleToResident, Instant createdAt) {
        jdbcTemplate.update(
            "insert into report_comment (id, report_id, author_id, text, visible_to_resident, created_at) "
                + "values (?, ?, ?, ?, ?, ?)",
            UUID.randomUUID(), reportId, authorId, text, visibleToResident, ts(createdAt));
    }

    private static Timestamp ts(Instant instant) {
        return instant == null ? null : Timestamp.from(instant);
    }

    private long nextReservationSequence() {
        return jdbcTemplate.queryForObject("select nextval('reservation_code_seq')", Long.class);
    }

    private long nextReportSequence() {
        return jdbcTemplate.queryForObject("select nextval('report_code_seq')", Long.class);
    }

    private static int yearOf(Instant instant) {
        return instant.atZone(java.time.ZoneOffset.UTC).getYear();
    }
}
