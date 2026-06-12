package sw1.backend.flowroad.controllers.ai;

import java.util.Map;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.client.RestTemplate;

import lombok.RequiredArgsConstructor;

@RestController
@RequestMapping("/ai")
@RequiredArgsConstructor
@PreAuthorize("isAuthenticated()")
public class AiProxyController {

    private final RestTemplate restTemplate;

    @Value("${flowroad.ia.base-url}")
    private String aiBaseUrl;

    @PostMapping("/diagram/message")
    public ResponseEntity<Object> diagramMessage(@RequestBody Map<String, Object> payload) {
        return forward("/ai/diagram/message", payload);
    }

    @PostMapping("/worker/template-assist")
    public ResponseEntity<Object> workerTemplateAssist(@RequestBody Map<String, Object> payload) {
        return forward("/ai/worker/template-assist", payload);
    }

    @PostMapping("/worker/fill-template")
    public ResponseEntity<Object> workerFillTemplate(@RequestBody Map<String, Object> payload) {
        return forward("/ai/worker/fill-template", payload);
    }

    @PostMapping("/dashboard/bottleneck-analysis")
    public ResponseEntity<Object> dashboardBottleneckAnalysis(@RequestBody Map<String, Object> payload) {
        return forward("/ai/dashboard/bottleneck-analysis", payload);
    }

    private ResponseEntity<Object> forward(String path, Map<String, Object> payload) {
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        HttpEntity<Map<String, Object>> entity = new HttpEntity<>(payload, headers);
        Object response = restTemplate.postForObject(aiBaseUrl + path, entity, Object.class);
        return ResponseEntity.ok(response);
    }
}
