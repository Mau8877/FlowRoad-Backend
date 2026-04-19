package sw1.backend.flowroad.dtos.templates;

import java.time.LocalDateTime;
import java.util.List;

import lombok.Data;
import sw1.backend.flowroad.models.templates.Template.FieldDefinition;

@Data
public class TemplateResponse {
    private String id;
    private String name;
    private String description;
    private String departmentId;
    private String departmentName;
    private Integer version;
    private Boolean isActive;
    private List<FieldDefinition> fields;
    private LocalDateTime createdAt;
    private String createdBy;
}