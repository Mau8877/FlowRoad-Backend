package sw1.backend.flowroad.dtos.client;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ClientStartProcessInstanceResponse {
    private String processInstanceId;
    private String trackingCode;
    private String workflowName;
    private String status;
    private String message;
}
