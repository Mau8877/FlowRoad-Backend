package sw1.backend.flowroad.controllers.organization;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import org.springframework.security.core.annotation.AuthenticationPrincipal;

import sw1.backend.flowroad.dtos.organization.DepartmentResponse;
import sw1.backend.flowroad.dtos.organization.CreateDepartmentRequest;
import sw1.backend.flowroad.dtos.organization.UpdateDepartmentRequest;
import sw1.backend.flowroad.services.organization.DepartmentService;
import sw1.backend.flowroad.models.user.User;

import java.util.List;

@RestController
@RequestMapping("/departments")
@RequiredArgsConstructor
public class DepartmentController {

    private final DepartmentService service;

    // 1. Crear un departamento
    @PostMapping
    @PreAuthorize("hasAuthority('ADMIN')")
    public ResponseEntity<DepartmentResponse> create(@Valid @RequestBody CreateDepartmentRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(service.create(request));
    }

    // 2. Obtener un departamento por ID
    @GetMapping("/{id}")
    @PreAuthorize("hasAuthority('ADMIN')")
    public ResponseEntity<DepartmentResponse> getById(@PathVariable String id) {
        return ResponseEntity.ok(service.getById(id));
    }

    // 3. Listar todos los departamentos de una organización
    @GetMapping("/organization/{orgId}")
    @PreAuthorize("hasAuthority('ADMIN')")
    public ResponseEntity<List<DepartmentResponse>> getByOrganization(@PathVariable String orgId) {
        return ResponseEntity.ok(service.getByOrganization(orgId));
    }

    // 4. Actualizar departamento
    @PatchMapping("/{id}")
    @PreAuthorize("hasAuthority('ADMIN')")
    public ResponseEntity<DepartmentResponse> update(
            @PathVariable String id,
            @Valid @RequestBody UpdateDepartmentRequest request) {
        return ResponseEntity.ok(service.update(id, request));
    }

    // 5. Eliminación lógica
    @DeleteMapping("/{id}")
    @PreAuthorize("hasAuthority('ADMIN')")
    public ResponseEntity<Void> delete(@PathVariable String id) {
        service.delete(id);
        return ResponseEntity.noContent().build();
    }

    @GetMapping("/my-organization")
    @PreAuthorize("hasAnyAuthority('ADMIN', 'DESIGNER')")
    public ResponseEntity<List<DepartmentResponse>> getMyOrganizationDepartments(
            @AuthenticationPrincipal User currentUser) {
        return ResponseEntity.ok(service.getByOrganization(currentUser.getOrgId()));
    }
}