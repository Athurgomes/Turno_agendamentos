package br.com.reservas.report.infra;

import br.com.reservas.report.domain.ReportComment;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ReportCommentRepository extends JpaRepository<ReportComment, UUID> {

    List<ReportComment> findByReportIdInOrderByCreatedAtAsc(Collection<UUID> reportIds);

    default Map<UUID, List<ReportComment>> groupByReportId(Collection<UUID> reportIds) {
        return findByReportIdInOrderByCreatedAtAsc(reportIds).stream()
            .collect(java.util.stream.Collectors.groupingBy(ReportComment::getReportId));
    }
}
