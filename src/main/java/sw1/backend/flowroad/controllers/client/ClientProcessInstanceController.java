package sw1.backend.flowroad.controllers.client;

import java.util.List;
import java.util.Objects;
import java.util.stream.Collectors;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import lombok.RequiredArgsConstructor;
import sw1.backend.flowroad.dtos.document.ClientDocumentExpedientResponse;
import sw1.backend.flowroad.dtos.document.DocumentDownloadUrlResponse;
import sw1.backend.flowroad.dtos.document.DocumentFileResponse;
import sw1.backend.flowroad.dtos.document.DocumentUploadResponse;
import sw1.backend.flowroad.dtos.process.ClientProcessInstanceResponse;
import sw1.backend.flowroad.exceptions.ResourceNotFoundException;
import sw1.backend.flowroad.models.process.ProcessInstance;
import sw1.backend.flowroad.models.user.User;
import sw1.backend.flowroad.repository.process.ProcessInstanceRepository;
import sw1.backend.flowroad.services.document.DocumentFileService;

@RestController
@RequestMapping("/client/process-instances")
@RequiredArgsConstructor
@PreAuthorize("hasAuthority('CLIENT')")
public class ClientProcessInstanceController {

    private final ProcessInstanceRepository processInstanceRepository;
    private final DocumentFileService documentFileService;

    @GetMapping
    public ResponseEntity<List<ClientProcessInstanceResponse>> getMyProcessInstances(
            @AuthenticationPrincipal User currentUser) {
        return ResponseEntity.ok(processInstanceRepository.findByClientIdOrderByUpdatedAtDesc(currentUser.getId())
                .stream()
                .map(this::toClientProcessResponse)
                .collect(Collectors.toList()));
    }

    @GetMapping("/{processInstanceId}")
    public ResponseEntity<ClientProcessInstanceResponse> getMyProcessInstanceDetail(
            @PathVariable String processInstanceId,
            @AuthenticationPrincipal User currentUser) {
        ProcessInstance instance = getClientInstance(processInstanceId, currentUser);
        return ResponseEntity.ok(toClientProcessResponse(instance));
    }

    @GetMapping("/{processInstanceId}/documents")
    public ResponseEntity<ClientDocumentExpedientResponse> getMyDocuments(
            @PathVariable String processInstanceId,
            @AuthenticationPrincipal User currentUser) {
        return ResponseEntity.ok(documentFileService.getClientExpedient(processInstanceId, currentUser));
    }

    @PostMapping("/{processInstanceId}/documents")
    public ResponseEntity<DocumentUploadResponse> uploadDocument(
            @PathVariable String processInstanceId,
            @RequestParam("file") MultipartFile file,
            @RequestParam("documentRequirementId") String documentRequirementId,
            @AuthenticationPrincipal User currentUser) {
        DocumentUploadResponse response = documentFileService.uploadClientDocument(
                processInstanceId,
                documentRequirementId,
                file,
                currentUser);

        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    @PutMapping("/{processInstanceId}/documents/{documentFileId}/replace")
    public ResponseEntity<DocumentFileResponse> replaceDocument(
            @PathVariable String processInstanceId,
            @PathVariable String documentFileId,
            @RequestParam("file") MultipartFile file,
            @AuthenticationPrincipal User currentUser) {
        return ResponseEntity.ok(documentFileService.replaceClientDocument(
                processInstanceId,
                documentFileId,
                file,
                currentUser));
    }

    @GetMapping("/{processInstanceId}/documents/{documentFileId}/download-url")
    public ResponseEntity<DocumentDownloadUrlResponse> generateDownloadUrl(
            @PathVariable String processInstanceId,
            @PathVariable String documentFileId,
            @AuthenticationPrincipal User currentUser) {
        return ResponseEntity.ok(documentFileService.generateClientDownloadUrl(
                processInstanceId,
                documentFileId,
                currentUser));
    }

    private ProcessInstance getClientInstance(String processInstanceId, User currentUser) {
        ProcessInstance instance = processInstanceRepository.findById(processInstanceId)
                .orElseThrow(() -> new ResourceNotFoundException("Instancia de proceso no encontrada."));

        if (!Objects.equals(instance.getClientId(), currentUser.getId())) {
            throw new AccessDeniedException("No tienes permiso para acceder a este tramite.");
        }

        return instance;
    }

    private ClientProcessInstanceResponse toClientProcessResponse(ProcessInstance instance) {
        return ClientProcessInstanceResponse.builder()
                .id(instance.getId())
                .code(instance.getCode())
                .diagramId(instance.getDiagramId())
                .diagramName(instance.getDiagramName())
                .diagramVersion(instance.getDiagramVersion())
                .status(instance.getStatus())
                .clientId(instance.getClientId())
                .clientName(instance.getClientName())
                .clientEmail(instance.getClientEmail())
                .startedAt(instance.getStartedAt())
                .updatedAt(instance.getUpdatedAt())
                .finishedAt(instance.getFinishedAt())
                .build();
    }
}
