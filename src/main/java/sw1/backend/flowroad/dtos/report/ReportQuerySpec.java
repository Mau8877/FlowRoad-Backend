package sw1.backend.flowroad.dtos.report;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.AllArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ReportQuerySpec {
    private String title;
    private String reportIntent;
    private String dataset;
    private DateRangeSpec dateRange;
    @Builder.Default
    private List<String> groupBy = new ArrayList<>();
    @Builder.Default
    private List<String> metrics = new ArrayList<>();
    @Builder.Default
    private List<ReportFilterSpec> filters = new ArrayList<>();
    @Builder.Default
    private List<ReportSortSpec> sort = new ArrayList<>();
    private Integer limit;
    private String chartType;
    private String summaryIntent;

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class DateRangeSpec {
        private String preset;
        private LocalDateTime from;
        private LocalDateTime to;
    }
}
