package br.com.reservas.reservation.infra;

import br.com.reservas.reservation.domain.Reservation;
import br.com.reservas.reservation.domain.ReservationKind;
import br.com.reservas.reservation.domain.ReservationStatus;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

// JpaSpecificationExecutor (D-53): GET /reservations tem 6 filtros opcionais combinaveis; um
// Specification por filtro so adiciona predicado quando o valor vem preenchido (sem os "?" nulos
// do padrao "campo is null or campo = ?", que o driver do Postgres nao tipa bem para timestamptz).
public interface ReservationRepository extends JpaRepository<Reservation, UUID>,
    JpaSpecificationExecutor<Reservation> {

    // RN-22: reservas futuras ativas da unidade, para o limite por unidade.
    long countByUnitIdAndStatusInAndStartAtGreaterThanEqual(UUID unitId, List<ReservationStatus> statuses,
        Instant now);

    // FutureReservationsPort (P-11/RN-10).
    List<Reservation> findByUnitIdAndStatusInAndStartAtGreaterThanEqualOrderByStartAtAsc(UUID unitId,
        List<ReservationStatus> statuses, Instant now);

    // AreaReservationsGateway (RN-16/D-44).
    List<Reservation> findByAreaIdAndStatusInAndStartAtGreaterThanEqualOrderByStartAtAsc(UUID areaId,
        List<ReservationStatus> statuses, Instant now);

    // GET /me/reservations?scope=upcoming (inclui canceladas, docs/03).
    Page<Reservation> findByUnitIdAndStartAtGreaterThanEqualOrderByStartAtAsc(UUID unitId, Instant now,
        Pageable pageable);

    // GET /me/reservations?scope=past.
    Page<Reservation> findByUnitIdAndStartAtLessThanOrderByStartAtDesc(UUID unitId, Instant now, Pageable pageable);

    // GET /areas/{id}/availability: reservas/bloqueios ativos que cruzam o intervalo [from, to).
    @Query("select r from Reservation r where r.areaId = :areaId and r.status in :statuses "
        + "and r.startAt < :to and r.endAt > :from order by r.startAt asc")
    List<Reservation> findActiveInRange(@Param("areaId") UUID areaId, @Param("statuses") List<ReservationStatus> statuses,
        @Param("from") Instant from, @Param("to") Instant to);

    /**
     * RNF-03/D-57: lock consultivo por área, liberado sozinho no commit/rollback da transação
     * (`pg_advisory_xact_lock`). Diagnóstico (RNF-03): sob 50 requisições concorrentes na mesma
     * área/horário, com 10+ transações tentando inserir ranges sobrepostos ao mesmo tempo, o
     * índice GiST da exclusion constraint (RN-24) pode formar um ciclo real de deadlock entre
     * 3+ transações (não só espera par-a-par) — o Postgres só resolve um ciclo por vez, a cada
     * `deadlock_timeout` (~1s), então esvaziar as ~40 requisições em espera no pool Hikari passa
     * de 30s e estoura `HikariPool - Connection is not available` (medido: um `deadlock detected`
     * por segundo até a fila drenar). Este lock, tomado antes do insert/flush que dispara a
     * exclusion constraint, serializa as transações da mesma área numa fila simples de espera
     * (fila de lock, sem custo de I/O) e evita o ciclo se formar; quem chega vê a linha já
     * commitada e recebe 409 imediatamente, sem nunca deadlockar.
     */
    @Query(value = "select pg_advisory_xact_lock(hashtext(cast(:areaId as text)))", nativeQuery = true)
    void lockArea(@Param("areaId") UUID areaId);

    // GET /payments/pending (ADMIN, RF-PAG-02): só reserva de morador, nunca bloqueio.
    List<Reservation> findByStatusAndKindOrderByStartAtAsc(ReservationStatus status, ReservationKind kind);

    // GET /dashboard/home (F7-1, RF-SIN-01): reservas/bloqueios ativos com inicio em [from, to).
    List<Reservation> findByStatusInAndStartAtGreaterThanEqualAndStartAtLessThanOrderByStartAtAsc(
        List<ReservationStatus> statuses, Instant from, Instant to);

    /**
     * RN-31: expira em lote, num único `UPDATE ... RETURNING id`, toda pendente
     * (`PENDING_PAYMENT`) cujo início já passou. Sem `@Modifying`: é assim que o
     * Postgres devolve as linhas afetadas (a query já não é mais um `SELECT`,
     * mas Hibernate lê o `ResultSet` do `RETURNING` normalmente). Idempotente e
     * seguro sob concorrência: a segunda chamada simultânea só enxerga a linha
     * já `CANCELLED` pela primeira depois de esperar o lock de linha da UPDATE
     * (Postgres reavalia o `WHERE` já com a linha bloqueada), então não
     * devolve o id de novo — sem evento `EXPIRED` duplicado.
     */
    @Query(value = "update reservation set status = 'CANCELLED', cancelled_by = 'SYSTEM', cancelled_at = :now, "
        + "status_reason = :reason where status = 'PENDING_PAYMENT' and start_at <= :now returning id",
        nativeQuery = true)
    List<UUID> expireOverduePending(@Param("now") Instant now, @Param("reason") String reason);
}
