package sw1.backend.flowroad.services.organization;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import sw1.backend.flowroad.dtos.organization.CreateOrganizationRequest;
import sw1.backend.flowroad.dtos.organization.OrganizationResponse;
import sw1.backend.flowroad.dtos.organization.UpdateOrganizationRequest;
import sw1.backend.flowroad.models.organization.Organization;
import sw1.backend.flowroad.repository.organization.OrganizationRepository;
import sw1.backend.flowroad.exceptions.ResourceNotFoundException;
import sw1.backend.flowroad.exceptions.AuthException;

import java.util.List;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class OrganizationService {

    private final OrganizationRepository repository;

    // 1. OBTENER TODAS
    public List<OrganizationResponse> getAllActive() {
        return repository.findAllByIsActiveTrue()
                .stream()
                .map(this::convertToResponseDTO)
                .collect(Collectors.toList());
    }

    // 2. OBTENER POR ID
    public OrganizationResponse getById(String id) {
        return repository.findById(id)
                .filter(Organization::getIsActive)
                .map(this::convertToResponseDTO)
                .orElseThrow(
                        () -> new ResourceNotFoundException("Organización no encontrada o inactiva con ID: " + id));
    }

    // 3. CREAR (Acceso vía record methods)
    public OrganizationResponse create(CreateOrganizationRequest request) {
        // En los records usamos .code() en lugar de .getCode()
        if (repository.findByCode(request.code().toUpperCase()).isPresent()) {
            throw new AuthException("El código de organización '" + request.code() + "' ya existe.");
        }

        Organization org = new Organization();
        org.setName(request.name());
        org.setCode(request.code().toUpperCase());
        org.setIsActive(true); // Controlado por el service

        Organization savedOrg = repository.save(org);
        return convertToResponseDTO(savedOrg);
    }

    // 4. ACTUALIZAR
    public OrganizationResponse update(String id, UpdateOrganizationRequest request) {
        Organization org = repository.findById(id)
                .filter(Organization::getIsActive)
                .orElseThrow(() -> new ResourceNotFoundException(
                        "No se puede actualizar: Organización no encontrada o inactiva"));

        // Acceso vía record methods: .name() y .code()
        org.setName(request.name());
        org.setCode(request.code().toUpperCase());

        Organization updated = repository.save(org);
        return convertToResponseDTO(updated);
    }

    // 5. SOFT DELETE
    public void softDelete(String id) {
        Organization org = repository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("No se puede eliminar: Organización no encontrada"));

        org.setIsActive(false);
        repository.save(org);
    }

    // 6. REACTIVACIÓN
    public void reactivate(String code) {
        Organization org = repository.findByCode(code.toUpperCase())
                .orElseThrow(() -> new ResourceNotFoundException("No se puede reactivar: Código no registrado"));

        org.setIsActive(true);
        repository.save(org);
    }

    // --- MAPPERS ---
    private OrganizationResponse convertToResponseDTO(Organization org) {
        OrganizationResponse dto = new OrganizationResponse();
        dto.setId(org.getId());
        dto.setName(org.getName());
        dto.setCode(org.getCode());
        dto.setCreatedAt(org.getCreatedAt());
        return dto;
    }
}