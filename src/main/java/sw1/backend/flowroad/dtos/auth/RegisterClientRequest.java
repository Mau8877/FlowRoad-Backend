package sw1.backend.flowroad.dtos.auth;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import sw1.backend.flowroad.dtos.user.UserProfileRequest;

public record RegisterClientRequest(
                @NotBlank(message = "El email es obligatorio") @Email(message = "El formato del correo no es válido") String email,

                @NotBlank(message = "La contraseña es obligatoria") @Size(min = 8, message = "La contraseña debe tener al menos 8 caracteres") String password,

                @NotNull(message = "El perfil es obligatorio") @Valid UserProfileRequest profile) {
}