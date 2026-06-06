package sw1.backend.flowroad.services.document;

import java.net.URL;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.function.Function;
import java.util.stream.Collectors;

import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;
import org.springframework.web.multipart.MultipartFile;

import lombok.RequiredArgsConstructor;
import sw1.backend.flowroad.dtos.document.ClientDocumentExpedientItemResponse;
import sw1.backend.flowroad.dtos.document.ClientDocumentExpedientResponse;
import sw1.backend.flowroad.dtos.document.DocumentDownloadUrlResponse;
import sw1.backend.flowroad.dtos.document.DocumentExpedientItemResponse;
import sw1.backend.flowroad.dtos.document.DocumentExpedientResponse;
import sw1.backend.flowroad.dtos.document.DocumentFileResponse;
import sw1.backend.flowroad.dtos.document.DocumentRequirementResponse;
import sw1.backend.flowroad.dtos.document.DocumentUploadResponse;
import sw1.backend.flowroad.exceptions.ResourceNotFoundException;
import sw1.backend.flowroad.models.document.DocumentFile;
import sw1.backend.flowroad.models.document.DocumentFile.DocumentFileStatus;
import sw1.backend.flowroad.models.document.DocumentRequirement;
import sw1.backend.flowroad.models.document.DocumentRequirement.DocumentRequirementStatus;
import sw1.backend.flowroad.models.process.ProcessAssignment;
import sw1.backend.flowroad.models.process.ProcessInstance;
import sw1.backend.flowroad.models.user.Roles;
import sw1.backend.flowroad.models.user.User;
import sw1.backend.flowroad.repository.document.DocumentFileRepository;
import sw1.backend.flowroad.repository.document.DocumentRequirementRepository;
import sw1.backend.flowroad.repository.organization.OrganizationRepository;
import sw1.backend.flowroad.repository.process.ProcessAssignmentRepository;
import sw1.backend.flowroad.repository.process.ProcessInstanceRepository;
import sw1.backend.flowroad.services.document.S3StorageService.DocumentStorageContext;
import sw1.backend.flowroad.services.document.S3StorageService.StoredDocumentObject;

@Service
@RequiredArgsConstructor
public class DocumentFileService {

    private static final Duration DOWNLOAD_URL_TTL = Duration.ofMinutes(10);
    private static final String STATUS_PENDING = "PENDING";
    private static final String STATUS_UPLOADED = "UPLOADED";
    private static final String STATUS_REPLACED = "REPLACED";

    private final DocumentFileRepository documentFileRepository;
    private final DocumentRequirementRepository documentRequirementRepository;
    private final ProcessInstanceRepository processInstanceRepository;
    private final ProcessAssignmentRepository processAssignmentRepository;
    private final OrganizationRepository organizationRepository;
    private final S3StorageService s3StorageService;

    public DocumentExpedientResponse getExpedient(String processInstanceId, User currentUser) {
        ProcessInstance instance = getInstance(processInstanceId, currentUser);

        List<DocumentRequirement> requirements = documentRequirementRepository.findByOrgIdAndDiagramIdAndStatus(
                currentUser.getOrgId(),
                instance.getDiagramId(),
                DocumentRequirementStatus.ACTIVE);

        List<DocumentFile> files = documentFileRepository.findByOrgIdAndProcessInstanceId(
                currentUser.getOrgId(),
                instance.getId());

        Map<String, DocumentFile> latestFileByRequirement = files.stream()
                .collect(Collectors.toMap(
                        DocumentFile::getDocumentRequirementId,
                        Function.identity(),
                        (left, right) -> safeVersion(left) >= safeVersion(right) ? left : right));

        DocumentExpedientResponse response = new DocumentExpedientResponse();
        response.setProcessInstanceId(instance.getId());
        response.setProcessCode(instance.getCode());
        response.setDiagramId(instance.getDiagramId());
        response.setDiagramName(instance.getDiagramName());
        response.setProcessStatus(instance.getStatus() != null ? instance.getStatus().name() : null);

        response.setItems(requirements.stream()
                .sorted(Comparator.comparing(DocumentRequirement::getNodeId, Comparator.nullsLast(String::compareTo))
                        .thenComparing(DocumentRequirement::getName, Comparator.nullsLast(String::compareTo)))
                .map(requirement -> toExpedientItem(requirement, latestFileByRequirement.get(requirement.getId()),
                        currentUser))
                .collect(Collectors.toList()));

        return response;
    }

    @Transactional
    public DocumentUploadResponse uploadDocument(
            String processInstanceId,
            String documentRequirementId,
            String processAssignmentId,
            MultipartFile file,
            User currentUser) {
        ProcessInstance instance = getInstance(processInstanceId, currentUser);
        DocumentRequirement requirement = getActiveRequirement(
                documentRequirementId,
                currentUser.getOrgId(),
                instance.getDiagramId());

        validateDepartmentPermission(currentUser, requirement.getUploadDepartmentIds(),
                "No tienes permiso departamental para cargar este documento.");
        validateOptionalAssignment(processAssignmentId, instance, requirement);
        validateFile(file, requirement);

        List<DocumentFile> activeFiles = documentFileRepository
                .findByOrgIdAndProcessInstanceIdAndDocumentRequirementIdAndStatus(
                        currentUser.getOrgId(),
                        instance.getId(),
                        requirement.getId(),
                        DocumentFileStatus.ACTIVE);

        if (!activeFiles.isEmpty()) {
            throw new IllegalArgumentException(
                    "Ya existe un documento activo para este requisito. Usa el endpoint de reemplazo.");
        }

        int version = resolveNextVersion(currentUser.getOrgId(), instance.getId(), requirement.getId());
        DocumentFile saved = createAndStoreDocument(
                instance,
                requirement,
                processAssignmentId,
                file,
                currentUser,
                version,
                null);

        return toUploadResponse(saved);
    }

    @Transactional
    public DocumentFileResponse replaceDocument(
            String processInstanceId,
            String documentFileId,
            String processAssignmentId,
            MultipartFile file,
            User currentUser) {
        ProcessInstance instance = getInstance(processInstanceId, currentUser);
        DocumentFile currentFile = getDocumentFile(documentFileId, currentUser.getOrgId());

        if (!Objects.equals(currentFile.getProcessInstanceId(), instance.getId())) {
            throw new IllegalArgumentException("El documento no pertenece a la instancia indicada.");
        }

        if (currentFile.getStatus() != DocumentFileStatus.ACTIVE) {
            throw new IllegalArgumentException("Solo se puede reemplazar un documento activo.");
        }

        DocumentRequirement requirement = getActiveRequirement(
                currentFile.getDocumentRequirementId(),
                currentUser.getOrgId(),
                instance.getDiagramId());

        validateDepartmentPermission(currentUser, requirement.getEditDepartmentIds(),
                "No tienes permiso departamental para reemplazar este documento.");
        validateOptionalAssignment(processAssignmentId, instance, requirement);
        validateFile(file, requirement);

        int version = Math.max(safeVersion(currentFile) + 1,
                resolveNextVersion(currentUser.getOrgId(), instance.getId(), requirement.getId()));

        DocumentFile replacement = createAndStoreDocument(
                instance,
                requirement,
                processAssignmentId,
                file,
                currentUser,
                version,
                null);

        currentFile.setStatus(DocumentFileStatus.REPLACED);
        currentFile.setUpdatedAt(LocalDateTime.now());
        currentFile.setReplacedByDocumentFileId(replacement.getId());
        documentFileRepository.save(currentFile);

        return toResponse(replacement);
    }

    public DocumentDownloadUrlResponse generateDownloadUrl(
            String processInstanceId,
            String documentFileId,
            User currentUser) {
        ProcessInstance instance = getInstance(processInstanceId, currentUser);
        DocumentFile documentFile = getDocumentFile(documentFileId, currentUser.getOrgId());

        if (!Objects.equals(documentFile.getProcessInstanceId(), instance.getId())) {
            throw new IllegalArgumentException("El documento no pertenece a la instancia indicada.");
        }

        DocumentRequirement requirement = getActiveRequirement(
                documentFile.getDocumentRequirementId(),
                currentUser.getOrgId(),
                instance.getDiagramId());

        validateDepartmentPermission(currentUser, requirement.getReadDepartmentIds(),
                "No tienes permiso departamental para descargar este documento.");

        URL downloadUrl = s3StorageService.generateDownloadUrl(documentFile.getS3Key(), DOWNLOAD_URL_TTL);

        DocumentDownloadUrlResponse response = new DocumentDownloadUrlResponse();
        response.setDocumentFileId(documentFile.getId());
        response.setOriginalFileName(documentFile.getOriginalFileName());
        response.setContentType(documentFile.getContentType());
        response.setExpiresInSeconds(DOWNLOAD_URL_TTL.toSeconds());
        response.setDownloadUrl(downloadUrl.toString());
        return response;
    }

    public ClientDocumentExpedientResponse getClientExpedient(String processInstanceId, User currentUser) {
        ProcessInstance instance = getClientInstance(processInstanceId, currentUser);

        List<DocumentRequirement> requirements = documentRequirementRepository.findByOrgIdAndDiagramIdAndStatus(
                instance.getOrgId(),
                instance.getDiagramId(),
                DocumentRequirementStatus.ACTIVE);

        List<DocumentFile> files = documentFileRepository.findByOrgIdAndProcessInstanceId(
                instance.getOrgId(),
                instance.getId());

        Map<String, DocumentFile> latestFileByRequirement = files.stream()
                .collect(Collectors.toMap(
                        DocumentFile::getDocumentRequirementId,
                        Function.identity(),
                        (left, right) -> safeVersion(left) >= safeVersion(right) ? left : right));

        ClientDocumentExpedientResponse response = new ClientDocumentExpedientResponse();
        response.setProcessInstanceId(instance.getId());
        response.setProcessCode(instance.getCode());
        response.setDiagramId(instance.getDiagramId());
        response.setDiagramName(instance.getDiagramName());
        response.setProcessStatus(instance.getStatus() != null ? instance.getStatus().name() : null);

        response.setItems(requirements.stream()
                .filter(this::hasAnyClientPermission)
                .sorted(Comparator.comparing(DocumentRequirement::getNodeId, Comparator.nullsLast(String::compareTo))
                        .thenComparing(DocumentRequirement::getName, Comparator.nullsLast(String::compareTo)))
                .map(requirement -> toClientExpedientItem(
                        instance,
                        requirement,
                        latestFileByRequirement.get(requirement.getId())))
                .collect(Collectors.toList()));

        return response;
    }

    @Transactional
    public DocumentUploadResponse uploadClientDocument(
            String processInstanceId,
            String documentRequirementId,
            MultipartFile file,
            User currentUser) {
        ProcessInstance instance = getClientInstance(processInstanceId, currentUser);
        DocumentRequirement requirement = getActiveRequirement(
                documentRequirementId,
                instance.getOrgId(),
                instance.getDiagramId());

        if (!Boolean.TRUE.equals(requirement.getClientCanUpload())) {
            throw new AccessDeniedException("El cliente no tiene permiso para subir este documento.");
        }

        validateRequirementNodeIsActive(instance, requirement);
        validateFile(file, requirement);

        List<DocumentFile> activeFiles = documentFileRepository
                .findByOrgIdAndProcessInstanceIdAndDocumentRequirementIdAndStatus(
                        instance.getOrgId(),
                        instance.getId(),
                        requirement.getId(),
                        DocumentFileStatus.ACTIVE);

        if (!activeFiles.isEmpty()) {
            throw new IllegalArgumentException(
                    "Ya existe un documento activo para este requisito. Usa el endpoint de reemplazo.");
        }

        int version = resolveNextVersion(instance.getOrgId(), instance.getId(), requirement.getId());
        DocumentFile saved = createAndStoreDocument(
                instance,
                requirement,
                null,
                file,
                currentUser,
                version,
                null);

        return toUploadResponse(saved);
    }

    @Transactional
    public DocumentFileResponse replaceClientDocument(
            String processInstanceId,
            String documentFileId,
            MultipartFile file,
            User currentUser) {
        ProcessInstance instance = getClientInstance(processInstanceId, currentUser);
        DocumentFile currentFile = getDocumentFile(documentFileId, instance.getOrgId());

        if (!Objects.equals(currentFile.getProcessInstanceId(), instance.getId())) {
            throw new IllegalArgumentException("El documento no pertenece a la instancia indicada.");
        }

        if (currentFile.getStatus() != DocumentFileStatus.ACTIVE) {
            throw new IllegalArgumentException("Solo se puede reemplazar un documento activo.");
        }

        DocumentRequirement requirement = getActiveRequirement(
                currentFile.getDocumentRequirementId(),
                instance.getOrgId(),
                instance.getDiagramId());

        if (!Boolean.TRUE.equals(requirement.getClientCanReplace())) {
            throw new AccessDeniedException("El cliente no tiene permiso para reemplazar este documento.");
        }

        validateRequirementNodeIsActive(instance, requirement);
        validateFile(file, requirement);

        int version = Math.max(safeVersion(currentFile) + 1,
                resolveNextVersion(instance.getOrgId(), instance.getId(), requirement.getId()));

        DocumentFile replacement = createAndStoreDocument(
                instance,
                requirement,
                null,
                file,
                currentUser,
                version,
                null);

        currentFile.setStatus(DocumentFileStatus.REPLACED);
        currentFile.setUpdatedAt(LocalDateTime.now());
        currentFile.setReplacedByDocumentFileId(replacement.getId());
        documentFileRepository.save(currentFile);

        return toResponse(replacement);
    }

    public DocumentDownloadUrlResponse generateClientDownloadUrl(
            String processInstanceId,
            String documentFileId,
            User currentUser) {
        ProcessInstance instance = getClientInstance(processInstanceId, currentUser);
        DocumentFile documentFile = getDocumentFile(documentFileId, instance.getOrgId());

        if (!Objects.equals(documentFile.getProcessInstanceId(), instance.getId())) {
            throw new IllegalArgumentException("El documento no pertenece a la instancia indicada.");
        }

        if (documentFile.getStatus() != DocumentFileStatus.ACTIVE) {
            throw new IllegalArgumentException("Solo se puede descargar un documento activo.");
        }

        DocumentRequirement requirement = getActiveRequirement(
                documentFile.getDocumentRequirementId(),
                instance.getOrgId(),
                instance.getDiagramId());

        if (!Boolean.TRUE.equals(requirement.getClientCanRead())) {
            throw new AccessDeniedException("El cliente no tiene permiso para descargar este documento.");
        }

        URL downloadUrl = s3StorageService.generateDownloadUrl(documentFile.getS3Key(), DOWNLOAD_URL_TTL);

        DocumentDownloadUrlResponse response = new DocumentDownloadUrlResponse();
        response.setDocumentFileId(documentFile.getId());
        response.setOriginalFileName(documentFile.getOriginalFileName());
        response.setContentType(documentFile.getContentType());
        response.setExpiresInSeconds(DOWNLOAD_URL_TTL.toSeconds());
        response.setDownloadUrl(downloadUrl.toString());
        return response;
    }

    private DocumentFile createAndStoreDocument(
            ProcessInstance instance,
            DocumentRequirement requirement,
            String processAssignmentId,
            MultipartFile file,
            User currentUser,
            int version,
            String replacedByDocumentFileId) {
        StoredDocumentObject stored = s3StorageService.uploadDocument(
                file,
                buildStorageContext(instance, requirement, instance.getOrgId()),
                version);

        LocalDateTime now = LocalDateTime.now();
        DocumentFile documentFile = DocumentFile.builder()
                .orgId(instance.getOrgId())
                .processInstanceId(instance.getId())
                .processAssignmentId(normalizeOptionalId(processAssignmentId))
                .diagramId(instance.getDiagramId())
                .nodeId(requirement.getNodeId())
                .documentRequirementId(requirement.getId())
                .requirementName(requirement.getName())
                .originalFileName(stored.originalFileName())
                .contentType(stored.contentType())
                .fileExtension(resolveFileExtension(stored.originalFileName()))
                .fileSizeBytes(stored.size())
                .s3Bucket(stored.bucket())
                .s3Key(stored.key())
                .status(DocumentFileStatus.ACTIVE)
                .version(version)
                .uploadedBy(currentUser.getId())
                .uploadedByName(resolveUserDisplayName(currentUser))
                .uploadedByDepartmentId(currentUser.getRole() == Roles.CLIENT ? null : currentUser.getDepartmentId())
                .createdAt(now)
                .updatedAt(now)
                .replacedByDocumentFileId(replacedByDocumentFileId)
                .build();

        return documentFileRepository.save(documentFile);
    }

    private ProcessInstance getInstance(String processInstanceId, User currentUser) {
        if (!StringUtils.hasText(processInstanceId)) {
            throw new IllegalArgumentException("El processInstanceId es obligatorio.");
        }

        return processInstanceRepository.findByIdAndOrgId(processInstanceId, currentUser.getOrgId())
                .orElseThrow(() -> new ResourceNotFoundException("Instancia de proceso no encontrada."));
    }

    private ProcessInstance getClientInstance(String processInstanceId, User currentUser) {
        if (currentUser == null || currentUser.getRole() != Roles.CLIENT) {
            throw new AccessDeniedException("Solo usuarios cliente pueden acceder a este recurso.");
        }

        if (!StringUtils.hasText(processInstanceId)) {
            throw new IllegalArgumentException("El processInstanceId es obligatorio.");
        }

        ProcessInstance instance = processInstanceRepository.findById(processInstanceId)
                .orElseThrow(() -> new ResourceNotFoundException("Instancia de proceso no encontrada."));

        if (!Objects.equals(instance.getClientId(), currentUser.getId())) {
            throw new AccessDeniedException("No tienes permiso para acceder a este tramite.");
        }

        return instance;
    }

    private DocumentStorageContext buildStorageContext(
            ProcessInstance instance,
            DocumentRequirement requirement,
            String orgId) {
        String orgName = organizationRepository.findById(orgId)
                .map(organization -> organization.getName())
                .orElse("organization");

        return new DocumentStorageContext(
                orgId,
                orgName,
                instance.getDiagramId(),
                instance.getDiagramName(),
                instance.getClientId(),
                instance.getClientName(),
                instance.getCode(),
                instance.getId(),
                requirement.getId(),
                requirement.getName());
    }

    private DocumentRequirement getActiveRequirement(String requirementId, String orgId, String diagramId) {
        if (!StringUtils.hasText(requirementId)) {
            throw new IllegalArgumentException("El documentRequirementId es obligatorio.");
        }

        DocumentRequirement requirement = documentRequirementRepository.findByIdAndOrgId(requirementId, orgId)
                .orElseThrow(() -> new ResourceNotFoundException("Requisito documental no encontrado."));

        if (requirement.getStatus() != DocumentRequirementStatus.ACTIVE) {
            throw new IllegalArgumentException("El requisito documental no esta activo.");
        }

        if (!Objects.equals(requirement.getDiagramId(), diagramId)) {
            throw new IllegalArgumentException("El requisito documental no pertenece al diagrama de la instancia.");
        }

        return requirement;
    }

    private DocumentFile getDocumentFile(String documentFileId, String orgId) {
        if (!StringUtils.hasText(documentFileId)) {
            throw new IllegalArgumentException("El documentFileId es obligatorio.");
        }

        return documentFileRepository.findByIdAndOrgId(documentFileId, orgId)
                .orElseThrow(() -> new ResourceNotFoundException("Documento no encontrado."));
    }

    private void validateOptionalAssignment(
            String processAssignmentId,
            ProcessInstance instance,
            DocumentRequirement requirement) {
        String normalizedAssignmentId = normalizeOptionalId(processAssignmentId);

        if (normalizedAssignmentId == null) {
            return;
        }

        ProcessAssignment assignment = processAssignmentRepository
                .findByIdAndProcessInstanceId(normalizedAssignmentId, instance.getId())
                .orElseThrow(() -> new ResourceNotFoundException("Asignacion no encontrada para esta instancia."));

        if (!Objects.equals(assignment.getNodeId(), requirement.getNodeId())) {
            throw new IllegalArgumentException("La asignacion no corresponde al nodo del requisito documental.");
        }
    }

    private void validateRequirementNodeIsActive(ProcessInstance instance, DocumentRequirement requirement) {
        List<String> activeNodeIds = instance.getActiveNodeIds() != null
                ? instance.getActiveNodeIds()
                : List.of();

        if (!activeNodeIds.contains(requirement.getNodeId())) {
            throw new IllegalArgumentException("El nodo del requisito documental no esta activo para este tramite.");
        }
    }

    private void validateDepartmentPermission(User currentUser, List<String> allowedDepartmentIds, String message) {
        String departmentId = currentUser.getDepartmentId();

        if (!StringUtils.hasText(departmentId)) {
            throw new AccessDeniedException("El usuario no tiene departamento asignado.");
        }

        if (allowedDepartmentIds == null || !allowedDepartmentIds.contains(departmentId)) {
            throw new AccessDeniedException(message);
        }
    }

    private void validateFile(MultipartFile file, DocumentRequirement requirement) {
        if (file == null || file.isEmpty()) {
            throw new IllegalArgumentException("El archivo es obligatorio.");
        }

        String extension = resolveFileExtension(file.getOriginalFilename());
        if (!StringUtils.hasText(extension)) {
            throw new IllegalArgumentException("El archivo debe tener extension.");
        }

        List<String> allowedTypes = requirement.getAllowedFileTypes() != null
                ? requirement.getAllowedFileTypes().stream()
                        .filter(StringUtils::hasText)
                        .map(this::normalizeExtension)
                        .collect(Collectors.toList())
                : List.of();

        if (!allowedTypes.contains(extension)) {
            throw new IllegalArgumentException("Tipo de archivo no permitido para este requisito.");
        }

        Integer maxFileSizeMb = requirement.getMaxFileSizeMb();
        if (maxFileSizeMb == null || maxFileSizeMb < 1) {
            throw new IllegalArgumentException("El requisito documental no tiene tamano maximo valido.");
        }

        long maxBytes = maxFileSizeMb.longValue() * 1024L * 1024L;
        if (file.getSize() > maxBytes) {
            throw new IllegalArgumentException("El archivo supera el tamano maximo permitido.");
        }
    }

    private int resolveNextVersion(String orgId, String processInstanceId, String requirementId) {
        return documentFileRepository
                .findTopByOrgIdAndProcessInstanceIdAndDocumentRequirementIdOrderByVersionDesc(
                        orgId,
                        processInstanceId,
                        requirementId)
                .map(file -> safeVersion(file) + 1)
                .orElse(1);
    }

    private DocumentExpedientItemResponse toExpedientItem(
            DocumentRequirement requirement,
            DocumentFile latestFile,
            User currentUser) {
        DocumentExpedientItemResponse item = new DocumentExpedientItemResponse();
        boolean canRead = hasDepartmentPermission(currentUser, requirement.getReadDepartmentIds());

        item.setRequirement(toRequirementResponse(requirement));
        item.setCurrentFile(canRead && latestFile != null && latestFile.getStatus() == DocumentFileStatus.ACTIVE
                ? toResponse(latestFile)
                : null);
        item.setStatus(resolveItemStatus(latestFile));
        item.setCanRead(canRead);
        item.setCanUpload(hasDepartmentPermission(currentUser, requirement.getUploadDepartmentIds()));
        item.setCanEdit(hasDepartmentPermission(currentUser, requirement.getEditDepartmentIds()));
        return item;
    }

    private ClientDocumentExpedientItemResponse toClientExpedientItem(
            ProcessInstance instance,
            DocumentRequirement requirement,
            DocumentFile latestFile) {
        boolean canRead = Boolean.TRUE.equals(requirement.getClientCanRead());
        boolean nodeActive = instance.getActiveNodeIds() != null
                && instance.getActiveNodeIds().contains(requirement.getNodeId());
        boolean hasActiveFile = latestFile != null && latestFile.getStatus() == DocumentFileStatus.ACTIVE;

        ClientDocumentExpedientItemResponse item = new ClientDocumentExpedientItemResponse();
        item.setRequirement(toRequirementResponse(requirement));
        item.setCurrentFile(canRead && hasActiveFile ? toResponse(latestFile) : null);
        item.setStatus(hasActiveFile ? STATUS_UPLOADED : STATUS_PENDING);
        item.setCanRead(canRead);
        item.setCanUpload(Boolean.TRUE.equals(requirement.getClientCanUpload()) && nodeActive);
        item.setCanReplace(Boolean.TRUE.equals(requirement.getClientCanReplace()) && nodeActive && hasActiveFile);
        return item;
    }

    private boolean hasAnyClientPermission(DocumentRequirement requirement) {
        return Boolean.TRUE.equals(requirement.getClientCanRead())
                || Boolean.TRUE.equals(requirement.getClientCanUpload())
                || Boolean.TRUE.equals(requirement.getClientCanReplace());
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

    private boolean hasDepartmentPermission(User currentUser, List<String> allowedDepartmentIds) {
        return StringUtils.hasText(currentUser.getDepartmentId())
                && allowedDepartmentIds != null
                && allowedDepartmentIds.contains(currentUser.getDepartmentId());
    }

    private DocumentUploadResponse toUploadResponse(DocumentFile documentFile) {
        DocumentUploadResponse response = new DocumentUploadResponse();
        response.setDocumentFile(toResponse(documentFile));
        response.setContentType(documentFile.getContentType());
        response.setSize(documentFile.getFileSizeBytes());
        response.setOriginalFileName(documentFile.getOriginalFileName());
        return response;
    }

    private DocumentFileResponse toResponse(DocumentFile documentFile) {
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

    private String resolveFileExtension(String fileName) {
        if (!StringUtils.hasText(fileName)) {
            return "";
        }

        String cleanName = fileName.trim();
        int lastDot = cleanName.lastIndexOf('.');

        if (lastDot < 0 || lastDot == cleanName.length() - 1) {
            return "";
        }

        return normalizeExtension(cleanName.substring(lastDot + 1));
    }

    private String normalizeExtension(String extension) {
        String clean = extension == null ? "" : extension.trim().toLowerCase(Locale.ROOT);
        return clean.startsWith(".") ? clean.substring(1) : clean;
    }

    private String normalizeOptionalId(String value) {
        return StringUtils.hasText(value) ? value.trim() : null;
    }

    private int safeVersion(DocumentFile file) {
        return Optional.ofNullable(file.getVersion()).orElse(0);
    }

    private String resolveUserDisplayName(User user) {
        if (user == null) {
            return "Usuario";
        }

        if (user.getProfile() != null) {
            String nombre = Optional.ofNullable(user.getProfile().getNombre()).orElse("");
            String apellido = Optional.ofNullable(user.getProfile().getApellido()).orElse("");
            String fullName = (nombre + " " + apellido).trim();

            if (!fullName.isBlank()) {
                return fullName;
            }
        }

        return StringUtils.hasText(user.getEmail()) ? user.getEmail() : user.getId();
    }
}
