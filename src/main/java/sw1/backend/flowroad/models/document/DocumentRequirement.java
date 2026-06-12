package sw1.backend.flowroad.models.document;

import java.time.LocalDateTime;
import java.util.ArrayList;
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
@Document(collection = "document_requirements")
public class DocumentRequirement {

    @Id
    private String id;

    private String orgId;
    private String diagramId;
    private String nodeId;

    private String name;
    private String description;
    private Boolean required;

    @Builder.Default
    private List<String> allowedFileTypes = new ArrayList<>();

    private Integer maxFileSizeMb;

    @Builder.Default
    private List<String> readDepartmentIds = new ArrayList<>();

    @Builder.Default
    private List<String> uploadDepartmentIds = new ArrayList<>();

    @Builder.Default
    private List<String> editDepartmentIds = new ArrayList<>();

    @Builder.Default
    private Boolean clientCanRead = false;

    @Builder.Default
    private Boolean clientCanUpload = false;

    @Builder.Default
    private Boolean clientCanReplace = false;

    private DocumentRequirementStatus status;
    private LocalDateTime createdAt;
    private String createdBy;
    private LocalDateTime updatedAt;
    private String updatedBy;

    public enum DocumentRequirementStatus {
        ACTIVE,
        INACTIVE
    }
}
