package sw1.backend.flowroad.dtos.tracking;

import java.time.LocalDateTime;
import java.util.List;

import sw1.backend.flowroad.models.process.ProcessInstance.ProcessInstanceStatus;

public record PublicTrackingResponse(
        String code,
        String diagramName,
        ProcessInstanceStatus status,
        String statusLabel,
        String currentStepName,
        String currentDepartmentName,
        LocalDateTime startedAt,
        LocalDateTime updatedAt,
        LocalDateTime finishedAt,
        List<PublicTimelineStepResponse> timeline) {
}