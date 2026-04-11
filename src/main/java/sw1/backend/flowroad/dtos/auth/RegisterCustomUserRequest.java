package sw1.backend.flowroad.dtos.auth;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import sw1.backend.flowroad.dtos.user.UserProfileRequest;
import sw1.backend.flowroad.models.user.Roles;

public record RegisterCustomUserRequest(
                @NotBlank(message = "El email es obligatorio") @Email(message = "Formato de email inválido") String email,

                @NotBlank(message = "La contraseña es obligatoria") String password,

                @NotNull(message = "El rol es obligatorio") Roles role,

                String orgId,

                @NotNull(message = "El perfil es obligatorio") @Valid UserProfileRequest profile) {
}