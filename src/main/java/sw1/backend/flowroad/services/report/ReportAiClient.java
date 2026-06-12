package sw1.backend.flowroad.services.report;

import java.util.Map;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import sw1.backend.flowroad.dtos.report.ReportQuerySpec;

@Service
@RequiredArgsConstructor
@Slf4j
public class ReportAiClient {
    private final ReportQueryBuilderService queryBuilderService;

    @Value("${flowroad.ia.base-url:http://localhost:8000}")
    private String aiBaseUrl;

    public ReportQuerySpec interpret(String prompt) {
        try {
            RestClient client = RestClient.builder().baseUrl(aiBaseUrl).build();
            return client.post()
                    .uri("/ai/reports/interpret")
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(Map.of(
                            "prompt", prompt,
                            "availableFields", queryBuilderService.availableFields(),
                            "availableMetrics", queryBuilderService.availableMetrics(),
                            "availableGroupings", queryBuilderService.availableGroupings()))
                    .retrieve()
                    .body(ReportQuerySpec.class);
        } catch (Exception ex) {
            log.warn("No se pudo interpretar reporte con IA. Se usara fallback local: {}", ex.getMessage());
            return queryBuilderService.buildFallback(prompt);
        }
    }
}
