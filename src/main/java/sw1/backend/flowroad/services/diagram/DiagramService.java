package sw1.backend.flowroad.services.diagram;

import java.io.IOException;
import java.text.Normalizer;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import com.fasterxml.jackson.databind.ObjectMapper;

import lombok.RequiredArgsConstructor;
import sw1.backend.flowroad.dtos.diagram.FlowroadDiagramData;
import sw1.backend.flowroad.dtos.diagram.FlowroadFile;
import sw1.backend.flowroad.models.diagram.DesignSession;
import sw1.backend.flowroad.models.diagram.Diagram;
import sw1.backend.flowroad.repository.diagram.DesignSessionRepository;
import sw1.backend.flowroad.repository.diagram.DiagramRepository;

@Service
@RequiredArgsConstructor
public class DiagramService {

    private static final int FLOWROAD_SCHEMA_VERSION = 1;
    private static final String FLOWROAD_APP_NAME = "FlowRoad";

    private final DiagramRepository diagramRepository;
    private final DesignSessionRepository designSessionRepository;
    private final ObjectMapper objectMapper;

    private final SimpMessagingTemplate messagingTemplate;

    @Transactional
    public Diagram createDiagram(String orgId, String name, String description, String userId) {
        if (diagramRepository.existsByOrgIdAndName(orgId, name)) {
            throw new RuntimeException("Ya existe un diagrama con el nombre '" + name + "' en tu organización.");
        }

        Diagram newDiagram = Diagram.builder()
                .orgId(orgId)
                .name(name)
                .description(description)
                .version(1)
                .isActive(true)
                .cells(new ArrayList<>())
                .lanes(new ArrayList<>())
                .createdAt(LocalDateTime.now())
                .createdBy(userId)
                .updatedAt(LocalDateTime.now())
                .build();

        return diagramRepository.save(newDiagram);
    }

    public List<Diagram> getAllDiagrams(String orgId) {
        return diagramRepository.findAllByOrgId(orgId);
    }

    public List<Diagram> getActiveDiagrams(String orgId) {
        return diagramRepository.findAllByOrgIdAndIsActiveTrue(orgId);
    }

    public Diagram getDiagramById(String id, String orgId) {
        return diagramRepository.findByIdAndOrgId(id, orgId)
                .orElseThrow(() -> new RuntimeException("Diagrama no encontrado o no pertenece a tu organización."));
    }

    @Transactional
    public Diagram updateMetadata(String id, String orgId, String newName, String newDescription) {
        Diagram diagram = getDiagramById(id, orgId);

        if (!diagram.getName().equals(newName) && diagramRepository.existsByOrgIdAndName(orgId, newName)) {
            throw new RuntimeException("El nombre '" + newName + "' ya está en uso por otro diagrama.");
        }

        diagram.setName(newName);
        diagram.setDescription(newDescription);
        diagram.setUpdatedAt(LocalDateTime.now());

        return diagramRepository.save(diagram);
    }

    @Transactional
    public Diagram toggleActiveStatus(String id, String orgId) {
        Diagram diagram = getDiagramById(id, orgId);

        diagram.setIsActive(!diagram.getIsActive());
        diagram.setUpdatedAt(LocalDateTime.now());

        return diagramRepository.save(diagram);
    }

    @Transactional
    public Diagram updateLanes(String id, String orgId, List<Diagram.DiagramLane> lanes) {
        Diagram diagram = getDiagramById(id, orgId);

        if (lanes == null) {
            throw new RuntimeException("La lista de lanes no puede ser nula.");
        }

        diagram.setLanes(lanes);
        diagram.setUpdatedAt(LocalDateTime.now());

        return diagramRepository.save(diagram);
    }

    public byte[] exportDiagramAsFlowroad(String id, String orgId) {
        try {
            Diagram diagram = getDiagramById(id, orgId);

            FlowroadFile exportFile = FlowroadFile.builder()
                    .schemaVersion(FLOWROAD_SCHEMA_VERSION)
                    .app(FLOWROAD_APP_NAME)
                    .exportedAt(LocalDateTime.now())
                    .diagram(FlowroadDiagramData.builder()
                            .name(diagram.getName())
                            .description(diagram.getDescription())
                            .cells(diagram.getCells() != null ? diagram.getCells() : new ArrayList<>())
                            .lanes(diagram.getLanes() != null ? diagram.getLanes() : new ArrayList<>())
                            .build())
                    .build();

            return objectMapper.writerWithDefaultPrettyPrinter().writeValueAsBytes(exportFile);
        } catch (Exception e) {
            throw new RuntimeException("No se pudo exportar el diagrama: " + e.getMessage(), e);
        }
    }

    public String buildExportFilename(String diagramName) {
        String safeBaseName = sanitizeFilename(diagramName);
        if (safeBaseName.isBlank()) {
            safeBaseName = "diagram";
        }
        return safeBaseName + ".flowroad";
    }

    @Transactional
    public Diagram importIntoExistingDiagram(String id, String orgId, MultipartFile file) {
        if (file == null || file.isEmpty()) {
            throw new RuntimeException("Debes seleccionar un archivo .flowroad válido.");
        }

        String originalFilename = file.getOriginalFilename();
        if (originalFilename == null || !originalFilename.toLowerCase().endsWith(".flowroad")) {
            throw new RuntimeException("El archivo debe tener extensión .flowroad.");
        }

        FlowroadFile importedFile;
        try {
            importedFile = objectMapper.readValue(file.getBytes(), FlowroadFile.class);
        } catch (IOException e) {
            throw new RuntimeException("No se pudo leer el archivo .flowroad: " + e.getMessage(), e);
        }

        validateFlowroadFile(importedFile);

        Diagram diagram = getDiagramById(id, orgId);
        FlowroadDiagramData importedDiagram = importedFile.getDiagram();

        String requestedName = importedDiagram.getName().trim();
        String importedName = resolveImportedDiagramName(orgId, diagram.getId(), requestedName);

        String importedDescription = importedDiagram.getDescription() != null
                ? importedDiagram.getDescription().trim()
                : "";

        List<Diagram.DiagramCell> importedCells = importedDiagram.getCells() != null ? importedDiagram.getCells()
                : new ArrayList<>();

        List<Diagram.DiagramLane> importedLanes = importedDiagram.getLanes() != null ? importedDiagram.getLanes()
                : new ArrayList<>();

        diagram.setName(importedName);
        diagram.setDescription(importedDescription);
        diagram.setCells(importedCells);
        diagram.setLanes(importedLanes);
        diagram.setUpdatedAt(LocalDateTime.now());
        diagram.setVersion((diagram.getVersion() != null ? diagram.getVersion() : 1) + 1);

        Diagram savedDiagram = diagramRepository.save(diagram);

        syncActiveSessionAfterImport(savedDiagram);

        return savedDiagram;
    }

    private void syncActiveSessionAfterImport(Diagram diagram) {
        try {
            Optional<DesignSession> existingSession = designSessionRepository.findByDiagramId(diagram.getId());
            if (existingSession.isEmpty()) {
                return;
            }

            DesignSession session = existingSession.get();

            String snapshot = objectMapper.writeValueAsString(
                    diagram.getCells() != null ? diagram.getCells() : new ArrayList<>());

            String lanesSnapshot = objectMapper.writeValueAsString(
                    diagram.getLanes() != null ? diagram.getLanes() : new ArrayList<>());

            session.setSnapshot(snapshot);
            session.setLanesSnapshot(lanesSnapshot);
            session.setLastActivity(LocalDateTime.now());

            if (session.getActiveLocks() == null) {
                session.setActiveLocks(new ArrayList<>());
            }
            session.getActiveLocks().clear();

            designSessionRepository.save(session);

            Map<String, Object> delta = new HashMap<>();
            delta.put("lanes", diagram.getLanes() != null ? diagram.getLanes() : new ArrayList<>());
            delta.put("cells", diagram.getCells() != null ? diagram.getCells() : new ArrayList<>());
            delta.put("name", diagram.getName());
            delta.put("description", diagram.getDescription());

            sw1.backend.flowroad.dtos.diagram.SocketOperationMessage message = new sw1.backend.flowroad.dtos.diagram.SocketOperationMessage();

            message.setOpType("SYNC_LANES");
            message.setCellId("lanes");
            message.setUserId("system-import");
            message.setDelta(delta);
            message.setDragId(null);

            messagingTemplate.convertAndSend(
                    "/topic/session/" + session.getSessionToken() + "/cambios",
                    message);
        } catch (Exception e) {
            throw new RuntimeException(
                    "Se importó el diagrama, pero falló la sincronización/broadcast de la sesión activa: "
                            + e.getMessage(),
                    e);
        }
    }

    private String resolveImportedDiagramName(String orgId, String currentDiagramId, String requestedName) {
        String baseName = requestedName;
        int counter = 1;

        while (true) {
            final String candidateName = (counter == 1)
                    ? baseName
                    : baseName + " (importado " + counter + ")";

            Optional<Diagram> existing = diagramRepository.findAllByOrgId(orgId).stream()
                    .filter(d -> d.getName().equals(candidateName))
                    .findFirst();

            if (existing.isEmpty() || existing.get().getId().equals(currentDiagramId)) {
                return candidateName;
            }

            counter++;
        }
    }

    private void validateFlowroadFile(FlowroadFile file) {
        if (file == null) {
            throw new RuntimeException("Archivo .flowroad inválido.");
        }

        if (file.getSchemaVersion() == null || file.getSchemaVersion() != FLOWROAD_SCHEMA_VERSION) {
            throw new RuntimeException("Versión de archivo .flowroad no compatible.");
        }

        if (file.getApp() == null || !FLOWROAD_APP_NAME.equals(file.getApp())) {
            throw new RuntimeException("El archivo no pertenece a FlowRoad o está corrupto.");
        }

        if (file.getDiagram() == null) {
            throw new RuntimeException("El archivo .flowroad no contiene un diagrama válido.");
        }

        if (file.getDiagram().getName() == null || file.getDiagram().getName().trim().isBlank()) {
            throw new RuntimeException("El archivo .flowroad no contiene un nombre de diagrama válido.");
        }

        if (file.getDiagram().getCells() == null) {
            throw new RuntimeException("El archivo .flowroad no contiene la lista de celdas.");
        }

        if (file.getDiagram().getLanes() == null) {
            throw new RuntimeException("El archivo .flowroad no contiene la lista de lanes.");
        }
    }

    private String sanitizeFilename(String input) {
        String normalized = Normalizer.normalize(input == null ? "" : input, Normalizer.Form.NFD)
                .replaceAll("\\p{M}", "");

        return normalized
                .replaceAll("[^a-zA-Z0-9-_ ]", "")
                .trim()
                .replaceAll("\\s+", "_")
                .toLowerCase();
    }
}