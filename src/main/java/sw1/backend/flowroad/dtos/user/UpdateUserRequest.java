package sw1.backend.flowroad.dtos.user;

import jakarta.validation.constraints.NotBlank;

public record UpdateUserRequest(
        @NotBlank(message = "El nombre es obligatorio") String nombre,

        @NotBlank(message = "El apellido es obligatorio") String apellido,

        String direccion,
        String telefono,
        String avatarUrl,

        // El admin podría querer cambiar estos, el usuario común no los mandaría
        String departmentId,
        String cargoId,
        Boolean isActive) {
}