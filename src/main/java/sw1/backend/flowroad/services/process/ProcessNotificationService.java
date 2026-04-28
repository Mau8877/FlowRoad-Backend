package sw1.backend.flowroad.services.process;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import sw1.backend.flowroad.dtos.process.ProcessAssignmentNotification;
import sw1.backend.flowroad.dtos.process.ProcessInstanceNotification;
import sw1.backend.flowroad.models.notifications.TrackingSubscription;
import sw1.backend.flowroad.models.process.ProcessAssignment;
import sw1.backend.flowroad.models.process.ProcessInstance;
import sw1.backend.flowroad.models.process.ProcessInstance.ProcessInstanceStatus;
import sw1.backend.flowroad.models.user.User;
import sw1.backend.flowroad.repository.notifications.TrackingSubscriptionRepository;
import sw1.backend.flowroad.services.notifications.FirebasePushService;

@Service
@RequiredArgsConstructor
@Slf4j
public class ProcessNotificationService {

    private final SimpMessagingTemplate messagingTemplate;
    private final TrackingSubscriptionRepository trackingSubscriptionRepository;
    private final FirebasePushService firebasePushService;

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

            notifySubscribedMobileDevices(instance, type);
        });
    }

    public void notifyProcessChanged(ProcessInstance instance, String type) {
        notifyProcessInstanceUpdated(instance, type);
    }

    private void notifySubscribedMobileDevices(ProcessInstance instance, String type) {
        if (instance == null || instance.getId() == null || instance.getId().isBlank()) {
            log.warn("[FCM] No se pudo enviar push porque la instancia es inválida.");
            return;
        }

        List<TrackingSubscription> subscriptions = trackingSubscriptionRepository
                .findByProcessInstanceIdAndActiveTrue(instance.getId());

        if (subscriptions.isEmpty()) {
            log.info("[FCM] No hay suscripciones móviles activas para processInstanceId={}", instance.getId());
            return;
        }

        String title = buildPushTitle(instance, type);
        String body = buildPushBody(instance, type);
        Map<String, String> data = buildPushData(instance, type);

        for (TrackingSubscription subscription : subscriptions) {
            firebasePushService.sendToToken(
                    subscription.getDeviceToken(),
                    title,
                    body,
                    data);
        }

        log.info("[FCM] Push procesado para {} suscripciones. processInstanceId={}",
                subscriptions.size(),
                instance.getId());
    }

    private String buildPushTitle(ProcessInstance instance, String type) {
        if ("PROCESS_COMPLETED".equals(type) || instance.getStatus() == ProcessInstanceStatus.COMPLETED) {
            return "Trámite completado";
        }

        if ("PROCESS_CANCELLED".equals(type) || instance.getStatus() == ProcessInstanceStatus.CANCELLED) {
            return "Trámite cancelado";
        }

        if ("PROCESS_PENDING_ASSIGNMENT".equals(type)
                || instance.getStatus() == ProcessInstanceStatus.PENDING_ASSIGNMENT) {
            return "Trámite pendiente de asignación";
        }

        return "Tu trámite avanzó";
    }

    private String buildPushBody(ProcessInstance instance, String type) {
        String code = instance.getCode() != null && !instance.getCode().isBlank()
                ? instance.getCode()
                : "tu trámite";

        String diagramName = instance.getDiagramName() != null && !instance.getDiagramName().isBlank()
                ? instance.getDiagramName()
                : "Proceso";

        if ("PROCESS_COMPLETED".equals(type) || instance.getStatus() == ProcessInstanceStatus.COMPLETED) {
            return "El trámite " + code + " de " + diagramName + " fue completado.";
        }

        if ("PROCESS_CANCELLED".equals(type) || instance.getStatus() == ProcessInstanceStatus.CANCELLED) {
            return "El trámite " + code + " de " + diagramName + " fue cancelado.";
        }

        if ("PROCESS_PENDING_ASSIGNMENT".equals(type)
                || instance.getStatus() == ProcessInstanceStatus.PENDING_ASSIGNMENT) {
            return "El trámite " + code + " de " + diagramName + " está pendiente de asignación.";
        }

        return "El trámite " + code + " de " + diagramName + " tuvo un nuevo avance.";
    }

    private Map<String, String> buildPushData(ProcessInstance instance, String type) {
        Map<String, String> data = new HashMap<>();

        putIfNotBlank(data, "type", type);
        putIfNotBlank(data, "processInstanceId", instance.getId());
        putIfNotBlank(data, "processCode", instance.getCode());
        putIfNotBlank(data, "diagramId", instance.getDiagramId());
        putIfNotBlank(data, "diagramName", instance.getDiagramName());

        if (instance.getStatus() != null) {
            data.put("status", instance.getStatus().name());
        }

        return data;
    }

    private void putIfNotBlank(Map<String, String> data, String key, String value) {
        if (value != null && !value.isBlank()) {
            data.put(key, value);
        }
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