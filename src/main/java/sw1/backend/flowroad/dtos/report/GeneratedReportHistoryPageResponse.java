package sw1.backend.flowroad.dtos.report;

import java.util.List;

import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class GeneratedReportHistoryPageResponse {
    private List<GeneratedReportHistoryResponse> content;
    private int page;
    private int size;
    private long totalElements;
    private int totalPages;
    private boolean first;
    private boolean last;
}
