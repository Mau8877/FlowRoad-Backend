package sw1.backend.flowroad.services.process;

import java.util.List;
import java.util.Objects;
import java.util.stream.Collectors;

import org.springframework.stereotype.Service;

import lombok.RequiredArgsConstructor;
import sw1.backend.flowroad.dtos.process.AssignmentResponse;
import sw1.backend.flowroad.exceptions.AuthException;
import sw1.backend.flowroad.exceptions.ResourceNotFoundException;
import sw1.backend.flowroad.models.process.ProcessAssignment;
import sw1.backend.flowroad.models.process.ProcessAssignment.ProcessAssignmentStatus;
import sw1.backend.flowroad.models.process.ProcessInstance;
import sw1.backend.flowroad.models.user.Roles;
import sw1.backend.flowroad.models.user.User;
import sw1.backend.flowroad.repository.process.ProcessAssignmentRepository;
import sw1.backend.flowroad.repository.process.ProcessInstanceRepository;

@Service
@RequiredArgsConstructor
public class ProcessAssignmentService {

    private final ProcessAssignmentRepository processAssignmentRepository;
    private final ProcessInstanceRepository processInstanceRepository;

    public List<AssignmentResponse> getMyAssignments(
            User currentUser,
            ProcessAssignmentStatus status) {

        List<ProcessAssignment> assignments;

        if (status != null) {
            assignments = processAssignmentRepository
                    .findByAssignedUserIdAndStatusOrderByAssignedAtDesc(
                            currentUser.getId(),
                            status);
        } else {
            assignments = processAssignmentRepository
                    .findByAssignedUserIdOrderByAssignedAtDesc(
                            currentUser.getId());
        }

        return assignments.stream()
                .map(this::mapAssignment)
                .collect(Collectors.toList());
    }

    public AssignmentResponse getAssignmentById(String assignmentId, User currentUser) {
        ProcessAssignment assignment = processAssignmentRepository.findById(assignmentId)
                .orElseThrow(() -> new ResourceNotFoundException("Asignación no encontrada."));

        ProcessInstance instance = processInstanceRepository.findById(assignment.getProcessInstanceId())
                .orElseThrow(() -> new ResourceNotFoundException("Instancia de proceso no encontrada."));

        if (!Objects.equals(instance.getOrgId(), currentUser.getOrgId())) {
            throw new AuthException("No tienes acceso a esta asignación.");
        }

        boolean isAdminOrDesigner = currentUser.getRole() == Roles.ADMIN
                || currentUser.getRole() == Roles.DESIGNER;

        if (!isAdminOrDesigner && !Objects.equals(assignment.getAssignedUserId(), currentUser.getId())) {
            throw new AuthException("No puedes acceder a una asignación de otro usuario.");
        }

        return mapAssignment(assignment);
    }

    private AssignmentResponse mapAssignment(ProcessAssignment assignment) {
        return AssignmentResponse.builder()
                .id(assignment.getId())
                .processInstanceId(assignment.getProcessInstanceId())
                .nodeId(assignment.getNodeId())
                .nodeName(assignment.getNodeName())
                .laneId(assignment.getLaneId())
                .laneName(assignment.getLaneName())
                .assignedDepartmentId(assignment.getAssignedDepartmentId())
                .assignedDepartmentName(assignment.getAssignedDepartmentName())
                .assignedCargoId(assignment.getAssignedCargoId())
                .assignedCargoName(assignment.getAssignedCargoName())
                .assignedUserId(assignment.getAssignedUserId())
                .assignedUserName(assignment.getAssignedUserName())
                .templateDocumentId(assignment.getTemplateDocumentId())
                .templateName(assignment.getTemplateName())
                .status(assignment.getStatus())
                .createdAt(assignment.getCreatedAt())
                .assignedAt(assignment.getAssignedAt())
                .completedAt(assignment.getCompletedAt())
                .build();
    }
}