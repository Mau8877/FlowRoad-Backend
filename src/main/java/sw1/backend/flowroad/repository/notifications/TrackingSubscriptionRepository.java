package sw1.backend.flowroad.repository.notifications;

import java.util.List;
import java.util.Optional;

import org.springframework.data.mongodb.repository.MongoRepository;

import sw1.backend.flowroad.models.notifications.TrackingSubscription;

public interface TrackingSubscriptionRepository extends MongoRepository<TrackingSubscription, String> {

    Optional<TrackingSubscription> findByUserIdAndTrackingCodeAndDeviceToken(
            String userId,
            String trackingCode,
            String deviceToken);

    List<TrackingSubscription> findByProcessInstanceIdAndActiveTrue(String processInstanceId);

    List<TrackingSubscription> findByTrackingCodeAndActiveTrue(String trackingCode);
}