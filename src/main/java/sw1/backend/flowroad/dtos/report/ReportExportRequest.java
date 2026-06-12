package sw1.backend.flowroad.dtos.report;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

@Data
public class ReportExportRequest {
    @NotBlank(message = "El prompt es obligatorio.")
    private String prompt;

    @NotBlank(message = "El formato es obligatorio.")
    private String format;
}
