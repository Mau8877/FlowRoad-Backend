package sw1.backend.flowroad.dtos.dashboard;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class DashboardKpiResponse {
    private long totalProcesses;
    private long completedProcesses;
    private long runningProcesses;
    private long pendingAssignmentProcesses;
    private long cancelledProcesses;
    
    private double completionRate;
    
    private long averageCompletionTimeMinutes;
    private String averageCompletionTimeLabel;
    
    private List<StatusCountResponse> processesByStatus;
    private List<DepartmentPendingTasksResponse> pendingTasksByDepartment;
    private List<PopularProcessResponse> mostUsedProcesses;
    
    private LocalDateTime generatedAt;
}
