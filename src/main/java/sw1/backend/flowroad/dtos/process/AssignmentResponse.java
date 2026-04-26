package sw1.backend.flowroad.dtos.process;

import java.time.LocalDateTime;

import lombok.Builder;
import lombok.Data;
import sw1.backend.flowroad.models.process.ProcessAssignment.ProcessAssignmentStatus;

@Data
@Builder
public class AssignmentResponse {
    private String id;
    private String processInstanceId;
    private String nodeId;
    private String nodeName;
    private String laneId;
    private String laneName;
    private String assignedDepartmentId;
    private String assignedDepartmentName;
    private String assignedCargoId;
    private String assignedCargoName;
    private String assignedUserId;
    private String assignedUserName;
    private String templateDocumentId;
    private String templateName;
    private ProcessAssignmentStatus status;
    private LocalDateTime createdAt;
    private LocalDateTime assignedAt;
    private LocalDateTime completedAt;
}

