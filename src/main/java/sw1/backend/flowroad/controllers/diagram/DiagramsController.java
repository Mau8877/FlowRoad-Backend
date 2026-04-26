package sw1.backend.flowroad.controllers.diagram;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
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
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import sw1.backend.flowroad.dtos.diagram.DiagramSummaryResponse;
import sw1.backend.flowroad.models.diagram.Diagram;
import sw1.backend.flowroad.models.user.User;
import sw1.backend.flowroad.security.JwtService;
import sw1.backend.flowroad.services.diagram.DiagramService;

@RestController
@RequestMapping("/diagrams")
@RequiredArgsConstructor
@CrossOrigin(origins = "*")
public class DiagramsController {

    private final DiagramService diagramService;
    private final JwtService jwtService;

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

    @GetMapping("/test")
    public ResponseEntity<?> test(@AuthenticationPrincipal User user) {
        if (user == null)
            return ResponseEntity.status(401).body("Usuario nulo en SecurityContext");
        return ResponseEntity.ok(Map.of(
                "orgId", user.getOrgId(),
                "userId", user.getId(),
                "email", user.getUsername()));
    }

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

    @GetMapping("/{id}")
    @PreAuthorize("hasAnyAuthority('ADMIN', 'DESIGNER')")
    public ResponseEntity<Diagram> getById(
            @PathVariable String id,
            @AuthenticationPrincipal User currentUser) {

        return ResponseEntity.ok(
                diagramService.getDiagramById(id, currentUser.getOrgId()));
    }

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

    @DeleteMapping("/{id}")
    @PreAuthorize("hasAnyAuthority('ADMIN', 'DESIGNER')")
    public ResponseEntity<Void> toggleActive(
            @PathVariable String id,
            @AuthenticationPrincipal User currentUser) {

        diagramService.toggleActiveStatus(id, currentUser.getOrgId());
        return ResponseEntity.noContent().build();
    }

    @PutMapping("/{id}/lanes")
    @PreAuthorize("hasAnyAuthority('ADMIN', 'DESIGNER')")
    public ResponseEntity<Diagram> updateLanes(
            @PathVariable String id,
            @RequestBody Diagram request,
            @AuthenticationPrincipal User currentUser) {

        Diagram updated = diagramService.updateLanes(
                id,
                currentUser.getOrgId(),
                request.getLanes());

        return ResponseEntity.ok(updated);
    }

    @GetMapping("/{id}/export")
    @PreAuthorize("hasAnyAuthority('ADMIN', 'DESIGNER')")
    public ResponseEntity<byte[]> exportDiagram(
            @PathVariable String id,
            @AuthenticationPrincipal User currentUser) {

        Diagram diagram = diagramService.getDiagramById(id, currentUser.getOrgId());
        byte[] fileContent = diagramService.exportDiagramAsFlowroad(id, currentUser.getOrgId());
        String filename = diagramService.buildExportFilename(diagram.getName());

        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_OCTET_STREAM);
        headers.setContentDisposition(ContentDisposition.attachment().filename(filename).build());

        return new ResponseEntity<>(fileContent, headers, HttpStatus.OK);
    }

    @PostMapping(value = "/{id}/import", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @PreAuthorize("hasAnyAuthority('ADMIN', 'DESIGNER')")
    public ResponseEntity<Diagram> importIntoCurrentDiagram(
            @PathVariable String id,
            @RequestPart("file") MultipartFile file,
            @AuthenticationPrincipal User currentUser) {

        Diagram updated = diagramService.importIntoExistingDiagram(id, currentUser.getOrgId(), file);
        return ResponseEntity.ok(updated);
    }

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