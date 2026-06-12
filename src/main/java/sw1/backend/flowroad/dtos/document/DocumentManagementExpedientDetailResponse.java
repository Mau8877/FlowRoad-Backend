package sw1.backend.flowroad.dtos.document;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

import lombok.Data;

@Data
public class DocumentManagementExpedientDetailResponse {
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
    private List<DocumentExpedientItemResponse> items = new ArrayList<>();
}
