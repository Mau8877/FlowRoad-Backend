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
@Document(collection = "form_telemetry")
public class FormTelemetry {
    @Id
    private String id;
    private String orgId;
    private String templateId;
    private Integer templateVersion;
    private String ticketId;
    private String workerId;

    private List<FieldMetric> fieldMetrics;
    private LocalDateTime createdAt;

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class FieldMetric {
        private String fieldId;
        private Long timeSpentSeconds;
        private Integer backspaceCount;
        private Integer focusLostCount;
    }
}