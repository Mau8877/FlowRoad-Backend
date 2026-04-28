package sw1.backend.flowroad.services.notifications;

import java.time.LocalDateTime;
import java.util.Locale;

import org.springframework.stereotype.Service;

import lombok.RequiredArgsConstructor;
import sw1.backend.flowroad.dtos.notifications.SubscribeTrackingRequest;
import sw1.backend.flowroad.dtos.notifications.TrackingSubscriptionResponse;
import sw1.backend.flowroad.exceptions.ResourceNotFoundException;
import sw1.backend.flowroad.models.notifications.TrackingSubscription;
import sw1.backend.flowroad.models.process.ProcessInstance;
import sw1.backend.flowroad.models.user.User;
import sw1.backend.flowroad.repository.notifications.TrackingSubscriptionRepository;
import sw1.backend.flowroad.repository.process.ProcessInstanceRepository;

@Service
@RequiredArgsConstructor
public class MobileNotificationService {

    private final TrackingSubscriptionRepository trackingSubscriptionRepository;
    private final ProcessInstanceRepository processInstanceRepository;

    public TrackingSubscriptionResponse subscribeToTracking(
            SubscribeTrackingRequest request,
            User currentUser) {

        String trackingCode = normalizeTrackingCode(request.trackingCode());

        ProcessInstance instance = processInstanceRepository.findByCode(trackingCode)
                .orElseThrow(() -> new ResourceNotFoundException("No se encontró un trámite con ese código."));

        LocalDateTime now = LocalDateTime.now();

        TrackingSubscription subscription = trackingSubscriptionRepository
                .findByUserIdAndTrackingCodeAndDeviceToken(
                        currentUser.getId(),
                        trackingCode,
                        request.deviceToken())
                .map(existing -> {
                    existing.setActive(true);
                    existing.setPlatform(normalizePlatform(request.platform()));
                    existing.setProcessInstanceId(instance.getId());
                    existing.setUpdatedAt(now);
                    return existing;
                })
                .orElseGet(() -> TrackingSubscription.builder()
                        .userId(currentUser.getId())
                        .userEmail(currentUser.getEmail())
                        .trackingCode(trackingCode)
                        .processInstanceId(instance.getId())
                        .deviceToken(request.deviceToken())
                        .platform(normalizePlatform(request.platform()))
                        .active(true)
                        .createdAt(now)
                        .updatedAt(now)
                        .build());

        TrackingSubscription saved = trackingSubscriptionRepository.save(subscription);

        return new TrackingSubscriptionResponse(
                saved.getId(),
                saved.getTrackingCode(),
                saved.getProcessInstanceId(),
                saved.getPlatform(),
                saved.getActive(),
                saved.getCreatedAt(),
                saved.getUpdatedAt(),
                "Suscripción activada correctamente.");
    }

    private String normalizeTrackingCode(String trackingCode) {
        if (trackingCode == null || trackingCode.isBlank()) {
            throw new ResourceNotFoundException("Debes ingresar un código de seguimiento.");
        }

        return trackingCode.trim().toUpperCase(Locale.ROOT);
    }

    private String normalizePlatform(String platform) {
        if (platform == null || platform.isBlank()) {
            return "UNKNOWN";
        }

        return platform.trim().toUpperCase(Locale.ROOT);
    }
}