package sw1.backend.flowroad.services.document.onlyoffice;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;
import org.springframework.web.util.UriComponentsBuilder;

import lombok.RequiredArgsConstructor;
import sw1.backend.flowroad.config.OnlyOfficeProperties;
import sw1.backend.flowroad.dtos.document.onlyoffice.OnlyOfficeCallbackRequest;
import sw1.backend.flowroad.dtos.document.onlyoffice.OnlyOfficeEditorConfigResponse;
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
import sw1.backend.flowroad.repository.organization.OrganizationRepository;
import sw1.backend.flowroad.repository.process.ProcessInstanceRepository;
import sw1.backend.flowroad.services.document.S3StorageService;
import sw1.backend.flowroad.services.document.S3StorageService.DocumentStorageContext;
import sw1.backend.flowroad.services.document.S3StorageService.DownloadedDocumentObject;
import sw1.backend.flowroad.services.document.S3StorageService.StoredDocumentObject;

@Service
@RequiredArgsConstructor
public class OnlyOfficeCollaborationService {

    private static final Logger log = LoggerFactory.getLogger(OnlyOfficeCollaborationService.class);
    private static final List<String> COLLABORATIVE_EXTENSIONS = List.of("doc", "docx", "xls", "xlsx");
    private static final long DOWNLOAD_TOKEN_TTL_SECONDS = 3600L;
    private static final int ONLYOFFICE_STATUS_EDITING = 1;
    private static final int ONLYOFFICE_STATUS_READY_TO_SAVE = 2;
    private static final int ONLYOFFICE_STATUS_SAVE_ERROR = 3;
    private static final int ONLYOFFICE_STATUS_CLOSED_WITHOUT_CHANGES = 4;
    private static final int ONLYOFFICE_STATUS_FORCE_SAVE = 6;
    private static final int ONLYOFFICE_STATUS_FORCE_SAVE_ERROR = 7;

    private final OnlyOfficeProperties onlyOfficeProperties;
    private final DocumentFileRepository documentFileRepository;
    private final DocumentRequirementRepository documentRequirementRepository;
    private final ProcessInstanceRepository processInstanceRepository;
    private final OrganizationRepository organizationRepository;
    private final S3StorageService s3StorageService;
    private final OnlyOfficeJwtService onlyOfficeJwtService;
    private final HttpClient onlyOfficeHttpClient = HttpClient.newBuilder().build();

    public OnlyOfficeEditorConfigResponse buildEditorConfig(String documentFileId, User currentUser) {
        validateInternalUser(currentUser);

        DocumentFile documentFile = getActiveDocumentFile(documentFileId);
        validateSameOrganization(documentFile, currentUser);
        String extension = validateCollaborativeExtension(documentFile);
        DocumentRequirement requirement = getActiveRequirement(documentFile);

        String mode = resolveEditorMode(currentUser, requirement);
        String downloadToken = onlyOfficeJwtService.sign(Map.of(
                "purpose", "onlyoffice-download",
                "documentFileId", documentFile.getId(),
                "exp", Instant.now().getEpochSecond() + DOWNLOAD_TOKEN_TTL_SECONDS));

        String downloadUrl = UriComponentsBuilder
                .fromHttpUrl(buildApiUrl("/document-collaboration/onlyoffice/files/" + documentFile.getId()
                        + "/download"))
                .queryParam("token", downloadToken)
                .toUriString();

        Map<String, Object> config = Map.of(
                "document", Map.of(
                        "fileType", extension,
                        "key", buildDocumentKey(documentFile),
                        "title", resolveTitle(documentFile),
                        "url", downloadUrl),
                "documentType", resolveDocumentType(extension),
                "editorConfig", Map.of(
                        "mode", mode,
                        "callbackUrl", buildApiUrl("/document-collaboration/onlyoffice/files/"
                                + documentFile.getId() + "/callback"),
                        "user", Map.of(
                                "id", currentUser.getId(),
                                "name", resolveUserDisplayName(currentUser))));

        String configToken = onlyOfficeJwtService.sign(config);
        if (StringUtils.hasText(configToken)) {
            config = Map.of(
                    "document", config.get("document"),
                    "documentType", config.get("documentType"),
                    "editorConfig", config.get("editorConfig"),
                    "token", configToken);
        }

        return new OnlyOfficeEditorConfigResponse(
                onlyOfficeProperties.getDocumentServerUrl(),
                config);
    }

    public DownloadedDocumentObject downloadDocument(String documentFileId, String token, User currentUser) {
        DocumentFile documentFile = getActiveDocumentFile(documentFileId);
        validateCollaborativeExtension(documentFile);

        if (currentUser != null) {
            validateInternalUser(currentUser);
            validateSameOrganization(documentFile, currentUser);
            DocumentRequirement requirement = getActiveRequirement(documentFile);
            if (!hasDepartmentPermission(currentUser, requirement.getReadDepartmentIds())
                    && !hasDepartmentPermission(currentUser, requirement.getEditDepartmentIds())) {
                throw new AccessDeniedException("No tienes permiso departamental para descargar este documento.");
            }
        } else {
            validateDownloadToken(documentFile, token);
        }

        return s3StorageService.downloadDocument(documentFile.getS3Key());
    }

    public DocumentFile getDownloadMetadata(String documentFileId) {
        return getActiveDocumentFile(documentFileId);
    }

    public boolean handleCallback(
            String documentFileId,
            OnlyOfficeCallbackRequest request,
            String authorizationHeader) {
        int status = request != null && request.getStatus() != null ? request.getStatus() : 0;
        String callbackKey = request != null ? request.getKey() : null;

        log.info(
                "[ONLYOFFICE][CALLBACK] documentFileId={} status={} key={}",
                documentFileId,
                status,
                callbackKey);

        try {
            validateCallbackTokenIfPresent(request, authorizationHeader, documentFileId);

            return switch (status) {
                case ONLYOFFICE_STATUS_EDITING, ONLYOFFICE_STATUS_CLOSED_WITHOUT_CHANGES -> {
                    log.info("[ONLYOFFICE][CALLBACK] Status {} sin guardado para documentFileId={}", status,
                            documentFileId);
                    yield true;
                }
                case ONLYOFFICE_STATUS_READY_TO_SAVE, ONLYOFFICE_STATUS_FORCE_SAVE -> saveEditedVersion(
                        documentFileId,
                        request);
                case ONLYOFFICE_STATUS_SAVE_ERROR, ONLYOFFICE_STATUS_FORCE_SAVE_ERROR -> {
                    log.warn(
                            "[ONLYOFFICE][CALLBACK] ONLYOFFICE reporto error status={} documentFileId={} error={}",
                            status,
                            documentFileId,
                            request != null ? request.getError() : null);
                    yield true;
                }
                default -> {
                    log.info("[ONLYOFFICE][CALLBACK] Status {} no versionable para documentFileId={}", status,
                            documentFileId);
                    yield true;
                }
            };
        } catch (Exception ex) {
            log.error(
                    "[ONLYOFFICE][CALLBACK] Error guardando callback documentFileId={} status={}",
                    documentFileId,
                    status,
                    ex);
            return false;
        }
    }

    private void validateInternalUser(User currentUser) {
        if (currentUser == null || currentUser.getRole() == null) {
            throw new AccessDeniedException("Usuario autenticado requerido.");
        }

        if (currentUser.getRole() != Roles.ADMIN
                && currentUser.getRole() != Roles.WORKER
                && currentUser.getRole() != Roles.RECEP) {
            throw new AccessDeniedException("Rol interno no autorizado para colaboracion documental.");
        }
    }

    private DocumentFile getActiveDocumentFile(String documentFileId) {
        if (!StringUtils.hasText(documentFileId)) {
            throw new IllegalArgumentException("El documentFileId es obligatorio.");
        }

        DocumentFile documentFile = documentFileRepository.findById(documentFileId)
                .orElseThrow(() -> new ResourceNotFoundException("Documento no encontrado."));

        if (documentFile.getStatus() != DocumentFileStatus.ACTIVE) {
            throw new IllegalArgumentException("Solo se puede abrir un documento activo.");
        }

        return documentFile;
    }

    private DocumentFile getDocumentFile(String documentFileId) {
        if (!StringUtils.hasText(documentFileId)) {
            throw new IllegalArgumentException("El documentFileId es obligatorio.");
        }

        return documentFileRepository.findById(documentFileId)
                .orElseThrow(() -> new ResourceNotFoundException("Documento no encontrado."));
    }

    private void validateSameOrganization(DocumentFile documentFile, User currentUser) {
        if (!Objects.equals(documentFile.getOrgId(), currentUser.getOrgId())) {
            throw new AccessDeniedException("El documento no pertenece a la organizacion del usuario.");
        }
    }

    private String validateCollaborativeExtension(DocumentFile documentFile) {
        String extension = normalizeExtension(documentFile.getFileExtension());
        if (!StringUtils.hasText(extension)) {
            extension = resolveFileExtension(documentFile.getOriginalFileName());
        }

        if (!COLLABORATIVE_EXTENSIONS.contains(extension)) {
            throw new IllegalArgumentException("Documento no colaborativo. Solo se permiten DOC, DOCX, XLS y XLSX.");
        }

        return extension;
    }

    private DocumentRequirement getActiveRequirement(DocumentFile documentFile) {
        DocumentRequirement requirement = documentRequirementRepository
                .findByIdAndOrgId(documentFile.getDocumentRequirementId(), documentFile.getOrgId())
                .orElseThrow(() -> new ResourceNotFoundException("Requisito documental no encontrado."));

        if (requirement.getStatus() != DocumentRequirementStatus.ACTIVE) {
            throw new IllegalArgumentException("El requisito documental no esta activo.");
        }

        return requirement;
    }

    private String resolveEditorMode(User currentUser, DocumentRequirement requirement) {
        if (hasDepartmentPermission(currentUser, requirement.getEditDepartmentIds())) {
            return "edit";
        }

        if (hasDepartmentPermission(currentUser, requirement.getReadDepartmentIds())) {
            return "view";
        }

        throw new AccessDeniedException("No tienes permiso departamental para abrir este documento.");
    }

    private boolean hasDepartmentPermission(User currentUser, List<String> allowedDepartmentIds) {
        return StringUtils.hasText(currentUser.getDepartmentId())
                && allowedDepartmentIds != null
                && allowedDepartmentIds.contains(currentUser.getDepartmentId());
    }

    private String resolveDocumentType(String extension) {
        return switch (extension) {
            case "doc", "docx" -> "word";
            case "xls", "xlsx" -> "cell";
            default -> throw new IllegalArgumentException("Documento no colaborativo.");
        };
    }

    private String buildDocumentKey(DocumentFile documentFile) {
        long updatedAt = Optional.ofNullable(documentFile.getUpdatedAt())
                .orElseGet(LocalDateTime::now)
                .atZone(ZoneId.systemDefault())
                .toInstant()
                .toEpochMilli();

        return documentFile.getId() + "-v" + Optional.ofNullable(documentFile.getVersion()).orElse(1)
                + "-" + updatedAt;
    }

    private synchronized boolean saveEditedVersion(String documentFileId, OnlyOfficeCallbackRequest request)
            throws IOException, InterruptedException {
        DocumentFile currentFile = getDocumentFile(documentFileId);

        if (currentFile.getStatus() != DocumentFileStatus.ACTIVE) {
            log.info(
                    "[ONLYOFFICE][CALLBACK] Documento {} ya no esta ACTIVE; se asume callback duplicado.",
                    documentFileId);
            return true;
        }

        validateCollaborativeExtension(currentFile);
        validateCallbackKey(currentFile, request);

        if (request == null || !StringUtils.hasText(request.getUrl())) {
            log.warn("[ONLYOFFICE][CALLBACK] Status versionable sin url para documentFileId={}", documentFileId);
            return false;
        }

        EditedDocumentBytes editedDocument = downloadEditedDocument(request.getUrl(), currentFile);
        int nextVersion = Optional.ofNullable(currentFile.getVersion()).orElse(0) + 1;
        StoredDocumentObject stored = s3StorageService.uploadDocumentBytes(
                editedDocument.bytes(),
                currentFile.getOriginalFileName(),
                editedDocument.contentType(),
                buildStorageContext(currentFile),
                nextVersion);

        LocalDateTime now = LocalDateTime.now();
        DocumentFile newVersion = DocumentFile.builder()
                .orgId(currentFile.getOrgId())
                .processInstanceId(currentFile.getProcessInstanceId())
                .processAssignmentId(currentFile.getProcessAssignmentId())
                .diagramId(currentFile.getDiagramId())
                .nodeId(currentFile.getNodeId())
                .documentRequirementId(currentFile.getDocumentRequirementId())
                .requirementName(currentFile.getRequirementName())
                .originalFileName(stored.originalFileName())
                .contentType(stored.contentType())
                .fileExtension(validateCollaborativeExtension(currentFile))
                .fileSizeBytes(stored.size())
                .s3Bucket(stored.bucket())
                .s3Key(stored.key())
                .status(DocumentFileStatus.ACTIVE)
                .version(nextVersion)
                .uploadedBy(resolveOnlyOfficeUploadedBy(currentFile, request))
                .uploadedByName(resolveOnlyOfficeUploadedByName(currentFile, request))
                .uploadedByDepartmentId(currentFile.getUploadedByDepartmentId())
                .createdAt(now)
                .updatedAt(now)
                .replacedByDocumentFileId(null)
                .build();

        DocumentFile savedNewVersion = documentFileRepository.save(newVersion);

        currentFile.setStatus(DocumentFileStatus.REPLACED);
        currentFile.setUpdatedAt(now);
        currentFile.setReplacedByDocumentFileId(savedNewVersion.getId());
        documentFileRepository.save(currentFile);

        log.info(
                "[ONLYOFFICE][CALLBACK] Nueva version creada documentFileId={} newDocumentFileId={} version={} bytes={}",
                documentFileId,
                savedNewVersion.getId(),
                savedNewVersion.getVersion(),
                savedNewVersion.getFileSizeBytes());

        return true;
    }

    private EditedDocumentBytes downloadEditedDocument(String url, DocumentFile currentFile)
            throws IOException, InterruptedException {
        URI uri = URI.create(url);
        String scheme = uri.getScheme();

        if (!"http".equalsIgnoreCase(scheme) && !"https".equalsIgnoreCase(scheme)) {
            throw new IllegalArgumentException("URL de ONLYOFFICE invalida.");
        }

        log.info("[ONLYOFFICE][CALLBACK] Descargando archivo editado documentFileId={}", currentFile.getId());

        HttpRequest request = HttpRequest.newBuilder(uri)
                .GET()
                .build();
        HttpResponse<byte[]> response = onlyOfficeHttpClient.send(
                request,
                HttpResponse.BodyHandlers.ofByteArray());

        if (response.statusCode() < 200 || response.statusCode() >= 300) {
            throw new IllegalStateException("ONLYOFFICE devolvio HTTP " + response.statusCode());
        }

        byte[] bytes = response.body();
        if (bytes == null || bytes.length == 0) {
            throw new IllegalStateException("ONLYOFFICE devolvio un archivo vacio.");
        }

        String contentType = response.headers()
                .firstValue("Content-Type")
                .filter(StringUtils::hasText)
                .orElse(currentFile.getContentType());

        log.info("[ONLYOFFICE][CALLBACK] Archivo editado descargado documentFileId={} bytes={}",
                currentFile.getId(),
                bytes.length);

        return new EditedDocumentBytes(bytes, contentType);
    }

    private DocumentStorageContext buildStorageContext(DocumentFile documentFile) {
        ProcessInstance instance = processInstanceRepository.findById(documentFile.getProcessInstanceId())
                .orElse(null);
        String orgName = organizationRepository.findById(documentFile.getOrgId())
                .map(organization -> organization.getName())
                .orElse("organization");

        return new DocumentStorageContext(
                documentFile.getOrgId(),
                orgName,
                documentFile.getDiagramId(),
                instance != null ? instance.getDiagramName() : documentFile.getDiagramId(),
                instance != null ? instance.getClientId() : "client",
                instance != null ? instance.getClientName() : "client",
                instance != null ? instance.getCode() : "process",
                documentFile.getProcessInstanceId(),
                documentFile.getDocumentRequirementId(),
                documentFile.getRequirementName());
    }

    private void validateCallbackKey(DocumentFile documentFile, OnlyOfficeCallbackRequest request) {
        if (request == null || !StringUtils.hasText(request.getKey())) {
            return;
        }

        String expectedKey = buildDocumentKey(documentFile);
        if (!Objects.equals(expectedKey, request.getKey())) {
            throw new AccessDeniedException("La key de ONLYOFFICE no coincide con el documento activo.");
        }
    }

    private void validateCallbackTokenIfPresent(
            OnlyOfficeCallbackRequest request,
            String authorizationHeader,
            String documentFileId) {
        String token = resolveCallbackToken(request, authorizationHeader);

        if (!StringUtils.hasText(token)) {
            log.warn("[ONLYOFFICE][CALLBACK] Callback sin JWT de ONLYOFFICE documentFileId={}", documentFileId);
            return;
        }

        Map<String, Object> claims = onlyOfficeJwtService.verify(token, false);
        Object document = claims.get("document");

        if (document instanceof Map<?, ?> documentClaims) {
            Object tokenKey = documentClaims.get("key");
            DocumentFile documentFile = getDocumentFile(documentFileId);

            if (tokenKey instanceof String key
                    && StringUtils.hasText(key)
                    && !Objects.equals(key, buildDocumentKey(documentFile))) {
                throw new AccessDeniedException("El JWT de ONLYOFFICE no corresponde al documento.");
            }
        }
    }

    private String resolveCallbackToken(OnlyOfficeCallbackRequest request, String authorizationHeader) {
        if (request != null && StringUtils.hasText(request.getToken())) {
            return request.getToken();
        }

        if (StringUtils.hasText(authorizationHeader) && authorizationHeader.startsWith("Bearer ")) {
            return authorizationHeader.substring(7);
        }

        return null;
    }

    private String resolveOnlyOfficeUploadedBy(DocumentFile currentFile, OnlyOfficeCallbackRequest request) {
        if (request != null && request.getUsers() != null && !request.getUsers().isEmpty()) {
            return request.getUsers().get(request.getUsers().size() - 1);
        }

        return StringUtils.hasText(currentFile.getUploadedBy())
                ? currentFile.getUploadedBy()
                : "onlyoffice";
    }

    private String resolveOnlyOfficeUploadedByName(DocumentFile currentFile, OnlyOfficeCallbackRequest request) {
        if (request != null && request.getUsers() != null && !request.getUsers().isEmpty()) {
            return "Edicion colaborativa";
        }

        return StringUtils.hasText(currentFile.getUploadedByName())
                ? currentFile.getUploadedByName()
                : "ONLYOFFICE";
    }

    private String buildApiUrl(String path) {
        String baseUrl = onlyOfficeProperties.getCallbackBaseUrl();
        if (!StringUtils.hasText(baseUrl)) {
            throw new IllegalStateException("onlyoffice.callback-base-url no esta configurado.");
        }

        return UriComponentsBuilder.fromHttpUrl(baseUrl)
                .path(path)
                .toUriString();
    }

    private void validateDownloadToken(DocumentFile documentFile, String token) {
        Map<String, Object> claims = onlyOfficeJwtService.verify(token);
        if (!Objects.equals(claims.get("purpose"), "onlyoffice-download")
                || !Objects.equals(claims.get("documentFileId"), documentFile.getId())) {
            throw new AccessDeniedException("Token de descarga no autorizado para este documento.");
        }
    }

    private String resolveTitle(DocumentFile documentFile) {
        return StringUtils.hasText(documentFile.getOriginalFileName())
                ? documentFile.getOriginalFileName()
                : "Documento." + validateCollaborativeExtension(documentFile);
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

    private String resolveUserDisplayName(User user) {
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

    private record EditedDocumentBytes(byte[] bytes, String contentType) {
    }
}
