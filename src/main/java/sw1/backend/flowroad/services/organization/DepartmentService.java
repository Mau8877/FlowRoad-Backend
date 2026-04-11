package sw1.backend.flowroad.services.organization;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import sw1.backend.flowroad.dtos.organization.DepartmentResponse;
import sw1.backend.flowroad.dtos.organization.CreateDepartmentRequest;
import sw1.backend.flowroad.dtos.organization.UpdateDepartmentRequest;
import sw1.backend.flowroad.models.organization.Department;
import sw1.backend.flowroad.repository.organization.DepartmentRepository;
import sw1.backend.flowroad.repository.organization.OrganizationRepository;
import sw1.backend.flowroad.repository.organization.CargoRepository;
import sw1.backend.flowroad.exceptions.ResourceNotFoundException;
import sw1.backend.flowroad.exceptions.AuthException;

import java.util.List;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class DepartmentService {

    private final DepartmentRepository departmentRepository;
    private final OrganizationRepository organizationRepository;
    private final CargoRepository cargoRepository;

    // 1. CREAR DEPARTAMENTO
    public DepartmentResponse create(CreateDepartmentRequest request) {
        if (!organizationRepository.existsById(request.orgId())) {
            throw new ResourceNotFoundException("Organización no encontrada con ID: " + request.orgId());
        }

        if (departmentRepository.existsByCodeAndOrgId(request.code().toUpperCase(), request.orgId())) {
            throw new AuthException(
                    "El código de departamento '" + request.code() + "' ya está en uso en esta empresa.");
        }

        for (String cargoId : request.cargoIds()) {
            if (!cargoRepository.existsById(cargoId)) {
                throw new ResourceNotFoundException(
                        "No se puede crear el departamento: El cargo con ID " + cargoId + " no existe.");
            }
        }

        Department dept = new Department();
        dept.setOrgId(request.orgId());
        dept.setManagerId(request.managerId());
        dept.setName(request.name());
        dept.setCode(request.code().toUpperCase());
        dept.setSlaHours(request.slaHours());
        dept.setCargoIds(request.cargoIds());
        dept.setIsActive(true);

        return convertToResponseDTO(departmentRepository.save(dept));
    }

    // 2. OBTENER POR ID
    public DepartmentResponse getById(String id) {
        return departmentRepository.findById(id)
                .map(this::convertToResponseDTO)
                .orElseThrow(() -> new ResourceNotFoundException("Departamento no encontrado con ID: " + id));
    }

    // 3. LISTAR POR ORGANIZACIÓN
    public List<DepartmentResponse> getByOrganization(String orgId) {
        return departmentRepository.findByOrgId(orgId)
                .stream()
                .map(this::convertToResponseDTO)
                .collect(Collectors.toList());
    }

    // 4. ACTUALIZAR
    public DepartmentResponse update(String id, UpdateDepartmentRequest request) {
        Department dept = departmentRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("No se puede actualizar: Departamento no encontrado"));

        // Validar cargos antes de actualizar la lista
        for (String cargoId : request.cargoIds()) {
            if (!cargoRepository.existsById(cargoId)) {
                throw new ResourceNotFoundException(
                        "Error en actualización: El cargo con ID " + cargoId + " no existe.");
            }
        }

        dept.setManagerId(request.managerId());
        dept.setName(request.name());
        dept.setSlaHours(request.slaHours());
        dept.setIsActive(request.isActive());
        dept.setCargoIds(request.cargoIds());

        return convertToResponseDTO(departmentRepository.save(dept));
    }

    // 5. SOFT DELETE
    public void delete(String id) {
        Department dept = departmentRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("No se puede eliminar: Departamento no encontrado"));

        dept.setIsActive(false);
        departmentRepository.save(dept);
    }

    // --- MAPPER PRIVADO ---
    private DepartmentResponse convertToResponseDTO(Department dept) {
        DepartmentResponse res = new DepartmentResponse();
        res.setId(dept.getId());
        res.setOrgId(dept.getOrgId());
        res.setManagerId(dept.getManagerId());
        res.setName(dept.getName());
        res.setCode(dept.getCode());
        res.setSlaHours(dept.getSlaHours());
        res.setIsActive(dept.getIsActive());
        res.setCreatedAt(dept.getCreatedAt());
        res.setCargoIds(dept.getCargoIds());
        return res;
    }
}