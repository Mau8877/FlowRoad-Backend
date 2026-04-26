package sw1.backend.flowroad.dtos.process;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class HistoryResponse {
    private String id;
    private String processInstanceId;
    private String assignmentId;

    private String fromNodeId;
    private String fromNodeName;

    private String toNodeId;
    private String toNodeName;

    private String transitionLabel;

    private String performedByUserId;
    private String performedByUserName;
    private LocalDateTime performedAt;

    private String templateDocumentId;
    private String templateName;

    /**
     * Respuesta cruda, guardada por fieldId.
     * Ej:
     * {
     * "6xz8ygj": "Alberto",
     * "67mry7q": "2026-11-11"
     * }
     */
    private Map<String, Object> templateResponseData;

    /**
     * Respuesta lista para mostrar en frontend.
     * Ej:
     * [
     * { fieldId: "6xz8ygj", label: "Nombre del cliente", value: "Alberto" }
     * ]
     */
    private List<HistoryFieldResponse> templateResponseFields;

    private List<Map<String, Object>> attachments;
    private String comment;
}