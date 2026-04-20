package sw1.backend.flowroad.services.diagram;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashMap;
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

    private final DesignSessionRepository sessionRepository;
    private final DiagramRepository diagramRepository;
    private final ObjectMapper objectMapper;

    @Transactional
    public DesignSession getOrCreateSession(String diagramId, String userId, String username, String color) {
        Optional<DesignSession> existingSession = sessionRepository.findByDiagramId(diagramId);

        if (existingSession.isPresent()) {
            DesignSession session = existingSession.get();
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
                .startedAt(LocalDateTime.now())
                .lastActivity(LocalDateTime.now())
                .build();

        agregarUsuarioASesion(newSession, userId, username, color);
        return sessionRepository.save(newSession);
    }

    /**
     * PASO 2: Registra la operación y SINCRONIZA el snapshot.
     */
    public void recordOperation(String sessionToken, DesignSession.OperationLog operation) {
        DesignSession session = sessionRepository.findBySessionToken(sessionToken)
                .orElseThrow(() -> new RuntimeException("Sesión caducada o inexistente"));

        operation.setTimestamp(LocalDateTime.now());
        session.getOpsLog().add(operation);

        // Si es un movimiento, actualizamos el snapshot interno de la sesión
        if ("MOVE".equals(operation.getOpType())) {
            actualizarPosicionEnSnapshot(session, operation);
        }

        session.setLastActivity(LocalDateTime.now());
        sessionRepository.save(session);
    }

    /**
     * Maneja el JSON del snapshot de forma segura.
     * Corregido: Acceso manual al Map de delta para evitar errores de tipo.
     */
    private void actualizarPosicionEnSnapshot(DesignSession session, DesignSession.OperationLog op) {
        try {
            // 1. Cargamos lo que tenemos (puede ser "[]")
            List<Diagram.DiagramCell> cells = objectMapper.readValue(
                    session.getSnapshot(),
                    new TypeReference<List<Diagram.DiagramCell>>() {
                    });

            Map<String, Object> delta = (Map<String, Object>) op.getDelta();
            double newX = Double.parseDouble(delta.get("x").toString());
            double newY = Double.parseDouble(delta.get("y").toString());

            // 2. Buscamos el nodo
            Diagram.DiagramCell targetCell = cells.stream()
                    .filter(c -> c.getId().equals(op.getNodeId()))
                    .findFirst()
                    .orElse(null);

            if (targetCell != null) {
                // Caso A: El nodo ya existía en el snapshot
                if (targetCell.getPosition() == null)
                    targetCell.setPosition(new Diagram.Position());
                targetCell.getPosition().setX(newX);
                targetCell.getPosition().setY(newY);
            } else {
                // Definimos atributos básicos para que JointJS no lo ignore
                Map<String, Object> bodyAttrs = new HashMap<>();
                bodyAttrs.put("fill", "#ffffff");
                bodyAttrs.put("stroke", "#541f14");
                bodyAttrs.put("strokeWidth", 2);
                bodyAttrs.put("rx", 20);
                bodyAttrs.put("ry", 20);

                Map<String, Object> labelAttrs = new HashMap<>();
                labelAttrs.put("text", "Nueva Actividad");
                labelAttrs.put("fill", "#020304");

                Map<String, Object> attrs = new HashMap<>();
                attrs.put("body", bodyAttrs);
                attrs.put("label", labelAttrs);

                Diagram.DiagramCell newCell = Diagram.DiagramCell.builder()
                        .id(op.getNodeId())
                        .type("standard.Rectangle")
                        .position(new Diagram.Position(newX, newY))
                        .size(new Diagram.Size(160, 60))
                        .attrs(attrs) // <--- ESTO ES LO QUE FALTABA
                        .build();
                cells.add(newCell);
                System.out.println("🚀 Nodo '" + op.getNodeId() + "' agregado al snapshot por primera vez.");
            }

            // 3. Guardamos el nuevo estado en Atlas
            session.setSnapshot(objectMapper.writeValueAsString(cells));

        } catch (Exception e) {
            System.err.println("❌ Error sincronizando snapshot: " + e.getMessage());
        }
    }

    public void pingUser(String sessionToken, String userId, double cursorX, double cursorY) {
        DesignSession session = sessionRepository.findBySessionToken(sessionToken).orElse(null);
        if (session == null)
            return;

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
            boolean removed = session.getActiveUsers()
                    .removeIf(u -> u.getUserId().equals(realUserId));

            if (removed) {
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

    /**
     * Persistencia final: Guarda el snapshot de la sesión en el Diagrama real.
     */
    private void saveSnapshotToDiagram(DesignSession session, String optionalJson) {
        try {
            Diagram diagram = diagramRepository.findById(session.getDiagramId()).orElse(null);
            if (diagram == null)
                return;

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
        LocalDateTime limit = LocalDateTime.now().minusMinutes(5); // Aumentamos a 5 min para ser menos agresivos

        for (DesignSession session : sessions) {
            if (session.getLastActivity().isBefore(limit) || session.getActiveUsers().isEmpty()) {
                saveSnapshotToDiagram(session, null);
                sessionRepository.delete(session);
            }
        }
    }

    private void agregarUsuarioASesion(DesignSession session, String userId, String username, String color) {
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
}