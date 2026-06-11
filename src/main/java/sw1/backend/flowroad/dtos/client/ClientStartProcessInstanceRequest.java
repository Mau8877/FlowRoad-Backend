package sw1.backend.flowroad.dtos.client;

import jakarta.validation.constraints.NotBlank;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import java.util.Map;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class ClientStartProcessInstanceRequest {
    @NotBlank(message = "El ID de organizacion es obligatorio")
    private String organizationId;
    
    @NotBlank(message = "El ID de workflow es obligatorio")
    private String workflowId;
    
    private Map<String, Object> initialData;
}
