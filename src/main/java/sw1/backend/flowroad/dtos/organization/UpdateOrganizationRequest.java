package sw1.backend.flowroad.dtos.organization;

import jakarta.validation.constraints.NotBlank;

public record UpdateOrganizationRequest(
        @NotBlank(message = "El nombre no puede estar vacío") String name,
        @NotBlank(message = "El código no puede estar vacío") String code) {
}
