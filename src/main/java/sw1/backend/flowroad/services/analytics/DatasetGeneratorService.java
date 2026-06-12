package sw1.backend.flowroad.services.analytics;

import org.springframework.stereotype.Service;
import lombok.RequiredArgsConstructor;

import sw1.backend.flowroad.models.process.ProcessInstance;
import sw1.backend.flowroad.models.process.ProcessAssignment;
import sw1.backend.flowroad.models.process.ProcessAssignment.ProcessAssignmentStatus;
import sw1.backend.flowroad.models.process.ProcessHistory;
import sw1.backend.flowroad.models.organization.Department;
import sw1.backend.flowroad.models.user.User;
import sw1.backend.flowroad.repository.process.ProcessInstanceRepository;
import sw1.backend.flowroad.repository.process.ProcessAssignmentRepository;
import sw1.backend.flowroad.repository.process.ProcessHistoryRepository;
import sw1.backend.flowroad.repository.organization.DepartmentRepository;
import sw1.backend.flowroad.repository.user.UserRepository;
import sw1.backend.flowroad.dtos.analytics.DeepLearningDatasetResponse;
import sw1.backend.flowroad.dtos.analytics.DeepLearningDatasetItemResponse;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.*;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class DatasetGeneratorService {

    private final ProcessInstanceRepository processInstanceRepository;
    private final ProcessAssignmentRepository processAssignmentRepository;
    private final ProcessHistoryRepository processHistoryRepository;
    private final DepartmentRepository departmentRepository;
    private final UserRepository userRepository;

    public DeepLearningDatasetResponse generateDataset(
            String orgId,
            String diagramId,
            LocalDateTime from,
            LocalDateTime to,
            Integer limit) {

        // 1. Obtener instancias de la organización
        List<ProcessInstance> instances = processInstanceRepository.findAllByOrgIdOrderByStartedAtDesc(orgId);

        // Aplicar filtros en memoria
        if (diagramId != null && !diagramId.trim().isEmpty()) {
            instances = instances.stream()
                    .filter(i -> diagramId.equals(i.getDiagramId()))
                    .collect(Collectors.toList());
        }

        if (from != null) {
            instances = instances.stream()
                    .filter(i -> i.getStartedAt() != null && !i.getStartedAt().isBefore(from))
                    .collect(Collectors.toList());
        }

        if (to != null) {
            instances = instances.stream()
                    .filter(i -> i.getStartedAt() != null && !i.getStartedAt().isAfter(to))
                    .collect(Collectors.toList());
        }

        if (instances.isEmpty()) {
            return DeepLearningDatasetResponse.builder()
                    .totalItems(0)
                    .generatedAt(LocalDateTime.now())
                    .items(new ArrayList<>())
                    .build();
        }

        List<String> instanceIds = instances.stream()
                .map(ProcessInstance::getId)
                .collect(Collectors.toList());

        // 2. Cargar dependencias en batch para evitar N+1 queries
        List<ProcessAssignment> allAssignments = processAssignmentRepository.findByProcessInstanceIdIn(instanceIds);
        List<ProcessHistory> allHistories = processHistoryRepository.findByProcessInstanceIdIn(instanceIds);
        
        List<Department> departments = departmentRepository.findByOrgId(orgId);
        Map<String, Department> departmentsMap = departments.stream()
                .filter(d -> d.getId() != null)
                .collect(Collectors.toMap(Department::getId, d -> d, (a, b) -> a));

        List<User> users = userRepository.findAllByOrgIdAndIsActiveTrue(orgId);
        Map<String, User> usersMap = users.stream()
                .filter(u -> u.getId() != null)
                .collect(Collectors.toMap(User::getId, u -> u, (a, b) -> a));

        // 3. Agrupaciones para calcular cargas de trabajo activas
        List<ProcessAssignment> pendingAssignments = processAssignmentRepository
                .findByProcessInstanceIdInAndStatus(instanceIds, ProcessAssignmentStatus.PENDING);

        Map<String, Long> deptActiveLoadMap = pendingAssignments.stream()
                .filter(a -> a.getAssignedDepartmentId() != null)
                .collect(Collectors.groupingBy(ProcessAssignment::getAssignedDepartmentId, Collectors.counting()));

        Map<String, Long> userActiveLoadMap = pendingAssignments.stream()
                .filter(a -> a.getAssignedUserId() != null)
                .collect(Collectors.groupingBy(ProcessAssignment::getAssignedUserId, Collectors.counting()));

        // Mapeos rápidos para asociar historial y asignaciones a cada instancia
        Map<String, List<ProcessAssignment>> assignmentsByInstance = allAssignments.stream()
                .collect(Collectors.groupingBy(ProcessAssignment::getProcessInstanceId));

        Map<String, List<ProcessHistory>> historiesByInstance = allHistories.stream()
                .collect(Collectors.groupingBy(ProcessHistory::getProcessInstanceId));

        List<DeepLearningDatasetItemResponse> datasetItems = new ArrayList<>();
        LocalDateTime now = LocalDateTime.now();

        // 4. Procesar y calcular features para cada asignación de cada trámite
        for (ProcessInstance instance : instances) {
            List<ProcessAssignment> instanceAssignments = assignmentsByInstance.getOrDefault(instance.getId(), new ArrayList<>());
            List<ProcessHistory> instanceHistories = historiesByInstance.getOrDefault(instance.getId(), new ArrayList<>());

            // Ordenar asignaciones cronológicamente para determinar el stepIndex correcto
            instanceAssignments.sort(Comparator.comparing(a -> a.getCreatedAt() != null ? a.getCreatedAt() : LocalDateTime.MIN));

            // Calcular reworkCount total de la instancia (historial que tenga marcas de retrabajo)
            int reworkCount = (int) instanceHistories.stream()
                    .filter(h -> (h.getTransitionLabel() != null && h.getTransitionLabel().contains("RETRABAJO"))
                            || (h.getComment() != null && h.getComment().toLowerCase().contains("retrabajo"))
                            || (h.getComment() != null && h.getComment().toLowerCase().contains("rework"))
                            || (h.getTemplateResponseData() != null && Boolean.TRUE.equals(h.getTemplateResponseData().get("rework"))))
                    .count();

            double accumulatedDuration = 0.0;

            for (int i = 0; i < instanceAssignments.size(); i++) {
                ProcessAssignment assignment = instanceAssignments.get(i);

                // 1. Duración de la asignación (horas)
                Double assignmentDurationHours = null;
                if (assignment.getStatus() == ProcessAssignmentStatus.COMPLETED && assignment.getCompletedAt() != null) {
                    LocalDateTime start = assignment.getAssignedAt() != null ? assignment.getAssignedAt() : assignment.getCreatedAt();
                    if (start != null) {
                        assignmentDurationHours = Duration.between(start, assignment.getCompletedAt()).toMinutes() / 60.0;
                    }
                }

                // 2. Duración del paso actual (horas)
                Double currentStepDurationHours = null;
                if (assignment.getStatus() == ProcessAssignmentStatus.PENDING) {
                    LocalDateTime start = assignment.getAssignedAt() != null ? assignment.getAssignedAt() : assignment.getCreatedAt();
                    if (start != null) {
                        currentStepDurationHours = Duration.between(start, now).toMinutes() / 60.0;
                    }
                } else {
                    currentStepDurationHours = assignmentDurationHours;
                }

                // 3. Carga activa de departamento y trabajador
                int departmentActiveLoad = deptActiveLoadMap.getOrDefault(assignment.getAssignedDepartmentId(), 0L).intValue();
                
                int workerActiveLoad = 0;
                if (assignment.getAssignedUserId() != null) {
                    User assignee = usersMap.get(assignment.getAssignedUserId());
                    if (assignee != null && assignee.getWorkload() != null) {
                        workerActiveLoad = assignee.getWorkload();
                    } else {
                        workerActiveLoad = userActiveLoadMap.getOrDefault(assignment.getAssignedUserId(), 0L).intValue();
                    }
                }

                // 4. SLA Objetivo
                Double slaHoursTarget = 24.0; // Default
                if (assignment.getAssignedDepartmentId() != null) {
                    Department dept = departmentsMap.get(assignment.getAssignedDepartmentId());
                    if (dept != null && dept.getSlaHours() != null) {
                        slaHoursTarget = dept.getSlaHours().doubleValue();
                    }
                }

                // 5. Conteo de activación de nodo
                int nodeActivationCount = 1;
                if (instance.getNodeActivationCounts() != null && instance.getNodeActivationCounts().containsKey(assignment.getNodeId())) {
                    nodeActivationCount = instance.getNodeActivationCounts().get(assignment.getNodeId());
                }

                // 6. Heurísticas del dataset
                boolean isBottleneck = assignmentDurationHours != null && assignmentDurationHours > slaHoursTarget;
                
                boolean isAnomalous = (assignmentDurationHours != null && assignmentDurationHours > (slaHoursTarget * 2))
                        || "ANOMALO".equals(instance.getRequestData() != null ? instance.getRequestData().get("simulationKind") : null);

                String priorityLabel = "NORMAL";
                if (isAnomalous || reworkCount > 0) {
                    priorityLabel = "HIGH";
                } else if (isBottleneck) {
                    priorityLabel = "MEDIUM";
                }

                String recommendedAction = "CONTINUE";
                if (isAnomalous) {
                    recommendedAction = "ESCALATE";
                } else if (isBottleneck) {
                    if (workerActiveLoad >= 3 || departmentActiveLoad >= 5) {
                        recommendedAction = "REASSIGN";
                    } else {
                        recommendedAction = "MONITOR";
                    }
                }

                datasetItems.add(DeepLearningDatasetItemResponse.builder()
                        .processInstanceId(instance.getId())
                        .assignmentId(assignment.getId())
                        .diagramId(instance.getDiagramId())
                        .diagramName(instance.getDiagramName())
                        .stepIndex(i)
                        .nodeId(assignment.getNodeId())
                        .assignedDepartmentId(assignment.getAssignedDepartmentId())
                        .assignedDepartmentName(assignment.getAssignedDepartmentName())
                        .assignedCargoId(assignment.getAssignedCargoId())
                        .assignedUserId(assignment.getAssignedUserId())
                        .workerActiveLoad(workerActiveLoad)
                        .departmentActiveLoad(departmentActiveLoad)
                        .assignmentDurationHours(assignmentDurationHours)
                        .currentStepDurationHours(currentStepDurationHours)
                        .accumulatedDurationHours(accumulatedDuration)
                        .reworkCount(reworkCount)
                        .slaHoursTarget(slaHoursTarget)
                        .nodeActivationCount(nodeActivationCount)
                        .isBottleneck(isBottleneck)
                        .isAnomalous(isAnomalous)
                        .priorityLabel(priorityLabel)
                        .recommendedAction(recommendedAction)
                        .build());

                // Actualizar acumulador cronológico para el siguiente paso
                if (assignmentDurationHours != null) {
                    accumulatedDuration += assignmentDurationHours;
                }
            }
        }

        // Aplicar límite si es especificado
        List<DeepLearningDatasetItemResponse> finalItems = datasetItems;
        if (limit != null && limit > 0 && limit < datasetItems.size()) {
            finalItems = datasetItems.subList(0, limit);
        }

        return DeepLearningDatasetResponse.builder()
                .totalItems(finalItems.size())
                .generatedAt(LocalDateTime.now())
                .items(finalItems)
                .build();
    }
}
