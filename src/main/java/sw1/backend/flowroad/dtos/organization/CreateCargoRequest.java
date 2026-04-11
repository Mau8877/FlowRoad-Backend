package sw1.backend.flowroad.dtos.organization;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

public record CreateCargoRequest(
        @NotBlank(message = "El ID de la organización es obligatorio") String orgId,

        @NotBlank(message = "El nombre del cargo no puede estar vacío") String name,

        @NotNull(message = "El nivel jerárquico es obligatorio") @Min(value = 1, message = "El nivel mínimo debe ser 1") Integer level) {
}