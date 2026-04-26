package sw1.backend.flowroad.controllers.process;

import java.util.List;

import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import lombok.RequiredArgsConstructor;
import sw1.backend.flowroad.dtos.process.AssignmentResponse;
import sw1.backend.flowroad.models.process.ProcessAssignment.ProcessAssignmentStatus;
import sw1.backend.flowroad.models.user.User;
import sw1.backend.flowroad.services.process.ProcessAssignmentService;

@RestController
@RequestMapping("/process-assignments")
@RequiredArgsConstructor
public class ProcessAssignmentController {

    private final ProcessAssignmentService processAssignmentService;

    @GetMapping("/my")
    @PreAuthorize("hasAnyAuthority('ADMIN', 'DESIGNER', 'WORKER', 'RECEP')")
    public ResponseEntity<List<AssignmentResponse>> getMyAssignments(
            @RequestParam(required = false) ProcessAssignmentStatus status,
            @AuthenticationPrincipal User currentUser) {

        return ResponseEntity.ok(
                processAssignmentService.getMyAssignments(currentUser, status));
    }

    @GetMapping("/{id}")
    @PreAuthorize("hasAnyAuthority('ADMIN', 'DESIGNER', 'WORKER', 'RECEP')")
    public ResponseEntity<AssignmentResponse> getAssignmentById(
            @PathVariable String id,
            @AuthenticationPrincipal User currentUser) {

        return ResponseEntity.ok(
                processAssignmentService.getAssignmentById(id, currentUser));
    }
}