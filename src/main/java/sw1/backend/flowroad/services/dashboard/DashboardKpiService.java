package sw1.backend.flowroad.services.dashboard;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import org.springframework.stereotype.Service;

import lombok.RequiredArgsConstructor;
import sw1.backend.flowroad.dtos.dashboard.DashboardKpiResponse;
import sw1.backend.flowroad.dtos.dashboard.DepartmentPendingTasksResponse;
import sw1.backend.flowroad.dtos.dashboard.PopularProcessResponse;
import sw1.backend.flowroad.dtos.dashboard.StatusCountResponse;
import sw1.backend.flowroad.models.process.ProcessAssignment;
import sw1.backend.flowroad.models.process.ProcessAssignment.ProcessAssignmentStatus;
import sw1.backend.flowroad.models.process.ProcessInstance;
import sw1.backend.flowroad.models.process.ProcessInstance.ProcessInstanceStatus;
import sw1.backend.flowroad.repository.process.ProcessAssignmentRepository;
import sw1.backend.flowroad.repository.process.ProcessInstanceRepository;

@Service
@RequiredArgsConstructor
public class DashboardKpiService {

    private final ProcessInstanceRepository processInstanceRepository;
    private final ProcessAssignmentRepository processAssignmentRepository;

    public DashboardKpiResponse getDashboardKpis(String orgId) {
        List<ProcessInstance> instances = processInstanceRepository.findAllByOrgIdOrderByStartedAtDesc(orgId);

        if (instances.isEmpty()) {
            return buildEmptyResponse();
        }

        long totalProcesses = instances.size();
        
        // Count by status
        long running = countByStatus(instances, ProcessInstanceStatus.RUNNING);
        long pendingAssignment = countByStatus(instances, ProcessInstanceStatus.PENDING_ASSIGNMENT);
        long completed = countByStatus(instances, ProcessInstanceStatus.COMPLETED);
        long cancelled = countByStatus(instances, ProcessInstanceStatus.CANCELLED);

        // Build processes by status
        List<StatusCountResponse> processesByStatus = new ArrayList<>();
        processesByStatus.add(new StatusCountResponse(ProcessInstanceStatus.RUNNING, "En curso", running));
        processesByStatus.add(new StatusCountResponse(ProcessInstanceStatus.PENDING_ASSIGNMENT, "Pendiente de asignación", pendingAssignment));
        processesByStatus.add(new StatusCountResponse(ProcessInstanceStatus.COMPLETED, "Completado", completed));
        processesByStatus.add(new StatusCountResponse(ProcessInstanceStatus.CANCELLED, "Cancelado", cancelled));

        // Completion rate
        double completionRate = 0.0;
        if (totalProcesses > 0) {
            completionRate = (double) completed / totalProcesses * 100.0;
            completionRate = new BigDecimal(completionRate).setScale(1, RoundingMode.HALF_UP).doubleValue();
        }

        // Average completion time
        List<ProcessInstance> completedInstances = instances.stream()
                .filter(i -> i.getStatus() == ProcessInstanceStatus.COMPLETED 
                        && i.getStartedAt() != null 
                        && i.getFinishedAt() != null)
                .collect(Collectors.toList());

        long totalMinutes = 0;
        for (ProcessInstance i : completedInstances) {
            totalMinutes += Duration.between(i.getStartedAt(), i.getFinishedAt()).toMinutes();
        }
        
        long averageCompletionTimeMinutes = 0;
        if (!completedInstances.isEmpty()) {
            averageCompletionTimeMinutes = totalMinutes / completedInstances.size();
        }
        
        String averageCompletionTimeLabel = formatMinutes(averageCompletionTimeMinutes);

        // Most used processes
        Map<String, List<ProcessInstance>> groupedByDiagram = instances.stream()
                .filter(i -> i.getDiagramId() != null)
                .collect(Collectors.groupingBy(ProcessInstance::getDiagramId));

        List<PopularProcessResponse> mostUsedProcesses = groupedByDiagram.entrySet().stream()
                .map(entry -> {
                    String diagramName = entry.getValue().get(0).getDiagramName();
                    if (diagramName == null || diagramName.isEmpty()) {
                        diagramName = "Proceso sin nombre";
                    }
                    return new PopularProcessResponse(entry.getKey(), diagramName, entry.getValue().size());
                })
                .sorted(Comparator.comparingLong(PopularProcessResponse::getTotalInstances).reversed())
                .limit(5)
                .collect(Collectors.toList());

        // Pending tasks by department
        List<String> instanceIds = instances.stream().map(ProcessInstance::getId).collect(Collectors.toList());
        List<ProcessAssignment> assignments = processAssignmentRepository
                .findByProcessInstanceIdInAndStatus(instanceIds, ProcessAssignmentStatus.PENDING);

        Map<String, List<ProcessAssignment>> groupedByDept = assignments.stream()
                .collect(Collectors.groupingBy(a -> a.getAssignedDepartmentId() != null ? a.getAssignedDepartmentId() : "none"));

        List<DepartmentPendingTasksResponse> pendingTasksByDepartment = groupedByDept.entrySet().stream()
                .map(entry -> {
                    String deptId = entry.getKey().equals("none") ? null : entry.getKey();
                    String deptName = "Sin departamento";
                    if (!entry.getValue().isEmpty() && entry.getValue().get(0).getAssignedDepartmentName() != null) {
                        deptName = entry.getValue().get(0).getAssignedDepartmentName();
                    }
                    return new DepartmentPendingTasksResponse(deptId, deptName, entry.getValue().size());
                })
                .sorted(Comparator.comparingLong(DepartmentPendingTasksResponse::getPendingTasks).reversed())
                .collect(Collectors.toList());

        return DashboardKpiResponse.builder()
                .totalProcesses(totalProcesses)
                .completedProcesses(completed)
                .runningProcesses(running)
                .pendingAssignmentProcesses(pendingAssignment)
                .cancelledProcesses(cancelled)
                .completionRate(completionRate)
                .averageCompletionTimeMinutes(averageCompletionTimeMinutes)
                .averageCompletionTimeLabel(averageCompletionTimeLabel)
                .processesByStatus(processesByStatus)
                .pendingTasksByDepartment(pendingTasksByDepartment)
                .mostUsedProcesses(mostUsedProcesses)
                .generatedAt(LocalDateTime.now())
                .build();
    }

    private long countByStatus(List<ProcessInstance> instances, ProcessInstanceStatus status) {
        return instances.stream().filter(i -> i.getStatus() == status).count();
    }

    private String formatMinutes(long totalMinutes) {
        if (totalMinutes == 0) {
            return "0min";
        }
        
        long days = totalMinutes / (24 * 60);
        long hours = (totalMinutes % (24 * 60)) / 60;
        long minutes = totalMinutes % 60;
        
        StringBuilder sb = new StringBuilder();
        if (days > 0) {
            sb.append(days).append("d ");
        }
        if (hours > 0) {
            sb.append(hours).append("h ");
        }
        if (minutes > 0 || (days == 0 && hours == 0)) {
            sb.append(minutes).append("min");
        }
        
        return sb.toString().trim();
    }

    private DashboardKpiResponse buildEmptyResponse() {
        List<StatusCountResponse> processesByStatus = new ArrayList<>();
        processesByStatus.add(new StatusCountResponse(ProcessInstanceStatus.RUNNING, "En curso", 0));
        processesByStatus.add(new StatusCountResponse(ProcessInstanceStatus.PENDING_ASSIGNMENT, "Pendiente de asignación", 0));
        processesByStatus.add(new StatusCountResponse(ProcessInstanceStatus.COMPLETED, "Completado", 0));
        processesByStatus.add(new StatusCountResponse(ProcessInstanceStatus.CANCELLED, "Cancelado", 0));

        return DashboardKpiResponse.builder()
                .totalProcesses(0)
                .completedProcesses(0)
                .runningProcesses(0)
                .pendingAssignmentProcesses(0)
                .cancelledProcesses(0)
                .completionRate(0.0)
                .averageCompletionTimeMinutes(0)
                .averageCompletionTimeLabel("0min")
                .processesByStatus(processesByStatus)
                .pendingTasksByDepartment(new ArrayList<>())
                .mostUsedProcesses(new ArrayList<>())
                .generatedAt(LocalDateTime.now())
                .build();
    }
}
