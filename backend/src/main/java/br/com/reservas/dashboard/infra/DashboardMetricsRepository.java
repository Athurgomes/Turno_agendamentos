package br.com.reservas.dashboard.infra;

import br.com.reservas.dashboard.application.AreaMetricView;
import br.com.reservas.dashboard.application.DashboardSummaryView.CategoryCount;
import br.com.reservas.dashboard.application.HeatmapCellView;
import br.com.reservas.dashboard.application.MonthlyReservationsView;
import br.com.reservas.dashboard.application.TopUnitView;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;

/**
 * Consultas agregadas do dashboard (F8-1, D-61): SQL nativo somente leitura
 * via {@link NamedParameterJdbcTemplate}, sem repositórios/entidades de
 * outros módulos (única exceção à regra do CLAUDE.md §5.2). Uma consulta por
 * indicador (ou poucas fixas, nunca uma por área/mês/unidade): ver D-62 para
 * a semântica de cada número.
 */
@Repository
public class DashboardMetricsRepository {

    private final NamedParameterJdbcTemplate jdbc;

    DashboardMetricsRepository(NamedParameterJdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    public long activeUnitsCount(UUID condominiumId) {
        return jdbc.queryForObject(
            "select count(*) from unit where condominium_id = :cid and active and deleted_at is null",
            params(condominiumId), Long.class);
    }

    public long activeResidentsCount(UUID condominiumId) {
        return jdbc.queryForObject("""
            select count(*) from resident r join unit u on u.id = r.unit_id
            where u.condominium_id = :cid and u.active and u.deleted_at is null and r.deleted_at is null
            """, params(condominiumId), Long.class);
    }

    public ReservationTotalsRow reservationTotals(UUID condominiumId, LocalDate from, LocalDate to, String timezone) {
        MapSqlParameterSource p = periodParams(condominiumId, from, to, timezone);
        return jdbc.queryForObject("""
            select
              count(*) as total,
              count(*) filter (where status = 'PENDING_PAYMENT') as pending_payment,
              count(*) filter (where status = 'CONFIRMED') as confirmed,
              count(*) filter (where status = 'CANCELLED') as cancelled,
              count(*) filter (where status = 'CANCELLED' and cancelled_by = 'RESIDENT') as cancelled_by_resident,
              count(*) filter (where status = 'CANCELLED' and cancelled_by = 'ADMIN') as cancelled_by_admin,
              count(*) filter (where status = 'CANCELLED' and cancelled_by = 'SYSTEM') as cancelled_by_system,
              coalesce(sum(price_snapshot) filter (where status = 'CONFIRMED' and requires_payment_snapshot),
                0) as amount_confirmed,
              coalesce(sum(price_snapshot) filter (where status = 'PENDING_PAYMENT'), 0) as amount_pending
            from reservation
            where condominium_id = :cid and kind = 'BOOKING'
              and ((start_at at time zone :tz)::date) between :from and :to
            """, p, (rs, i) -> new ReservationTotalsRow(rs.getLong("total"), rs.getLong("pending_payment"),
            rs.getLong("confirmed"), rs.getLong("cancelled"), rs.getLong("cancelled_by_resident"),
            rs.getLong("cancelled_by_admin"), rs.getLong("cancelled_by_system"),
            rs.getBigDecimal("amount_confirmed"), rs.getBigDecimal("amount_pending")));
    }

    public ReportTotalsRow reportTotals(UUID condominiumId, LocalDate from, LocalDate to, String timezone) {
        MapSqlParameterSource p = periodParams(condominiumId, from, to, timezone);
        return jdbc.queryForObject("""
            select
              count(*) filter (where ((created_at at time zone :tz)::date) between :from and :to) as opened,
              count(*) filter (where ((resolved_at at time zone :tz)::date) between :from and :to) as resolved,
              count(*) filter (where status not in ('RESOLVED', 'DISMISSED')) as open,
              round(avg(extract(epoch from (resolved_at - created_at))::numeric / 3600)
                filter (where ((resolved_at at time zone :tz)::date) between :from and :to), 1) as avg_resolution_hours,
              coalesce(sum(maintenance_cost)
                filter (where ((resolved_at at time zone :tz)::date) between :from and :to), 0) as maintenance_cost
            from report
            where condominium_id = :cid and deleted_at is null
            """, p, (rs, i) -> new ReportTotalsRow(rs.getLong("opened"), rs.getLong("resolved"), rs.getLong("open"),
            (BigDecimal) rs.getObject("avg_resolution_hours"), rs.getBigDecimal("maintenance_cost")));
    }

    public List<CategoryCount> reportCountsByCategory(UUID condominiumId, LocalDate from, LocalDate to, String timezone) {
        MapSqlParameterSource p = periodParams(condominiumId, from, to, timezone);
        return jdbc.query("""
            select category, count(*) as count
            from report
            where condominium_id = :cid and deleted_at is null
              and ((created_at at time zone :tz)::date) between :from and :to
            group by category
            having count(*) > 0
            order by category
            """, p, (rs, i) -> new CategoryCount(rs.getString("category"), rs.getLong("count")));
    }

    public List<MonthlyReservationsView> reservationsByMonth(UUID condominiumId, LocalDate rangeStart, LocalDate rangeEnd,
        String timezone) {
        MapSqlParameterSource p = periodParams(condominiumId, rangeStart, rangeEnd, timezone);
        List<MonthlyReservationsView> rows = jdbc.query("""
            select to_char((start_at at time zone :tz), 'YYYY-MM') as month,
              count(*) as total,
              count(*) filter (where status = 'CONFIRMED') as confirmed,
              count(*) filter (where status = 'PENDING_PAYMENT') as pending_payment,
              count(*) filter (where status = 'CANCELLED') as cancelled
            from reservation
            where condominium_id = :cid and kind = 'BOOKING'
              and ((start_at at time zone :tz)::date) between :from and :to
            group by month
            """, p, (rs, i) -> new MonthlyReservationsView(rs.getString("month"), rs.getLong("total"),
            rs.getLong("confirmed"), rs.getLong("pending_payment"), rs.getLong("cancelled")));
        return rows;
    }

    public List<AreaMetricView> areaMetrics(UUID condominiumId, LocalDate from, LocalDate to, String timezone) {
        MapSqlParameterSource p = periodParams(condominiumId, from, to, timezone);

        record ReservationAgg(long reservations, BigDecimal reservedHours) {
        }
        record ReportAgg(long reports, BigDecimal maintenanceCost) {
        }
        record AreaRow(UUID areaId, String name, String category, String status, BigDecimal availableHours) {
        }

        Map<UUID, ReservationAgg> reservationsByArea = jdbc.query("""
            select area_id,
              count(*) as reservations,
              coalesce(round(sum(extract(epoch from (end_at - start_at))::numeric / 3600), 4), 0) as reserved_hours
            from reservation
            where condominium_id = :cid and kind = 'BOOKING' and status <> 'CANCELLED'
              and ((start_at at time zone :tz)::date) between :from and :to
            group by area_id
            """, p, (rs, i) -> Map.entry((UUID) rs.getObject("area_id"),
                new ReservationAgg(rs.getLong("reservations"), rs.getBigDecimal("reserved_hours"))))
            .stream().collect(java.util.stream.Collectors.toMap(Map.Entry::getKey, Map.Entry::getValue));

        Map<UUID, ReportAgg> reportsByArea = jdbc.query("""
            select area_id,
              count(*) filter (where ((created_at at time zone :tz)::date) between :from and :to) as reports,
              coalesce(sum(maintenance_cost)
                filter (where ((resolved_at at time zone :tz)::date) between :from and :to), 0) as maintenance_cost
            from report
            where condominium_id = :cid and deleted_at is null
            group by area_id
            """, p, (rs, i) -> Map.entry((UUID) rs.getObject("area_id"),
                new ReportAgg(rs.getLong("reports"), rs.getBigDecimal("maintenance_cost"))))
            .stream().collect(java.util.stream.Collectors.toMap(Map.Entry::getKey, Map.Entry::getValue));

        List<AreaRow> areas = jdbc.query("""
            select a.id as area_id, a.name, a.category, a.status,
              coalesce(oh.available_hours, 0) as available_hours
            from common_area a
            left join (
              select o.area_id,
                round(sum(extract(epoch from (o.close_time - o.open_time))::numeric / 3600), 4) as available_hours
              from area_opening_hours o
              join generate_series(:from::date, :to::date, interval '1 day') as d(day)
                on extract(isodow from d.day) = o.day_of_week
              group by o.area_id
            ) oh on oh.area_id = a.id
            where a.condominium_id = :cid and a.deleted_at is null
            order by a.name
            """, p, (rs, i) -> new AreaRow((UUID) rs.getObject("area_id"), rs.getString("name"),
            rs.getString("category"), rs.getString("status"), rs.getBigDecimal("available_hours")));

        return areas.stream()
            .map(area -> {
                ReservationAgg res = reservationsByArea.getOrDefault(area.areaId(),
                    new ReservationAgg(0, BigDecimal.ZERO));
                ReportAgg rep = reportsByArea.getOrDefault(area.areaId(), new ReportAgg(0, BigDecimal.ZERO));
                BigDecimal occupancy = occupancyRate(res.reservedHours(), area.availableHours());
                return new AreaMetricView(area.areaId(), area.name(), area.category(), area.status(),
                    res.reservations(), res.reservedHours(), area.availableHours(), occupancy, rep.reports(),
                    rep.maintenanceCost());
            })
            .toList();
    }

    public List<HeatmapCellView> demandHeatmap(UUID condominiumId, LocalDate from, LocalDate to, String timezone) {
        MapSqlParameterSource p = periodParams(condominiumId, from, to, timezone);
        return jdbc.query("""
            select extract(isodow from (start_at at time zone :tz))::int as day_of_week,
              extract(hour from (start_at at time zone :tz))::int as hour,
              count(*) as count
            from reservation
            where condominium_id = :cid and kind = 'BOOKING'
              and ((start_at at time zone :tz)::date) between :from and :to
            group by 1, 2
            having count(*) > 0
            order by 1, 2
            """, p, (rs, i) -> new HeatmapCellView(rs.getInt("day_of_week"), rs.getInt("hour"),
            rs.getLong("count")));
    }

    public List<TopUnitView> topUnits(UUID condominiumId, LocalDate from, LocalDate to, String timezone) {
        MapSqlParameterSource p = periodParams(condominiumId, from, to, timezone);
        return jdbc.query("""
            select u.id as unit_id, u.identifier as unit_identifier,
              count(*) as reservations,
              coalesce(round(sum(extract(epoch from (r.end_at - r.start_at))::numeric / 3600), 4), 0)
                as reserved_hours
            from reservation r
            join unit u on u.id = r.unit_id
            where r.condominium_id = :cid and r.kind = 'BOOKING' and r.status <> 'CANCELLED'
              and ((r.start_at at time zone :tz)::date) between :from and :to
            group by u.id, u.identifier
            order by count(*) desc, u.identifier asc
            limit 10
            """, p, (rs, i) -> new TopUnitView((UUID) rs.getObject("unit_id"), rs.getString("unit_identifier"),
            rs.getLong("reservations"), rs.getBigDecimal("reserved_hours")));
    }

    /** `GET /exports/reservations` (F8-2): reservas e bloqueios do período, por início. */
    public List<ReservationExportRow> reservationExportRows(UUID condominiumId, LocalDate from, LocalDate to,
        String timezone) {
        MapSqlParameterSource p = periodParams(condominiumId, from, to, timezone);
        return jdbc.query("""
            select r.code, r.kind, a.name as area_name, u.identifier as unit_identifier,
              r.resident_name_snapshot, (r.start_at at time zone :tz) as start_local,
              (r.end_at at time zone :tz) as end_local, r.guests, r.status, r.cancelled_by, r.status_reason,
              r.price_snapshot
            from reservation r
            join common_area a on a.id = r.area_id
            left join unit u on u.id = r.unit_id
            where r.condominium_id = :cid
              and ((r.start_at at time zone :tz)::date) between :from and :to
            order by r.start_at
            """, p, (rs, i) -> new ReservationExportRow(rs.getString("code"), rs.getString("kind"),
            rs.getString("area_name"), rs.getString("unit_identifier"), rs.getString("resident_name_snapshot"),
            rs.getTimestamp("start_local").toLocalDateTime(), rs.getTimestamp("end_local").toLocalDateTime(),
            (Integer) rs.getObject("guests"), rs.getString("status"), rs.getString("cancelled_by"),
            rs.getString("status_reason"), rs.getBigDecimal("price_snapshot")));
    }

    /** `GET /exports/reports` (F8-2): reports criados no período. */
    public List<ReportExportRow> reportExportRows(UUID condominiumId, LocalDate from, LocalDate to,
        String timezone) {
        MapSqlParameterSource p = periodParams(condominiumId, from, to, timezone);
        return jdbc.query("""
            select rep.code, a.name as area_name, u.identifier as unit_identifier, rep.category, rep.status,
              (rep.created_at at time zone :tz) as opened_local,
              (rep.resolved_at at time zone :tz) as resolved_local, rep.maintenance_cost
            from report rep
            join common_area a on a.id = rep.area_id
            join unit u on u.id = rep.unit_id
            where rep.condominium_id = :cid and rep.deleted_at is null
              and ((rep.created_at at time zone :tz)::date) between :from and :to
            order by rep.created_at
            """, p, (rs, i) -> new ReportExportRow(rs.getString("code"), rs.getString("area_name"),
            rs.getString("unit_identifier"), rs.getString("category"), rs.getString("status"),
            rs.getTimestamp("opened_local").toLocalDateTime(),
            rs.getTimestamp("resolved_local") == null ? null : rs.getTimestamp("resolved_local").toLocalDateTime(),
            rs.getBigDecimal("maintenance_cost")));
    }

    /** `GET /exports/payments` (F8-2): reservas com cobrança (`requires_payment_snapshot`) no período. */
    public List<PaymentExportRow> paymentExportRows(UUID condominiumId, LocalDate from, LocalDate to,
        String timezone) {
        MapSqlParameterSource p = periodParams(condominiumId, from, to, timezone);
        return jdbc.query("""
            select r.code, a.name as area_name, u.identifier as unit_identifier,
              ((r.start_at at time zone :tz)::date) as date_local, r.price_snapshot, r.status,
              (r.payment_confirmed_at at time zone :tz) as payment_confirmed_local
            from reservation r
            join common_area a on a.id = r.area_id
            left join unit u on u.id = r.unit_id
            where r.condominium_id = :cid and r.kind = 'BOOKING' and r.requires_payment_snapshot
              and ((r.start_at at time zone :tz)::date) between :from and :to
            order by r.start_at
            """, p, (rs, i) -> new PaymentExportRow(rs.getString("code"), rs.getString("area_name"),
            rs.getString("unit_identifier"), rs.getDate("date_local").toLocalDate(),
            rs.getBigDecimal("price_snapshot"), rs.getString("status"),
            rs.getTimestamp("payment_confirmed_local") == null ? null
                : rs.getTimestamp("payment_confirmed_local").toLocalDateTime()));
    }

    /** `GET /exports/units` (F8-2): unidades não excluídas, com moradores ativos e reservas do período. */
    public List<UnitExportRow> unitExportRows(UUID condominiumId, LocalDate from, LocalDate to, String timezone) {
        MapSqlParameterSource p = periodParams(condominiumId, from, to, timezone);
        return jdbc.query("""
            select u.identifier, u.block, u.number, u.active,
              coalesce(res_count.residents, 0) as active_residents,
              coalesce(rsv_count.reservations, 0) as reservations
            from unit u
            left join (
              select unit_id, count(*) as residents from resident where deleted_at is null group by unit_id
            ) res_count on res_count.unit_id = u.id
            left join (
              select unit_id, count(*) as reservations from reservation
              where condominium_id = :cid and kind = 'BOOKING' and status <> 'CANCELLED'
                and ((start_at at time zone :tz)::date) between :from and :to
              group by unit_id
            ) rsv_count on rsv_count.unit_id = u.id
            where u.condominium_id = :cid and u.deleted_at is null
            order by u.identifier
            """, p, (rs, i) -> new UnitExportRow(rs.getString("identifier"), rs.getString("block"),
            rs.getString("number"), rs.getBoolean("active"), rs.getLong("active_residents"),
            rs.getLong("reservations")));
    }

    private static BigDecimal occupancyRate(BigDecimal reservedHours, BigDecimal availableHours) {
        if (availableHours == null || availableHours.signum() == 0) {
            return BigDecimal.ZERO.setScale(4, java.math.RoundingMode.HALF_UP);
        }
        return reservedHours.divide(availableHours, 4, java.math.RoundingMode.HALF_UP);
    }

    private static MapSqlParameterSource params(UUID condominiumId) {
        return new MapSqlParameterSource("cid", condominiumId);
    }

    private static MapSqlParameterSource periodParams(UUID condominiumId, LocalDate from, LocalDate to,
        String timezone) {
        return new MapSqlParameterSource()
            .addValue("cid", condominiumId)
            .addValue("from", from)
            .addValue("to", to)
            .addValue("tz", timezone);
    }
}
