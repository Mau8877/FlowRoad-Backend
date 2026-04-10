package sw1.backend.flowroad.services;

import lombok.RequiredArgsConstructor;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import sw1.backend.flowroad.dtos.AuthResponse;
import sw1.backend.flowroad.dtos.LoginRequest;
import sw1.backend.flowroad.dtos.RegisterRequest;
import sw1.backend.flowroad.models.User;
import sw1.backend.flowroad.repository.UserRepository;
import sw1.backend.flowroad.security.JwtService;
import sw1.backend.flowroad.exceptions.AuthException;

@Service
@RequiredArgsConstructor
public class AuthService {

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final JwtService jwtService;
    private final AuthenticationManager authenticationManager;

    public AuthResponse register(RegisterRequest request) {

        // Verificar si el correo ya existe
        if (userRepository.findByEmail(request.email()).isPresent()) {
            throw new AuthException("El correo " + request.email() + " ya está registrado en FlowRoad");
        }

        // Transformación: De DTO a Model (Entidad)
        User user = new User(
                request.email(),
                passwordEncoder.encode(request.password()),
                request.role() != null ? request.role() : "ROLE_CLIENT",
                request.perfil(),
                request.empresaId(),
                request.departamento(),
                request.cargo());

        // Persistencia: Manda el Model al Repository
        userRepository.save(user);

        // Seguridad: Prepara los datos extra y generamos el Token JWT
        java.util.Map<String, Object> extraClaims = new java.util.HashMap<>();
        extraClaims.put("role", user.getRole());
        extraClaims.put("userId", user.getId());
        String jwtToken = jwtService.generateToken(extraClaims, user);

        // Retorno: Empaqueta el Token en un DTO de respuesta
        return new AuthResponse(jwtToken, "Usuario registrado con éxito");
    }

    public AuthResponse login(LoginRequest request) {
        // Autenticación
        authenticationManager.authenticate(
                new UsernamePasswordAuthenticationToken(
                        request.email(),
                        request.password()));

        // Si llegamos aquí, la contraseña es correcta. Buscamos al usuario.
        var user = userRepository.findByEmail(request.email())
                .orElseThrow(() -> new RuntimeException("Usuario no encontrado"));

        // Genera los Claims extra para el token
        java.util.Map<String, Object> extraClaims = new java.util.HashMap<>();
        extraClaims.put("role", user.getRole());
        extraClaims.put("userId", user.getId());

        // Genera el token
        String jwtToken = jwtService.generateToken(extraClaims, user);

        return new AuthResponse(jwtToken, "Login exitoso");
    }
}
