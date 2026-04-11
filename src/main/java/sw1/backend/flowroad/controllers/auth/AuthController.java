package sw1.backend.flowroad.controllers.auth;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import sw1.backend.flowroad.dtos.auth.AuthResponse;
import sw1.backend.flowroad.dtos.auth.LoginRequest;
import sw1.backend.flowroad.dtos.auth.RegisterClientRequest;
import sw1.backend.flowroad.dtos.auth.RegisterCustomUserRequest;
import sw1.backend.flowroad.dtos.auth.RegisterWorkerRequest;
import sw1.backend.flowroad.services.auth.AuthService;

@RestController
@RequestMapping("/auth")
@RequiredArgsConstructor
public class AuthController {

    private final AuthService authService;

    // 1. Registro para clientes externos (Público)
    @PostMapping("/register-client")
    public ResponseEntity<AuthResponse> register(@Valid @RequestBody RegisterClientRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(authService.registerClient(request));
    }

    // 2. Registro para trabajadores (Solo ADMIN de Nissan puede crear empleados)
    @PostMapping("/register-worker")
    @PreAuthorize("hasAuthority('ADMIN')")
    public ResponseEntity<AuthResponse> registerWorker(@Valid @RequestBody RegisterWorkerRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(authService.registerWorker(request));
    }

    // 3. Registro Custom (Solo ROOT o ADMIN puede crear roles arbitrarios)
    @PostMapping("/register-custom")
    // @PreAuthorize("hasAuthority('ADMIN')")
    public ResponseEntity<AuthResponse> registerCustom(@Valid @RequestBody RegisterCustomUserRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(authService.registerCustomUser(request));
    }

    // 4. Login universal (Público)
    @PostMapping("/login")
    public ResponseEntity<AuthResponse> login(@Valid @RequestBody LoginRequest request) {
        return ResponseEntity.ok(authService.login(request));
    }
}