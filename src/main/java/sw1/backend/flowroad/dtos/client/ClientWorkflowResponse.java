package sw1.backend.flowroad.dtos.client;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ClientWorkflowResponse {
    private String id;
    private String name;
    private String description;
    private String organizationId;
}
