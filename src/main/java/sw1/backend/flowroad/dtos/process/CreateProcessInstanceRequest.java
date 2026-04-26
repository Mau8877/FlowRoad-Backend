package sw1.backend.flowroad.dtos.process;

import java.util.Map;

import jakarta.validation.constraints.NotBlank;

public record CreateProcessInstanceRequest(
        @NotBlank(message = "El diagramId es obligatorio") String diagramId,
        Map<String, Object> requestData) {
}

