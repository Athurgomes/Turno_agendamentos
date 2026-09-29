package br.com.reservas.report.infra;

import br.com.reservas.report.domain.ReportPhoto;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ReportPhotoRepository extends JpaRepository<ReportPhoto, UUID> {

    List<ReportPhoto> findByReportIdInOrderByCreatedAtAsc(java.util.Collection<UUID> reportIds);

    default Map<UUID, List<ReportPhoto>> groupByReportId(java.util.Collection<UUID> reportIds) {
        return findByReportIdInOrderByCreatedAtAsc(reportIds).stream()
            .collect(java.util.stream.Collectors.groupingBy(ReportPhoto::getReportId));
    }
}
