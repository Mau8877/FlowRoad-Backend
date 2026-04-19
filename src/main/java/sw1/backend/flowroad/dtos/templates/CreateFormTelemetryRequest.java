package sw1.backend.flowroad.dtos.templates;

import java.util.List;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import sw1.backend.flowroad.models.templates.FormTelemetry.FieldMetric;

public record CreateFormTelemetryRequest(
        @NotBlank(message = "El templateId es obligatorio") String templateId,

        @NotNull(message = "La versión de la plantilla es obligatoria") Integer templateVersion,

        @NotBlank(message = "El ticketId es obligatorio") String ticketId,

        @NotEmpty(message = "Debe enviar al menos una métrica") List<FieldMetric> fieldMetrics) {
}
// Nota: No pedimos workerId ni orgId. Tu Spring Boot los sacará del token del
// mecánico.