package sw1.backend.flowroad.dtos.process;

import java.time.LocalDateTime;

import lombok.Builder;
import lombok.Data;
import sw1.backend.flowroad.models.process.ProcessInstance.ProcessInstanceStatus;

@Data
@Builder
public class ClientProcessInstanceResponse {
    private String id;
    private String code;
    private String diagramId;
    private String diagramName;
    private Integer diagramVersion;
    private ProcessInstanceStatus status;
    private String clientId;
    private String clientName;
    private String clientEmail;
    private LocalDateTime startedAt;
    private LocalDateTime updatedAt;
    private LocalDateTime finishedAt;
}
