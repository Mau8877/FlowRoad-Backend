package sw1.backend.flowroad.dtos.dashboard;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import sw1.backend.flowroad.models.process.ProcessInstance.ProcessInstanceStatus;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class StatusCountResponse {
    private ProcessInstanceStatus status;
    private String label;
    private long count;
}
