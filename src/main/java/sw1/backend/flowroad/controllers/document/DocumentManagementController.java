package sw1.backend.flowroad.controllers.document;

import java.util.List;

import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import lombok.RequiredArgsConstructor;
import sw1.backend.flowroad.dtos.document.DocumentManagementExpedientDetailResponse;
import sw1.backend.flowroad.dtos.document.DocumentManagementExpedientSummaryResponse;
import sw1.backend.flowroad.models.user.User;
import sw1.backend.flowroad.services.document.DocumentManagementService;

@RestController
@RequestMapping("/document-management/expedients")
@RequiredArgsConstructor
@PreAuthorize("hasAnyAuthority('ADMIN', 'WORKER', 'RECEP')")
public class DocumentManagementController {

    private final DocumentManagementService documentManagementService;

    @GetMapping
    public ResponseEntity<List<DocumentManagementExpedientSummaryResponse>> listExpedients(
            @AuthenticationPrincipal User currentUser) {
        return ResponseEntity.ok(documentManagementService.listVisibleExpedients(currentUser));
    }

    @GetMapping("/{processInstanceId}")
    public ResponseEntity<DocumentManagementExpedientDetailResponse> getExpedientDetail(
            @PathVariable String processInstanceId,
            @AuthenticationPrincipal User currentUser) {
        return ResponseEntity.ok(documentManagementService.getVisibleExpedientDetail(processInstanceId, currentUser));
    }
}
