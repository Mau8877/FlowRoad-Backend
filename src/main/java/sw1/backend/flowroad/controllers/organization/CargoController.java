package sw1.backend.flowroad.controllers.organization;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import sw1.backend.flowroad.dtos.organization.CargoResponse;
import sw1.backend.flowroad.dtos.organization.CreateCargoRequest;
import sw1.backend.flowroad.dtos.organization.UpdateCargoRequest;
import sw1.backend.flowroad.services.organization.CargoService;

import java.util.List;

@RestController
@RequestMapping("/cargos") // Ruta final: /api/v1/cargos
@RequiredArgsConstructor
public class CargoController {

    private final CargoService service;

    // 1. Crear un nuevo cargo
    @PostMapping
    @PreAuthorize("hasAuthority('ADMIN')")
    public ResponseEntity<CargoResponse> create(@Valid @RequestBody CreateCargoRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(service.create(request));
    }

    // 2. Obtener un cargo por su ID
    @GetMapping("/{id}")
    @PreAuthorize("hasAuthority('ADMIN')")
    public ResponseEntity<CargoResponse> getById(@PathVariable String id) {
        return ResponseEntity.ok(service.getById(id));
    }

    // 3. Listar todos los cargos de una organización específica
    @GetMapping("/organization/{orgId}")
    @PreAuthorize("hasAuthority('ADMIN')")
    public ResponseEntity<List<CargoResponse>> getByOrganization(@PathVariable String orgId) {
        return ResponseEntity.ok(service.getByOrganization(orgId));
    }

    // 4. Actualizar un cargo existente
    @PatchMapping("/{id}")
    @PreAuthorize("hasAuthority('ADMIN')")
    public ResponseEntity<CargoResponse> update(
            @PathVariable String id,
            @Valid @RequestBody UpdateCargoRequest request) {
        return ResponseEntity.ok(service.update(id, request));
    }

    // 5. Eliminación lógica (Desactivar)
    @DeleteMapping("/{id}")
    @PreAuthorize("hasAuthority('ADMIN')")
    public ResponseEntity<Void> delete(@PathVariable String id) {
        service.delete(id);
        return ResponseEntity.noContent().build();
    }
}