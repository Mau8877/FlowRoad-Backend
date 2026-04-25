package sw1.backend.flowroad.services.diagram;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;

import lombok.RequiredArgsConstructor;
import sw1.backend.flowroad.models.diagram.DesignSession;
import sw1.backend.flowroad.models.diagram.Diagram;
import sw1.backend.flowroad.repository.diagram.DesignSessionRepository;
import sw1.backend.flowroad.repository.diagram.DiagramRepository;

@Service
@RequiredArgsConstructor
public class DesignSessionService {

    private static final long LOCK_TIMEOUT_SECONDS = 30L;

    private final DesignSessionRepository sessionRepository;
    private final DiagramRepository diagramRepository;
    private final ObjectMapper objectMapper;

    @Transactional
    public DesignSession getOrCreateSession(String diagramId, String userId, String username, String color) {
        Optional<DesignSession> existingSession = sessionRepository.findByDiagramId(diagramId);

        if (existingSession.isPresent()) {
            DesignSession session = existingSession.get();
            ensureCollections(session);
            pruneExpiredLocks(session);
            agregarUsuarioASesion(session, userId, username, color);
            return sessionRepository.save(session);
        }

        Diagram diagram = diagramRepository.findById(diagramId)
                .orElseThrow(() -> new RuntimeException("El diagrama no existe"));

        String initialSnapshot = "[]";
        try {
            if (diagram.getCells() != null) {
                initialSnapshot = objectMapper.writeValueAsString(diagram.getCells());
            }
        } catch (Exception e) {
            System.err.println("Error al serializar el snapshot inicial: " + e.getMessage());
        }

        DesignSession newSession = DesignSession.builder()
                .diagramId(diagramId)
                .sessionToken("WS-" + UUID.randomUUID().toString().substring(0, 8).toUpperCase())
                .snapshot(initialSnapshot)
                .activeUsers(new ArrayList<>())
                .opsLog(new ArrayList<>())
                .activeLocks(new ArrayList<>())
                .startedAt(LocalDateTime.now())
                .lastActivity(LocalDateTime.now())
                .build();

        agregarUsuarioASesion(newSession, userId, username, color);
        return sessionRepository.save(newSession);
    }

    @Transactional
    public boolean lockCell(String sessionToken, String cellId, String userId, String dragId) {
        DesignSession session = sessionRepository.findBySessionToken(sessionToken)
                .orElseThrow(() -> new RuntimeException("Sesión caducada o inexistente"));

        ensureCollections(session);
        pruneExpiredLocks(session);

        DesignSession.CellLock existingLock = session.getActiveLocks().stream()
                .filter(lock -> lock.getCellId().equals(cellId))
                .findFirst()
                .orElse(null);

        if (existingLock != null) {
            if (existingLock.getUserId().equals(userId)) {
                existingLock.setLockedAt(LocalDateTime.now());
                existingLock.setDragId(dragId);
                session.setLastActivity(LocalDateTime.now());
                sessionRepository.save(session);
                return true;
            }
            return false;
        }

        String username = session.getActiveUsers().stream()
                .filter(u -> u.getUserId().equals(userId))
                .map(DesignSession.ActiveUser::getNombre)
                .findFirst()
                .orElse("Usuario");

        DesignSession.CellLock newLock = DesignSession.CellLock.builder()
                .cellId(cellId)
                .userId(userId)
                .username(username)
                .dragId(dragId)
                .lockedAt(LocalDateTime.now())
                .build();

        session.getActiveLocks().add(newLock);
        session.setLastActivity(LocalDateTime.now());
        sessionRepository.save(session);
        return true;
    }

    @Transactional
    public boolean unlockCell(String sessionToken, String cellId, String userId, String dragId) {
        DesignSession session = sessionRepository.findBySessionToken(sessionToken)
                .orElseThrow(() -> new RuntimeException("Sesión caducada o inexistente"));

        ensureCollections(session);

        boolean removed = session.getActiveLocks().removeIf(lock -> lock.getCellId().equals(cellId)
                && lock.getUserId().equals(userId)
                && ((dragId == null && lock.getDragId() == null)
                        || (dragId != null && dragId.equals(lock.getDragId()))));

        session.setLastActivity(LocalDateTime.now());
        sessionRepository.save(session);
        return removed;
    }

    public boolean canOperateOnCell(String sessionToken, String cellId, String userId, String dragId) {
        DesignSession session = sessionRepository.findBySessionToken(sessionToken)
                .orElseThrow(() -> new RuntimeException("Sesión caducada o inexistente"));

        ensureCollections(session);
        pruneExpiredLocks(session);

        DesignSession.CellLock existingLock = session.getActiveLocks().stream()
                .filter(lock -> lock.getCellId().equals(cellId))
                .findFirst()
                .orElse(null);

        if (existingLock == null) {
            return false;
        }

        if (!existingLock.getUserId().equals(userId)) {
            return false;
        }

        if (dragId == null || existingLock.getDragId() == null) {
            return false;
        }

        return dragId.equals(existingLock.getDragId());
    }

    private void persistSessionSnapshotToDiagram(DesignSession session) {
        try {
            Diagram diagram = diagramRepository.findById(session.getDiagramId()).orElse(null);
            if (diagram == null) {
                return;
            }

            String snapshot = session.getSnapshot();
            if (snapshot == null || snapshot.isBlank()) {
                return;
            }

            List<Diagram.DiagramCell> cells = objectMapper.readValue(
                    snapshot,
                    new TypeReference<List<Diagram.DiagramCell>>() {
                    });

            diagram.setCells(cells);
            diagram.setUpdatedAt(LocalDateTime.now());
            diagramRepository.save(diagram);

        } catch (Exception e) {
            System.err.println("❌ Error persistiendo snapshot al diagrama oficial: " + e.getMessage());
        }
    }

    public void recordOperation(String sessionToken, DesignSession.OperationLog operation) {
        DesignSession session = sessionRepository.findBySessionToken(sessionToken)
                .orElseThrow(() -> new RuntimeException("Sesión caducada o inexistente"));

        ensureCollections(session);
        pruneExpiredLocks(session);

        String opType = operation.getOpType();

        // MOVE_LIVE y CURSOR no deben persistir snapshot ni guardar la sesión,
        // porque generan carreras entre hilos y pueden pisar locks.
        if ("CURSOR".equals(opType) || "MOVE_LIVE".equals(opType)) {
            return;
        }

        session.setLastActivity(LocalDateTime.now());
        operation.setTimestamp(LocalDateTime.now());
        session.getOpsLog().add(operation);

        boolean shouldPersistDiagram = false;

        switch (opType) {
            case "MOVE_COMMIT" -> {
                actualizarPosicionEnSnapshot(session, operation);
                shouldPersistDiagram = true;
            }
            case "CREATE_NODE", "CREATE_LINK" -> {
                crearCeldaEnSnapshot(session, operation);
                shouldPersistDiagram = true;
            }
            case "UPDATE_NODE", "UPDATE_LINK" -> {
                actualizarCeldaEnSnapshot(session, operation);
                shouldPersistDiagram = true;
            }
            case "DELETE_CELL", "DELETE_LINK" -> {
                eliminarCeldaEnSnapshot(session, operation);
                shouldPersistDiagram = true;
            }
            default -> {
            }
        }

        sessionRepository.save(session);

        if (shouldPersistDiagram) {
            persistSessionSnapshotToDiagram(session);
        }
    }

    private void actualizarPosicionEnSnapshot(DesignSession session, DesignSession.OperationLog op) {
        try {
            List<Diagram.DiagramCell> cells = readSnapshotCells(session);

            if (op.getDelta() == null) {
                return;
            }

            Object xValue = op.getDelta().get("x");
            Object yValue = op.getDelta().get("y");

            if (xValue == null || yValue == null) {
                return;
            }

            double newX = Double.parseDouble(xValue.toString());
            double newY = Double.parseDouble(yValue.toString());

            Diagram.DiagramCell targetCell = cells.stream()
                    .filter(c -> c.getId().equals(op.getCellId()))
                    .findFirst()
                    .orElse(null);

            if (targetCell != null) {
                if (targetCell.getPosition() == null) {
                    targetCell.setPosition(new Diagram.Position());
                }
                targetCell.getPosition().setX(newX);
                targetCell.getPosition().setY(newY);

                writeSnapshotCells(session, cells);
            }

        } catch (Exception e) {
            System.err.println("❌ Error sincronizando MOVE_COMMIT: " + e.getMessage());
        }
    }

    private void crearCeldaEnSnapshot(DesignSession session, DesignSession.OperationLog op) {
        try {
            List<Diagram.DiagramCell> cells = readSnapshotCells(session);

            if (op.getDelta() == null || !op.getDelta().containsKey("cell")) {
                return;
            }

            Diagram.DiagramCell newCell = objectMapper.convertValue(
                    op.getDelta().get("cell"),
                    Diagram.DiagramCell.class);

            if (newCell == null || newCell.getId() == null || newCell.getId().isBlank()) {
                return;
            }

            boolean alreadyExists = cells.stream()
                    .anyMatch(c -> c.getId().equals(newCell.getId()));

            if (!alreadyExists) {
                cells.add(newCell);
                writeSnapshotCells(session, cells);
            }

        } catch (Exception e) {
            System.err.println("❌ Error creando celda en snapshot: " + e.getMessage());
        }
    }

    private void actualizarCeldaEnSnapshot(DesignSession session, DesignSession.OperationLog op) {
        try {
            List<Diagram.DiagramCell> cells = readSnapshotCells(session);

            Diagram.DiagramCell targetCell = cells.stream()
                    .filter(c -> c.getId().equals(op.getCellId()))
                    .findFirst()
                    .orElse(null);

            if (targetCell == null || op.getDelta() == null) {
                return;
            }

            Map<String, Object> delta = op.getDelta();

            if (delta.containsKey("position") && delta.get("position") != null) {
                Diagram.Position position = objectMapper.convertValue(
                        delta.get("position"),
                        Diagram.Position.class);
                targetCell.setPosition(position);
            }

            if (delta.containsKey("size") && delta.get("size") != null) {
                Diagram.Size size = objectMapper.convertValue(
                        delta.get("size"),
                        Diagram.Size.class);
                targetCell.setSize(size);
            }

            if (delta.containsKey("source") && delta.get("source") != null) {
                Diagram.CellReference source = objectMapper.convertValue(
                        delta.get("source"),
                        Diagram.CellReference.class);
                targetCell.setSource(source);
            }

            if (delta.containsKey("target") && delta.get("target") != null) {
                Diagram.CellReference target = objectMapper.convertValue(
                        delta.get("target"),
                        Diagram.CellReference.class);
                targetCell.setTarget(target);
            }

            if (delta.containsKey("attrs") && delta.get("attrs") != null) {
                Map<String, Object> incomingAttrs = objectMapper.convertValue(
                        delta.get("attrs"),
                        new TypeReference<Map<String, Object>>() {
                        });

                Map<String, Object> mergedAttrs = mergeMaps(
                        targetCell.getAttrs(),
                        incomingAttrs);

                targetCell.setAttrs(mergedAttrs);
            }

            if (delta.containsKey("customData") && delta.get("customData") != null) {
                Map<String, Object> incomingCustomData = objectMapper.convertValue(
                        delta.get("customData"),
                        new TypeReference<Map<String, Object>>() {
                        });

                Map<String, Object> mergedCustomData = mergeMaps(
                        targetCell.getCustomData(),
                        incomingCustomData);

                targetCell.setCustomData(mergedCustomData);
            }

            if (delta.containsKey("type") && delta.get("type") != null) {
                targetCell.setType(delta.get("type").toString());
            }

            writeSnapshotCells(session, cells);

        } catch (Exception e) {
            System.err.println("❌ Error actualizando celda en snapshot: " + e.getMessage());
        }
    }

    private void eliminarCeldaEnSnapshot(DesignSession session, DesignSession.OperationLog op) {
        try {
            List<Diagram.DiagramCell> cells = readSnapshotCells(session);

            String cellId = op.getCellId();
            if (cellId == null || cellId.isBlank()) {
                return;
            }

            Diagram.DiagramCell targetCell = cells.stream()
                    .filter(c -> cellId.equals(c.getId()))
                    .findFirst()
                    .orElse(null);

            if (targetCell == null) {
                return;
            }

            boolean isNode = !"standard.Link".equals(targetCell.getType());

            cells.removeIf(c -> cellId.equals(c.getId()));

            if (isNode) {
                cells.removeIf(c -> ("standard.Link".equals(c.getType())) &&
                        ((c.getSource() != null && cellId.equals(c.getSource().getId())) ||
                                (c.getTarget() != null && cellId.equals(c.getTarget().getId()))));
            }

            session.getActiveLocks().removeIf(lock -> cellId.equals(lock.getCellId()));

            writeSnapshotCells(session, cells);

        } catch (Exception e) {
            System.err.println("❌ Error eliminando celda del snapshot: " + e.getMessage());
        }
    }

    public void pingUser(String sessionToken, String userId, double cursorX, double cursorY) {
        DesignSession session = sessionRepository.findBySessionToken(sessionToken).orElse(null);
        if (session == null) {
            return;
        }

        ensureCollections(session);
        pruneExpiredLocks(session);

        session.getActiveUsers().stream()
                .filter(u -> u.getUserId().equals(userId))
                .findFirst()
                .ifPresent(u -> {
                    u.setLastPing(LocalDateTime.now());
                    u.setCursor(new DesignSession.CursorPosition(cursorX, cursorY));
                });

        session.setLastActivity(LocalDateTime.now());
        sessionRepository.save(session);
    }

    @Transactional
    public void saveAndCloseSession(String sessionToken, String finalSnapshotJson) {
        DesignSession session = sessionRepository.findBySessionToken(sessionToken)
                .orElseThrow(() -> new RuntimeException("Sesión no encontrada"));

        saveSnapshotToDiagram(session, finalSnapshotJson);
        sessionRepository.delete(session);
    }

    @Transactional
    public void handleUserDisconnection(String realUserId) {
        List<DesignSession> allSessions = sessionRepository.findAll();

        for (DesignSession session : allSessions) {
            ensureCollections(session);
            pruneExpiredLocks(session);

            boolean removed = session.getActiveUsers()
                    .removeIf(u -> u.getUserId().equals(realUserId));

            if (removed) {
                session.getActiveLocks().removeIf(lock -> lock.getUserId().equals(realUserId));

                if (session.getActiveUsers().isEmpty()) {
                    saveSnapshotToDiagram(session, null);
                    sessionRepository.delete(session);
                } else {
                    session.setLastActivity(LocalDateTime.now());
                    sessionRepository.save(session);
                }
                break;
            }
        }
    }

    private void saveSnapshotToDiagram(DesignSession session, String optionalJson) {
        try {
            Diagram diagram = diagramRepository.findById(session.getDiagramId()).orElse(null);
            if (diagram == null) {
                return;
            }

            String jsonToSave = (optionalJson != null) ? optionalJson : session.getSnapshot();

            if (jsonToSave != null && !jsonToSave.equals("[]")) {
                List<Diagram.DiagramCell> cells = objectMapper.readValue(
                        jsonToSave,
                        new TypeReference<List<Diagram.DiagramCell>>() {
                        });
                diagram.setCells(cells);
                diagram.setUpdatedAt(LocalDateTime.now());
                diagramRepository.save(diagram);
            }
        } catch (Exception e) {
            System.err.println("❌ Falló el guardado final en el Diagrama: " + e.getMessage());
        }
    }

    @Transactional
    public void cleanupEmptySessions() {
        List<DesignSession> sessions = sessionRepository.findAll();
        LocalDateTime limit = LocalDateTime.now().minusMinutes(5);

        for (DesignSession session : sessions) {
            ensureCollections(session);
            pruneExpiredLocks(session);

            if (session.getLastActivity().isBefore(limit) || session.getActiveUsers().isEmpty()) {
                saveSnapshotToDiagram(session, null);
                sessionRepository.delete(session);
            } else {
                sessionRepository.save(session);
            }
        }
    }

    private void agregarUsuarioASesion(DesignSession session, String userId, String username, String color) {
        ensureCollections(session);

        boolean alreadyIn = session.getActiveUsers().stream()
                .anyMatch(u -> u.getUserId().equals(userId));

        if (!alreadyIn) {
            DesignSession.ActiveUser newUser = DesignSession.ActiveUser.builder()
                    .userId(userId)
                    .nombre(username)
                    .color(color)
                    .lastPing(LocalDateTime.now())
                    .build();
            session.getActiveUsers().add(newUser);
        }
    }

    private void ensureCollections(DesignSession session) {
        if (session.getActiveUsers() == null) {
            session.setActiveUsers(new ArrayList<>());
        }
        if (session.getOpsLog() == null) {
            session.setOpsLog(new ArrayList<>());
        }
        if (session.getActiveLocks() == null) {
            session.setActiveLocks(new ArrayList<>());
        }
    }

    private void pruneExpiredLocks(DesignSession session) {
        LocalDateTime cutoff = LocalDateTime.now().minusSeconds(LOCK_TIMEOUT_SECONDS);
        session.getActiveLocks().removeIf(lock -> lock.getLockedAt() == null || lock.getLockedAt().isBefore(cutoff));
    }

    private void touchLock(DesignSession session, String cellId, String userId) {
        if (cellId == null || userId == null) {
            return;
        }

        session.getActiveLocks().stream()
                .filter(lock -> cellId.equals(lock.getCellId()) && userId.equals(lock.getUserId()))
                .findFirst()
                .ifPresent(lock -> lock.setLockedAt(LocalDateTime.now()));
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> mergeMaps(Map<String, Object> base, Map<String, Object> incoming) {
        Map<String, Object> result = new java.util.HashMap<>();

        if (base != null) {
            result.putAll(base);
        }

        if (incoming == null) {
            return result;
        }

        for (Map.Entry<String, Object> entry : incoming.entrySet()) {
            String key = entry.getKey();
            Object incomingValue = entry.getValue();
            Object baseValue = result.get(key);

            if (baseValue instanceof Map && incomingValue instanceof Map) {
                result.put(
                        key,
                        mergeMaps(
                                (Map<String, Object>) baseValue,
                                (Map<String, Object>) incomingValue));
            } else {
                result.put(key, incomingValue);
            }
        }

        return result;
    }

    private List<Diagram.DiagramCell> readSnapshotCells(DesignSession session) throws Exception {
        String snapshot = session.getSnapshot();
        if (snapshot == null || snapshot.isBlank()) {
            return new ArrayList<>();
        }

        return objectMapper.readValue(
                snapshot,
                new TypeReference<List<Diagram.DiagramCell>>() {
                });
    }

    private void writeSnapshotCells(DesignSession session, List<Diagram.DiagramCell> cells) throws Exception {
        session.setSnapshot(objectMapper.writeValueAsString(cells));
    }
}