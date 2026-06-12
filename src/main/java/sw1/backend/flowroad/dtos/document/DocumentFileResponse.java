package sw1.backend.flowroad.dtos.document;

import java.time.LocalDateTime;

import lombok.Data;

@Data
public class DocumentFileResponse {
    private String id;
    private String orgId;
    private String processInstanceId;
    private String processAssignmentId;
    private String diagramId;
    private String nodeId;
    private String documentRequirementId;
    private String requirementName;
    private String originalFileName;
    private String contentType;
    private String fileExtension;
    private Long fileSizeBytes;
    private String status;
    private Integer version;
    private String uploadedBy;
    private String uploadedByName;
    private String uploadedByDepartmentId;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
    private String replacedByDocumentFileId;
}
