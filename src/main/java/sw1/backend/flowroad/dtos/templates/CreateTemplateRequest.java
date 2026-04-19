package sw1.backend.flowroad.dtos.templates;

import java.util.List;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import sw1.backend.flowroad.models.templates.Template.FieldDefinition;

public record CreateTemplateRequest(
                @NotBlank(message = "El nombre de la plantilla es obligatorio") String name,

                String description,

                String departmentId,

                @NotEmpty(message = "La plantilla debe tener al menos un campo") @Valid List<FieldDefinition> fields) {
}
