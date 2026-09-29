package br.com.reservas.report.infra;

import br.com.reservas.report.application.ReportFilter;
import br.com.reservas.report.domain.Report;
import java.time.Instant;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.List;
import org.springframework.data.jpa.domain.Specification;

/** `GET /reports` (S/A): um {@link Specification} por filtro presente (mesmo padrão de `ReservationSpecifications`). */
public final class ReportSpecifications {

    private ReportSpecifications() {
    }

    public static Specification<Report> of(ReportFilter filter, ZoneId zone) {
        List<Specification<Report>> specs = new ArrayList<>();
        if (filter.status() != null) {
            specs.add((root, query, cb) -> cb.equal(root.get("status"), filter.status()));
        }
        if (filter.areaId() != null) {
            specs.add((root, query, cb) -> cb.equal(root.get("areaId"), filter.areaId()));
        }
        if (filter.category() != null) {
            specs.add((root, query, cb) -> cb.equal(root.get("category"), filter.category()));
        }
        if (filter.from() != null) {
            Instant from = filter.from().atStartOfDay(zone).toInstant();
            specs.add((root, query, cb) -> cb.greaterThanOrEqualTo(root.get("createdAt"), from));
        }
        if (filter.to() != null) {
            Instant to = filter.to().plusDays(1).atStartOfDay(zone).toInstant();
            specs.add((root, query, cb) -> cb.lessThan(root.get("createdAt"), to));
        }
        return specs.stream().reduce(Specification::and).orElse(Specification.where(null));
    }
}
