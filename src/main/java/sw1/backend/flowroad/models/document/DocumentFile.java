package sw1.backend.flowroad.models.document;

import java.time.LocalDateTime;

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
@Document(collection = "document_files")
public class DocumentFile {

    @Id
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
    private String s3Bucket;
    private String s3Key;
    private DocumentFileStatus status;
    private Integer version;
    private String uploadedBy;
    private String uploadedByName;
    private String uploadedByDepartmentId;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
    private String replacedByDocumentFileId;

    public enum DocumentFileStatus {
        ACTIVE,
        REPLACED,
        DELETED
    }
}
