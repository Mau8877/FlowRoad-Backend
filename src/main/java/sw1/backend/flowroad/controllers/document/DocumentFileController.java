package sw1.backend.flowroad.controllers.document;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
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
import sw1.backend.flowroad.dtos.document.DocumentDownloadUrlResponse;
import sw1.backend.flowroad.dtos.document.DocumentExpedientResponse;
import sw1.backend.flowroad.dtos.document.DocumentFileResponse;
import sw1.backend.flowroad.dtos.document.DocumentUploadResponse;
import sw1.backend.flowroad.models.user.User;
import sw1.backend.flowroad.services.document.DocumentFileService;

@RestController
@RequestMapping("/process-instances/{processInstanceId}/documents")
@RequiredArgsConstructor
public class DocumentFileController {

    private final DocumentFileService documentFileService;

    @GetMapping
    @PreAuthorize("hasAnyAuthority('ADMIN', 'DESIGNER', 'WORKER', 'RECEP')")
    public ResponseEntity<DocumentExpedientResponse> getExpedient(
            @PathVariable String processInstanceId,
            @AuthenticationPrincipal User currentUser) {
        return ResponseEntity.ok(documentFileService.getExpedient(processInstanceId, currentUser));
    }

    @PostMapping
    @PreAuthorize("hasAnyAuthority('ADMIN', 'DESIGNER', 'WORKER', 'RECEP')")
    public ResponseEntity<DocumentUploadResponse> uploadDocument(
            @PathVariable String processInstanceId,
            @RequestParam("file") MultipartFile file,
            @RequestParam("documentRequirementId") String documentRequirementId,
            @RequestParam(required = false) String processAssignmentId,
            @AuthenticationPrincipal User currentUser) {
        DocumentUploadResponse response = documentFileService.uploadDocument(
                processInstanceId,
                documentRequirementId,
                processAssignmentId,
                file,
                currentUser);

        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    @PutMapping("/{documentFileId}/replace")
    @PreAuthorize("hasAnyAuthority('ADMIN', 'DESIGNER', 'WORKER', 'RECEP')")
    public ResponseEntity<DocumentFileResponse> replaceDocument(
            @PathVariable String processInstanceId,
            @PathVariable String documentFileId,
            @RequestParam("file") MultipartFile file,
            @RequestParam(required = false) String processAssignmentId,
            @AuthenticationPrincipal User currentUser) {
        return ResponseEntity.ok(documentFileService.replaceDocument(
                processInstanceId,
                documentFileId,
                processAssignmentId,
                file,
                currentUser));
    }

    @GetMapping("/{documentFileId}/download-url")
    @PreAuthorize("hasAnyAuthority('ADMIN', 'DESIGNER', 'WORKER', 'RECEP')")
    public ResponseEntity<DocumentDownloadUrlResponse> generateDownloadUrl(
            @PathVariable String processInstanceId,
            @PathVariable String documentFileId,
            @AuthenticationPrincipal User currentUser) {
        return ResponseEntity.ok(documentFileService.generateDownloadUrl(
                processInstanceId,
                documentFileId,
                currentUser));
    }
}
