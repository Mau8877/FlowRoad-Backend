package sw1.backend.flowroad.dtos;

import sw1.backend.flowroad.models.UserProfile;

public record RegisterRequest(
        String email,
        String password,
        String role,
        UserProfile perfil,
        String empresaId,
        String departamento,
        String cargo) {
}
