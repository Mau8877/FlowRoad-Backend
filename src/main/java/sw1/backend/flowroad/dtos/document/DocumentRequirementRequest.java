package sw1.backend.flowroad.dtos.document;

import java.util.List;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;

public record DocumentRequirementRequest(
        @NotBlank(message = "El nombre del requisito documental es obligatorio") String name,
        String description,
        @NotNull(message = "Debe indicar si el requisito es obligatorio") Boolean required,
        @NotEmpty(message = "Debe indicar al menos un tipo de archivo permitido") List<String> allowedFileTypes,
        @NotNull(message = "El tamano maximo es obligatorio") @Min(value = 1, message = "El tamano maximo minimo es 1 MB") @Max(value = 25, message = "El tamano maximo permitido es 25 MB") Integer maxFileSizeMb,
        List<String> readDepartmentIds,
        List<String> uploadDepartmentIds,
        List<String> editDepartmentIds) {
}
