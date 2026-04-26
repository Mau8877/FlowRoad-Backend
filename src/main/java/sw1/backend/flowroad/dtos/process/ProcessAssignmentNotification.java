package sw1.backend.flowroad.dtos.process;

import java.time.LocalDateTime;

import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class ProcessAssignmentNotification {

    private String type;

    private String assignmentId;
    private String processInstanceId;
    private String processCode;

    private String diagramId;
    private String diagramName;

    private String nodeId;
    private String nodeName;

    private String assignedUserId;
    private String assignedUserName;

    private String assignedDepartmentId;
    private String assignedDepartmentName;

    private String assignedCargoId;
    private String assignedCargoName;

    private String templateDocumentId;
    private String templateName;

    private LocalDateTime assignedAt;
}