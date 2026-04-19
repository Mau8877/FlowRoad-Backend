package sw1.backend.flowroad.dtos.templates;

import java.time.LocalDateTime;
import java.util.List;

import lombok.Data;
import sw1.backend.flowroad.models.templates.FormTelemetry.FieldMetric;

@Data
public class FormTelemetryResponse {
    private String id;
    private String templateId;
    private Integer templateVersion;
    private String ticketId;
    private String workerName; // Mejor devolver el nombre de "Carlos" en lugar de su ObjectId
    private List<FieldMetric> fieldMetrics;
    private LocalDateTime createdAt;
}