package sw1.backend.flowroad.services.process;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import lombok.RequiredArgsConstructor;
import sw1.backend.flowroad.dtos.process.AssignmentResponse;
import sw1.backend.flowroad.dtos.process.CompleteAssignmentRequest;
import sw1.backend.flowroad.dtos.process.HistoryFieldResponse;
import sw1.backend.flowroad.dtos.process.HistoryResponse;
import sw1.backend.flowroad.dtos.process.ProcessInstanceDetailResponse;
import sw1.backend.flowroad.dtos.process.ProcessInstanceSummaryResponse;
import sw1.backend.flowroad.exceptions.AuthException;
import sw1.backend.flowroad.exceptions.ResourceNotFoundException;
import sw1.backend.flowroad.models.diagram.Diagram;
import sw1.backend.flowroad.models.process.ProcessAssignment;
import sw1.backend.flowroad.models.process.ProcessAssignment.ProcessAssignmentStatus;
import sw1.backend.flowroad.models.process.ProcessHistory;
import sw1.backend.flowroad.models.process.ProcessInstance;
import sw1.backend.flowroad.models.process.ProcessInstance.ProcessInstanceStatus;
import sw1.backend.flowroad.models.templates.Template;
import sw1.backend.flowroad.models.user.Roles;
import sw1.backend.flowroad.models.user.User;
import sw1.backend.flowroad.repository.diagram.DiagramRepository;
import sw1.backend.flowroad.repository.organization.CargoRepository;
import sw1.backend.flowroad.repository.organization.DepartmentRepository;
import sw1.backend.flowroad.repository.process.ProcessAssignmentRepository;
import sw1.backend.flowroad.repository.process.ProcessHistoryRepository;
import sw1.backend.flowroad.repository.process.ProcessInstanceRepository;
import sw1.backend.flowroad.repository.templates.TemplateRepository;
import sw1.backend.flowroad.repository.user.UserRepository;

@Service
@RequiredArgsConstructor
public class ProcessInstanceService {
    private static final int MAX_NODE_ACTIVATIONS = 5;

    private final ProcessInstanceRepository processInstanceRepository;
    private final ProcessAssignmentRepository processAssignmentRepository;
    private final ProcessHistoryRepository processHistoryRepository;
    private final ProcessNotificationService processNotificationService;
    private final DiagramRepository diagramRepository;
    private final UserRepository userRepository;
    private final DepartmentRepository departmentRepository;
    private final CargoRepository cargoRepository;
    private final TemplateRepository templateRepository;

    @Transactional
    public ProcessInstanceSummaryResponse createProcessInstance(
            String diagramId,
            Map<String, Object> requestData,
            User startedBy) {

        Diagram diagram = diagramRepository.findByIdAndOrgId(diagramId, startedBy.getOrgId())
                .orElseThrow(() -> new ResourceNotFoundException("Diagrama no encontrado para crear la instancia."));

        DiagramRuntime runtime = buildRuntime(diagram);

        Diagram.DiagramCell initialNode = runtime.nodes.values().stream()
                .filter(node -> getNodeType(runtime, node) == NodeType.INITIAL)
                .findFirst()
                .orElseThrow(() -> new AuthException("El diagrama no contiene un nodo INITIAL."));

        List<Diagram.DiagramCell> initialOutgoing = runtime.outgoingByNode.getOrDefault(initialNode.getId(), List.of());

        if (initialOutgoing.isEmpty()) {
            throw new AuthException("El nodo INITIAL no tiene una salida configurada.");
        }

        if (initialOutgoing.size() > 1) {
            throw new AuthException("El nodo INITIAL no puede tener mÃ¡s de una salida en esta versiÃ³n.");
        }

        LocalDateTime now = LocalDateTime.now();

        ProcessInstance instance = ProcessInstance.builder()
                .code(generateProcessCode())
                .orgId(startedBy.getOrgId())
                .diagramId(diagram.getId())
                .diagramName(diagram.getName())
                .diagramVersion(diagram.getVersion())
                .status(ProcessInstanceStatus.RUNNING)
                .activeNodeIds(new ArrayList<>())
                .completedNodeIds(new ArrayList<>(List.of(initialNode.getId())))
                .nodeActivationCounts(new HashMap<>())
                .requestData(requestData != null ? requestData : Map.of())
                .startedByUserId(startedBy.getId())
                .startedByUserName(getUserDisplayName(startedBy))
                .startedAt(now)
                .updatedAt(now)
                .build();

        ProcessInstance saved = processInstanceRepository.save(instance);

        String targetId = getTargetNodeId(initialOutgoing.get(0));
        if (targetId != null) {
            activateOrAdvanceNode(saved, runtime, targetId, initialNode.getId());
        }

        refreshProcessStatus(saved);
        ProcessInstance finalSaved = processInstanceRepository.save(saved);
        processNotificationService.notifyProcessInstanceUpdated(finalSaved, "PROCESS_CREATED");
        return mapSummary(finalSaved);
    }

    public List<ProcessInstanceSummaryResponse> getAllProcessInstances(String orgId) {
        return processInstanceRepository.findAllByOrgIdOrderByStartedAtDesc(orgId)
                .stream()
                .map(this::mapSummary)
                .collect(Collectors.toList());
    }

    public ProcessInstanceDetailResponse getProcessInstanceDetail(
            String processInstanceId,
            String orgId,
            boolean includeDiagram) {

        ProcessInstance instance = processInstanceRepository.findByIdAndOrgId(processInstanceId, orgId)
                .orElseThrow(() -> new ResourceNotFoundException("Instancia de proceso no encontrada."));

        List<AssignmentResponse> activeAssignments = processAssignmentRepository
                .findByProcessInstanceIdAndStatus(instance.getId(), ProcessAssignmentStatus.PENDING)
                .stream()
                .map(this::mapAssignment)
                .collect(Collectors.toList());

        List<HistoryResponse> history = processHistoryRepository
                .findByProcessInstanceIdOrderByPerformedAtAsc(instance.getId())
                .stream()
                .map(this::mapHistory)
                .collect(Collectors.toList());

        Diagram diagram = null;
        if (includeDiagram) {
            diagram = diagramRepository.findByIdAndOrgId(instance.getDiagramId(), orgId)
                    .orElse(null);
        }

        return ProcessInstanceDetailResponse.builder()
                .instance(mapSummary(instance))
                .requestData(instance.getRequestData())
                .activeAssignments(activeAssignments)
                .history(history)
                .diagram(diagram)
                .build();
    }

    @Transactional
    public ProcessInstanceDetailResponse completeAssignment(
            String processInstanceId,
            String assignmentId,
            CompleteAssignmentRequest request,
            User currentUser) {

        CompleteAssignmentRequest safeRequest = request != null
                ? request
                : new CompleteAssignmentRequest(null, null, null, null, null);

        ProcessInstance instance = processInstanceRepository.findByIdAndOrgId(processInstanceId, currentUser.getOrgId())
                .orElseThrow(() -> new ResourceNotFoundException("Instancia de proceso no encontrada."));

        ProcessAssignment assignment = processAssignmentRepository
                .findByIdAndProcessInstanceId(assignmentId, processInstanceId)
                .orElseThrow(() -> new ResourceNotFoundException("AsignaciÃ³n no encontrada."));

        if (assignment.getStatus() != ProcessAssignmentStatus.PENDING) {
            throw new AuthException("La asignaciÃ³n ya no estÃ¡ pendiente.");
        }

        if (!Objects.equals(assignment.getAssignedUserId(), currentUser.getId())) {
            throw new AuthException("No puedes completar una asignaciÃ³n de otro usuario.");
        }

        Diagram diagram = diagramRepository.findByIdAndOrgId(instance.getDiagramId(), currentUser.getOrgId())
                .orElseThrow(() -> new ResourceNotFoundException("Diagrama base de la instancia no encontrado."));

        DiagramRuntime runtime = buildRuntime(diagram);

        Diagram.DiagramCell currentNode = runtime.nodes.get(assignment.getNodeId());
        if (currentNode == null) {
            throw new AuthException("El nodo actual de la asignaciÃ³n no existe en el diagrama.");
        }

        List<Diagram.DiagramCell> outgoingLinks = runtime.outgoingByNode.getOrDefault(currentNode.getId(), List.of());
        if (outgoingLinks.isEmpty()) {
            throw new AuthException("El nodo actual no tiene transiciones salientes.");
        }

        Diagram.DiagramCell selectedTransition = selectTransition(outgoingLinks, safeRequest);
        String targetNodeId = getTargetNodeId(selectedTransition);

        if (targetNodeId == null || !runtime.nodes.containsKey(targetNodeId)) {
            throw new AuthException("La transiciÃ³n elegida no apunta a un nodo vÃ¡lido.");
        }

        Diagram.DiagramCell nextNode = runtime.nodes.get(targetNodeId);

        ProcessHistory history = ProcessHistory.builder()
                .processInstanceId(instance.getId())
                .assignmentId(assignment.getId())
                .fromNodeId(assignment.getNodeId())
                .fromNodeName(assignment.getNodeName())
                .toNodeId(targetNodeId)
                .toNodeName(resolveNodeName(nextNode))
                .transitionLabel(resolveTransitionLabel(selectedTransition))
                .performedByUserId(currentUser.getId())
                .performedByUserName(getUserDisplayName(currentUser))
                .performedAt(LocalDateTime.now())
                .templateDocumentId(assignment.getTemplateDocumentId())
                .templateName(assignment.getTemplateName())
                .templateResponseData(
                        safeRequest.templateResponseData() != null ? safeRequest.templateResponseData() : Map.of())
                .attachments(safeRequest.attachments() != null ? safeRequest.attachments() : List.of())
                .comment(safeRequest.comment())
                .build();

        processHistoryRepository.save(history);

        assignment.setStatus(ProcessAssignmentStatus.COMPLETED);
        assignment.setCompletedAt(LocalDateTime.now());
        processAssignmentRepository.save(assignment);

        decrementUserWorkloadById(assignment.getAssignedUserId());
        markNodeCompleted(instance, assignment.getNodeId());

        activateOrAdvanceNode(instance, runtime, targetNodeId, assignment.getNodeId());
        refreshProcessStatus(instance);

        ProcessInstance savedInstance = processInstanceRepository.save(instance);

        processNotificationService.notifyProcessInstanceUpdated(
                savedInstance,
                resolveProcessNotificationType(savedInstance));

        return getProcessInstanceDetail(savedInstance.getId(), currentUser.getOrgId(), false);
    }

    @Transactional
    public ProcessInstanceSummaryResponse cancelProcessInstance(String processInstanceId, User currentUser) {
        ProcessInstance instance = processInstanceRepository
                .findByIdAndOrgId(processInstanceId, currentUser.getOrgId())
                .orElseThrow(() -> new ResourceNotFoundException("Instancia de proceso no encontrada."));

        if (instance.getStatus() == ProcessInstanceStatus.COMPLETED) {
            throw new AuthException("Un proceso completado no puede cancelarse.");
        }

        if (instance.getStatus() == ProcessInstanceStatus.CANCELLED) {
            throw new AuthException("La instancia de proceso ya fue cancelada.");
        }

        if (instance.getStatus() != ProcessInstanceStatus.RUNNING
                && instance.getStatus() != ProcessInstanceStatus.PENDING_ASSIGNMENT) {
            throw new AuthException("La instancia de proceso no se encuentra en un estado cancelable.");
        }

        ProcessInstance savedInstance = cancelInstanceInternal(instance, LocalDateTime.now());

        return mapSummary(savedInstance);
    }

    private String resolveProcessNotificationType(ProcessInstance instance) {
        if (instance.getStatus() == ProcessInstanceStatus.COMPLETED) {
            return "PROCESS_COMPLETED";
        }

        if (instance.getStatus() == ProcessInstanceStatus.CANCELLED) {
            return "PROCESS_CANCELLED";
        }

        if (instance.getStatus() == ProcessInstanceStatus.PENDING_ASSIGNMENT) {
            return "PROCESS_PENDING_ASSIGNMENT";
        }

        return "PROCESS_UPDATED";
    }

    private void activateOrAdvanceNode(
            ProcessInstance instance,
            DiagramRuntime runtime,
            String nodeId,
            String arrivingSourceNodeId) {

        Diagram.DiagramCell node = runtime.nodes.get(nodeId);
        if (node == null) {
            throw new AuthException("El diagrama contiene una transiciÃ³n hacia un nodo inexistente: " + nodeId);
        }

        NodeType nodeType = getNodeType(runtime, node);

        boolean alreadyHasPendingAssignmentForNode = nodeType == NodeType.TASK
                && hasPendingAssignmentForNode(instance.getId(), nodeId);

        if (!alreadyHasPendingAssignmentForNode) {
            registerNodeActivation(instance, nodeId);
        }

        if (nodeType == NodeType.FINAL) {
            markNodeCompleted(instance, nodeId);

            if (instance.getActiveNodeIds().isEmpty()) {
                instance.setStatus(ProcessInstanceStatus.COMPLETED);
                if (instance.getFinishedAt() == null) {
                    instance.setFinishedAt(LocalDateTime.now());
                }
            }

            instance.setUpdatedAt(LocalDateTime.now());
            return;
        }

        if (nodeType == NodeType.DECISION) {
            markNodeCompleted(instance, nodeId);

            Diagram.DiagramCell selectedTransition = resolveDecisionTransition(instance, runtime, node);
            String targetNodeId = getTargetNodeId(selectedTransition);

            if (targetNodeId == null || !runtime.nodes.containsKey(targetNodeId)) {
                throw new AuthException("La decisiÃ³n apunta a un nodo destino invÃ¡lido.");
            }

            activateOrAdvanceNode(instance, runtime, targetNodeId, nodeId);
            instance.setUpdatedAt(LocalDateTime.now());
            return;
        }

        if (nodeType == NodeType.FORK) {
            markNodeCompleted(instance, nodeId);

            List<Diagram.DiagramCell> outgoing = runtime.outgoingByNode.getOrDefault(nodeId, List.of());
            for (Diagram.DiagramCell link : outgoing) {
                String forkTarget = getTargetNodeId(link);
                if (forkTarget != null) {
                    activateOrAdvanceNode(instance, runtime, forkTarget, nodeId);
                }
            }

            instance.setUpdatedAt(LocalDateTime.now());
            return;
        }

        if (nodeType == NodeType.JOIN) {
            registerJoinArrival(instance, nodeId, arrivingSourceNodeId);
            markNodeCompleted(instance, nodeId);

            if (!isJoinReady(instance, runtime, nodeId)) {
                instance.setUpdatedAt(LocalDateTime.now());
                return;
            }

            clearJoinArrivals(instance, nodeId);

            List<Diagram.DiagramCell> outgoing = runtime.outgoingByNode.getOrDefault(nodeId, List.of());
            if (outgoing.isEmpty()) {
                instance.setUpdatedAt(LocalDateTime.now());
                return;
            }

            if (outgoing.size() > 1) {
                throw new AuthException("JOIN invalido: el nodo '" + nodeId + "' tiene multiples salidas.");
            }

            String joinTarget = getTargetNodeId(outgoing.get(0));
            if (joinTarget != null) {
                activateOrAdvanceNode(instance, runtime, joinTarget, nodeId);
            }

            instance.setUpdatedAt(LocalDateTime.now());
            return;
        }

        if (nodeType == NodeType.PASSTHROUGH) {
            markNodeCompleted(instance, nodeId);

            List<Diagram.DiagramCell> outgoing = runtime.outgoingByNode.getOrDefault(nodeId, List.of());
            if (!outgoing.isEmpty()) {
                String nextTarget = getTargetNodeId(outgoing.get(0));
                if (nextTarget != null) {
                    activateOrAdvanceNode(instance, runtime, nextTarget, nodeId);
                }
            }

            instance.setUpdatedAt(LocalDateTime.now());
            return;
        }

        if (!instance.getActiveNodeIds().contains(nodeId)) {
            instance.getActiveNodeIds().add(nodeId);
        }

        if (!alreadyHasPendingAssignmentForNode) {
            assignNode(instance, node, runtime);
        }

        instance.setUpdatedAt(LocalDateTime.now());
    }

    private void assignNode(
            ProcessInstance instance,
            Diagram.DiagramCell node,
            DiagramRuntime runtime) {

        boolean alreadyHasPendingAssignmentForNode = hasPendingAssignmentForNode(instance.getId(), node.getId());

        if (alreadyHasPendingAssignmentForNode) {
            return;
        }

        String laneId = readString(node.getCustomData(), "laneId");
        Diagram.DiagramLane lane = laneId != null ? runtime.lanesById.get(laneId) : null;

        String departmentId = lane != null ? lane.getDepartmentId() : null;
        String departmentName = lane != null ? lane.getDepartmentName() : null;
        String requiredCargoId = readString(node.getCustomData(), "requiredCargoId");

        if (departmentId == null || departmentId.isBlank()) {
            instance.setStatus(ProcessInstanceStatus.PENDING_ASSIGNMENT);
            instance.setUpdatedAt(LocalDateTime.now());
            return;
        }

        List<User> candidates = findCandidates(instance.getOrgId(), departmentId, requiredCargoId);
        if (candidates.isEmpty()) {
            instance.setStatus(ProcessInstanceStatus.PENDING_ASSIGNMENT);
            instance.setUpdatedAt(LocalDateTime.now());
            return;
        }

        User selected = selectByWorkloadAndRoundRobin(candidates, departmentId, requiredCargoId);
        if (selected == null) {
            instance.setStatus(ProcessInstanceStatus.PENDING_ASSIGNMENT);
            instance.setUpdatedAt(LocalDateTime.now());
            return;
        }

        selected.setWorkload(safeWorkload(selected) + 1);
        userRepository.save(selected);

        String templateDocumentId = readString(node.getCustomData(), "templateDocumentId");
        String templateName = resolveTemplateName(instance.getOrgId(), templateDocumentId);

        ProcessAssignment assignment = ProcessAssignment.builder()
                .processInstanceId(instance.getId())
                .nodeId(node.getId())
                .nodeName(resolveNodeName(node))
                .laneId(laneId)
                .laneName(departmentName)
                .assignedDepartmentId(departmentId)
                .assignedDepartmentName(resolveDepartmentName(departmentId, departmentName))
                .assignedCargoId(requiredCargoId)
                .assignedCargoName(resolveCargoName(requiredCargoId))
                .assignedUserId(selected.getId())
                .assignedUserName(getUserDisplayName(selected))
                .templateDocumentId(templateDocumentId)
                .templateName(templateName)
                .status(ProcessAssignmentStatus.PENDING)
                .createdAt(LocalDateTime.now())
                .assignedAt(LocalDateTime.now())
                .build();

        ProcessAssignment savedAssignment = processAssignmentRepository.save(assignment);

        processNotificationService.notifyAssignmentCreated(
                instance,
                savedAssignment,
                selected);

        instance.setStatus(ProcessInstanceStatus.RUNNING);
        instance.setUpdatedAt(LocalDateTime.now());
    }

    private boolean hasPendingAssignmentForNode(String processInstanceId, String nodeId) {
        return processAssignmentRepository
                .findByProcessInstanceIdAndStatus(processInstanceId, ProcessAssignmentStatus.PENDING)
                .stream()
                .anyMatch(a -> nodeId.equals(a.getNodeId()));
    }

    private void registerNodeActivation(ProcessInstance instance, String nodeId) {
        if (instance == null || nodeId == null || nodeId.isBlank()) {
            return;
        }

        if (instance.getNodeActivationCounts() == null) {
            instance.setNodeActivationCounts(new HashMap<>());
        }

        int currentCount = instance.getNodeActivationCounts().getOrDefault(nodeId, 0);
        if (currentCount >= MAX_NODE_ACTIVATIONS) {
            cancelInstanceInternal(instance, LocalDateTime.now());
            throw new AuthException(
                    "El proceso fue cancelado automÃ¡ticamente por posible bucle infinito en el nodo: " + nodeId);
        }

        instance.getNodeActivationCounts().put(nodeId, currentCount + 1);
    }

    private void registerJoinArrival(ProcessInstance instance, String joinNodeId, String sourceNodeId) {
        if (instance == null || joinNodeId == null || joinNodeId.isBlank() || sourceNodeId == null
                || sourceNodeId.isBlank()) {
            return;
        }

        if (instance.getJoinArrivals() == null) {
            instance.setJoinArrivals(new HashMap<>());
        }

        List<String> arrivals = instance.getJoinArrivals().computeIfAbsent(joinNodeId, key -> new ArrayList<>());
        if (!arrivals.contains(sourceNodeId)) {
            arrivals.add(sourceNodeId);
        }
    }

    private boolean isJoinReady(ProcessInstance instance, DiagramRuntime runtime, String joinNodeId) {
        List<Diagram.DiagramCell> incoming = runtime.incomingByNode.getOrDefault(joinNodeId, List.of());
        Set<String> expectedSources = incoming.stream()
                .map(this::getSourceNodeId)
                .filter(Objects::nonNull)
                .collect(Collectors.toSet());

        if (expectedSources.isEmpty()) {
            return true;
        }

        Map<String, List<String>> joinArrivals = instance.getJoinArrivals();
        if (joinArrivals == null) {
            return false;
        }

        List<String> arrivals = joinArrivals.getOrDefault(joinNodeId, List.of());
        return arrivals.containsAll(expectedSources);
    }

    private void clearJoinArrivals(ProcessInstance instance, String joinNodeId) {
        if (instance.getJoinArrivals() == null) {
            return;
        }

        instance.getJoinArrivals().remove(joinNodeId);
    }

    private Diagram.DiagramCell resolveDecisionTransition(
            ProcessInstance instance,
            DiagramRuntime runtime,
            Diagram.DiagramCell decisionNode) {

        List<Diagram.DiagramCell> outgoingLinks = runtime.outgoingByNode
                .getOrDefault(decisionNode.getId(), List.of());

        if (outgoingLinks.isEmpty()) {
            throw new AuthException("El nodo de decisiÃ³n no tiene transiciones salientes.");
        }

        if (outgoingLinks.size() == 1) {
            return outgoingLinks.get(0);
        }

        String decisionFieldId = readString(decisionNode.getCustomData(), "decisionFieldId");

        Set<String> decisionValues = resolveLatestDecisionValues(
                instance.getId(),
                decisionFieldId);

        if (decisionValues.isEmpty()) {
            throw new AuthException(
                    "No se pudo resolver la decisiÃ³n porque el Ãºltimo informe no contiene una respuesta compatible.");
        }

        List<Diagram.DiagramCell> matches = outgoingLinks.stream()
                .filter(link -> {
                    String linkLabel = canonicalDecisionValue(resolveTransitionLabel(link));
                    return decisionValues.contains(linkLabel);
                })
                .toList();

        if (matches.size() == 1) {
            return matches.get(0);
        }

        if (matches.size() > 1) {
            throw new AuthException("La decisiÃ³n es ambigua: mÃ¡s de una transiciÃ³n coincide con la respuesta.");
        }

        throw new AuthException(
                "No se encontrÃ³ una transiciÃ³n de decisiÃ³n compatible con la respuesta registrada. "
                        + "Verifica que la respuesta coincida con los labels del diagrama, por ejemplo Si o No.");
    }

    private Set<String> resolveLatestDecisionValues(String processInstanceId, String decisionFieldId) {
        List<ProcessHistory> history = processHistoryRepository
                .findByProcessInstanceIdOrderByPerformedAtAsc(processInstanceId);

        if (history.isEmpty()) {
            return Set.of();
        }

        ProcessHistory latestHistory = history.get(history.size() - 1);
        Map<String, Object> responseData = latestHistory.getTemplateResponseData();

        if (responseData == null || responseData.isEmpty()) {
            return Set.of();
        }

        Set<String> values = new HashSet<>();

        if (decisionFieldId != null && !decisionFieldId.isBlank()) {
            Object specificValue = responseData.get(decisionFieldId);
            collectDecisionValues(specificValue, values);
            return values;
        }

        collectDecisionValues(responseData, values);
        return values;
    }

    private void collectDecisionValues(Object value, Set<String> values) {
        if (value == null) {
            return;
        }

        if (value instanceof Boolean boolValue) {
            values.add(boolValue ? "SI" : "NO");
            return;
        }

        if (value instanceof String stringValue) {
            String normalized = canonicalDecisionValue(stringValue);

            if (!normalized.isBlank()) {
                values.add(normalized);
            }

            return;
        }

        if (value instanceof Number numberValue) {
            values.add(canonicalDecisionValue(numberValue.toString()));
            return;
        }

        if (value instanceof List<?> listValue) {
            for (Object item : listValue) {
                collectDecisionValues(item, values);
            }

            return;
        }

        if (value instanceof Map<?, ?> mapValue) {
            for (Object item : mapValue.values()) {
                collectDecisionValues(item, values);
            }
        }
    }

    private String canonicalDecisionValue(String value) {
        if (value == null) {
            return "";
        }

        String normalized = value
                .trim()
                .toUpperCase(Locale.ROOT)
                .replace("Ã", "A")
                .replace("Ã‰", "E")
                .replace("Ã", "I")
                .replace("Ã“", "O")
                .replace("Ãš", "U");

        return switch (normalized) {
            case "SI", "SÃ", "YES", "TRUE", "VERDADERO", "APROBADO", "ACEPTADO", "ACEPTA" -> "SI";
            case "NO", "FALSE", "FALSO", "RECHAZADO", "NO ACEPTADO", "NO ACEPTA" -> "NO";
            default -> normalized;
        };
    }

    private List<User> findCandidates(String orgId, String departmentId, String requiredCargoId) {
        List<Roles> assignableRoles = List.of(Roles.WORKER, Roles.RECEP);

        if (requiredCargoId != null && !requiredCargoId.isBlank()) {
            return userRepository.findByOrgIdAndDepartmentIdAndCargoIdAndRoleInAndIsActiveTrue(
                    orgId,
                    departmentId,
                    requiredCargoId,
                    assignableRoles);
        }

        return userRepository.findByOrgIdAndDepartmentIdAndRoleInAndIsActiveTrue(
                orgId,
                departmentId,
                assignableRoles);
    }

    private User selectByWorkloadAndRoundRobin(List<User> candidates, String departmentId, String cargoId) {
        if (candidates.isEmpty()) {
            return null;
        }

        int minWorkload = candidates.stream()
                .map(this::safeWorkload)
                .min(Integer::compareTo)
                .orElse(0);

        List<User> tied = candidates.stream()
                .filter(user -> safeWorkload(user) == minWorkload)
                .collect(Collectors.toList());

        if (tied.size() == 1) {
            return tied.get(0);
        }

        Map<String, LocalDateTime> latestByUser = getLatestAssignmentByUser(tied, departmentId, cargoId);

        return tied.stream()
                .sorted(Comparator
                        .comparing((User u) -> latestByUser.getOrDefault(u.getId(), LocalDateTime.MIN))
                        .thenComparing(User::getId))
                .findFirst()
                .orElse(tied.get(0));
    }

    private Map<String, LocalDateTime> getLatestAssignmentByUser(
            List<User> tiedUsers,
            String departmentId,
            String cargoId) {

        List<String> userIds = tiedUsers.stream().map(User::getId).toList();
        List<ProcessAssignment> history;

        if (cargoId != null && !cargoId.isBlank()) {
            history = processAssignmentRepository
                    .findByAssignedDepartmentIdAndAssignedCargoIdAndAssignedUserIdInOrderByAssignedAtDesc(
                            departmentId, cargoId, userIds);
        } else {
            history = processAssignmentRepository
                    .findByAssignedDepartmentIdAndAssignedUserIdInOrderByAssignedAtDesc(
                            departmentId, userIds);
        }

        Map<String, LocalDateTime> latestByUser = new HashMap<>();
        for (ProcessAssignment assignment : history) {
            if (!latestByUser.containsKey(assignment.getAssignedUserId())) {
                latestByUser.put(assignment.getAssignedUserId(), assignment.getAssignedAt());
            }
        }

        return latestByUser;
    }

    private void decrementUserWorkloadById(String userId) {
        if (userId == null || userId.isBlank()) {
            return;
        }

        userRepository.findById(userId).ifPresent(user -> {
            int current = safeWorkload(user);
            user.setWorkload(Math.max(0, current - 1));
            userRepository.save(user);
        });
    }

    private ProcessInstance cancelInstanceInternal(ProcessInstance instance, LocalDateTime now) {
        List<ProcessAssignment> pendingAssignments = processAssignmentRepository
                .findByProcessInstanceIdAndStatus(instance.getId(), ProcessAssignmentStatus.PENDING);

        for (ProcessAssignment assignment : pendingAssignments) {
            assignment.setStatus(ProcessAssignmentStatus.CANCELLED);

            if (assignment.getCompletedAt() == null) {
                assignment.setCompletedAt(now);
            }

            decrementUserWorkloadById(assignment.getAssignedUserId());
        }

        if (!pendingAssignments.isEmpty()) {
            processAssignmentRepository.saveAll(pendingAssignments);
        }

        instance.setStatus(ProcessInstanceStatus.CANCELLED);
        instance.setActiveNodeIds(new ArrayList<>());
        instance.setUpdatedAt(now);
        instance.setFinishedAt(now);

        ProcessInstance savedInstance = processInstanceRepository.save(instance);
        processNotificationService.notifyProcessInstanceUpdated(savedInstance, "PROCESS_CANCELLED");
        return savedInstance;
    }

    private void refreshProcessStatus(ProcessInstance instance) {
        if (instance.getStatus() == ProcessInstanceStatus.CANCELLED
                || instance.getStatus() == ProcessInstanceStatus.COMPLETED) {
            instance.setUpdatedAt(LocalDateTime.now());
            return;
        }

        long pendingAssignments = processAssignmentRepository
                .countByProcessInstanceIdAndStatus(instance.getId(), ProcessAssignmentStatus.PENDING);

        if (instance.getActiveNodeIds().isEmpty() && pendingAssignments == 0) {
            instance.setStatus(ProcessInstanceStatus.COMPLETED);
            if (instance.getFinishedAt() == null) {
                instance.setFinishedAt(LocalDateTime.now());
            }
        } else if (pendingAssignments > 0) {
            instance.setStatus(ProcessInstanceStatus.RUNNING);
            instance.setFinishedAt(null);
        } else {
            instance.setStatus(ProcessInstanceStatus.PENDING_ASSIGNMENT);
            instance.setFinishedAt(null);
        }

        instance.setUpdatedAt(LocalDateTime.now());
    }

    private void markNodeCompleted(ProcessInstance instance, String nodeId) {
        if (nodeId == null || nodeId.isBlank()) {
            return;
        }

        if (!instance.getCompletedNodeIds().contains(nodeId)) {
            instance.getCompletedNodeIds().add(nodeId);
        }

        instance.getActiveNodeIds().removeIf(nodeId::equals);
    }

    private DiagramRuntime buildRuntime(Diagram diagram) {
        Map<String, Diagram.DiagramCell> nodes = new LinkedHashMap<>();
        List<Diagram.DiagramCell> links = new ArrayList<>();
        Map<String, List<Diagram.DiagramCell>> outgoing = new HashMap<>();
        Map<String, List<Diagram.DiagramCell>> incoming = new HashMap<>();

        for (Diagram.DiagramCell cell : diagram.getCells() != null
                ? diagram.getCells()
                : List.<Diagram.DiagramCell>of()) {

            if (isLink(cell)) {
                links.add(cell);

                String sourceId = getSourceNodeId(cell);
                String targetId = getTargetNodeId(cell);

                if (sourceId != null && !sourceId.isBlank()) {
                    outgoing.computeIfAbsent(sourceId, key -> new ArrayList<>()).add(cell);
                }

                if (targetId != null && !targetId.isBlank()) {
                    incoming.computeIfAbsent(targetId, key -> new ArrayList<>()).add(cell);
                }
            } else if (cell.getId() != null && !cell.getId().isBlank()) {
                nodes.put(cell.getId(), cell);
            }
        }

        Map<String, Diagram.DiagramLane> lanesById = new HashMap<>();
        for (Diagram.DiagramLane lane : diagram.getLanes() != null
                ? diagram.getLanes()
                : List.<Diagram.DiagramLane>of()) {
            if (lane.getId() != null) {
                lanesById.put(lane.getId(), lane);
            }
        }

        return new DiagramRuntime(nodes, links, outgoing, incoming, lanesById);
    }

    private Diagram.DiagramCell selectTransition(
            List<Diagram.DiagramCell> outgoingLinks,
            CompleteAssignmentRequest request) {

        if (request != null && request.targetNodeId() != null && !request.targetNodeId().isBlank()) {
            return outgoingLinks.stream()
                    .filter(link -> request.targetNodeId().equals(getTargetNodeId(link)))
                    .findFirst()
                    .orElseThrow(() -> new AuthException("El targetNodeId no es una transiciÃ³n vÃ¡lida."));
        }

        if (request != null && request.transitionLabel() != null && !request.transitionLabel().isBlank()) {
            String expected = normalizeLabel(request.transitionLabel());

            return outgoingLinks.stream()
                    .filter(link -> normalizeLabel(resolveTransitionLabel(link)).equals(expected))
                    .findFirst()
                    .orElseThrow(() -> new AuthException("La transiciÃ³n indicada no existe en el flujo."));
        }

        if (outgoingLinks.size() == 1) {
            return outgoingLinks.get(0);
        }

        throw new AuthException("Debes indicar transitionLabel o targetNodeId para avanzar este nodo.");
    }

    private String resolveTransitionLabel(Diagram.DiagramCell link) {
        if (link == null) {
            return "";
        }

        String fromCustomData = readString(link.getCustomData(), "linkLabel");
        if (fromCustomData != null && !fromCustomData.isBlank()) {
            return fromCustomData.trim();
        }

        if (link.getLabels() == null || link.getLabels().isEmpty()) {
            return "";
        }

        for (Map<String, Object> label : link.getLabels()) {
            String text = deepFindText(label);
            if (text != null && !text.isBlank()) {
                return text.trim();
            }
        }

        return "";
    }

    private String deepFindText(Object value) {
        if (value == null) {
            return null;
        }

        if (value instanceof String str) {
            return str;
        }

        if (value instanceof Map<?, ?> map) {
            Object direct = map.get("text");
            String directText = deepFindText(direct);
            if (directText != null && !directText.isBlank()) {
                return directText;
            }

            for (Object entryValue : map.values()) {
                String nested = deepFindText(entryValue);
                if (nested != null && !nested.isBlank()) {
                    return nested;
                }
            }

            return null;
        }

        if (value instanceof List<?> list) {
            for (Object item : list) {
                String nested = deepFindText(item);
                if (nested != null && !nested.isBlank()) {
                    return nested;
                }
            }

            return null;
        }

        return null;
    }

    private String resolveNodeName(Diagram.DiagramCell node) {
        if (node == null) {
            return "Nodo";
        }

        String fromCustomNombre = readString(node.getCustomData(), "nombre");
        if (fromCustomNombre != null && !fromCustomNombre.isBlank()) {
            return fromCustomNombre;
        }

        String fromCustomName = readString(node.getCustomData(), "name");
        if (fromCustomName != null && !fromCustomName.isBlank()) {
            return fromCustomName;
        }

        String fromCustomLabel = readString(node.getCustomData(), "label");
        if (fromCustomLabel != null && !fromCustomLabel.isBlank()) {
            return fromCustomLabel;
        }

        String fromAttrs = deepFindText(node.getAttrs());
        if (fromAttrs != null && !fromAttrs.isBlank()) {
            return fromAttrs.trim();
        }

        return node.getId();
    }

    private String readString(Map<String, Object> map, String key) {
        if (map == null || key == null) {
            return null;
        }

        Object value = map.get(key);
        if (value == null) {
            return null;
        }

        return value.toString();
    }

    private NodeType getNodeType(DiagramRuntime runtime, Diagram.DiagramCell node) {
        if (node == null) {
            return NodeType.TASK;
        }

        String tipo = normalizeLabel(readString(node.getCustomData(), "tipo"));

        NodeType directType = switch (tipo) {
            case "INITIAL", "INICIO", "START" -> NodeType.INITIAL;
            case "FINAL", "FIN", "END" -> NodeType.FINAL;
            case "DECISION", "DECISIÃ“N", "GATEWAY" -> NodeType.DECISION;
            case "FORK", "PARALLEL", "JOIN" -> NodeType.BAR;
            default -> NodeType.TASK;
        };

        if (directType != NodeType.BAR) {
            return directType;
        }

        String nodeId = node.getId();
        int incomingCount = runtime.incomingByNode.getOrDefault(nodeId, List.of()).size();
        int outgoingCount = runtime.outgoingByNode.getOrDefault(nodeId, List.of()).size();

        if (incomingCount == 1 && outgoingCount >= 2) {
            return NodeType.FORK;
        }

        if (incomingCount >= 2 && outgoingCount == 1) {
            return NodeType.JOIN;
        }

        if (incomingCount == 1 && outgoingCount == 1) {
            return NodeType.PASSTHROUGH;
        }

        if (incomingCount >= 2 && outgoingCount >= 2) {
            throw new AuthException(
                    "Barra de control ambigua en nodo '" + nodeId + "': tiene multiples entradas y salidas.");
        }

        throw new AuthException(
                "Barra de control invalida en nodo '" + nodeId + "': se esperaban conexiones de FORK o JOIN.");
    }

    private boolean isLink(Diagram.DiagramCell cell) {
        return cell != null && "standard.Link".equals(cell.getType());
    }

    private String getSourceNodeId(Diagram.DiagramCell link) {
        return link != null && link.getSource() != null ? link.getSource().getId() : null;
    }

    private String getTargetNodeId(Diagram.DiagramCell link) {
        return link != null && link.getTarget() != null ? link.getTarget().getId() : null;
    }

    private String resolveTemplateName(String orgId, String templateDocumentId) {
        if (templateDocumentId == null || templateDocumentId.isBlank()) {
            return null;
        }

        return templateRepository.findByIdAndOrgId(templateDocumentId, orgId)
                .map(Template::getName)
                .orElse(null);
    }

    private String resolveDepartmentName(String departmentId, String fallback) {
        if (departmentId == null || departmentId.isBlank()) {
            return fallback;
        }

        return departmentRepository.findById(departmentId)
                .map(dep -> dep.getName())
                .orElse(fallback);
    }

    private String resolveCargoName(String cargoId) {
        if (cargoId == null || cargoId.isBlank()) {
            return null;
        }

        return cargoRepository.findById(cargoId)
                .map(cargo -> cargo.getName())
                .orElse(null);
    }

    private String normalizeLabel(String value) {
        return value == null ? "" : value.trim().toUpperCase(Locale.ROOT);
    }

    private int safeWorkload(User user) {
        return user.getWorkload() == null ? 0 : user.getWorkload();
    }

    private String getUserDisplayName(User user) {
        if (user == null) {
            return "Usuario";
        }

        if (user.getProfile() != null) {
            String nombre = Optional.ofNullable(user.getProfile().getNombre()).orElse("");
            String apellido = Optional.ofNullable(user.getProfile().getApellido()).orElse("");
            String fullName = (nombre + " " + apellido).trim();

            if (!fullName.isBlank()) {
                return fullName;
            }
        }

        return user.getEmail() != null ? user.getEmail() : user.getId();
    }

    private String generateProcessCode() {
        String datePart = LocalDate.now().format(DateTimeFormatter.ofPattern("yyyyMMdd"));
        String shortRandom = UUID.randomUUID().toString().substring(0, 6).toUpperCase(Locale.ROOT);
        return "PROC-" + datePart + "-" + shortRandom;
    }

    private ProcessInstanceSummaryResponse mapSummary(ProcessInstance instance) {
        return ProcessInstanceSummaryResponse.builder()
                .id(instance.getId())
                .code(instance.getCode())
                .diagramId(instance.getDiagramId())
                .diagramName(instance.getDiagramName())
                .diagramVersion(instance.getDiagramVersion())
                .status(instance.getStatus())
                .activeNodeIds(new ArrayList<>(
                        instance.getActiveNodeIds() != null ? instance.getActiveNodeIds() : List.of()))
                .completedNodeIds(new ArrayList<>(
                        instance.getCompletedNodeIds() != null ? instance.getCompletedNodeIds() : List.of()))
                .startedByUserId(instance.getStartedByUserId())
                .startedByUserName(instance.getStartedByUserName())
                .startedAt(instance.getStartedAt())
                .updatedAt(instance.getUpdatedAt())
                .finishedAt(instance.getFinishedAt())
                .build();
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

    private HistoryResponse mapHistory(ProcessHistory history) {
        return HistoryResponse.builder()
                .id(history.getId())
                .processInstanceId(history.getProcessInstanceId())
                .assignmentId(history.getAssignmentId())
                .fromNodeId(history.getFromNodeId())
                .fromNodeName(history.getFromNodeName())
                .toNodeId(history.getToNodeId())
                .toNodeName(history.getToNodeName())
                .transitionLabel(history.getTransitionLabel())
                .performedByUserId(history.getPerformedByUserId())
                .performedByUserName(history.getPerformedByUserName())
                .performedAt(history.getPerformedAt())
                .templateDocumentId(history.getTemplateDocumentId())
                .templateName(history.getTemplateName())
                .templateResponseData(history.getTemplateResponseData())
                .templateResponseFields(buildHistoryFieldResponses(history))
                .attachments(history.getAttachments())
                .comment(history.getComment())
                .build();
    }

    private List<HistoryFieldResponse> buildHistoryFieldResponses(ProcessHistory history) {
        Map<String, Object> responseData = history.getTemplateResponseData();

        if (responseData == null || responseData.isEmpty()) {
            return List.of();
        }

        Map<String, String> fieldLabels = resolveTemplateFieldLabels(
                history.getTemplateDocumentId(),
                history.getProcessInstanceId());

        return responseData.entrySet()
                .stream()
                .map(entry -> HistoryFieldResponse.builder()
                        .fieldId(entry.getKey())
                        .label(fieldLabels.getOrDefault(entry.getKey(), entry.getKey()))
                        .value(entry.getValue())
                        .build())
                .collect(Collectors.toList());
    }

    private Map<String, String> resolveTemplateFieldLabels(
            String templateDocumentId,
            String processInstanceId) {

        if (templateDocumentId == null || templateDocumentId.isBlank()) {
            return Map.of();
        }

        ProcessInstance instance = processInstanceRepository.findById(processInstanceId)
                .orElse(null);

        if (instance == null || instance.getOrgId() == null) {
            return Map.of();
        }

        return templateRepository.findByIdAndOrgId(templateDocumentId, instance.getOrgId())
                .map(template -> {
                    Map<String, String> labels = new HashMap<>();

                    if (template.getFields() != null) {
                        template.getFields().forEach(field -> {
                            if (field.getFieldId() != null && field.getLabel() != null) {
                                labels.put(field.getFieldId(), field.getLabel());
                            }
                        });
                    }

                    return labels;
                })
                .orElse(Map.of());
    }

    private enum NodeType {
        INITIAL,
        FINAL,
        DECISION,
        BAR,
        FORK,
        JOIN,
        PASSTHROUGH,
        TASK
    }

    private record DiagramRuntime(
            Map<String, Diagram.DiagramCell> nodes,
            List<Diagram.DiagramCell> links,
            Map<String, List<Diagram.DiagramCell>> outgoingByNode,
            Map<String, List<Diagram.DiagramCell>> incomingByNode,
            Map<String, Diagram.DiagramLane> lanesById) {
    }
}
