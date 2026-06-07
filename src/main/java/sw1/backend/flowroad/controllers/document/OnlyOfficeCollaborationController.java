package sw1.backend.flowroad.controllers.document;

import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import lombok.RequiredArgsConstructor;
import sw1.backend.flowroad.dtos.document.onlyoffice.OnlyOfficeCallbackRequest;
import sw1.backend.flowroad.dtos.document.onlyoffice.OnlyOfficeCallbackResponse;
import sw1.backend.flowroad.dtos.document.onlyoffice.OnlyOfficeEditorConfigResponse;
import sw1.backend.flowroad.models.document.DocumentFile;
import sw1.backend.flowroad.models.user.User;
import sw1.backend.flowroad.services.document.S3StorageService.DownloadedDocumentObject;
import sw1.backend.flowroad.services.document.onlyoffice.OnlyOfficeCollaborationService;

@RestController
@RequestMapping("/document-collaboration/onlyoffice")
@RequiredArgsConstructor
public class OnlyOfficeCollaborationController {

    private final OnlyOfficeCollaborationService onlyOfficeCollaborationService;

    @PostMapping("/files/{documentFileId}/editor-config")
    @PreAuthorize("hasAnyAuthority('ADMIN', 'WORKER', 'RECEP')")
    public ResponseEntity<OnlyOfficeEditorConfigResponse> buildEditorConfig(
            @PathVariable String documentFileId,
            @AuthenticationPrincipal User currentUser) {
        return ResponseEntity.ok(onlyOfficeCollaborationService.buildEditorConfig(documentFileId, currentUser));
    }

    @GetMapping("/files/{documentFileId}/download")
    public ResponseEntity<byte[]> download(
            @PathVariable String documentFileId,
            @RequestParam(required = false) String token,
            @AuthenticationPrincipal User currentUser) {
        DocumentFile documentFile = onlyOfficeCollaborationService.getDownloadMetadata(documentFileId);
        DownloadedDocumentObject downloaded = onlyOfficeCollaborationService.downloadDocument(
                documentFileId,
                token,
                currentUser);

        String contentType = documentFile.getContentType() != null
                ? documentFile.getContentType()
                : downloaded.contentType();

        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(resolveMediaType(contentType));
        headers.setContentDisposition(ContentDisposition.inline()
                .filename(resolveFileName(documentFile))
                .build());
        headers.setContentLength(downloaded.bytes().length);

        return ResponseEntity.ok()
                .headers(headers)
                .body(downloaded.bytes());
    }

    @PostMapping("/files/{documentFileId}/callback")
    public ResponseEntity<OnlyOfficeCallbackResponse> callback(
            @PathVariable String documentFileId,
            @RequestBody OnlyOfficeCallbackRequest request,
            @RequestHeader(value = HttpHeaders.AUTHORIZATION, required = false) String authorizationHeader) {
        boolean saved = onlyOfficeCollaborationService.handleCallback(
                documentFileId,
                request,
                authorizationHeader);
        return ResponseEntity.ok(new OnlyOfficeCallbackResponse(saved ? 0 : 1));
    }

    private MediaType resolveMediaType(String contentType) {
        try {
            return MediaType.parseMediaType(contentType);
        } catch (Exception ex) {
            return MediaType.APPLICATION_OCTET_STREAM;
        }
    }

    private String resolveFileName(DocumentFile documentFile) {
        return documentFile.getOriginalFileName() != null
                ? documentFile.getOriginalFileName()
                : documentFile.getId();
    }
}
