package sw1.backend.flowroad.services.auth;

import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.Map;

import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

import lombok.RequiredArgsConstructor;
import sw1.backend.flowroad.dtos.auth.AuthResponse;
import sw1.backend.flowroad.dtos.auth.LoginRequest;
import sw1.backend.flowroad.dtos.auth.RegisterClientRequest;
import sw1.backend.flowroad.dtos.auth.RegisterCustomUserRequest;
import sw1.backend.flowroad.dtos.auth.RegisterWorkerRequest;
import sw1.backend.flowroad.dtos.user.UserProfileRequest;
import sw1.backend.flowroad.exceptions.AuthException;
import sw1.backend.flowroad.exceptions.ResourceNotFoundException;
import sw1.backend.flowroad.models.user.Roles;
import sw1.backend.flowroad.models.user.User;
import sw1.backend.flowroad.models.user.UserProfile;
import sw1.backend.flowroad.repository.organization.CargoRepository;
import sw1.backend.flowroad.repository.organization.DepartmentRepository;
import sw1.backend.flowroad.repository.organization.OrganizationRepository;
import sw1.backend.flowroad.repository.user.UserRepository;
import sw1.backend.flowroad.security.JwtService;

@Service
@RequiredArgsConstructor
public class AuthService {

    private final UserRepository userRepository;
    private final OrganizationRepository organizationRepository;
    private final DepartmentRepository departmentRepository;
    private final CargoRepository cargoRepository;
    private final PasswordEncoder passwordEncoder;
    private final JwtService jwtService;
    private final AuthenticationManager authenticationManager;

    /**
     * REGISTRO DE CLIENTE (Público)
     */
    public AuthResponse registerClient(RegisterClientRequest request) {
        if (userRepository.existsByEmail(request.email())) {
            throw new AuthException("El correo " + request.email() + " ya está registrado.");
        }

        User user = User.builder()
                .email(request.email())
                .password(passwordEncoder.encode(request.password()))
                .role(Roles.CLIENT)
                .isActive(true)
                .workload(0)
                .profile(mapToProfileEntity(request.profile()))
                .createdAt(LocalDateTime.now())
                .build();

        userRepository.save(user);
        return generateAuthResponse(user, "Registro de cliente exitoso");
    }

    /**
     * REGISTRO DE TRABAJADOR (Administrativo)
     */
    public AuthResponse registerWorker(RegisterWorkerRequest request) {
        if (userRepository.existsByEmail(request.email())) {
            throw new AuthException("El correo ya está en uso.");
        }

        if (!organizationRepository.existsById(request.orgId())) {
            throw new ResourceNotFoundException("La organización no existe.");
        }

        // 1. PRIMERO validamos la regla de negocio: Si es WORKER, es obligatorio que
        // vengan los IDs
        if (request.role() == Roles.WORKER) {
            if (request.departmentId() == null || request.cargoId() == null) {
                throw new IllegalArgumentException(
                        "Los trabajadores operativos deben tener un departamento y cargo asignado.");
            }
        }

        // 2. LUEGO, consultamos a la base de datos SOLO si los IDs no son nulos
        if (request.departmentId() != null && !departmentRepository.existsById(request.departmentId())) {
            throw new ResourceNotFoundException("El departamento no existe.");
        }

        if (request.cargoId() != null && !cargoRepository.existsById(request.cargoId())) {
            throw new ResourceNotFoundException("El cargo no existe.");
        }

        // 3. Finalmente, guardamos
        User user = User.builder()
                .email(request.email())
                .password(passwordEncoder.encode(request.password()))
                .role(request.role())
                .orgId(request.orgId())
                .departmentId(request.departmentId())
                .cargoId(request.cargoId())
                .isActive(true)
                .workload(0)
                .profile(mapToProfileEntity(request.profile()))
                .createdAt(LocalDateTime.now())
                .build();

        userRepository.save(user);
        return generateAuthResponse(user, "Trabajador registrado con éxito");
    }

    /**
     * REGISTRO CUSTOM (Admin / Root)
     */
    public AuthResponse registerCustomUser(RegisterCustomUserRequest request) {
        if (userRepository.existsByEmail(request.email())) {
            throw new AuthException("El correo ya existe.");
        }

        User user = User.builder()
                .email(request.email())
                .password(passwordEncoder.encode(request.password()))
                .role(request.role())
                .orgId(request.orgId())
                .isActive(true)
                .workload(0)
                .profile(mapToProfileEntity(request.profile()))
                .createdAt(LocalDateTime.now())
                .build();

        userRepository.save(user);
        return generateAuthResponse(user, "Usuario con rol " + user.getRole() + " creado.");
    }

    /**
     * LOGIN UNIVERSAL
     */
    public AuthResponse login(LoginRequest request) {
        authenticationManager.authenticate(
                new UsernamePasswordAuthenticationToken(request.email(), request.password()));

        User user = userRepository.findByEmail(request.email())
                .orElseThrow(() -> new AuthException("Credenciales inválidas o usuario no encontrado"));

        return generateAuthResponse(user, "Login exitoso");
    }

    // --- MAPPERS ---

    private UserProfile mapToProfileEntity(UserProfileRequest dto) {
        if (dto == null)
            return new UserProfile();

        return UserProfile.builder()
                .nombre(dto.nombre())
                .apellido(dto.apellido())
                .telefono(dto.telefono())
                .direccion(dto.direccion())
                .avatarUrl(dto.avatarUrl())
                .build();
    }

    private AuthResponse generateAuthResponse(User user, String message) {
        Map<String, Object> extraClaims = new HashMap<>();
        extraClaims.put("role", user.getRole().name());
        extraClaims.put("orgId", user.getOrgId());
        extraClaims.put("depId", user.getDepartmentId());

        String jwtToken = jwtService.generateToken(extraClaims, user);
        return new AuthResponse(jwtToken, message);
    }
}