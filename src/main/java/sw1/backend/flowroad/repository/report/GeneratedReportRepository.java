package sw1.backend.flowroad.repository.report;

import java.util.List;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.mongodb.repository.MongoRepository;

import sw1.backend.flowroad.models.report.GeneratedReport;

public interface GeneratedReportRepository extends MongoRepository<GeneratedReport, String> {
    List<GeneratedReport> findTop10ByOrgIdAndGeneratedByUserIdOrderByGeneratedAtDesc(String orgId, String generatedByUserId);

    Page<GeneratedReport> findByOrgIdAndGeneratedByUserIdOrderByGeneratedAtDesc(
            String orgId,
            String generatedByUserId,
            Pageable pageable);
}
