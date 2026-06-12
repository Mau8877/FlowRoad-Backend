package sw1.backend.flowroad.dtos.report;

import java.time.LocalDateTime;

import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class GeneratedReportHistoryResponse {
    private String id;
    private String title;
    private String prompt;
    private String chartType;
    private Integer rowCount;
    private LocalDateTime generatedAt;
}
