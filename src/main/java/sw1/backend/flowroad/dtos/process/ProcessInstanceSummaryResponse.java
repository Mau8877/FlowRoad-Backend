package sw1.backend.flowroad.dtos.process;

import java.time.LocalDateTime;
import java.util.List;

import lombok.Builder;
import lombok.Data;
import sw1.backend.flowroad.models.process.ProcessInstance.ProcessInstanceStatus;

@Data
@Builder
public class ProcessInstanceSummaryResponse {
    private String id;
    private String code;
    private String diagramId;
    private String diagramName;
    private Integer diagramVersion;
    private ProcessInstanceStatus status;
    private List<String> activeNodeIds;
    private List<String> completedNodeIds;
    private String startedByUserId;
    private String startedByUserName;
    private LocalDateTime startedAt;
    private LocalDateTime updatedAt;
    private LocalDateTime finishedAt;
}

