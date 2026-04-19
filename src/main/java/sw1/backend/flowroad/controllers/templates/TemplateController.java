package sw1.backend.flowroad.controllers.templates;

import java.util.List;

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
import sw1.backend.flowroad.dtos.templates.UpdateTemplateRequest;
import sw1.backend.flowroad.models.user.User;
import sw1.backend.flowroad.services.templates.TemplateService;

@RestController
@RequestMapping("/templates")
@RequiredArgsConstructor
public class TemplateController {

    private final TemplateService templateService;

    /**
     * 1. OBTENER TODAS LAS PLANTILLAS DE LA EMPRESA
     * Ideal para la tabla principal donde el DESIGNER administra los formularios.
     */
    @GetMapping
    @PreAuthorize("hasAnyAuthority('ADMIN', 'DESIGNER')")
    public ResponseEntity<List<TemplateResponse>> getAllByOrganization(@AuthenticationPrincipal User currentUser) {
        // Extraemos el orgId del Token JWT de forma segura
        return ResponseEntity.ok(templateService.getAllByOrganization(currentUser.getOrgId()));
    }

    /**
     * 2. OBTENER PLANTILLAS ACTIVAS POR DEPARTAMENTO
     * Lo usa Angular cuando un WORKER va a crear un reporte y necesita ver qué
     * formularios usar.
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
     * Se usa para cargar la plantilla en modo "Edición" en Angular o para
     * renderizarla.
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
     * Registra automáticamente quién la creó y a qué organización pertenece.
     */
    @PostMapping
    @PreAuthorize("hasAnyAuthority('ADMIN', 'DESIGNER')")
    public ResponseEntity<TemplateResponse> create(
            @Valid @RequestBody CreateTemplateRequest request,
            @AuthenticationPrincipal User currentUser) {

        TemplateResponse createdTemplate = templateService.create(
                request,
                currentUser.getOrgId(),
                currentUser.getId() // El ID del usuario logueado es el creador
        );
        return ResponseEntity.status(HttpStatus.CREATED).body(createdTemplate);
    }

    /**
     * 5. ACTUALIZAR PLANTILLA (Sube la versión a +1)
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
     * 6. ELIMINACIÓN LÓGICA (SOFT DELETE)
     * La desactiva para que ya no aparezca en los menús de los trabajadores.
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
     * La vuelve a activar para que aparezca en los menús de los trabajadores.
     */
    @PutMapping("/{id}/reactivate")
    @PreAuthorize("hasAnyAuthority('ADMIN', 'DESIGNER')")
    public ResponseEntity<TemplateResponse> reactivate(
            @PathVariable String id,
            @AuthenticationPrincipal User currentUser) {
        return ResponseEntity.ok(templateService.reactivate(id, currentUser.getOrgId()));
    }
}