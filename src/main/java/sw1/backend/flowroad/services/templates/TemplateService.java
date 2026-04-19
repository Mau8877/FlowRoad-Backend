package sw1.backend.flowroad.services.templates;

import java.time.LocalDateTime;
import java.util.List;
import java.util.stream.Collectors;

import org.springframework.stereotype.Service;

import lombok.RequiredArgsConstructor;
import sw1.backend.flowroad.dtos.templates.CreateTemplateRequest;
import sw1.backend.flowroad.dtos.templates.TemplateResponse;
import sw1.backend.flowroad.dtos.templates.UpdateTemplateRequest;
import sw1.backend.flowroad.exceptions.ResourceNotFoundException;
import sw1.backend.flowroad.models.organization.Department;
import sw1.backend.flowroad.models.templates.Template;
import sw1.backend.flowroad.repository.organization.DepartmentRepository;
import sw1.backend.flowroad.repository.templates.TemplateRepository;

@Service
@RequiredArgsConstructor
public class TemplateService {

    private final TemplateRepository repository;
    private final DepartmentRepository departmentRepository;

    // 1. OBTENER TODAS LAS PLANTILLAS (Para el panel de administración del
    // DESIGNER)
    public List<TemplateResponse> getAllByOrganization(String orgId) {
        return repository.findAllByOrgId(orgId)
                .stream()
                .map(this::convertToResponseDTO)
                .collect(Collectors.toList());
    }

    // 2. OBTENER PLANTILLAS ACTIVAS POR DEPARTAMENTO (Para el menú del Trabajador)
    public List<TemplateResponse> getActiveByDepartment(String orgId, String departmentId) {
        return repository.findAllByOrgIdAndDepartmentIdAndIsActiveTrue(orgId, departmentId)
                .stream()
                .map(this::convertToResponseDTO)
                .collect(Collectors.toList());
    }

    // 3. OBTENER POR ID (Con seguridad de Organización)
    public TemplateResponse getById(String id, String orgId) {
        return repository.findByIdAndOrgId(id, orgId)
                .map(this::convertToResponseDTO)
                .orElseThrow(() -> new ResourceNotFoundException(
                        "Plantilla no encontrada o no pertenece a su organización."));
    }

    // 4. CREAR PLANTILLA (Inicia en Versión 1)
    public TemplateResponse create(CreateTemplateRequest request, String orgId, String createdBy) {
        // Regla de Negocio: No permitir nombres duplicados en la misma organización
        if (repository.existsByOrgIdAndName(orgId, request.name())) {
            throw new IllegalArgumentException("Ya existe una plantilla con el nombre '" + request.name() + "'.");
        }

        Template template = Template.builder()
                .orgId(orgId)
                .name(request.name())
                .description(request.description())
                .departmentId(request.departmentId())
                .fields(request.fields())
                .version(1) // 👈 Inicia siempre en versión 1
                .isActive(true)
                .createdAt(LocalDateTime.now())
                .createdBy(createdBy)
                .build();

        Template savedTemplate = repository.save(template);
        return convertToResponseDTO(savedTemplate);
    }

    // 5. ACTUALIZAR PLANTILLA (Autoincremento de Versión)
    public TemplateResponse update(String id, UpdateTemplateRequest request, String orgId) {
        Template template = repository.findByIdAndOrgId(id, orgId)
                .orElseThrow(() -> new ResourceNotFoundException("No se puede actualizar: Plantilla no encontrada."));

        // Validar que si cambia el nombre, el nuevo nombre no esté siendo usado por
        // OTRA plantilla
        if (!template.getName().equals(request.name()) && repository.existsByOrgIdAndName(orgId, request.name())) {
            throw new IllegalArgumentException("Ya existe otra plantilla con el nombre '" + request.name() + "'.");
        }

        template.setName(request.name());
        template.setDescription(request.description());
        template.setDepartmentId(request.departmentId());
        template.setFields(request.fields());

        if (request.isActive() != null) {
            template.setIsActive(request.isActive());
        }

        // 👈 MAGIA DE ARQUITECTURA: Subimos la versión automáticamente por cada edición
        template.setVersion(template.getVersion() + 1);

        Template updatedTemplate = repository.save(template);
        return convertToResponseDTO(updatedTemplate);
    }

    // 6. SOFT DELETE (Desactivar)
    public void softDelete(String id, String orgId) {
        Template template = repository.findByIdAndOrgId(id, orgId)
                .orElseThrow(() -> new ResourceNotFoundException("No se puede desactivar: Plantilla no encontrada."));

        template.setIsActive(false);
        repository.save(template);
    }

    // 7. REACTIVAR PLANTILLA
    public TemplateResponse reactivate(String id, String orgId) {
        Template template = repository.findByIdAndOrgId(id, orgId)
                .orElseThrow(() -> new ResourceNotFoundException("No se puede reactivar: Plantilla no encontrada."));

        template.setIsActive(true);

        Template updatedTemplate = repository.save(template);
        return convertToResponseDTO(updatedTemplate);
    }

    // --- MAPPERS ---
    private TemplateResponse convertToResponseDTO(Template template) {
        TemplateResponse dto = new TemplateResponse();
        dto.setId(template.getId());
        dto.setName(template.getName());
        dto.setDescription(template.getDescription());
        dto.setDepartmentId(template.getDepartmentId());

        String deptName = departmentRepository.findById(template.getDepartmentId())
                .map(Department::getName)
                .orElse("Sin Departamento");
        dto.setDepartmentName(deptName);

        dto.setVersion(template.getVersion());
        dto.setIsActive(template.getIsActive());
        dto.setFields(template.getFields());
        dto.setCreatedAt(template.getCreatedAt());
        dto.setCreatedBy(template.getCreatedBy());
        return dto;
    }
}