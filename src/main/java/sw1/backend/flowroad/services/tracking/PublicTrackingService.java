package sw1.backend.flowroad.services.tracking;

import java.text.Normalizer;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

import org.springframework.stereotype.Service;

import lombok.RequiredArgsConstructor;
import sw1.backend.flowroad.dtos.tracking.PublicTimelineStepResponse;
import sw1.backend.flowroad.dtos.tracking.PublicTrackingResponse;
import sw1.backend.flowroad.exceptions.ResourceNotFoundException;
import sw1.backend.flowroad.models.diagram.Diagram;
import sw1.backend.flowroad.models.process.ProcessAssignment;
import sw1.backend.flowroad.models.process.ProcessAssignment.ProcessAssignmentStatus;
import sw1.backend.flowroad.models.process.ProcessHistory;
import sw1.backend.flowroad.models.process.ProcessInstance;
import sw1.backend.flowroad.models.process.ProcessInstance.ProcessInstanceStatus;
import sw1.backend.flowroad.repository.diagram.DiagramRepository;
import sw1.backend.flowroad.repository.process.ProcessAssignmentRepository;
import sw1.backend.flowroad.repository.process.ProcessHistoryRepository;
import sw1.backend.flowroad.repository.process.ProcessInstanceRepository;

@Service
@RequiredArgsConstructor
public class PublicTrackingService {

    private final ProcessInstanceRepository processInstanceRepository;
    private final ProcessAssignmentRepository processAssignmentRepository;
    private final ProcessHistoryRepository processHistoryRepository;
    private final DiagramRepository diagramRepository;

    public PublicTrackingResponse getTrackingByCode(String code) {
        String normalizedCode = normalizeTrackingCode(code);

        ProcessInstance instance = processInstanceRepository.findByCode(normalizedCode)
                .orElseThrow(() -> new ResourceNotFoundException("No se encontró un trámite con ese código."));

        Diagram diagram = diagramRepository.findByIdAndOrgId(instance.getDiagramId(), instance.getOrgId())
                .orElseThrow(() -> new ResourceNotFoundException("No se encontró el diagrama del trámite."));

        DiagramRuntime runtime = buildRuntime(diagram);

        List<ProcessAssignment> allAssignments = processAssignmentRepository
                .findByProcessInstanceId(instance.getId());

        List<ProcessAssignment> pendingAssignments = allAssignments.stream()
                .filter(assignment -> assignment.getStatus() == ProcessAssignmentStatus.PENDING)
                .toList();

        List<ProcessHistory> history = processHistoryRepository
                .findByProcessInstanceIdOrderByPerformedAtAsc(instance.getId());

        List<PublicTimelineStepResponse> timeline = buildTimeline(
                instance,
                runtime,
                allAssignments,
                history);

        return new PublicTrackingResponse(
                instance.getCode(),
                instance.getDiagramName(),
                instance.getStatus(),
                resolveStatusLabel(instance.getStatus()),
                resolveCurrentStepName(instance, runtime, pendingAssignments),
                resolveCurrentDepartmentName(pendingAssignments),
                instance.getStartedAt(),
                instance.getUpdatedAt(),
                instance.getFinishedAt(),
                timeline);
    }

    private String normalizeTrackingCode(String code) {
        if (code == null || code.isBlank()) {
            throw new ResourceNotFoundException("Debes ingresar un código de seguimiento.");
        }

        return code.trim().toUpperCase(Locale.ROOT);
    }

    private List<PublicTimelineStepResponse> buildTimeline(
            ProcessInstance instance,
            DiagramRuntime runtime,
            List<ProcessAssignment> assignments,
            List<ProcessHistory> history) {

        Map<String, ProcessAssignment> latestAssignmentByNode = buildLatestAssignmentByNode(assignments);
        Map<String, ProcessHistory> latestHistoryByFromNode = buildLatestHistoryByFromNode(history);

        List<String> orderedNodeIds = orderNodeIds(runtime);
        List<PublicTimelineStepResponse> timeline = new ArrayList<>();

        for (String nodeId : orderedNodeIds) {
            Diagram.DiagramCell node = runtime.nodes().get(nodeId);

            if (node == null) {
                continue;
            }

            String publicType = resolvePublicNodeType(runtime, node);

            if (!shouldDisplayNode(publicType)) {
                continue;
            }

            ProcessAssignment assignment = latestAssignmentByNode.get(nodeId);
            ProcessHistory nodeHistory = latestHistoryByFromNode.get(nodeId);

            String status = resolveStepStatus(instance, nodeId, assignment);
            String label = resolveNodeName(node);
            String laneId = readString(node.getCustomData(), "laneId");
            Diagram.DiagramLane lane = laneId != null ? runtime.lanesById().get(laneId) : null;

            String departmentId = assignment != null && assignment.getAssignedDepartmentId() != null
                    ? assignment.getAssignedDepartmentId()
                    : lane != null ? lane.getDepartmentId() : null;

            String departmentName = assignment != null && assignment.getAssignedDepartmentName() != null
                    ? assignment.getAssignedDepartmentName()
                    : lane != null ? lane.getDepartmentName() : null;

            LocalDateTime startedAt = resolveStepStartedAt(instance, publicType, status, assignment);
            LocalDateTime completedAt = resolveStepCompletedAt(instance, publicType, status, assignment, nodeHistory);

            String comment = nodeHistory != null ? nodeHistory.getComment() : null;

            timeline.add(new PublicTimelineStepResponse(
                    nodeId,
                    label,
                    publicType,
                    status,
                    departmentId,
                    departmentName,
                    startedAt,
                    completedAt,
                    comment));
        }

        return timeline;
    }

    private Map<String, ProcessAssignment> buildLatestAssignmentByNode(List<ProcessAssignment> assignments) {
        Map<String, ProcessAssignment> result = new HashMap<>();

        for (ProcessAssignment assignment : assignments) {
            if (assignment.getNodeId() == null) {
                continue;
            }

            ProcessAssignment current = result.get(assignment.getNodeId());

            if (current == null || isAfter(assignment.getAssignedAt(), current.getAssignedAt())) {
                result.put(assignment.getNodeId(), assignment);
            }
        }

        return result;
    }

    private Map<String, ProcessHistory> buildLatestHistoryByFromNode(List<ProcessHistory> history) {
        Map<String, ProcessHistory> result = new HashMap<>();

        for (ProcessHistory item : history) {
            if (item.getFromNodeId() == null) {
                continue;
            }

            ProcessHistory current = result.get(item.getFromNodeId());

            if (current == null || isAfter(item.getPerformedAt(), current.getPerformedAt())) {
                result.put(item.getFromNodeId(), item);
            }
        }

        return result;
    }

    private boolean isAfter(LocalDateTime candidate, LocalDateTime current) {
        if (candidate == null) {
            return false;
        }

        if (current == null) {
            return true;
        }

        return candidate.isAfter(current);
    }

    private String resolveStepStatus(
            ProcessInstance instance,
            String nodeId,
            ProcessAssignment assignment) {

        List<String> completedNodeIds = safeList(instance.getCompletedNodeIds());
        List<String> activeNodeIds = safeList(instance.getActiveNodeIds());

        if (completedNodeIds.contains(nodeId)) {
            return "COMPLETED";
        }

        if (activeNodeIds.contains(nodeId)) {
            return "CURRENT";
        }

        if (assignment != null && assignment.getStatus() == ProcessAssignmentStatus.PENDING) {
            return "CURRENT";
        }

        if (instance.getStatus() == ProcessInstanceStatus.CANCELLED) {
            return "CANCELLED";
        }

        return "PENDING";
    }

    private LocalDateTime resolveStepStartedAt(
            ProcessInstance instance,
            String publicType,
            String status,
            ProcessAssignment assignment) {

        if ("INITIAL".equals(publicType)) {
            return instance.getStartedAt();
        }

        if (assignment != null && assignment.getAssignedAt() != null) {
            return assignment.getAssignedAt();
        }

        if ("CURRENT".equals(status)) {
            return instance.getUpdatedAt();
        }

        return null;
    }

    private LocalDateTime resolveStepCompletedAt(
            ProcessInstance instance,
            String publicType,
            String status,
            ProcessAssignment assignment,
            ProcessHistory history) {

        if (!"COMPLETED".equals(status)) {
            return null;
        }

        if ("INITIAL".equals(publicType)) {
            return instance.getStartedAt();
        }

        if ("FINAL".equals(publicType)) {
            return instance.getFinishedAt();
        }

        if (assignment != null && assignment.getCompletedAt() != null) {
            return assignment.getCompletedAt();
        }

        if (history != null) {
            return history.getPerformedAt();
        }

        return null;
    }

    private String resolveCurrentStepName(
            ProcessInstance instance,
            DiagramRuntime runtime,
            List<ProcessAssignment> pendingAssignments) {

        if (instance.getStatus() == ProcessInstanceStatus.COMPLETED) {
            return "Trámite finalizado";
        }

        if (instance.getStatus() == ProcessInstanceStatus.CANCELLED) {
            return "Trámite cancelado";
        }

        if (!pendingAssignments.isEmpty()) {
            return pendingAssignments.stream()
                    .map(ProcessAssignment::getNodeName)
                    .filter(Objects::nonNull)
                    .filter(value -> !value.isBlank())
                    .distinct()
                    .collect(Collectors.joining(", "));
        }

        List<String> activeNodeIds = safeList(instance.getActiveNodeIds());

        if (!activeNodeIds.isEmpty()) {
            return activeNodeIds.stream()
                    .map(runtime.nodes()::get)
                    .filter(Objects::nonNull)
                    .map(this::resolveNodeName)
                    .filter(value -> !value.isBlank())
                    .distinct()
                    .collect(Collectors.joining(", "));
        }

        if (instance.getStatus() == ProcessInstanceStatus.PENDING_ASSIGNMENT) {
            return "Pendiente de asignación";
        }

        return "En proceso";
    }

    private String resolveCurrentDepartmentName(List<ProcessAssignment> pendingAssignments) {
        if (pendingAssignments == null || pendingAssignments.isEmpty()) {
            return null;
        }

        return pendingAssignments.stream()
                .map(ProcessAssignment::getAssignedDepartmentName)
                .filter(Objects::nonNull)
                .filter(value -> !value.isBlank())
                .distinct()
                .collect(Collectors.joining(", "));
    }

    private String resolveStatusLabel(ProcessInstanceStatus status) {
        if (status == null) {
            return "Sin estado";
        }

        return switch (status) {
            case RUNNING -> "En curso";
            case PENDING_ASSIGNMENT -> "Pendiente de asignación";
            case COMPLETED -> "Completado";
            case CANCELLED -> "Cancelado";
        };
    }

    private List<String> orderNodeIds(DiagramRuntime runtime) {
        List<Diagram.DiagramCell> sortedNodes = runtime.nodes()
                .values()
                .stream()
                .sorted(nodePositionComparator())
                .toList();

        List<Diagram.DiagramCell> initialNodes = sortedNodes.stream()
                .filter(node -> "INITIAL".equals(resolvePublicNodeType(runtime, node)))
                .toList();

        LinkedHashSet<String> visited = new LinkedHashSet<>();

        for (Diagram.DiagramCell initialNode : initialNodes) {
            visitNode(initialNode.getId(), runtime, visited);
        }

        for (Diagram.DiagramCell node : sortedNodes) {
            visitNode(node.getId(), runtime, visited);
        }

        return new ArrayList<>(visited);
    }

    private void visitNode(
            String nodeId,
            DiagramRuntime runtime,
            LinkedHashSet<String> visited) {

        if (nodeId == null || nodeId.isBlank() || visited.contains(nodeId)) {
            return;
        }

        Diagram.DiagramCell node = runtime.nodes().get(nodeId);

        if (node == null) {
            return;
        }

        visited.add(nodeId);

        List<Diagram.DiagramCell> outgoingLinks = runtime.outgoingByNode()
                .getOrDefault(nodeId, List.of())
                .stream()
                .sorted(Comparator.comparing(this::resolveTransitionLabel))
                .toList();

        for (Diagram.DiagramCell link : outgoingLinks) {
            String targetNodeId = getTargetNodeId(link);
            visitNode(targetNodeId, runtime, visited);
        }
    }

    private Comparator<Diagram.DiagramCell> nodePositionComparator() {
        return Comparator
                .comparingDouble(
                        (Diagram.DiagramCell node) -> node.getPosition() != null ? node.getPosition().getY() : 0)
                .thenComparingDouble(node -> node.getPosition() != null ? node.getPosition().getX() : 0)
                .thenComparing(Diagram.DiagramCell::getId, Comparator.nullsLast(String::compareTo));
    }

    private boolean shouldDisplayNode(String publicType) {
        return Set.of("INITIAL", "ACTION", "DECISION", "FINAL").contains(publicType);
    }

    private String resolvePublicNodeType(DiagramRuntime runtime, Diagram.DiagramCell node) {
        if (node == null) {
            return "ACTION";
        }

        String rawType = normalizeText(readString(node.getCustomData(), "tipo"));

        if (Set.of("INITIAL", "INICIO", "START").contains(rawType)) {
            return "INITIAL";
        }

        if (Set.of("FINAL", "FIN", "END").contains(rawType)) {
            return "FINAL";
        }

        if (Set.of("DECISION", "GATEWAY").contains(rawType)) {
            return "DECISION";
        }

        if (Set.of("ACTION", "ACTIVITY", "ACTIVIDAD", "TASK", "TAREA").contains(rawType)) {
            return "ACTION";
        }

        if (Set.of("FORK", "JOIN", "PARALLEL", "BARRA", "BAR").contains(rawType)) {
            return resolveBarType(runtime, node);
        }

        return "ACTION";
    }

    private String resolveBarType(DiagramRuntime runtime, Diagram.DiagramCell node) {
        String nodeId = node.getId();

        int incomingCount = runtime.incomingByNode().getOrDefault(nodeId, List.of()).size();
        int outgoingCount = runtime.outgoingByNode().getOrDefault(nodeId, List.of()).size();

        if (incomingCount == 1 && outgoingCount >= 2) {
            return "FORK";
        }

        if (incomingCount >= 2 && outgoingCount == 1) {
            return "JOIN";
        }

        if (incomingCount == 1 && outgoingCount == 1) {
            return "PASSTHROUGH";
        }

        return "FORK";
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

    private boolean isLink(Diagram.DiagramCell cell) {
        return cell != null && "standard.Link".equals(cell.getType());
    }

    private String getSourceNodeId(Diagram.DiagramCell link) {
        return link != null && link.getSource() != null ? link.getSource().getId() : null;
    }

    private String getTargetNodeId(Diagram.DiagramCell link) {
        return link != null && link.getTarget() != null ? link.getTarget().getId() : null;
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
        }

        return null;
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

    private String normalizeText(String value) {
        if (value == null) {
            return "";
        }

        String withoutAccents = Normalizer.normalize(value.trim(), Normalizer.Form.NFD)
                .replaceAll("\\p{M}", "");

        return withoutAccents.toUpperCase(Locale.ROOT);
    }

    private List<String> safeList(List<String> values) {
        return values != null ? values : List.of();
    }

    private record DiagramRuntime(
            Map<String, Diagram.DiagramCell> nodes,
            List<Diagram.DiagramCell> links,
            Map<String, List<Diagram.DiagramCell>> outgoingByNode,
            Map<String, List<Diagram.DiagramCell>> incomingByNode,
            Map<String, Diagram.DiagramLane> lanesById) {
    }
}