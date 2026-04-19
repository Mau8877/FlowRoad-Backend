package sw1.backend.flowroad.dtos.templates;

import java.util.List;

import jakarta.validation.Valid;
import sw1.backend.flowroad.models.templates.Template.FieldDefinition;

public record UpdateTemplateRequest(
        String name,
        String description,
        String departmentId,
        Boolean isActive,
        @Valid List<FieldDefinition> fields) {
}