package sw1.backend.flowroad.services.user;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import sw1.backend.flowroad.dtos.user.UserResponse;
import sw1.backend.flowroad.dtos.user.UpdateUserRequest;
import sw1.backend.flowroad.dtos.user.UserProfileResponse;
import sw1.backend.flowroad.models.user.User;
import sw1.backend.flowroad.models.user.UserProfile;
import sw1.backend.flowroad.models.user.Roles;
import sw1.backend.flowroad.repository.user.UserRepository;
import sw1.backend.flowroad.exceptions.ResourceNotFoundException;

import java.util.List;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class UserService {

    private final UserRepository userRepository;

    // 1. OBTENER PERFIL POR ID
    public UserResponse getById(String id) {
        return userRepository.findById(id)
                .map(this::convertToResponseDTO)
                .orElseThrow(() -> new ResourceNotFoundException("Usuario no encontrado con ID: " + id));
    }

    // 2. LISTAR USUARIOS DE UNA ORGANIZACIÓN
    public List<UserResponse> getByOrganization(String orgId) {
        return userRepository.findAllByOrgIdAndIsActiveTrue(orgId)
                .stream()
                .map(this::convertToResponseDTO)
                .collect(Collectors.toList());
    }

    // 3. ACTUALIZAR USUARIO
    public UserResponse update(String id, UpdateUserRequest request) {
        User user = userRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("No se puede actualizar: Usuario no encontrado"));

        UserProfile profile = user.getProfile();
        if (profile == null)
            profile = new UserProfile();

        profile.setNombre(request.nombre());
        profile.setApellido(request.apellido());
        profile.setTelefono(request.telefono());
        profile.setDireccion(request.direccion());
        profile.setAvatarUrl(request.avatarUrl());

        user.setProfile(profile);

        if (request.departmentId() != null)
            user.setDepartmentId(request.departmentId());
        if (request.cargoId() != null)
            user.setCargoId(request.cargoId());
        if (request.isActive() != null)
            user.setIsActive(request.isActive());

        // OPCIONAL: Si permites cambiar el ROL en el update
        // if (request.role() != null) user.setRole(request.role());

        return convertToResponseDTO(userRepository.save(user));
    }

    // 4. SOFT DELETE
    public void delete(String id) {
        User user = userRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Usuario no encontrado"));
        user.setIsActive(false);
        userRepository.save(user);
    }

    // 5. LÓGICA DE ASIGNACIÓN (Round Robin para Tickets)
    public UserResponse getAvailableWorker(String orgId, String departmentId) {
        return userRepository.findFirstByOrgIdAndDepartmentIdAndRoleAndIsActiveTrueOrderByWorkloadAsc(
                orgId, departmentId, Roles.WORKER)
                .map(this::convertToResponseDTO)
                .orElseThrow(
                        () -> new ResourceNotFoundException("No hay trabajadores disponibles en este departamento"));
    }

    // --- MAPPERS PRIVADOS ---

    private UserResponse convertToResponseDTO(User user) {
        UserResponse res = new UserResponse();
        res.setId(user.getId());
        res.setEmail(user.getEmail());

        res.setRole(user.getRole());

        res.setOrgId(user.getOrgId());
        res.setDepartmentId(user.getDepartmentId());
        res.setCargoId(user.getCargoId());
        res.setWorkload(user.getWorkload());
        res.setIsActive(user.getIsActive());
        res.setCreatedAt(user.getCreatedAt());

        if (user.getProfile() != null) {
            res.setProfile(new UserProfileResponse(
                    user.getProfile().getNombre(),
                    user.getProfile().getApellido(),
                    user.getProfile().getDireccion(),
                    user.getProfile().getTelefono(),
                    user.getProfile().getAvatarUrl()));
        }
        return res;
    }
}