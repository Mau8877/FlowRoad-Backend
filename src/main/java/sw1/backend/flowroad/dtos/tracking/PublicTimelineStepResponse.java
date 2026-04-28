package sw1.backend.flowroad.dtos.tracking;

import java.time.LocalDateTime;

public record PublicTimelineStepResponse(
        String nodeId,
        String label,
        String type,
        String status,
        String departmentId,
        String departmentName,
        LocalDateTime startedAt,
        LocalDateTime completedAt,
        String comment) {
}