package sw1.backend.flowroad.models.process;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

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
@Document(collection = "process_instances")
public class ProcessInstance {

    @Id
    private String id;

    private String code;
    private String orgId;
    private String diagramId;
    private String diagramName;
    private Integer diagramVersion;
    private ProcessInstanceStatus status;

    @Builder.Default
    private List<String> activeNodeIds = new ArrayList<>();

    @Builder.Default
    private List<String> completedNodeIds = new ArrayList<>();

    @Builder.Default
    private Map<String, Integer> nodeActivationCounts = new HashMap<>();

    @Builder.Default
    private Map<String, List<String>> joinArrivals = new HashMap<>();

    private Map<String, Object> requestData;

    private String startedByUserId;
    private String startedByUserName;
    private LocalDateTime startedAt;
    private LocalDateTime updatedAt;
    private LocalDateTime finishedAt;

    public enum ProcessInstanceStatus {
        RUNNING,
        PENDING_ASSIGNMENT,
        COMPLETED,
        CANCELLED
    }
}
