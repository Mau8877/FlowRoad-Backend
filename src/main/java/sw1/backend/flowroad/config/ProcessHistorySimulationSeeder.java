package sw1.backend.flowroad.config;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.ApplicationContext;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import lombok.RequiredArgsConstructor;
import sw1.backend.flowroad.models.diagram.Diagram;
import sw1.backend.flowroad.models.organization.Cargo;
import sw1.backend.flowroad.models.organization.Department;
import sw1.backend.flowroad.models.organization.Organization;
import sw1.backend.flowroad.models.process.ProcessAssignment;
import sw1.backend.flowroad.models.process.ProcessAssignment.ProcessAssignmentStatus;
import sw1.backend.flowroad.models.process.ProcessHistory;
import sw1.backend.flowroad.models.process.ProcessInstance;
import sw1.backend.flowroad.models.process.ProcessInstance.ProcessInstanceStatus;
import sw1.backend.flowroad.models.user.Roles;
import sw1.backend.flowroad.models.user.User;
import sw1.backend.flowroad.repository.diagram.DiagramRepository;
import sw1.backend.flowroad.repository.organization.CargoRepository;
import sw1.backend.flowroad.repository.organization.DepartmentRepository;
import sw1.backend.flowroad.repository.organization.OrganizationRepository;
import sw1.backend.flowroad.repository.process.ProcessAssignmentRepository;
import sw1.backend.flowroad.repository.process.ProcessHistoryRepository;
import sw1.backend.flowroad.repository.process.ProcessInstanceRepository;
import sw1.backend.flowroad.repository.user.UserRepository;

@Component
@RequiredArgsConstructor
@Order(3)
@ConditionalOnProperty(prefix = "flowroad.seed.history", name = "enabled", havingValue = "true")
public class ProcessHistorySimulationSeeder implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(ProcessHistorySimulationSeeder.class);
    private static final int NORMAL_COUNT = 21;
    private static final int SLOW_COUNT = 6;
    private static final Set<String> TARGET_DIAGRAM_NAMES = Set.of(
            "Solicitud de Prestamo Personal",
            "Apertura de Cuenta Bancaria",
            "Solicitud de Credito Hipotecario",
            "Reclamo por Transaccion No Reconocida");

    private final OrganizationRepository organizationRepository;
    private final DiagramRepository diagramRepository;
    private final UserRepository userRepository;
    private final DepartmentRepository departmentRepository;
    private final CargoRepository cargoRepository;
    private final ProcessInstanceRepository processInstanceRepository;
    private final ProcessAssignmentRepository processAssignmentRepository;
    private final ProcessHistoryRepository processHistoryRepository;
    private final ApplicationContext applicationContext;

    @Value("${flowroad.seed.history.org-code:BCB}")
    private String organizationCode;

    @Value("${flowroad.seed.history.instances-per-diagram:30}")
    private int instancesPerDiagram;

    @Value("${flowroad.seed.history.days-back:90}")
    private int daysBack;

    @Value("${flowroad.seed.history.prefix:DEMO-CU19}")
    private String historyPrefix;

    @Value("${flowroad.seed.history.cleanup-enabled:false}")
    private boolean cleanupEnabled;

    @Override
    public void run(ApplicationArguments args) {
        if (cleanupEnabled) {
            applicationContext.getBean(ProcessHistorySimulationSeeder.class).cleanupHistoricalProcesses();
            return;
        }

        seedHistoricalProcesses();
    }

    @Transactional
    public void cleanupHistoricalProcesses() {
        List<ProcessInstance> demoInstances = processInstanceRepository.findAllByCodeStartingWith(historyPrefix);

        if (demoInstances.isEmpty()) {
            log.info("[SEED][CU19][CLEANUP] No hay process_instances con prefijo {}.", historyPrefix);
            return;
        }

        List<String> processInstanceIds = demoInstances.stream()
                .map(ProcessInstance::getId)
                .filter(id -> id != null && !id.isBlank())
                .toList();

        if (processInstanceIds.isEmpty()) {
            log.info("[SEED][CU19][CLEANUP] No hay ids validos para limpiar con prefijo {}.", historyPrefix);
            return;
        }

        long deletedHistory = processHistoryRepository.deleteByProcessInstanceIdIn(processInstanceIds);
        long deletedAssignments = processAssignmentRepository.deleteByProcessInstanceIdIn(processInstanceIds);
        long deletedInstances = processInstanceRepository.deleteByIdIn(processInstanceIds);

        log.info(
                "[SEED][CU19][CLEANUP] Eliminados: process_history={}, process_assignments={}, process_instances={}",
                deletedHistory,
                deletedAssignments,
                deletedInstances);
    }

    private void seedHistoricalProcesses() {
        Optional<Organization> organization = findBankOrganization();
        if (organization.isEmpty()) {
            log.warn("[SEED][CU19] No existe organizacion con codigo {}. No se crea historial demo.", organizationCode);
            return;
        }

        Organization bank = organization.get();
        List<Diagram> diagrams = findActiveDemoDiagrams(bank.getId());
        List<User> clients = findClients();
        User starter = findStarterUser(bank.getId()).orElse(null);

        if (diagrams.isEmpty()) {
            log.warn("[SEED][CU19] No hay workflows activos esperados para generar historial demo.");
            return;
        }

        if (clients.isEmpty()) {
            log.warn("[SEED][CU19] No hay clientes CLIENT activos con orgId null. No se crea historial demo.");
            return;
        }

        if (starter == null) {
            log.warn("[SEED][CU19] No hay usuario interno ADMIN activo para Banco BCB. No se crea historial demo.");
            return;
        }

        int created = 0;
        int skipped = 0;
        for (int diagramIndex = 0; diagramIndex < diagrams.size(); diagramIndex++) {
            SimulationResult result = simulateDiagramExecutions(bank, diagrams.get(diagramIndex), clients, starter, diagramIndex);
            created += result.created();
            skipped += result.skipped();
        }

        log.info("[SEED][CU19] Historial demo finalizado. Creadas: {}, existentes omitidas: {}", created, skipped);
    }

    private Optional<Organization> findBankOrganization() {
        return organizationRepository.findByCode(organizationCode.toUpperCase(Locale.ROOT));
    }

    private List<Diagram> findActiveDemoDiagrams(String orgId) {
        return diagramRepository.findAllByOrgIdAndIsActiveTrue(orgId)
                .stream()
                .filter(diagram -> TARGET_DIAGRAM_NAMES.contains(normalizeName(diagram.getName())))
                .collect(Collectors.toMap(
                        diagram -> normalizeName(diagram.getName()),
                        diagram -> diagram,
                        this::selectNewestDiagram,
                        LinkedHashMap::new))
                .values()
                .stream()
                .sorted(Comparator.comparing(Diagram::getName))
                .toList();
    }

    private List<User> findClients() {
        return userRepository.findByRole(Roles.CLIENT)
                .stream()
                .filter(user -> user.getOrgId() == null)
                .filter(user -> Boolean.TRUE.equals(user.getIsActive()))
                .sorted(Comparator.comparing(User::getEmail))
                .toList();
    }

    private Diagram selectNewestDiagram(Diagram current, Diagram candidate) {
        int currentVersion = current.getVersion() != null ? current.getVersion() : 0;
        int candidateVersion = candidate.getVersion() != null ? candidate.getVersion() : 0;

        if (candidateVersion > currentVersion) {
            return candidate;
        }

        if (candidateVersion < currentVersion) {
            return current;
        }

        LocalDateTime currentUpdatedAt = current.getUpdatedAt() != null ? current.getUpdatedAt() : LocalDateTime.MIN;
        LocalDateTime candidateUpdatedAt = candidate.getUpdatedAt() != null ? candidate.getUpdatedAt() : LocalDateTime.MIN;

        return candidateUpdatedAt.isAfter(currentUpdatedAt) ? candidate : current;
    }

    private Optional<User> findStarterUser(String orgId) {
        return userRepository.findAllByOrgIdAndIsActiveTrue(orgId)
                .stream()
                .filter(user -> user.getRole() == Roles.ADMIN)
                .findFirst();
    }

    private SimulationResult simulateDiagramExecutions(
            Organization organization,
            Diagram diagram,
            List<User> clients,
            User starter,
            int diagramIndex) {
        List<ActionNode> actionNodes = readActionNodes(diagram);

        if (actionNodes.isEmpty()) {
            log.warn("[SEED][CU19] Workflow {} no tiene actividades ACTION/TASK simulables.", diagram.getName());
            return new SimulationResult(0, 0);
        }

        int created = 0;
        int skipped = 0;
        int safeCount = Math.max(1, instancesPerDiagram);

        for (int instanceIndex = 0; instanceIndex < safeCount; instanceIndex++) {
            SimulationKind kind = resolveKind(instanceIndex);
            String code = buildProcessCode(diagram, instanceIndex);

            if (!processInstanceRepository.findAllByCode(code).isEmpty()) {
                skipped++;
                continue;
            }

            User client = clients.get((diagramIndex * safeCount + instanceIndex) % clients.size());
            applicationContext.getBean(ProcessHistorySimulationSeeder.class)
                    .createProcessInstance(organization, diagram, actionNodes, client, starter, kind, instanceIndex, code);
            created++;
        }

        log.info(
                "[SEED][CU19] Workflow {} procesado. Creadas: {}, existentes: {}",
                diagram.getName(),
                created,
                skipped);
        return new SimulationResult(created, skipped);
    }

    @Transactional
    public void createProcessInstance(
            Organization organization,
            Diagram diagram,
            List<ActionNode> actionNodes,
            User client,
            User starter,
            SimulationKind kind,
            int instanceIndex,
            String code) {
        LocalDateTime startedAt = resolveStartedAt(instanceIndex);
        LocalDateTime cursor = startedAt.plusMinutes(15L + instanceIndex);
        List<String> completedNodeIds = new ArrayList<>();
        Map<String, Integer> nodeActivationCounts = new HashMap<>();
        List<ProcessAssignment> assignments = new ArrayList<>();
        List<ProcessHistory> histories = new ArrayList<>();
        List<ProcessHistory> extraHistories = new ArrayList<>();

        for (int nodeIndex = 0; nodeIndex < actionNodes.size(); nodeIndex++) {
            ActionNode actionNode = actionNodes.get(nodeIndex);
            AssignmentContext context = resolveAssignmentContext(organization.getId(), actionNode, nodeIndex);
            LocalDateTime createdAt = cursor;
            LocalDateTime assignedAt = createdAt.plusMinutes(5);
            LocalDateTime completedAt = assignedAt.plusHours(resolveTaskHours(kind, actionNode, nodeIndex, instanceIndex));
            User assignee = context.assignee();

            ProcessAssignment assignment = ProcessAssignment.builder()
                    .processInstanceId(null)
                    .nodeId(actionNode.id())
                    .nodeName(actionNode.name())
                    .laneId(actionNode.laneId())
                    .laneName(actionNode.departmentName())
                    .assignedDepartmentId(context.departmentId())
                    .assignedDepartmentName(context.departmentName())
                    .assignedCargoId(context.cargoId())
                    .assignedCargoName(context.cargoName())
                    .assignedUserId(assignee != null ? assignee.getId() : null)
                    .assignedUserName(assignee != null ? getUserDisplayName(assignee) : null)
                    .status(ProcessAssignmentStatus.COMPLETED)
                    .createdAt(createdAt)
                    .assignedAt(assignedAt)
                    .completedAt(completedAt)
                    .build();

            assignments.add(assignment);
            completedNodeIds.add(actionNode.id());
            nodeActivationCounts.put(actionNode.id(), anomalyActivationCount(kind, actionNode, nodeIndex));

            histories.add(ProcessHistory.builder()
                    .fromNodeId(actionNode.id())
                    .fromNodeName(actionNode.name())
                    .toNodeId(nextNodeId(actionNodes, nodeIndex))
                    .toNodeName(nextNodeName(actionNodes, nodeIndex))
                    .transitionLabel(kind.name())
                    .performedByUserId(assignee != null ? assignee.getId() : null)
                    .performedByUserName(assignee != null ? getUserDisplayName(assignee) : null)
                    .performedAt(completedAt)
                    .templateResponseData(Map.of(
                            "simulationKind", kind.name(),
                            "department", context.departmentName() != null ? context.departmentName() : "Sin departamento"))
                    .attachments(List.of())
                    .comment(buildHistoryComment(kind, actionNode, nodeIndex))
                    .build());

            if (kind == SimulationKind.ANOMALO && isBottleneckNode(actionNode, nodeIndex)) {
                extraHistories.add(ProcessHistory.builder()
                        .fromNodeId(actionNode.id())
                        .fromNodeName(actionNode.name())
                        .toNodeId(actionNode.id())
                        .toNodeName(actionNode.name())
                        .transitionLabel("RETRABAJO_DEMO")
                        .performedByUserId(assignee != null ? assignee.getId() : null)
                        .performedByUserName(assignee != null ? getUserDisplayName(assignee) : null)
                        .performedAt(completedAt.minusDays(2))
                        .templateResponseData(Map.of("simulationKind", kind.name(), "rework", true))
                        .attachments(List.of())
                        .comment("Retrabajo simulado CU19 para patron anomalo.")
                        .build());
            }

            cursor = completedAt.plusMinutes(20);
        }

        LocalDateTime finishedAt = cursor.plusMinutes(10);
        ProcessInstance instance = ProcessInstance.builder()
                .code(code)
                .orgId(organization.getId())
                .diagramId(diagram.getId())
                .diagramName(diagram.getName())
                .diagramVersion(diagram.getVersion())
                .status(ProcessInstanceStatus.COMPLETED)
                .activeNodeIds(new ArrayList<>())
                .completedNodeIds(completedNodeIds)
                .nodeActivationCounts(nodeActivationCounts)
                .joinArrivals(new HashMap<>())
                .requestData(buildRequestData(kind, client, diagram, instanceIndex))
                .clientId(client.getId())
                .clientName(getUserDisplayName(client))
                .clientEmail(client.getEmail())
                .startedByUserId(starter.getId())
                .startedByUserName(getUserDisplayName(starter))
                .startedAt(startedAt)
                .updatedAt(finishedAt)
                .finishedAt(finishedAt)
                .build();

        ProcessInstance savedInstance = processInstanceRepository.save(instance);

        for (int i = 0; i < assignments.size(); i++) {
            ProcessAssignment assignment = assignments.get(i);
            assignment.setProcessInstanceId(savedInstance.getId());
            ProcessAssignment savedAssignment = processAssignmentRepository.save(assignment);

            ProcessHistory history = histories.get(i);
            history.setProcessInstanceId(savedInstance.getId());
            history.setAssignmentId(savedAssignment.getId());
            processHistoryRepository.save(history);
        }

        for (ProcessHistory history : extraHistories) {
            history.setProcessInstanceId(savedInstance.getId());
            processHistoryRepository.save(history);
        }
    }

    private List<ActionNode> readActionNodes(Diagram diagram) {
        Map<String, Diagram.DiagramCell> nodes = new LinkedHashMap<>();
        List<Diagram.DiagramCell> links = new ArrayList<>();
        Map<String, List<Diagram.DiagramCell>> outgoing = new HashMap<>();
        Map<String, Integer> incomingCounts = new HashMap<>();

        for (Diagram.DiagramCell cell : diagram.getCells() != null ? diagram.getCells() : List.<Diagram.DiagramCell>of()) {
            if (isLink(cell)) {
                links.add(cell);
                String sourceId = getSourceNodeId(cell);
                String targetId = getTargetNodeId(cell);
                if (sourceId != null) {
                    outgoing.computeIfAbsent(sourceId, ignored -> new ArrayList<>()).add(cell);
                }
                if (targetId != null) {
                    incomingCounts.merge(targetId, 1, Integer::sum);
                }
            } else if (cell.getId() != null) {
                nodes.put(cell.getId(), cell);
            }
        }

        Map<String, Diagram.DiagramLane> lanesById = diagram.getLanes() != null
                ? diagram.getLanes().stream().collect(Collectors.toMap(Diagram.DiagramLane::getId, lane -> lane, (a, b) -> a))
                : Map.of();

        List<ActionNode> traversed = traverseActionNodes(nodes, outgoing, lanesById);
        if (!traversed.isEmpty()) {
            return traversed;
        }

        return nodes.values()
                .stream()
                .filter(this::isActionNode)
                .sorted(Comparator
                        .comparing((Diagram.DiagramCell cell) -> cell.getPosition() != null ? cell.getPosition().getY() : 0)
                        .thenComparing(cell -> cell.getPosition() != null ? cell.getPosition().getX() : 0))
                .map(cell -> toActionNode(cell, lanesById))
                .toList();
    }

    private List<ActionNode> traverseActionNodes(
            Map<String, Diagram.DiagramCell> nodes,
            Map<String, List<Diagram.DiagramCell>> outgoing,
            Map<String, Diagram.DiagramLane> lanesById) {
        List<ActionNode> result = new ArrayList<>();
        Optional<Diagram.DiagramCell> initialNode = nodes.values().stream()
                .filter(this::isInitialNode)
                .findFirst();

        String currentId = initialNode.map(Diagram.DiagramCell::getId).orElse(null);
        Set<String> visited = new java.util.HashSet<>();

        while (currentId != null && visited.add(currentId)) {
            Diagram.DiagramCell current = nodes.get(currentId);
            if (current != null && isActionNode(current)) {
                result.add(toActionNode(current, lanesById));
            }

            List<Diagram.DiagramCell> links = outgoing.getOrDefault(currentId, List.of());
            currentId = links.stream()
                    .map(this::getTargetNodeId)
                    .filter(nodes::containsKey)
                    .findFirst()
                    .orElse(null);
        }

        return result;
    }

    private AssignmentContext resolveAssignmentContext(String orgId, ActionNode actionNode, int nodeIndex) {
        Department department = actionNode.departmentId() != null
                ? departmentRepository.findById(actionNode.departmentId()).orElse(null)
                : null;

        List<User> candidates = department != null
                ? userRepository.findByOrgIdAndDepartmentIdAndRoleInAndIsActiveTrue(
                        orgId,
                        department.getId(),
                        List.of(Roles.WORKER, Roles.RECEP, Roles.DESIGNER, Roles.ADMIN))
                : userRepository.findAllByOrgIdAndIsActiveTrue(orgId);

        User assignee = candidates.isEmpty() ? null : candidates.get(nodeIndex % candidates.size());
        Cargo cargo = resolveCargo(actionNode, department, assignee);

        return new AssignmentContext(
                department != null ? department.getId() : actionNode.departmentId(),
                department != null ? department.getName() : actionNode.departmentName(),
                cargo != null ? cargo.getId() : assignee != null ? assignee.getCargoId() : null,
                cargo != null ? cargo.getName() : null,
                assignee);
    }

    private Cargo resolveCargo(ActionNode actionNode, Department department, User assignee) {
        if (actionNode.requiredCargoId() != null && !actionNode.requiredCargoId().isBlank()) {
            Optional<Cargo> requiredCargo = cargoRepository.findById(actionNode.requiredCargoId());
            if (requiredCargo.isPresent()) {
                return requiredCargo.get();
            }
        }

        if (assignee != null && assignee.getCargoId() != null) {
            Optional<Cargo> assignedCargo = cargoRepository.findById(assignee.getCargoId());
            if (assignedCargo.isPresent()) {
                return assignedCargo.get();
            }
        }

        if (department != null && department.getCargoIds() != null && !department.getCargoIds().isEmpty()) {
            return cargoRepository.findAllById(department.getCargoIds())
                    .stream()
                    .findFirst()
                    .orElse(null);
        }

        return null;
    }

    private Map<String, Object> buildRequestData(
            SimulationKind kind,
            User client,
            Diagram diagram,
            int instanceIndex) {
        return Map.of(
                "seed", historyPrefix,
                "simulationKind", kind.name(),
                "workflow", diagram.getName(),
                "clientId", client.getId(),
                "clientEmail", client.getEmail(),
                "caseNumber", instanceIndex + 1);
    }

    private LocalDateTime resolveStartedAt(int instanceIndex) {
        int safeDaysBack = Math.max(1, daysBack);
        int days = 1 + ((instanceIndex * 3) % safeDaysBack);
        return LocalDateTime.now()
                .minusDays(days)
                .withHour(8 + (instanceIndex % 5))
                .withMinute((instanceIndex * 7) % 60)
                .withSecond(0)
                .withNano(0);
    }

    private long resolveTaskHours(SimulationKind kind, ActionNode actionNode, int nodeIndex, int instanceIndex) {
        return switch (kind) {
            case NORMAL -> 2L + ((nodeIndex + instanceIndex) % 7);
            case LENTO -> isBottleneckNode(actionNode, nodeIndex)
                    ? 48L + ((instanceIndex % 3) * 24L)
                    : 8L + ((nodeIndex + instanceIndex) % 12);
            case ANOMALO -> isBottleneckNode(actionNode, nodeIndex)
                    ? 240L + ((instanceIndex % 3) * 72L)
                    : 16L + ((nodeIndex + instanceIndex) % 24);
        };
    }

    private int anomalyActivationCount(SimulationKind kind, ActionNode actionNode, int nodeIndex) {
        if (kind == SimulationKind.ANOMALO && isBottleneckNode(actionNode, nodeIndex)) {
            return 3;
        }
        return 1;
    }

    private boolean isBottleneckNode(ActionNode actionNode, int nodeIndex) {
        String departmentName = normalizeName(actionNode.departmentName());
        return departmentName.contains("Creditos")
                || departmentName.contains("Riesgos")
                || departmentName.contains("Operaciones")
                || nodeIndex == 1;
    }

    private String buildHistoryComment(SimulationKind kind, ActionNode actionNode, int nodeIndex) {
        return switch (kind) {
            case NORMAL -> "Ejecucion historica normal generada para CU19.";
            case LENTO -> isBottleneckNode(actionNode, nodeIndex)
                    ? "Cuello de botella historico simulado para CU19."
                    : "Ejecucion historica lenta generada para CU19.";
            case ANOMALO -> "Comportamiento historico anomalo generado para CU19.";
        };
    }

    private SimulationKind resolveKind(int instanceIndex) {
        if (instanceIndex < NORMAL_COUNT) {
            return SimulationKind.NORMAL;
        }
        if (instanceIndex < NORMAL_COUNT + SLOW_COUNT) {
            return SimulationKind.LENTO;
        }
        return SimulationKind.ANOMALO;
    }

    private String buildProcessCode(Diagram diagram, int instanceIndex) {
        return historyPrefix + "-" + slug(diagram.getName()) + "-" + String.format("%03d", instanceIndex + 1);
    }

    private ActionNode toActionNode(Diagram.DiagramCell cell, Map<String, Diagram.DiagramLane> lanesById) {
        String laneId = readString(cell.getCustomData(), "laneId");
        Diagram.DiagramLane lane = laneId != null ? lanesById.get(laneId) : null;
        return new ActionNode(
                cell.getId(),
                resolveNodeName(cell),
                laneId,
                lane != null ? lane.getDepartmentId() : null,
                lane != null ? lane.getDepartmentName() : null,
                readString(cell.getCustomData(), "requiredCargoId"));
    }

    private boolean isActionNode(Diagram.DiagramCell cell) {
        String type = normalizeName(readString(cell.getCustomData(), "tipo"));
        if (type.isBlank()) {
            type = normalizeName(readString(cell.getCustomData(), "type"));
        }

        boolean domainAction = type.equals("ACTION")
                || type.equals("ACTIVITY")
                || type.equals("TASK")
                || type.equals("TAREA");

        return domainAction || (readString(cell.getCustomData(), "laneId") != null
                && !isInitialNode(cell)
                && !isFinalNode(cell)
                && !type.equals("DECISION")
                && !type.equals("FORK")
                && !type.equals("JOIN"));
    }

    private boolean isInitialNode(Diagram.DiagramCell cell) {
        String type = normalizeName(readString(cell.getCustomData(), "tipo"));
        return type.equals("INITIAL") || type.equals("INICIO") || type.equals("START");
    }

    private boolean isFinalNode(Diagram.DiagramCell cell) {
        String type = normalizeName(readString(cell.getCustomData(), "tipo"));
        return type.equals("FINAL") || type.equals("FIN") || type.equals("END");
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

    private String nextNodeId(List<ActionNode> actionNodes, int nodeIndex) {
        return nodeIndex + 1 < actionNodes.size() ? actionNodes.get(nodeIndex + 1).id() : "FINAL";
    }

    private String nextNodeName(List<ActionNode> actionNodes, int nodeIndex) {
        return nodeIndex + 1 < actionNodes.size() ? actionNodes.get(nodeIndex + 1).name() : "Finalizado";
    }

    private String resolveNodeName(Diagram.DiagramCell node) {
        String name = readString(node.getCustomData(), "nombre");
        if (name == null || name.isBlank()) {
            name = readString(node.getCustomData(), "name");
        }
        if (name == null || name.isBlank()) {
            name = readString(node.getCustomData(), "label");
        }
        return name != null && !name.isBlank() ? name : node.getId();
    }

    private String readString(Map<String, Object> map, String key) {
        if (map == null || key == null || !map.containsKey(key) || map.get(key) == null) {
            return null;
        }
        return map.get(key).toString();
    }

    private String getUserDisplayName(User user) {
        if (user.getProfile() != null) {
            String firstName = user.getProfile().getNombre() != null ? user.getProfile().getNombre() : "";
            String lastName = user.getProfile().getApellido() != null ? user.getProfile().getApellido() : "";
            String fullName = (firstName + " " + lastName).trim();
            if (!fullName.isBlank()) {
                return fullName;
            }
        }
        return user.getEmail();
    }

    private String normalizeName(String value) {
        if (value == null) {
            return "";
        }
        return value
                .replace("á", "a")
                .replace("é", "e")
                .replace("í", "i")
                .replace("ó", "o")
                .replace("ú", "u")
                .replace("ñ", "n")
                .replace("Á", "A")
                .replace("É", "E")
                .replace("Í", "I")
                .replace("Ó", "O")
                .replace("Ú", "U")
                .replace("Ñ", "N")
                .trim();
    }

    private String slug(String value) {
        return normalizeName(value)
                .toUpperCase(Locale.ROOT)
                .replaceAll("[^A-Z0-9]+", "-")
                .replaceAll("(^-|-$)", "");
    }

    private enum SimulationKind {
        NORMAL,
        LENTO,
        ANOMALO
    }

    private record SimulationResult(int created, int skipped) {
    }

    private record ActionNode(
            String id,
            String name,
            String laneId,
            String departmentId,
            String departmentName,
            String requiredCargoId) {
    }

    private record AssignmentContext(
            String departmentId,
            String departmentName,
            String cargoId,
            String cargoName,
            User assignee) {
    }
}
