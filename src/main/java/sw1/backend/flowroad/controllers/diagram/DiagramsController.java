package sw1.backend.flowroad.controllers.diagram;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.CrossOrigin;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import sw1.backend.flowroad.dtos.diagram.DiagramSummaryResponse;
import sw1.backend.flowroad.models.diagram.Diagram;
import sw1.backend.flowroad.models.user.User;
import sw1.backend.flowroad.repository.diagram.DiagramRepository;
import sw1.backend.flowroad.security.JwtService;
import sw1.backend.flowroad.services.diagram.DiagramService;

@RestController
@RequestMapping("/diagrams") // Mantenemos el estándar en plural
@RequiredArgsConstructor
@CrossOrigin(origins = "*")
public class DiagramsController {

    private final DiagramService diagramService;
    private final DiagramRepository diagramRepository;
    private final JwtService jwtService; // Inyectado para que funcionen los debugs

    // =========================================================================
    // SECTION: DEBUGGING TOOLS (Para rastrear errores de Token/Usuario)
    // =========================================================================

    /**
     * Revisa qué objeto está guardado en el Contexto de Seguridad de Spring.
     */
    @GetMapping("/debug")
    public ResponseEntity<?> debug(Authentication authentication) {
        if (authentication == null)
            return ResponseEntity.status(401).body("No hay autenticación activa");

        Object principal = authentication.getPrincipal();
        Map<String, Object> response = new HashMap<>();
        response.put("principalClass", principal.getClass().getName());

        if (principal instanceof User user) {
            response.put("userId", user.getId());
            response.put("orgId", user.getOrgId());
            response.put("role", user.getRole());
        } else {
            response.put("error", "El principal no es una instancia de la clase User");
        }
        return ResponseEntity.ok(response);
    }

    /**
     * Test rápido para verificar si @AuthenticationPrincipal está inyectando bien.
     */
    @GetMapping("/test")
    public ResponseEntity<?> test(@AuthenticationPrincipal User user) {
        if (user == null)
            return ResponseEntity.status(401).body("Usuario nulo en SecurityContext");
        return ResponseEntity.ok(Map.of(
                "orgId", user.getOrgId(),
                "userId", user.getId(),
                "email", user.getUsername()));
    }

    /**
     * Decodifica el Token manualmente para ver qué trae adentro.
     */
    @GetMapping("/debug-token")
    public ResponseEntity<?> debugToken(HttpServletRequest request) {
        String authHeader = request.getHeader("Authorization");
        if (authHeader == null || !authHeader.startsWith("Bearer ")) {
            return ResponseEntity.badRequest().body("Falta el Header de Authorization o no es Bearer");
        }

        String jwt = authHeader.substring(7);
        Map<String, Object> response = new HashMap<>();
        try {
            response.put("username", jwtService.extractUsername(jwt));
            response.put("userId", jwtService.extractUserId(jwt));
            response.put("orgId", jwtService.extractOrgId(jwt));
            response.put("role", jwtService.extractRole(jwt));
        } catch (Exception e) {
            response.put("error", "Error procesando token: " + e.getMessage());
        }
        return ResponseEntity.ok(response);
    }

    // =========================================================================
    // SECTION: CORE DIAGRAM OPERATIONS (CRUD)
    // =========================================================================

    /**
     * 1. OBTENER RESUMEN: Lista todos los diagramas de la organización del usuario.
     */
    @GetMapping
    @PreAuthorize("hasAnyAuthority('ADMIN', 'DESIGNER')")
    public ResponseEntity<List<DiagramSummaryResponse>> getAllByOrganization(
            @AuthenticationPrincipal User currentUser) {

        return ResponseEntity.ok(
                diagramService.getAllDiagrams(currentUser.getOrgId())
                        .stream()
                        .map(this::mapToSummary)
                        .collect(Collectors.toList()));
    }

    /**
     * 2. CREAR: Genera un nuevo diagrama con nombre único basado en timestamp.
     */
    @PostMapping
    @PreAuthorize("hasAnyAuthority('ADMIN', 'DESIGNER')")
    public ResponseEntity<DiagramSummaryResponse> create(
            @AuthenticationPrincipal User currentUser) {

        String uniqueId = java.util.UUID.randomUUID().toString().substring(0, 5).toUpperCase();
        String timestamp = java.time.format.DateTimeFormatter.ofPattern("ddMMyy-HHmm")
                .format(java.time.LocalDateTime.now());

        String defaultName = "Nuevo-" + timestamp + "-" + uniqueId;
        String defaultDescription = "Creado por " + currentUser.getUsername();

        Diagram created = diagramService.createDiagram(
                currentUser.getOrgId(),
                defaultName,
                defaultDescription,
                currentUser.getId());

        return ResponseEntity.status(HttpStatus.CREATED).body(mapToSummary(created));
    }

    /**
     * 3. LECTURA: Obtiene el diagrama completo (incluyendo el JSON de las celdas).
     */
    @GetMapping("/{id}")
    @PreAuthorize("hasAnyAuthority('ADMIN', 'DESIGNER')")
    public ResponseEntity<Diagram> getById(
            @PathVariable String id,
            @AuthenticationPrincipal User currentUser) {

        return ResponseEntity.ok(
                diagramService.getDiagramById(id, currentUser.getOrgId()));
    }

    /**
     * 4. ACTUALIZAR: Modifica nombre y descripción.
     */
    @PutMapping("/{id}")
    @PreAuthorize("hasAnyAuthority('ADMIN', 'DESIGNER')")
    public ResponseEntity<DiagramSummaryResponse> updateMetadata(
            @PathVariable String id,
            @RequestParam String name,
            @RequestParam String description,
            @AuthenticationPrincipal User currentUser) {

        Diagram updated = diagramService.updateMetadata(
                id,
                currentUser.getOrgId(),
                name,
                description);

        return ResponseEntity.ok(mapToSummary(updated));
    }

    /**
     * 5. ESTADO: Activa o desactiva (borrado lógico) un diagrama.
     */
    @DeleteMapping("/{id}")
    @PreAuthorize("hasAnyAuthority('ADMIN', 'DESIGNER')")
    public ResponseEntity<Void> toggleActive(
            @PathVariable String id,
            @AuthenticationPrincipal User currentUser) {

        diagramService.toggleActiveStatus(id, currentUser.getOrgId());
        return ResponseEntity.noContent().build();
    }

    /**
     * 6. IMPORTAR: Crea un diagrama a partir de un archivo .flowroad (JSON).
     */
    @PostMapping("/import")
    @PreAuthorize("hasAnyAuthority('ADMIN', 'DESIGNER')")
    public ResponseEntity<Diagram> importDiagram(
            @RequestBody Diagram importedDiagram,
            @AuthenticationPrincipal User currentUser) {

        importedDiagram.setId(null); // Forzamos creación de nuevo ID en Mongo
        importedDiagram.setOrgId(currentUser.getOrgId());
        importedDiagram.setCreatedAt(java.time.LocalDateTime.now());

        return ResponseEntity.status(HttpStatus.CREATED)
                .body(diagramRepository.save(importedDiagram));
    }

    // =========================================================================
    // SECTION: HELPERS
    // =========================================================================

    /**
     * Convierte la entidad Diagram en un DTO ligero para las tablas.
     */
    private DiagramSummaryResponse mapToSummary(Diagram diagram) {
        return DiagramSummaryResponse.builder()
                .id(diagram.getId())
                .name(diagram.getName())
                .description(diagram.getDescription())
                .version(diagram.getVersion())
                .isActive(diagram.getIsActive())
                .updatedAt(diagram.getUpdatedAt())
                .build();
    }
}