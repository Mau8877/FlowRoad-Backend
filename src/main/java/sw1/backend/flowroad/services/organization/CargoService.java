package sw1.backend.flowroad.services.organization;

import lombok.RequiredArgsConstructor;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;

import sw1.backend.flowroad.dtos.organization.CargoResponse;
import sw1.backend.flowroad.dtos.organization.CreateCargoRequest;
import sw1.backend.flowroad.dtos.organization.UpdateCargoRequest;
import sw1.backend.flowroad.models.organization.Cargo;
import sw1.backend.flowroad.models.user.User;
import sw1.backend.flowroad.repository.organization.CargoRepository;
import sw1.backend.flowroad.repository.organization.OrganizationRepository;
import sw1.backend.flowroad.exceptions.ResourceNotFoundException;

import java.util.List;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class CargoService {

    private final CargoRepository cargoRepository;
    private final OrganizationRepository organizationRepository;

    // 1. CREAR CARGO
    public CargoResponse create(CreateCargoRequest request) {
        if (!organizationRepository.existsById(request.orgId())) {
            throw new ResourceNotFoundException(
                    "NO SE PUEDE CREAR EL CARGO: ORGANIZACIÓN NO ENCONTRADA CON ID: " + request.orgId());
        }

        Cargo cargo = Cargo.builder()
                .orgId(request.orgId())
                .name(request.name())
                .level(request.level())
                .isActive(true)
                .build();

        return convertToResponseDTO(cargoRepository.save(cargo));
    }

    // 2. OBTENER CARGO POR ID
    public CargoResponse getById(String id) {
        return cargoRepository.findById(id)
                .map(this::convertToResponseDTO)
                .orElseThrow(() -> new ResourceNotFoundException("CARGO NO ENCONTRADO CON ID: " + id));
    }

    // 3. LISTAR POR ORGANIZACIÓN (MULTI-TENANT VALIDATION)
    public List<CargoResponse> getByOrganization(String orgId) {
        var auth = SecurityContextHolder.getContext().getAuthentication();

        if (auth == null || !(auth.getPrincipal() instanceof User currentUser)) {
            throw new AccessDeniedException("USUARIO NO AUTENTICADO O CONTEXTO INVÁLIDO");
        }

        // VALIDACIÓN: EL SUPER ADMIN (SIN ORGID) PUEDE VER CUALQUIER EMPRESA
        boolean isSuperAdmin = "ADMIN".equals(currentUser.getRole().toString())
                && currentUser.getOrgId() == null;

        if (!isSuperAdmin) {
            // SI NO ES SUPER ADMIN, DEBE PERTENECER A LA ORGANIZACIÓN SOLICITADA
            if (currentUser.getOrgId() == null || !currentUser.getOrgId().equals(orgId)) {
                throw new AccessDeniedException("ACCESO DENEGADO: NO TIENES PERMISOS PARA ESTA ORGANIZACIÓN");
            }
        }

        return cargoRepository.findAllByOrgId(orgId)
                .stream()
                .map(this::convertToResponseDTO)
                .collect(Collectors.toList());
    }

    // 4. ACTUALIZAR CARGO
    public CargoResponse update(String id, UpdateCargoRequest request) {
        Cargo cargo = cargoRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("NO SE PUEDE ACTUALIZAR: CARGO NO ENCONTRADO"));

        cargo.setName(request.name());
        cargo.setLevel(request.level());
        cargo.setIsActive(request.isActive());

        return convertToResponseDTO(cargoRepository.save(cargo));
    }

    // 5. ELIMINACIÓN LÓGICA
    public void delete(String id) {
        Cargo cargo = cargoRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("NO SE PUEDE ELIMINAR: CARGO NO ENCONTRADO"));

        cargo.setIsActive(false);
        cargoRepository.save(cargo);
    }

    // --- MAPPER PRIVADO ---
    private CargoResponse convertToResponseDTO(Cargo cargo) {
        CargoResponse response = new CargoResponse();
        response.setId(cargo.getId());
        response.setOrgId(cargo.getOrgId());
        response.setName(cargo.getName());
        response.setLevel(cargo.getLevel());
        response.setIsActive(cargo.getIsActive());
        return response;
    }
}