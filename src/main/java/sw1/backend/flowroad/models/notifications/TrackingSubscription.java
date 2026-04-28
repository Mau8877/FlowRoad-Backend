package sw1.backend.flowroad.models.notifications;

import java.time.LocalDateTime;

import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.index.Indexed;
import org.springframework.data.mongodb.core.mapping.Document;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Document(collection = "tracking_subscriptions")
public class TrackingSubscription {

    @Id
    private String id;

    @Indexed
    private String userId;

    private String userEmail;

    @Indexed
    private String trackingCode;

    @Indexed
    private String processInstanceId;

    @Indexed
    private String deviceToken;

    private String platform;

    private Boolean active;

    private LocalDateTime createdAt;

    private LocalDateTime updatedAt;
}