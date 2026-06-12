package sw1.backend.flowroad.models.report;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.mapping.Document;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import sw1.backend.flowroad.dtos.report.ReportQuerySpec;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Document(collection = "generated_reports")
public class GeneratedReport {
    @Id
    private String id;
    private String orgId;
    private String generatedByUserId;
    private String title;
    private String prompt;
    private String summary;
    private String chartType;
    private List<String> columns;
    private List<Map<String, Object>> rows;
    private ReportQuerySpec querySpec;
    private LocalDateTime generatedAt;
}
