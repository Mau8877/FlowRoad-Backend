package sw1.backend.flowroad.dtos.report;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class ReportPreviewResponse {
    private String reportId;
    private String title;
    private String summary;
    private String prompt;
    private String generatedAt;
    private String reportIntent;
    private String dateRangeLabel;
    private String dataSource;
    private List<String> columns;
    private List<Map<String, Object>> rows;
    private String chartType;
    private List<Map<String, Object>> chartData;
    private ReportQuerySpec querySpec;
    @Builder.Default
    private List<String> warnings = new ArrayList<>();
}
