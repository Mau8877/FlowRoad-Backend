package sw1.backend.flowroad.dtos.notifications;

import jakarta.validation.constraints.NotBlank;

public record SubscribeTrackingRequest(
        @NotBlank(message = "El código de seguimiento es obligatorio") String trackingCode,

        @NotBlank(message = "El token del dispositivo es obligatorio") String deviceToken,

        @NotBlank(message = "La plataforma es obligatoria") String platform) {
}