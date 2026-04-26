package sw1.backend.flowroad.models.process;

import java.time.LocalDateTime;

import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.mapping.Document;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Document(collection = "process_assignments")
public class ProcessAssignment {

    @Id
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

    public enum ProcessAssignmentStatus {
        PENDING,
        COMPLETED,
        CANCELLED
    }
}

