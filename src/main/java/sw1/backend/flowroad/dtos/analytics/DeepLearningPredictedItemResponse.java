package sw1.backend.flowroad.dtos.analytics;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class DeepLearningPredictedItemResponse {
    private String processInstanceId;
    private String assignmentId;
    private String diagramId;
    private String diagramName;
    private int stepIndex;
    private String nodeId;
    private String assignedDepartmentId;
    private String assignedDepartmentName;
    private String assignedCargoId;
    private String assignedUserId;

    private int workerActiveLoad;
    private int departmentActiveLoad;
    private Double assignmentDurationHours;
    private Double currentStepDurationHours;
    private Double accumulatedDurationHours;
    private int reworkCount;
    private Double slaHoursTarget;
    private int nodeActivationCount;

    private String originalPriorityLabel;
    private String originalRecommendedAction;
    private Boolean originalBottleneck;
    private Boolean originalAnomalous;
    
    private DeepLearningPredictResponse prediction;
}
