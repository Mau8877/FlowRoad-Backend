package sw1.backend.flowroad.dtos.process;

import java.util.List;
import java.util.Map;

import lombok.Builder;
import lombok.Data;
import sw1.backend.flowroad.models.diagram.Diagram;

@Data
@Builder
public class ProcessInstanceDetailResponse {
    private ProcessInstanceSummaryResponse instance;
    private Map<String, Object> requestData;
    private List<AssignmentResponse> activeAssignments;
    private List<HistoryResponse> history;
    private Diagram diagram;
}

