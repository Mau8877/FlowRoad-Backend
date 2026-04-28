package sw1.backend.flowroad.controllers.notifications;

import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import sw1.backend.flowroad.dtos.notifications.SubscribeTrackingRequest;
import sw1.backend.flowroad.dtos.notifications.TrackingSubscriptionResponse;
import sw1.backend.flowroad.models.user.User;
import sw1.backend.flowroad.services.notifications.MobileNotificationService;

@RestController
@RequestMapping("/mobile/notifications")
@RequiredArgsConstructor
public class MobileNotificationController {

    private final MobileNotificationService mobileNotificationService;

    @PostMapping("/subscribe")
    @PreAuthorize("hasAuthority('CLIENT')")
    public ResponseEntity<TrackingSubscriptionResponse> subscribeToTracking(
            @Valid @RequestBody SubscribeTrackingRequest request,
            @AuthenticationPrincipal User currentUser) {

        return ResponseEntity.ok(
                mobileNotificationService.subscribeToTracking(request, currentUser));
    }
}