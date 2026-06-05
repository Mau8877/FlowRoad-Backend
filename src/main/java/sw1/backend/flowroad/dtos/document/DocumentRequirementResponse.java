package sw1.backend.flowroad.dtos.document;

import java.time.LocalDateTime;
import java.util.List;

import lombok.Data;

@Data
public class DocumentRequirementResponse {
    private String id;
    private String orgId;
    private String diagramId;
    private String nodeId;
    private String name;
    private String description;
    private Boolean required;
    private List<String> allowedFileTypes;
    private Integer maxFileSizeMb;
    private List<String> readDepartmentIds;
    private List<String> uploadDepartmentIds;
    private List<String> editDepartmentIds;
    private String status;
    private LocalDateTime createdAt;
    private String createdBy;
    private LocalDateTime updatedAt;
    private String updatedBy;
}
