package sw1.backend.flowroad.dtos.organization;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.util.List;

public record UpdateDepartmentRequest(
                String managerId,

                @NotBlank(message = "El nombre no puede estar vacío") String name,

                @NotBlank(message = "El código es obligatorio") @Size(min = 2, max = 5, message = "El código debe tener entre 2 y 5 caracteres") String code,

                @NotNull(message = "El SLA de horas es obligatorio") @Min(value = 1, message = "El SLA mínimo debe ser de 1 hora") Integer slaHours,

                @NotNull(message = "El estado de actividad es obligatorio") Boolean isActive,

                @NotNull(message = "La lista de cargos no puede ser nula") List<String> cargos) {
}