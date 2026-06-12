package sw1.backend.flowroad.controllers.report;

import java.util.List;

import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import sw1.backend.flowroad.dtos.report.GeneratedReportHistoryResponse;
import sw1.backend.flowroad.dtos.report.GeneratedReportHistoryPageResponse;
import sw1.backend.flowroad.dtos.report.ReportExportRequest;
import sw1.backend.flowroad.dtos.report.ReportPreviewResponse;
import sw1.backend.flowroad.dtos.report.ReportPromptRequest;
import sw1.backend.flowroad.dtos.report.ReportSuggestionsResponse;
import sw1.backend.flowroad.models.user.User;
import sw1.backend.flowroad.services.report.IntelligentReportService;
import sw1.backend.flowroad.services.report.ReportExportService;
import sw1.backend.flowroad.services.report.ReportExportService.ExportedReport;

@RestController
@RequestMapping("/reports/intelligent")
@RequiredArgsConstructor
@PreAuthorize("hasAuthority('ADMIN')")
public class IntelligentReportController {
    private final IntelligentReportService intelligentReportService;
    private final ReportExportService reportExportService;

    @PostMapping("/preview")
    public ResponseEntity<ReportPreviewResponse> preview(
            @Valid @RequestBody ReportPromptRequest request,
            @AuthenticationPrincipal User currentUser) {
        validateAdminContext(currentUser);
        return ResponseEntity.ok(intelligentReportService.preview(request.getPrompt(), currentUser));
    }

    @PostMapping("/export")
    public ResponseEntity<byte[]> export(
            @Valid @RequestBody ReportExportRequest request,
            @AuthenticationPrincipal User currentUser) {
        validateAdminContext(currentUser);
        ReportPreviewResponse report = intelligentReportService.preview(request.getPrompt(), currentUser);
        ExportedReport exported = reportExportService.export(report, request.getFormat());
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_TYPE, exported.contentType())
                .header(HttpHeaders.CONTENT_DISPOSITION,
                        ContentDisposition.attachment().filename(exported.filename()).build().toString())
                .body(exported.content());
    }

    @GetMapping("/history")
    public ResponseEntity<List<GeneratedReportHistoryResponse>> history(@AuthenticationPrincipal User currentUser) {
        validateAdminContext(currentUser);
        return ResponseEntity.ok(intelligentReportService.history(currentUser));
    }

    @GetMapping("/history/page")
    public ResponseEntity<GeneratedReportHistoryPageResponse> historyPage(
            @AuthenticationPrincipal User currentUser,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "5") int size) {
        validateAdminContext(currentUser);
        return ResponseEntity.ok(intelligentReportService.historyPage(currentUser, page, size));
    }

    @GetMapping("/suggestions")
    public ResponseEntity<ReportSuggestionsResponse> suggestions() {
        return ResponseEntity.ok(intelligentReportService.suggestions());
    }

    private void validateAdminContext(User currentUser) {
        if (currentUser == null || currentUser.getOrgId() == null || currentUser.getOrgId().isBlank()) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED,
                    "El usuario no pertenece a ninguna organizacion.");
        }
    }
}
