package sw1.backend.flowroad.services.document;

import java.time.LocalDateTime;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.stream.Collectors;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import lombok.RequiredArgsConstructor;
import sw1.backend.flowroad.dtos.document.DocumentRequirementRequest;
import sw1.backend.flowroad.dtos.document.DocumentRequirementResponse;
import sw1.backend.flowroad.exceptions.ResourceNotFoundException;
import sw1.backend.flowroad.models.diagram.Diagram;
import sw1.backend.flowroad.models.document.DocumentRequirement;
import sw1.backend.flowroad.models.document.DocumentRequirement.DocumentRequirementStatus;
import sw1.backend.flowroad.models.organization.Department;
import sw1.backend.flowroad.models.user.User;
import sw1.backend.flowroad.repository.diagram.DiagramRepository;
import sw1.backend.flowroad.repository.document.DocumentRequirementRepository;
import sw1.backend.flowroad.repository.organization.DepartmentRepository;

@Service
@RequiredArgsConstructor
public class DocumentRequirementService {

    private static final Set<String> ALLOWED_FILE_TYPES = Set.of(
            "pdf",
            "doc",
            "docx",
            "xls",
            "xlsx",
            "jpg",
            "jpeg",
            "png");

    private final DocumentRequirementRepository repository;
    private final DiagramRepository diagramRepository;
    private final DepartmentRepository departmentRepository;

    public List<DocumentRequirementResponse> listByNode(String diagramId, String nodeId, User currentUser) {
        validateDiagramNode(diagramId, nodeId, currentUser.getOrgId());

        return repository
                .findByOrgIdAndDiagramIdAndNodeIdAndStatus(
                        currentUser.getOrgId(),
                        diagramId,
                        nodeId,
                        DocumentRequirementStatus.ACTIVE)
                .stream()
                .map(this::toResponse)
                .collect(Collectors.toList());
    }

    @Transactional
    public DocumentRequirementResponse create(
            String diagramId,
            String nodeId,
            DocumentRequirementRequest request,
            User currentUser) {
        validateRequest(request);
        validateDiagramNode(diagramId, nodeId, currentUser.getOrgId());

        String normalizedName = normalizeName(request.name());
        ensureNoActiveDuplicate(currentUser.getOrgId(), diagramId, nodeId, normalizedName, null);

        NormalizedRequest normalized = normalizeAndValidateRequest(request, currentUser.getOrgId());
        LocalDateTime now = LocalDateTime.now();

        DocumentRequirement requirement = DocumentRequirement.builder()
                .orgId(currentUser.getOrgId())
                .diagramId(diagramId)
                .nodeId(nodeId)
                .name(normalizedName)
                .description(normalizeDescription(request.description()))
                .required(request.required())
                .allowedFileTypes(normalized.allowedFileTypes())
                .maxFileSizeMb(request.maxFileSizeMb())
                .readDepartmentIds(normalized.readDepartmentIds())
                .uploadDepartmentIds(normalized.uploadDepartmentIds())
                .editDepartmentIds(normalized.editDepartmentIds())
                .clientCanRead(normalized.clientCanRead())
                .clientCanUpload(normalized.clientCanUpload())
                .clientCanReplace(normalized.clientCanReplace())
                .status(DocumentRequirementStatus.ACTIVE)
                .createdAt(now)
                .createdBy(currentUser.getId())
                .updatedAt(now)
                .updatedBy(currentUser.getId())
                .build();

        return toResponse(repository.save(requirement));
    }

    @Transactional
    public DocumentRequirementResponse update(
            String requirementId,
            DocumentRequirementRequest request,
            User currentUser) {
        validateRequest(request);
        DocumentRequirement requirement = getByIdAndOrg(requirementId, currentUser.getOrgId());
        validateDiagramNode(requirement.getDiagramId(), requirement.getNodeId(), currentUser.getOrgId());

        String normalizedName = normalizeName(request.name());
        if (requirement.getStatus() == DocumentRequirementStatus.ACTIVE) {
            ensureNoActiveDuplicate(
                    currentUser.getOrgId(),
                    requirement.getDiagramId(),
                    requirement.getNodeId(),
                    normalizedName,
                    requirement.getId());
        }

        NormalizedRequest normalized = normalizeAndValidateRequest(request, currentUser.getOrgId());

        requirement.setName(normalizedName);
        requirement.setDescription(normalizeDescription(request.description()));
        requirement.setRequired(request.required());
        requirement.setAllowedFileTypes(normalized.allowedFileTypes());
        requirement.setMaxFileSizeMb(request.maxFileSizeMb());
        requirement.setReadDepartmentIds(normalized.readDepartmentIds());
        requirement.setUploadDepartmentIds(normalized.uploadDepartmentIds());
        requirement.setEditDepartmentIds(normalized.editDepartmentIds());
        requirement.setClientCanRead(normalized.clientCanRead());
        requirement.setClientCanUpload(normalized.clientCanUpload());
        requirement.setClientCanReplace(normalized.clientCanReplace());
        requirement.setUpdatedAt(LocalDateTime.now());
        requirement.setUpdatedBy(currentUser.getId());

        return toResponse(repository.save(requirement));
    }

    @Transactional
    public void deactivate(String requirementId, User currentUser) {
        DocumentRequirement requirement = getByIdAndOrg(requirementId, currentUser.getOrgId());
        requirement.setStatus(DocumentRequirementStatus.INACTIVE);
        requirement.setUpdatedAt(LocalDateTime.now());
        requirement.setUpdatedBy(currentUser.getId());
        repository.save(requirement);
    }

    @Transactional
    public DocumentRequirementResponse reactivate(String requirementId, User currentUser) {
        DocumentRequirement requirement = getByIdAndOrg(requirementId, currentUser.getOrgId());
        validateDiagramNode(requirement.getDiagramId(), requirement.getNodeId(), currentUser.getOrgId());
        ensureNoActiveDuplicate(
                currentUser.getOrgId(),
                requirement.getDiagramId(),
                requirement.getNodeId(),
                requirement.getName(),
                requirement.getId());

        requirement.setStatus(DocumentRequirementStatus.ACTIVE);
        requirement.setUpdatedAt(LocalDateTime.now());
        requirement.setUpdatedBy(currentUser.getId());

        return toResponse(repository.save(requirement));
    }

    private DocumentRequirement getByIdAndOrg(String requirementId, String orgId) {
        return repository.findByIdAndOrgId(requirementId, orgId)
                .orElseThrow(() -> new ResourceNotFoundException(
                        "Requisito documental no encontrado o no pertenece a su organizacion."));
    }

    private Diagram validateDiagramNode(String diagramId, String nodeId, String orgId) {
        if (diagramId == null || diagramId.isBlank()) {
            throw new IllegalArgumentException("El diagramId es obligatorio.");
        }

        if (nodeId == null || nodeId.isBlank()) {
            throw new IllegalArgumentException("El nodeId es obligatorio.");
        }

        Diagram diagram = diagramRepository.findByIdAndOrgId(diagramId, orgId)
                .orElseThrow(() -> new ResourceNotFoundException(
                        "Diagrama no encontrado o no pertenece a su organizacion."));

        Diagram.DiagramCell node = diagram.getCells() == null
                ? null
                : diagram.getCells()
                        .stream()
                        .filter(cell -> nodeId.equals(cell.getId()))
                        .findFirst()
                        .orElse(null);

        if (node == null) {
            throw new ResourceNotFoundException("Nodo no encontrado dentro del diagrama.");
        }

        if ("standard.Link".equals(node.getType())) {
            throw new IllegalArgumentException("No se pueden configurar requisitos documentales en conectores.");
        }

        return diagram;
    }

    private NormalizedRequest normalizeAndValidateRequest(DocumentRequirementRequest request, String orgId) {
        List<String> allowedFileTypes = normalizeAllowedFileTypes(request.allowedFileTypes());
        validateMaxFileSize(request.maxFileSizeMb());

        List<String> uploadDepartmentIds = normalizeDepartmentList(request.uploadDepartmentIds(), "uploadDepartmentIds");
        List<String> editDepartmentIds = normalizeDepartmentList(request.editDepartmentIds(), "editDepartmentIds");
        List<String> readDepartmentIds = normalizeDepartmentList(request.readDepartmentIds(), "readDepartmentIds");

        LinkedHashSet<String> normalizedReadIds = new LinkedHashSet<>(readDepartmentIds);
        normalizedReadIds.addAll(uploadDepartmentIds);
        normalizedReadIds.addAll(editDepartmentIds);

        boolean clientCanUpload = Boolean.TRUE.equals(request.clientCanUpload());
        boolean clientCanReplace = Boolean.TRUE.equals(request.clientCanReplace());
        boolean clientCanRead = Boolean.TRUE.equals(request.clientCanRead()) || clientCanUpload || clientCanReplace;

        if (normalizedReadIds.isEmpty()
                && uploadDepartmentIds.isEmpty()
                && editDepartmentIds.isEmpty()
                && !clientCanRead
                && !clientCanUpload
                && !clientCanReplace) {
            throw new IllegalArgumentException(
                    "Debe existir al menos un permiso departamental o un permiso habilitado para cliente.");
        }

        validateDepartmentsBelongToOrg(normalizedReadIds, uploadDepartmentIds, editDepartmentIds, orgId);

        return new NormalizedRequest(
                allowedFileTypes,
                List.copyOf(normalizedReadIds),
                uploadDepartmentIds,
                editDepartmentIds,
                clientCanRead,
                clientCanUpload,
                clientCanReplace);
    }

    private void validateRequest(DocumentRequirementRequest request) {
        if (request == null) {
            throw new IllegalArgumentException("La solicitud del requisito documental es obligatoria.");
        }
    }

    private String normalizeName(String name) {
        if (name == null || name.trim().isBlank()) {
            throw new IllegalArgumentException("El nombre del requisito documental es obligatorio.");
        }

        return name.trim();
    }

    private String normalizeDescription(String description) {
        return description == null ? null : description.trim();
    }

    private List<String> normalizeAllowedFileTypes(List<String> fileTypes) {
        if (fileTypes == null || fileTypes.isEmpty()) {
            throw new IllegalArgumentException("Debe indicar al menos un tipo de archivo permitido.");
        }

        LinkedHashSet<String> normalized = new LinkedHashSet<>();
        for (String fileType : fileTypes) {
            if (fileType == null || fileType.trim().isBlank()) {
                throw new IllegalArgumentException("Los tipos de archivo no pueden estar vacios.");
            }

            String cleanType = fileType.trim().toLowerCase(Locale.ROOT);
            if (cleanType.startsWith(".")) {
                cleanType = cleanType.substring(1);
            }

            if (!ALLOWED_FILE_TYPES.contains(cleanType)) {
                throw new IllegalArgumentException(
                        "Tipo de archivo no permitido: " + cleanType
                                + ". Permitidos: pdf, doc, docx, xls, xlsx, jpg, jpeg, png.");
            }

            normalized.add(cleanType);
        }

        return List.copyOf(normalized);
    }

    private void validateMaxFileSize(Integer maxFileSizeMb) {
        if (maxFileSizeMb == null) {
            throw new IllegalArgumentException("El tamano maximo es obligatorio.");
        }

        if (maxFileSizeMb < 1 || maxFileSizeMb > 25) {
            throw new IllegalArgumentException("El tamano maximo debe estar entre 1 y 25 MB.");
        }
    }

    private List<String> normalizeDepartmentList(List<String> departmentIds, String fieldName) {
        if (departmentIds == null || departmentIds.isEmpty()) {
            return List.of();
        }

        LinkedHashSet<String> normalized = new LinkedHashSet<>();
        for (String departmentId : departmentIds) {
            if (departmentId == null || departmentId.trim().isBlank()) {
                throw new IllegalArgumentException("El campo " + fieldName + " contiene un departamento vacio.");
            }

            String cleanId = departmentId.trim();
            if (!normalized.add(cleanId)) {
                throw new IllegalArgumentException("El campo " + fieldName + " contiene departamentos duplicados.");
            }
        }

        return List.copyOf(normalized);
    }

    private void validateDepartmentsBelongToOrg(
            Set<String> readDepartmentIds,
            List<String> uploadDepartmentIds,
            List<String> editDepartmentIds,
            String orgId) {
        LinkedHashSet<String> allDepartmentIds = new LinkedHashSet<>();
        allDepartmentIds.addAll(readDepartmentIds);
        allDepartmentIds.addAll(uploadDepartmentIds);
        allDepartmentIds.addAll(editDepartmentIds);

        List<Department> departments = departmentRepository.findAllById(allDepartmentIds);
        Set<String> validDepartmentIds = departments.stream()
                .filter(department -> orgId.equals(department.getOrgId()))
                .filter(department -> Boolean.TRUE.equals(department.getIsActive()))
                .map(Department::getId)
                .collect(Collectors.toSet());

        for (String departmentId : allDepartmentIds) {
            if (!validDepartmentIds.contains(departmentId)) {
                throw new IllegalArgumentException(
                        "Departamento invalido, inactivo o fuera de la organizacion: " + departmentId);
            }
        }
    }

    private void ensureNoActiveDuplicate(
            String orgId,
            String diagramId,
            String nodeId,
            String name,
            String currentRequirementId) {
        repository.findByOrgIdAndDiagramIdAndNodeIdAndNameAndStatus(
                orgId,
                diagramId,
                nodeId,
                name,
                DocumentRequirementStatus.ACTIVE)
                .ifPresent(existing -> {
                    if (currentRequirementId == null || !currentRequirementId.equals(existing.getId())) {
                        throw new IllegalArgumentException(
                                "Ya existe un requisito documental activo con ese nombre en el nodo.");
                    }
                });
    }

    private DocumentRequirementResponse toResponse(DocumentRequirement requirement) {
        DocumentRequirementResponse response = new DocumentRequirementResponse();
        response.setId(requirement.getId());
        response.setOrgId(requirement.getOrgId());
        response.setDiagramId(requirement.getDiagramId());
        response.setNodeId(requirement.getNodeId());
        response.setName(requirement.getName());
        response.setDescription(requirement.getDescription());
        response.setRequired(requirement.getRequired());
        response.setAllowedFileTypes(requirement.getAllowedFileTypes());
        response.setMaxFileSizeMb(requirement.getMaxFileSizeMb());
        response.setReadDepartmentIds(requirement.getReadDepartmentIds());
        response.setUploadDepartmentIds(requirement.getUploadDepartmentIds());
        response.setEditDepartmentIds(requirement.getEditDepartmentIds());
        response.setClientCanRead(Boolean.TRUE.equals(requirement.getClientCanRead()));
        response.setClientCanUpload(Boolean.TRUE.equals(requirement.getClientCanUpload()));
        response.setClientCanReplace(Boolean.TRUE.equals(requirement.getClientCanReplace()));
        response.setStatus(requirement.getStatus() != null ? requirement.getStatus().name() : null);
        response.setCreatedAt(requirement.getCreatedAt());
        response.setCreatedBy(requirement.getCreatedBy());
        response.setUpdatedAt(requirement.getUpdatedAt());
        response.setUpdatedBy(requirement.getUpdatedBy());
        return response;
    }

    private record NormalizedRequest(
            List<String> allowedFileTypes,
            List<String> readDepartmentIds,
            List<String> uploadDepartmentIds,
            List<String> editDepartmentIds,
            Boolean clientCanRead,
            Boolean clientCanUpload,
            Boolean clientCanReplace) {
    }
}
