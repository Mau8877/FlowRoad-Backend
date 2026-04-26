package sw1.backend.flowroad.services.process;

import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import sw1.backend.flowroad.dtos.process.ProcessAssignmentNotification;
import sw1.backend.flowroad.dtos.process.ProcessInstanceNotification;
import sw1.backend.flowroad.models.process.ProcessAssignment;
import sw1.backend.flowroad.models.process.ProcessInstance;
import sw1.backend.flowroad.models.user.User;

@Service
@RequiredArgsConstructor
@Slf4j
public class ProcessNotificationService {

    private final SimpMessagingTemplate messagingTemplate;

    public void notifyAssignmentCreated(
            ProcessInstance instance,
            ProcessAssignment assignment,
            User assignedUser) {

        if (assignedUser == null || assignedUser.getEmail() == null || assignedUser.getEmail().isBlank()) {
            log.warn("[PROCESS-WS] No se pudo notificar asignación {} porque el usuario asignado no tiene email.",
                    assignment.getId());
            return;
        }

        ProcessAssignmentNotification notification = ProcessAssignmentNotification.builder()
                .type("ASSIGNMENT_CREATED")
                .assignmentId(assignment.getId())
                .processInstanceId(instance.getId())
                .processCode(instance.getCode())
                .diagramId(instance.getDiagramId())
                .diagramName(instance.getDiagramName())
                .nodeId(assignment.getNodeId())
                .nodeName(assignment.getNodeName())
                .assignedUserId(assignment.getAssignedUserId())
                .assignedUserName(assignment.getAssignedUserName())
                .assignedDepartmentId(assignment.getAssignedDepartmentId())
                .assignedDepartmentName(assignment.getAssignedDepartmentName())
                .assignedCargoId(assignment.getAssignedCargoId())
                .assignedCargoName(assignment.getAssignedCargoName())
                .templateDocumentId(assignment.getTemplateDocumentId())
                .templateName(assignment.getTemplateName())
                .assignedAt(assignment.getAssignedAt())
                .build();

        sendAfterCommit(() -> {
            messagingTemplate.convertAndSendToUser(
                    assignedUser.getEmail(),
                    "/queue/process-assignments",
                    notification);

            log.info("[PROCESS-WS] Nueva asignación enviada a user={} assignmentId={} processInstanceId={}",
                    assignedUser.getEmail(),
                    assignment.getId(),
                    instance.getId());
        });
    }

    public void notifyProcessInstanceUpdated(ProcessInstance instance, String type) {
        if (instance == null || instance.getOrgId() == null || instance.getOrgId().isBlank()) {
            log.warn("[PROCESS-WS] No se pudo notificar cambio de proceso porque no tiene orgId.");
            return;
        }

        ProcessInstanceNotification notification = ProcessInstanceNotification.builder()
                .type(type)
                .processInstanceId(instance.getId())
                .processCode(instance.getCode())
                .diagramId(instance.getDiagramId())
                .diagramName(instance.getDiagramName())
                .status(instance.getStatus())
                .updatedAt(instance.getUpdatedAt())
                .finishedAt(instance.getFinishedAt())
                .build();

        String destination = "/topic/process-instances/org/" + instance.getOrgId();

        sendAfterCommit(() -> {
            messagingTemplate.convertAndSend(destination, notification);

            log.info("[PROCESS-WS] Cambio global enviado destination={} type={} processInstanceId={} status={}",
                    destination,
                    type,
                    instance.getId(),
                    instance.getStatus());
        });
    }

    public void notifyProcessChanged(ProcessInstance instance, String type) {
        notifyProcessInstanceUpdated(instance, type);
    }

    private void sendAfterCommit(Runnable action) {
        if (TransactionSynchronizationManager.isSynchronizationActive()) {
            TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                @Override
                public void afterCommit() {
                    action.run();
                }
            });
            return;
        }

        action.run();
    }
}
