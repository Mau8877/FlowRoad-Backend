package sw1.backend.flowroad.controllers.organization;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.security.access.prepost.PreAuthorize;

import sw1.backend.flowroad.dtos.organization.CreateOrganizationRequest;
import sw1.backend.flowroad.dtos.organization.OrganizationResponse;
import sw1.backend.flowroad.dtos.organization.UpdateOrganizationRequest;
import sw1.backend.flowroad.services.organization.OrganizationService;

import java.util.List;

@RestController
@RequestMapping("/organizations")
@RequiredArgsConstructor
public class OrganizationController {

    private final OrganizationService service;

    @GetMapping
    @PreAuthorize("hasAuthority('ADMIN')")
    public ResponseEntity<List<OrganizationResponse>> getAll() {
        return ResponseEntity.ok(service.getAllActive());
    }

    @GetMapping("/{id}")
    @PreAuthorize("hasAuthority('ADMIN')")
    public ResponseEntity<OrganizationResponse> getById(@PathVariable String id) {
        // La excepción ResourceNotFoundException se encarga del 404 si no existe
        return ResponseEntity.ok(service.getById(id));
    }

    @PostMapping
    @PreAuthorize("hasAuthority('ADMIN')")
    public ResponseEntity<OrganizationResponse> create(@Valid @RequestBody CreateOrganizationRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(service.create(request));
    }

    @PatchMapping("/{id}")
    @PreAuthorize("hasAuthority('ADMIN')")
    public ResponseEntity<OrganizationResponse> update(
            @PathVariable String id,
            @Valid @RequestBody UpdateOrganizationRequest request) {
        return ResponseEntity.ok(service.update(id, request));
    }

    @DeleteMapping("/{id}")
    @PreAuthorize("hasAuthority('ADMIN')")
    public ResponseEntity<Void> delete(@PathVariable String id) {
        service.softDelete(id);
        return ResponseEntity.noContent().build();
    }

    @PatchMapping("/reactivate/{code}")
    @PreAuthorize("hasAuthority('ADMIN')")
    public ResponseEntity<Void> reactivate(@PathVariable String code) {
        service.reactivate(code);
        return ResponseEntity.ok().build();
    }
}