package sw1.backend.flowroad.dtos.auth;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import sw1.backend.flowroad.dtos.user.UserProfileRequest;
import sw1.backend.flowroad.models.user.Roles;

public record RegisterWorkerRequest(
        @NotBlank(message = "El email es obligatorio") @Email(message = "El formato del correo no es válido") String email,

        @NotBlank(message = "La contraseña temporal es obligatoria") String password,

        @NotNull(message = "El rol es obligatorio") Roles role,

        @NotBlank(message = "El orgId es obligatorio para trabajadores") String orgId,

        String departmentId,
        String cargoId,

        @NotNull(message = "Los datos del perfil son obligatorios") @Valid UserProfileRequest profile) {
}