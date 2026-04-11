package sw1.backend.flowroad.dtos.user;

import jakarta.validation.constraints.NotBlank;

public record UserProfileRequest(
        @NotBlank(message = "El nombre no puede estar vacío") String nombre,

        @NotBlank(message = "El apellido no puede estar vacío") String apellido,

        String telefono,
        String direccion,
        String avatarUrl) {
}