package sw1.backend.flowroad.controllers.document;

import java.util.List;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import sw1.backend.flowroad.dtos.document.DocumentRequirementRequest;
import sw1.backend.flowroad.dtos.document.DocumentRequirementResponse;
import sw1.backend.flowroad.models.user.User;
import sw1.backend.flowroad.services.document.DocumentRequirementService;

@RestController
@RequestMapping
@RequiredArgsConstructor
public class DocumentRequirementController {

    private final DocumentRequirementService service;

    @GetMapping("/diagrams/{diagramId}/nodes/{nodeId}/document-requirements")
    @PreAuthorize("hasAnyAuthority('ADMIN', 'DESIGNER')")
    public ResponseEntity<List<DocumentRequirementResponse>> listByNode(
            @PathVariable String diagramId,
            @PathVariable String nodeId,
            @AuthenticationPrincipal User currentUser) {
        return ResponseEntity.ok(service.listByNode(diagramId, nodeId, currentUser));
    }

    @PostMapping("/diagrams/{diagramId}/nodes/{nodeId}/document-requirements")
    @PreAuthorize("hasAnyAuthority('ADMIN', 'DESIGNER')")
    public ResponseEntity<DocumentRequirementResponse> create(
            @PathVariable String diagramId,
            @PathVariable String nodeId,
            @Valid @RequestBody DocumentRequirementRequest request,
            @AuthenticationPrincipal User currentUser) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(service.create(diagramId, nodeId, request, currentUser));
    }

    @PutMapping("/document-requirements/{requirementId}")
    @PreAuthorize("hasAnyAuthority('ADMIN', 'DESIGNER')")
    public ResponseEntity<DocumentRequirementResponse> update(
            @PathVariable String requirementId,
            @Valid @RequestBody DocumentRequirementRequest request,
            @AuthenticationPrincipal User currentUser) {
        return ResponseEntity.ok(service.update(requirementId, request, currentUser));
    }

    @DeleteMapping("/document-requirements/{requirementId}")
    @PreAuthorize("hasAnyAuthority('ADMIN', 'DESIGNER')")
    public ResponseEntity<Void> deactivate(
            @PathVariable String requirementId,
            @AuthenticationPrincipal User currentUser) {
        service.deactivate(requirementId, currentUser);
        return ResponseEntity.noContent().build();
    }

    @PutMapping("/document-requirements/{requirementId}/reactivate")
    @PreAuthorize("hasAnyAuthority('ADMIN', 'DESIGNER')")
    public ResponseEntity<DocumentRequirementResponse> reactivate(
            @PathVariable String requirementId,
            @AuthenticationPrincipal User currentUser) {
        return ResponseEntity.ok(service.reactivate(requirementId, currentUser));
    }
}
