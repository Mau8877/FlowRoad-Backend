package sw1.backend.flowroad.dtos.organization;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record CreateOrganizationRequest(
        @NotBlank(message = "El nombre es obligatorio") String name,
        @NotBlank(message = "El código es obligatorio") @Size(min = 2, max = 10) String code) {
}