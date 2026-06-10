package sw1.backend.flowroad.services.analytics;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestTemplate;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import sw1.backend.flowroad.dtos.analytics.*;

@Component
@RequiredArgsConstructor
@Slf4j
public class DeepLearningPredictionClient {

    private final RestTemplate restTemplate;

    @Value("${flowroad.ia.base-url}")
    private String baseUrl;

    public DeepLearningHealthResponse getHealth() {
        String url = baseUrl + "/ai/deep-learning/health";
        try {
            return restTemplate.getForObject(url, DeepLearningHealthResponse.class);
        } catch (Exception e) {
            log.error("Error al conectar con el servicio de IA health (url: {}): {}", url, e.getMessage());
            throw new RuntimeException("El servicio de IA no está disponible.", e);
        }
    }

    public DeepLearningPredictResponse predict(DeepLearningPredictRequest request) {
        String url = baseUrl + "/ai/deep-learning/predict";
        try {
            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_JSON);
            HttpEntity<DeepLearningPredictRequest> entity = new HttpEntity<>(request, headers);
            ResponseEntity<DeepLearningPredictResponse> response = restTemplate.postForEntity(url, entity, DeepLearningPredictResponse.class);
            return response.getBody();
        } catch (Exception e) {
            log.error("Error en predict (url: {}): {}", url, e.getMessage());
            throw new RuntimeException("Error al procesar la predicción en el servicio de IA.", e);
        }
    }

    public DeepLearningPredictBatchResponse predictBatch(DeepLearningPredictBatchRequest request) {
        String url = baseUrl + "/ai/deep-learning/predict-batch";
        try {
            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_JSON);
            HttpEntity<DeepLearningPredictBatchRequest> entity = new HttpEntity<>(request, headers);
            ResponseEntity<DeepLearningPredictBatchResponse> response = restTemplate.postForEntity(url, entity, DeepLearningPredictBatchResponse.class);
            return response.getBody();
        } catch (Exception e) {
            log.error("Error en predictBatch (url: {}): {}", url, e.getMessage());
            throw new RuntimeException("Error al procesar la predicción en lote en el servicio de IA.", e);
        }
    }
}
