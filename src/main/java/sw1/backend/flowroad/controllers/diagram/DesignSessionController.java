package sw1.backend.flowroad.controllers.diagram;

import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import lombok.RequiredArgsConstructor;
import sw1.backend.flowroad.dtos.diagram.JoinSessionResponse;
import sw1.backend.flowroad.models.diagram.DesignSession;
import sw1.backend.flowroad.models.user.User;
import sw1.backend.flowroad.services.diagram.DesignSessionService;

@RestController
@RequestMapping("/design-sessions")
@RequiredArgsConstructor
public class DesignSessionController {

    private final DesignSessionService sessionService;

    @GetMapping("/join/{diagramId}")
    @PreAuthorize("hasAnyAuthority('ADMIN', 'DESIGNER')")
    public ResponseEntity<JoinSessionResponse> joinSession(
            @PathVariable String diagramId,
            @AuthenticationPrincipal User currentUser) {

        String userColor = "#" + Integer.toHexString(currentUser.getId().hashCode()).substring(0, 6);

        DesignSession session = sessionService.getOrCreateSession(
                diagramId,
                currentUser.getId(),
                currentUser.getUsername(),
                userColor);

        JoinSessionResponse response = JoinSessionResponse.builder()
                .sessionToken(session.getSessionToken())
                .diagramId(session.getDiagramId())
                .snapshot(session.getSnapshot())
                .lanesSnapshot(session.getLanesSnapshot())
                .currentUsers(session.getActiveUsers())
                .build();

        return ResponseEntity.ok(response);
    }

    @PostMapping("/{sessionToken}/close")
    @PreAuthorize("hasAnyAuthority('ADMIN', 'DESIGNER')")
    public ResponseEntity<Void> closeSession(
            @PathVariable String sessionToken,
            @RequestBody String finalSnapshotJson) {

        sessionService.saveAndCloseSession(sessionToken, finalSnapshotJson);
        return ResponseEntity.ok().build();
    }
}