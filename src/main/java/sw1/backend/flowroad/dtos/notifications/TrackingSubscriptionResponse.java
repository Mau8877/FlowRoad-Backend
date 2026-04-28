package sw1.backend.flowroad.dtos.notifications;

import java.time.LocalDateTime;

public record TrackingSubscriptionResponse(
        String id,
        String trackingCode,
        String processInstanceId,
        String platform,
        Boolean active,
        LocalDateTime createdAt,
        LocalDateTime updatedAt,
        String message) {
}