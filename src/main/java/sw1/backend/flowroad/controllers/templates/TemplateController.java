package sw1.backend.flowroad.controllers.templates;

import java.util.List;
import java.util.Map;

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

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import sw1.backend.flowroad.dtos.templates.CreateTemplateRequest;
import sw1.backend.flowroad.dtos.templates.TemplateResponse;
import sw1.backend.flowroad.dtos.templates.TemplateSummaryResponse;
import sw1.backend.flowroad.dtos.templates.UpdateTemplateRequest;
import sw1.backend.flowroad.models.user.User;
import sw1.backend.flowroad.services.templates.TemplateService;

@RestController
@RequestMapping("/templates")
@RequiredArgsConstructor
public class TemplateController {

    private final TemplateService templateService;

    @GetMapping("/test")
    public ResponseEntity<?> test(@AuthenticationPrincipal User user) {
        return ResponseEntity.ok(Map.of(
                "orgId", user.getOrgId(),
                "userId", user.getId(),
                "email", user.getUsername()));
    }

    /**
     * 1. OBTENER TODAS LAS PLANTILLAS DE LA EMPRESA
     */
    @GetMapping
    @PreAuthorize("hasAnyAuthority('ADMIN', 'DESIGNER')")
    public ResponseEntity<List<TemplateResponse>> getAllByOrganization(@AuthenticationPrincipal User currentUser) {
        return ResponseEntity.ok(templateService.getAllByOrganization(currentUser.getOrgId()));
    }

    /**
     * NUEVO: OBTENER RESUMEN DE PLANTILLAS ACTIVAS DE MI EMPRESA
     * Ideal para selects livianos en el editor de diagramas.
     */
    @GetMapping("/my-organization/summary")
    @PreAuthorize("hasAnyAuthority('ADMIN', 'DESIGNER', 'WORKER')")
    public ResponseEntity<List<TemplateSummaryResponse>> getSummaryByMyOrganization(
            @AuthenticationPrincipal User currentUser) {
        return ResponseEntity.ok(templateService.getSummaryByOrganization(currentUser.getOrgId()));
    }

    /**
     * 2. OBTENER PLANTILLAS ACTIVAS POR DEPARTAMENTO
     */
    @GetMapping("/department/{departmentId}")
    @PreAuthorize("hasAnyAuthority('ADMIN', 'DESIGNER', 'WORKER')")
    public ResponseEntity<List<TemplateResponse>> getActiveByDepartment(
            @PathVariable String departmentId,
            @AuthenticationPrincipal User currentUser) {
        return ResponseEntity.ok(templateService.getActiveByDepartment(currentUser.getOrgId(), departmentId));
    }

    /**
     * 3. OBTENER PLANTILLA POR ID
     */
    @GetMapping("/{id}")
    @PreAuthorize("hasAnyAuthority('ADMIN', 'DESIGNER', 'WORKER')")
    public ResponseEntity<TemplateResponse> getById(
            @PathVariable String id,
            @AuthenticationPrincipal User currentUser) {
        return ResponseEntity.ok(templateService.getById(id, currentUser.getOrgId()));
    }

    /**
     * 4. CREAR NUEVA PLANTILLA
     */
    @PostMapping
    @PreAuthorize("hasAnyAuthority('ADMIN', 'DESIGNER')")
    public ResponseEntity<TemplateResponse> create(
            @Valid @RequestBody CreateTemplateRequest request,
            @AuthenticationPrincipal User currentUser) {

        TemplateResponse createdTemplate = templateService.create(
                request,
                currentUser.getOrgId(),
                currentUser.getId());
        return ResponseEntity.status(HttpStatus.CREATED).body(createdTemplate);
    }

    /**
     * 5. ACTUALIZAR PLANTILLA
     */
    @PutMapping("/{id}")
    @PreAuthorize("hasAnyAuthority('ADMIN', 'DESIGNER')")
    public ResponseEntity<TemplateResponse> update(
            @PathVariable String id,
            @Valid @RequestBody UpdateTemplateRequest request,
            @AuthenticationPrincipal User currentUser) {
        return ResponseEntity.ok(templateService.update(id, request, currentUser.getOrgId()));
    }

    /**
     * 6. ELIMINACIÓN LÓGICA
     */
    @DeleteMapping("/{id}")
    @PreAuthorize("hasAnyAuthority('ADMIN', 'DESIGNER')")
    public ResponseEntity<Void> delete(
            @PathVariable String id,
            @AuthenticationPrincipal User currentUser) {
        templateService.softDelete(id, currentUser.getOrgId());
        return ResponseEntity.noContent().build();
    }

    /**
     * 7. REACTIVAR PLANTILLA
     */
    @PutMapping("/{id}/reactivate")
    @PreAuthorize("hasAnyAuthority('ADMIN', 'DESIGNER')")
    public ResponseEntity<TemplateResponse> reactivate(
            @PathVariable String id,
            @AuthenticationPrincipal User currentUser) {
        return ResponseEntity.ok(templateService.reactivate(id, currentUser.getOrgId()));
    }
}