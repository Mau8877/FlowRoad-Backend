package sw1.backend.flowroad.controllers.user;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import sw1.backend.flowroad.dtos.user.UserResponse;
import sw1.backend.flowroad.dtos.user.UpdateUserRequest;
import sw1.backend.flowroad.models.user.User;
import sw1.backend.flowroad.services.user.UserService;

import java.util.List;

@RestController
@RequestMapping("/users")
@RequiredArgsConstructor
public class UserController {

    private final UserService userService;

    /**
     * 1. OBTENER MI PERFIL
     * Usa @AuthenticationPrincipal para obtener el usuario del contexto de
     * seguridad (JWT)
     */
    @GetMapping("/me")
    public ResponseEntity<UserResponse> getMyProfile(@AuthenticationPrincipal User currentUser) {
        return ResponseEntity.ok(userService.getById(currentUser.getId()));
    }

    /**
     * 2. OBTENER USUARIO POR ID
     */
    @GetMapping("/{id}")
    @PreAuthorize("hasAuthority('ADMIN')")
    public ResponseEntity<UserResponse> getById(@PathVariable String id) {
        return ResponseEntity.ok(userService.getById(id));
    }

    /**
     * 3. LISTAR USUARIOS POR ORGANIZACIÓN (NISSAN)
     */
    @GetMapping("/organization/{orgId}")
    @PreAuthorize("hasAuthority('ADMIN')")
    public ResponseEntity<List<UserResponse>> getByOrganization(@PathVariable String orgId) {
        return ResponseEntity.ok(userService.getByOrganization(orgId));
    }

    /**
     * 4. ACTUALIZAR PERFIL / DATOS
     */
    @PatchMapping("/{id}")
    @PreAuthorize("hasAuthority('ADMIN') or #id == authentication.principal.id")
    public ResponseEntity<UserResponse> update(
            @PathVariable String id,
            @Valid @RequestBody UpdateUserRequest request) {
        return ResponseEntity.ok(userService.update(id, request));
    }

    /**
     * 5. ELIMINACIÓN LÓGICA (SOFT DELETE)
     */
    @DeleteMapping("/{id}")
    @PreAuthorize("hasAuthority('ADMIN')")
    public ResponseEntity<Void> delete(@PathVariable String id) {
        userService.delete(id);
        return ResponseEntity.noContent().build();
    }
}