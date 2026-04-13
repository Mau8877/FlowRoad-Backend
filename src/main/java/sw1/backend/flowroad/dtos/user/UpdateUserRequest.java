package sw1.backend.flowroad.dtos.user;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.Valid;

public record UpdateUserRequest(
                @NotNull(message = "Los datos del perfil son obligatorios") @Valid UserProfileRequest profile,
                String departmentId,
                String cargoId,
                Boolean isActive) {
}