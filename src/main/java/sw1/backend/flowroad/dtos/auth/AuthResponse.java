package sw1.backend.flowroad.dtos.auth;

import lombok.Builder;

@Builder
public record AuthResponse(
        String token,
        String message) {
}