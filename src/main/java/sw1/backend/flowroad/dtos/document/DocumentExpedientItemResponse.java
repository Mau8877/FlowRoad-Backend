package sw1.backend.flowroad.dtos.document;

import lombok.Data;

@Data
public class DocumentExpedientItemResponse {
    private DocumentRequirementResponse requirement;
    private DocumentFileResponse currentFile;
    private String status;
    private Boolean canRead;
    private Boolean canUpload;
    private Boolean canEdit;
}
