package sw1.backend.flowroad.services.document;

import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import lombok.RequiredArgsConstructor;
import sw1.backend.flowroad.dtos.document.DocumentExpedientItemResponse;
import sw1.backend.flowroad.dtos.document.DocumentFileResponse;
import sw1.backend.flowroad.dtos.document.DocumentManagementExpedientDetailResponse;
import sw1.backend.flowroad.dtos.document.DocumentManagementExpedientSummaryResponse;
import sw1.backend.flowroad.dtos.document.DocumentRequirementResponse;
import sw1.backend.flowroad.exceptions.ResourceNotFoundException;
import sw1.backend.flowroad.models.document.DocumentFile;
import sw1.backend.flowroad.models.document.DocumentFile.DocumentFileStatus;
import sw1.backend.flowroad.models.document.DocumentRequirement;
import sw1.backend.flowroad.models.document.DocumentRequirement.DocumentRequirementStatus;
import sw1.backend.flowroad.models.process.ProcessInstance;
import sw1.backend.flowroad.models.user.Roles;
import sw1.backend.flowroad.models.user.User;
import sw1.backend.flowroad.repository.document.DocumentFileRepository;
import sw1.backend.flowroad.repository.document.DocumentRequirementRepository;
import sw1.backend.flowroad.repository.process.ProcessInstanceRepository;

@Service
@RequiredArgsConstructor
public class DocumentManagementService {

    private static final String STATUS_PENDING = "PENDING";
    private static final String STATUS_UPLOADED = "UPLOADED";
    private static final String STATUS_REPLACED = "REPLACED";

    private final ProcessInstanceRepository processInstanceRepository;
    private final DocumentRequirementRepository documentRequirementRepository;
    private final DocumentFileRepository documentFileRepository;

    public List<DocumentManagementExpedientSummaryResponse> listVisibleExpedients(User currentUser) {
        validateDocumentManagementUser(currentUser);

        String orgId = currentUser.getOrgId();
        String departmentId = currentUser.getDepartmentId();
        Map<String, List<DocumentRequirement>> readableRequirementsByDiagram = new HashMap<>();

        return processInstanceRepository.findAllByOrgIdOrderByStartedAtDesc(orgId).stream()
                .map(instance -> toVisibleSummary(instance, orgId, departmentId, readableRequirementsByDiagram))
                .filter(Optional::isPresent)
                .map(Optional::get)
                .collect(Collectors.toList());
    }

    public DocumentManagementExpedientDetailResponse getVisibleExpedientDetail(
            String processInstanceId,
            User currentUser) {
        validateDocumentManagementUser(currentUser);

        if (!StringUtils.hasText(processInstanceId)) {
            throw new IllegalArgumentException("El processInstanceId es obligatorio.");
        }

        ProcessInstance instance = processInstanceRepository
                .findByIdAndOrgId(processInstanceId, currentUser.getOrgId())
                .orElseThrow(() -> new ResourceNotFoundException("Instancia de proceso no encontrada."));

        List<DocumentRequirement> readableRequirements = findReadableRequirements(
                currentUser.getOrgId(),
                instance.getDiagramId(),
                currentUser.getDepartmentId());

        if (readableRequirements.isEmpty()) {
            throw new AccessDeniedException("No tienes permisos de lectura documental para este expediente.");
        }

        List<DocumentFile> files = documentFileRepository.findByOrgIdAndProcessInstanceId(
                currentUser.getOrgId(),
                instance.getId());

        Map<String, DocumentFile> latestFileByRequirement = files.stream()
                .collect(Collectors.toMap(
                        DocumentFile::getDocumentRequirementId,
                        Function.identity(),
                        (left, right) -> safeVersion(left) >= safeVersion(right) ? left : right));

        DocumentManagementExpedientDetailResponse response = new DocumentManagementExpedientDetailResponse();
        fillProcessMetadata(response, instance);
        response.setItems(readableRequirements.stream()
                .sorted(Comparator.comparing(DocumentRequirement::getNodeId, Comparator.nullsLast(String::compareTo))
                        .thenComparing(DocumentRequirement::getName, Comparator.nullsLast(String::compareTo)))
                .map(requirement -> toDetailItem(
                        requirement,
                        latestFileByRequirement.get(requirement.getId()),
                        currentUser.getDepartmentId()))
                .collect(Collectors.toList()));

        return response;
    }

    private Optional<DocumentManagementExpedientSummaryResponse> toVisibleSummary(
            ProcessInstance instance,
            String orgId,
            String departmentId,
            Map<String, List<DocumentRequirement>> readableRequirementsByDiagram) {
        List<DocumentRequirement> readableRequirements = readableRequirementsByDiagram.computeIfAbsent(
                instance.getDiagramId(),
                diagramId -> findReadableRequirements(orgId, diagramId, departmentId));

        if (readableRequirements.isEmpty()) {
            return Optional.empty();
        }

        Set<String> readableRequirementIds = readableRequirements.stream()
                .map(DocumentRequirement::getId)
                .collect(Collectors.toCollection(HashSet::new));

        long uploadedDocumentsCount = documentFileRepository.findByOrgIdAndProcessInstanceId(orgId, instance.getId())
                .stream()
                .filter(file -> file.getStatus() == DocumentFileStatus.ACTIVE)
                .map(DocumentFile::getDocumentRequirementId)
                .filter(readableRequirementIds::contains)
                .distinct()
                .count();

        int readableRequirementsCount = readableRequirements.size();
        DocumentManagementExpedientSummaryResponse response = new DocumentManagementExpedientSummaryResponse();
        fillProcessMetadata(response, instance);
        response.setReadableRequirementsCount(readableRequirementsCount);
        response.setUploadedDocumentsCount(Math.toIntExact(uploadedDocumentsCount));
        response.setPendingDocumentsCount(readableRequirementsCount - Math.toIntExact(uploadedDocumentsCount));

        return Optional.of(response);
    }

    private List<DocumentRequirement> findReadableRequirements(String orgId, String diagramId, String departmentId) {
        return documentRequirementRepository.findByOrgIdAndDiagramIdAndStatus(
                orgId,
                diagramId,
                DocumentRequirementStatus.ACTIVE).stream()
                .filter(requirement -> hasDepartmentPermission(departmentId, requirement.getReadDepartmentIds()))
                .collect(Collectors.toList());
    }

    private DocumentExpedientItemResponse toDetailItem(
            DocumentRequirement requirement,
            DocumentFile latestFile,
            String departmentId) {
        boolean hasActiveFile = latestFile != null && latestFile.getStatus() == DocumentFileStatus.ACTIVE;

        DocumentExpedientItemResponse item = new DocumentExpedientItemResponse();
        item.setRequirement(toRequirementResponse(requirement));
        item.setCurrentFile(hasActiveFile ? toFileResponse(latestFile) : null);
        item.setStatus(resolveItemStatus(latestFile));
        item.setCanRead(true);
        item.setCanUpload(hasDepartmentPermission(departmentId, requirement.getUploadDepartmentIds()));
        item.setCanEdit(hasDepartmentPermission(departmentId, requirement.getEditDepartmentIds()));
        return item;
    }

    private void validateDocumentManagementUser(User currentUser) {
        if (currentUser == null
                || (currentUser.getRole() != Roles.ADMIN
                        && currentUser.getRole() != Roles.WORKER
                        && currentUser.getRole() != Roles.RECEP)) {
            throw new AccessDeniedException("No tienes permiso para acceder a gestion documental interna.");
        }

        if (!StringUtils.hasText(currentUser.getOrgId())) {
            throw new AccessDeniedException("El usuario no tiene organizacion asignada.");
        }

        if (!StringUtils.hasText(currentUser.getDepartmentId())) {
            throw new AccessDeniedException("El usuario no tiene departamento asignado.");
        }
    }

    private boolean hasDepartmentPermission(String departmentId, List<String> allowedDepartmentIds) {
        return StringUtils.hasText(departmentId)
                && allowedDepartmentIds != null
                && allowedDepartmentIds.contains(departmentId);
    }

    private String resolveItemStatus(DocumentFile latestFile) {
        if (latestFile == null) {
            return STATUS_PENDING;
        }

        if (latestFile.getStatus() == DocumentFileStatus.ACTIVE) {
            return STATUS_UPLOADED;
        }

        if (latestFile.getStatus() == DocumentFileStatus.REPLACED) {
            return STATUS_REPLACED;
        }

        return STATUS_PENDING;
    }

    private void fillProcessMetadata(
            DocumentManagementExpedientSummaryResponse response,
            ProcessInstance instance) {
        response.setProcessInstanceId(instance.getId());
        response.setProcessCode(instance.getCode());
        response.setDiagramId(instance.getDiagramId());
        response.setDiagramName(instance.getDiagramName());
        response.setDiagramVersion(instance.getDiagramVersion());
        response.setProcessStatus(instance.getStatus() != null ? instance.getStatus().name() : null);
        response.setClientId(instance.getClientId());
        response.setClientName(instance.getClientName());
        response.setClientEmail(instance.getClientEmail());
        response.setStartedAt(instance.getStartedAt());
        response.setUpdatedAt(instance.getUpdatedAt());
        response.setFinishedAt(instance.getFinishedAt());
    }

    private void fillProcessMetadata(
            DocumentManagementExpedientDetailResponse response,
            ProcessInstance instance) {
        response.setProcessInstanceId(instance.getId());
        response.setProcessCode(instance.getCode());
        response.setDiagramId(instance.getDiagramId());
        response.setDiagramName(instance.getDiagramName());
        response.setDiagramVersion(instance.getDiagramVersion());
        response.setProcessStatus(instance.getStatus() != null ? instance.getStatus().name() : null);
        response.setClientId(instance.getClientId());
        response.setClientName(instance.getClientName());
        response.setClientEmail(instance.getClientEmail());
        response.setStartedAt(instance.getStartedAt());
        response.setUpdatedAt(instance.getUpdatedAt());
        response.setFinishedAt(instance.getFinishedAt());
    }

    private DocumentFileResponse toFileResponse(DocumentFile documentFile) {
        DocumentFileResponse response = new DocumentFileResponse();
        response.setId(documentFile.getId());
        response.setOrgId(documentFile.getOrgId());
        response.setProcessInstanceId(documentFile.getProcessInstanceId());
        response.setProcessAssignmentId(documentFile.getProcessAssignmentId());
        response.setDiagramId(documentFile.getDiagramId());
        response.setNodeId(documentFile.getNodeId());
        response.setDocumentRequirementId(documentFile.getDocumentRequirementId());
        response.setRequirementName(documentFile.getRequirementName());
        response.setOriginalFileName(documentFile.getOriginalFileName());
        response.setContentType(documentFile.getContentType());
        response.setFileExtension(documentFile.getFileExtension());
        response.setFileSizeBytes(documentFile.getFileSizeBytes());
        response.setStatus(documentFile.getStatus() != null ? documentFile.getStatus().name() : null);
        response.setVersion(documentFile.getVersion());
        response.setUploadedBy(documentFile.getUploadedBy());
        response.setUploadedByName(documentFile.getUploadedByName());
        response.setUploadedByDepartmentId(documentFile.getUploadedByDepartmentId());
        response.setCreatedAt(documentFile.getCreatedAt());
        response.setUpdatedAt(documentFile.getUpdatedAt());
        response.setReplacedByDocumentFileId(documentFile.getReplacedByDocumentFileId());
        return response;
    }

    private DocumentRequirementResponse toRequirementResponse(DocumentRequirement requirement) {
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

    private int safeVersion(DocumentFile file) {
        return Optional.ofNullable(file.getVersion()).orElse(0);
    }
}
