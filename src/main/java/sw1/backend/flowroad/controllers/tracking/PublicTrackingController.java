package sw1.backend.flowroad.controllers.tracking;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import lombok.RequiredArgsConstructor;
import sw1.backend.flowroad.dtos.tracking.PublicTrackingResponse;
import sw1.backend.flowroad.services.tracking.PublicTrackingService;

@RestController
@RequestMapping("/public/tracking")
@RequiredArgsConstructor
public class PublicTrackingController {

    private final PublicTrackingService publicTrackingService;

    @GetMapping("/{code}")
    public ResponseEntity<PublicTrackingResponse> getTrackingByCode(@PathVariable String code) {
        return ResponseEntity.ok(publicTrackingService.getTrackingByCode(code));
    }
}