package sw1.backend.flowroad.dtos.report;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

@Data
public class ReportPromptRequest {
    @NotBlank(message = "El prompt es obligatorio.")
    private String prompt;
}
