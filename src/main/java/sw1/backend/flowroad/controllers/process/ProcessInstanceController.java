package sw1.backend.flowroad.controllers.process;

import java.util.List;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import sw1.backend.flowroad.dtos.process.CompleteAssignmentRequest;
import sw1.backend.flowroad.dtos.process.CreateProcessInstanceRequest;
import sw1.backend.flowroad.dtos.process.ProcessInstanceDetailResponse;
import sw1.backend.flowroad.dtos.process.ProcessInstanceSummaryResponse;
import sw1.backend.flowroad.models.user.User;
import sw1.backend.flowroad.services.process.ProcessInstanceService;

@RestController
@RequestMapping("/process-instances")
@RequiredArgsConstructor
public class ProcessInstanceController {

    private final ProcessInstanceService processInstanceService;

    @PostMapping
    @PreAuthorize("hasAnyAuthority('ADMIN', 'DESIGNER', 'WORKER', 'RECEP')")
    public ResponseEntity<ProcessInstanceSummaryResponse> createProcessInstance(
            @Valid @RequestBody CreateProcessInstanceRequest request,
            @AuthenticationPrincipal User currentUser) {

        ProcessInstanceSummaryResponse created = processInstanceService.createProcessInstance(
                request.diagramId(),
                request.requestData(),
                currentUser);

        return ResponseEntity.status(HttpStatus.CREATED).body(created);
    }

    @GetMapping
    @PreAuthorize("hasAnyAuthority('ADMIN', 'DESIGNER', 'WORKER', 'RECEP')")
    public ResponseEntity<List<ProcessInstanceSummaryResponse>> getAllProcessInstances(
            @AuthenticationPrincipal User currentUser) {
        return ResponseEntity.ok(processInstanceService.getAllProcessInstances(currentUser.getOrgId()));
    }

    @GetMapping("/{id}")
    @PreAuthorize("hasAnyAuthority('ADMIN', 'DESIGNER', 'WORKER', 'RECEP')")
    public ResponseEntity<ProcessInstanceDetailResponse> getProcessInstanceDetail(
            @PathVariable String id,
            @RequestParam(defaultValue = "false") boolean includeDiagram,
            @AuthenticationPrincipal User currentUser) {
        return ResponseEntity.ok(
                processInstanceService.getProcessInstanceDetail(id, currentUser.getOrgId(), includeDiagram));
    }

    @PostMapping("/{id}/assignments/{assignmentId}/complete")
    @PreAuthorize("hasAnyAuthority('ADMIN', 'DESIGNER', 'WORKER', 'RECEP')")
    public ResponseEntity<ProcessInstanceDetailResponse> completeAssignment(
            @PathVariable String id,
            @PathVariable String assignmentId,
            @RequestBody CompleteAssignmentRequest request,
            @AuthenticationPrincipal User currentUser) {
        return ResponseEntity.ok(
                processInstanceService.completeAssignment(id, assignmentId, request, currentUser));
    }

}
