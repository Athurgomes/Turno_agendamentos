package br.com.reservas.report.infra;

import br.com.reservas.report.domain.Report;
import br.com.reservas.report.domain.ReportStatus;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;

public interface ReportRepository extends JpaRepository<Report, UUID>, JpaSpecificationExecutor<Report> {

    List<Report> findByUnitIdOrderByCreatedAtDesc(UUID unitId);

    Optional<Report> findByIdAndUnitId(UUID id, UUID unitId);

    long countByStatusIn(List<ReportStatus> statuses);
}
