package sw1.backend.flowroad.services.client;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestTemplate;
import sw1.backend.flowroad.dtos.client.ClientOrganizationResponse;
import sw1.backend.flowroad.dtos.client.ClientWorkflowResponse;
import sw1.backend.flowroad.dtos.client.ClientWorkflowStartRequirementsResponse;

import java.util.List;
import java.util.Map;

@Component
@RequiredArgsConstructor
@Slf4j
public class ClientAgentAiClient {

    private final RestTemplate restTemplate;

    @Value("${flowroad.ia.base-url}")
    private String baseUrl;

    public FastAPIResponse respond(FastAPIRequest request) {
        String url = baseUrl + "/ai/client-agent/respond";
        try {
            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_JSON);
            HttpEntity<FastAPIRequest> entity = new HttpEntity<>(request, headers);
            ResponseEntity<FastAPIResponse> response = restTemplate.postForEntity(url, entity, FastAPIResponse.class);
            return response.getBody();
        } catch (Exception e) {
            log.error("Error al conectar con el servicio de IA respond (url: {}): {}", url, e.getMessage());
            FastAPIResponse fallback = new FastAPIResponse();
            fallback.setReply("Estoy teniendo problemas para responder en este momento. Intenta nuevamente en unos segundos.");
            fallback.setIntent("ERROR");
            fallback.setNextState(request.getConversationState());
            fallback.setNeedsHumanHelp(true);
            fallback.setConfidence(0.0);
            return fallback;
        }
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class FastAPIRequest {
        private String message;
        private String conversationState;
        private FastAPIContext context;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class FastAPIContext {
        private List<ClientOrganizationResponse> availableOrganizations;
        private List<ClientWorkflowResponse> availableWorkflows;
        private ClientOrganizationResponse selectedOrganization;
        private ClientWorkflowResponse selectedWorkflow;
        private ClientWorkflowStartRequirementsResponse startRequirements;
        private List<String> missingData;
        private List<String> missingDocuments;
    }

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class FastAPIResponse {
        private String reply;
        private String intent;
        private String nextState;
        private String selectedOrganizationId;
        private String selectedWorkflowId;
        private Map<String, Object> collectedData;
        private List<String> requestedDocumentIds;
        private boolean readyToStart;
        private double confidence;
        private boolean needsHumanHelp;
    }
}
