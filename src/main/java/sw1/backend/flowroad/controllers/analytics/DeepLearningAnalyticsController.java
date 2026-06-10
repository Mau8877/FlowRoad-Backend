package sw1.backend.flowroad.controllers.analytics;

import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

import lombok.RequiredArgsConstructor;
import sw1.backend.flowroad.models.user.User;
import sw1.backend.flowroad.dtos.analytics.DeepLearningDatasetResponse;
import sw1.backend.flowroad.services.analytics.DatasetGeneratorService;

import java.time.LocalDateTime;

@RestController
@RequestMapping("/analytics/deep-learning")
@RequiredArgsConstructor
public class DeepLearningAnalyticsController {

    private final DatasetGeneratorService datasetGeneratorService;

    @GetMapping("/dataset")
    @PreAuthorize("hasAnyAuthority('ADMIN')")
    public ResponseEntity<DeepLearningDatasetResponse> getDatasetForDeepLearning(
            @AuthenticationPrincipal User currentUser,
            @RequestParam(required = false) String diagramId,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime from,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime to,
            @RequestParam(required = false) Integer limit) {

        if (currentUser == null || currentUser.getOrgId() == null || currentUser.getOrgId().isEmpty()) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED,
                    "El usuario no pertenece a ninguna organización.");
        }

        DeepLearningDatasetResponse dataset = datasetGeneratorService.generateDataset(
                currentUser.getOrgId(),
                diagramId,
                from,
                to,
                limit
        );

        return ResponseEntity.ok(dataset);
    }
}
