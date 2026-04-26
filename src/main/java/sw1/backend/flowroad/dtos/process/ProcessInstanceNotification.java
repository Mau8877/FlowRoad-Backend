package sw1.backend.flowroad.dtos.process;

import java.time.LocalDateTime;

import lombok.Builder;
import lombok.Data;
import sw1.backend.flowroad.models.process.ProcessInstance.ProcessInstanceStatus;

@Data
@Builder
public class ProcessInstanceNotification {

    private String type;

    private String processInstanceId;
    private String processCode;

    private String diagramId;
    private String diagramName;

    private ProcessInstanceStatus status;

    private LocalDateTime updatedAt;
    private LocalDateTime finishedAt;
}