package sw1.backend.flowroad.controllers.dashboard;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

import lombok.RequiredArgsConstructor;
import sw1.backend.flowroad.dtos.dashboard.DashboardKpiResponse;
import sw1.backend.flowroad.models.user.User;
import sw1.backend.flowroad.services.dashboard.DashboardKpiService;

@RestController
@RequestMapping("/dashboard")
@RequiredArgsConstructor
public class DashboardKpiController {

    private final DashboardKpiService dashboardKpiService;

    @GetMapping("/kpis")
    @PreAuthorize("hasAnyAuthority('ADMIN')")
    public ResponseEntity<DashboardKpiResponse> getDashboardKpis(@AuthenticationPrincipal User currentUser) {
        if (currentUser == null || currentUser.getOrgId() == null || currentUser.getOrgId().isEmpty()) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED,
                    "El usuario no pertenece a ninguna organización.");
        }

        DashboardKpiResponse response = dashboardKpiService.getDashboardKpis(currentUser.getOrgId());
        return ResponseEntity.ok(response);
    }
}
