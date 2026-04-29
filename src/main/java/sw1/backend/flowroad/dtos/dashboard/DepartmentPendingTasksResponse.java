package sw1.backend.flowroad.dtos.dashboard;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class DepartmentPendingTasksResponse {
    private String departmentId;
    private String departmentName;
    private long pendingTasks;
}
