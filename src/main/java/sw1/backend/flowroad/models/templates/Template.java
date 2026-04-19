package sw1.backend.flowroad.models.templates;

import java.time.LocalDateTime;
import java.util.List;

import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.mapping.Document;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Document(collection = "templates")
public class Template {
    @Id
    private String id;
    private String orgId;
    private String name;
    private String description;
    private String departmentId;
    private Integer version;
    private Boolean isActive;

    private List<FieldDefinition> fields;

    private LocalDateTime createdAt;
    private String createdBy;

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class FieldDefinition {
        private String fieldId; // Ej: "f_revision_frenos"
        private FieldType type;
        private String label;
        private Boolean required;
        private Boolean isInternalOnly; // Privacidad para la empresa

        private List<SelectOption> options;
        private UIProps uiProps;

        // El Asistente: Sugerencias de texto generadas por IA
        private List<String> aiSuggestions;
    }

    @Data
    @AllArgsConstructor
    @NoArgsConstructor
    public static class SelectOption {
        private String label;
        private String value;
    }

    @Data
    public static class UIProps {
        private Integer order;
        private Integer gridCols;
        private String placeholder;
    }

    public enum FieldType {
        TEXT, TEXTAREA, NUMBER, SELECT, MULTIPLE_CHOICE, DATE, FILE, PHOTO
    }
}