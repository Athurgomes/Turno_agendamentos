package br.com.reservas.reservation.infra;

import br.com.reservas.reservation.application.ReservationFilter;
import br.com.reservas.reservation.domain.Reservation;
import java.time.Instant;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.domain.Specification;

/**
 * `GET /reservations` (S/A, D-53): um {@link Specification} por filtro
 * presente, combinados com `and`. Evita o padrão "`campo is null or campo =
 * ?`" em JPQL puro, que o driver do Postgres não consegue tipar direito
 * quando o parâmetro (ex.: `timestamptz`) só aparece num `is null` (erro
 * "could not determine data type of parameter").
 */
public final class ReservationSpecifications {

    private ReservationSpecifications() {
    }

    public static Specification<Reservation> of(ReservationFilter filter, UUID resolvedUnitId, ZoneId zone) {
        List<Specification<Reservation>> specs = new ArrayList<>();
        if (filter.areaId() != null) {
            specs.add((root, query, cb) -> cb.equal(root.get("areaId"), filter.areaId()));
        }
        if (filter.from() != null) {
            Instant from = filter.from().atStartOfDay(zone).toInstant();
            specs.add((root, query, cb) -> cb.greaterThanOrEqualTo(root.get("startAt"), from));
        }
        if (filter.to() != null) {
            Instant to = filter.to().plusDays(1).atStartOfDay(zone).toInstant();
            specs.add((root, query, cb) -> cb.lessThan(root.get("startAt"), to));
        }
        if (filter.status() != null) {
            specs.add((root, query, cb) -> cb.equal(root.get("status"), filter.status()));
        }
        if (resolvedUnitId != null) {
            specs.add((root, query, cb) -> cb.equal(root.get("unitId"), resolvedUnitId));
        }
        if (filter.kind() != null) {
            specs.add((root, query, cb) -> cb.equal(root.get("kind"), filter.kind()));
        }
        return specs.stream().reduce(Specification::and).orElse(Specification.where(null));
    }
}
