package sw1.backend.flowroad.dtos.document;

import java.time.LocalDateTime;

import lombok.Data;

@Data
public class DocumentManagementExpedientSummaryResponse {
    private String processInstanceId;
    private String processCode;
    private String diagramId;
    private String diagramName;
    private Integer diagramVersion;
    private String processStatus;
    private String clientId;
    private String clientName;
    private String clientEmail;
    private LocalDateTime startedAt;
    private LocalDateTime updatedAt;
    private LocalDateTime finishedAt;
    private Integer readableRequirementsCount;
    private Integer uploadedDocumentsCount;
    private Integer pendingDocumentsCount;
}
