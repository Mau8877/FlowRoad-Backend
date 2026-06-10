package sw1.backend.flowroad.services.analytics;

import org.springframework.stereotype.Service;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import sw1.backend.flowroad.dtos.analytics.*;

import java.util.ArrayList;
import java.util.List;

@Service
@RequiredArgsConstructor
@Slf4j
public class DeepLearningPredictionService {

    private final DeepLearningPredictionClient predictionClient;

    public DeepLearningHealthResponse getHealth() {
        try {
            return predictionClient.getHealth();
        } catch (Exception e) {
            log.warn("Servicio de IA inactivo, retornando fallback health");
            return DeepLearningHealthResponse.builder()
                    .status("error")
                    .module("deep-learning")
                    .modelLoaded(false)
                    .hasTensorFlow(false)
                    .build();
        }
    }

    public DeepLearningPredictResponse predict(DeepLearningPredictRequest request) {
        try {
            return predictionClient.predict(request);
        } catch (Exception e) {
            log.warn("Error al llamar a predict en la IA, usando fallback local heurístico: {}", e.getMessage());
            return buildFallbackPredict(request.getItem());
        }
    }

    public DeepLearningPredictBatchResponse predictBatch(DeepLearningPredictBatchRequest request) {
        try {
            return predictionClient.predictBatch(request);
        } catch (Exception e) {
            log.warn("Error al llamar a predictBatch en la IA, usando fallback local heurístico: {}", e.getMessage());
            List<DeepLearningPredictResponse> predictions = new ArrayList<>();
            if (request.getItems() != null) {
                for (DeepLearningDatasetItemResponse item : request.getItems()) {
                    predictions.add(buildFallbackPredict(item));
                }
            }
            return DeepLearningPredictBatchResponse.builder()
                    .totalItems(predictions.size())
                    .predictions(predictions)
                    .build();
        }
    }

    private DeepLearningPredictResponse buildFallbackPredict(DeepLearningDatasetItemResponse item) {
        if (item == null) {
            return DeepLearningPredictResponse.builder()
                    .riskScore(0.1)
                    .bottleneckScore(0.0)
                    .priorityScore(10.0)
                    .priorityLabel("NORMAL")
                    .recommendedAction("CONTINUE")
                    .modelUsed(false)
                    .bottleneckDelayHours(0.0)
                    .bottleneckRatio(0.0)
                    .slaExceeded(false)
                    .build();
        }

        Double duration = item.getCurrentStepDurationHours();
        if (duration == null) {
            duration = item.getAssignmentDurationHours() != null ? item.getAssignmentDurationHours() : 0.0;
        }

        Double sla = item.getSlaHoursTarget() != null ? item.getSlaHoursTarget() : 24.0;
        if (sla <= 0) {
            sla = 24.0;
        }

        Double delayHours = Math.max(0.0, duration - sla);
        Double ratio = duration / sla;
        boolean slaExceeded = duration > sla;

        Double bottleneckScore = 0.0;
        if (ratio > 1.0) {
            if (ratio < 2.0) {
                bottleneckScore = 50.0 + ((ratio - 1.0) * 30.0);
            } else {
                bottleneckScore = Math.min(100.0, 80.0 + ((ratio - 2.0) * 10.0));
            }
        }
        if (item.isAnomalous()) {
            bottleneckScore = Math.max(bottleneckScore, 90.0);
        }
        if (item.isBottleneck()) {
            bottleneckScore = Math.max(bottleneckScore, 50.0);
        }
        if (item.getReworkCount() > 0) {
            bottleneckScore = Math.min(100.0, bottleneckScore + 5.0);
        }

        String priorityLabel = "NORMAL";
        String recommendedAction = "CONTINUE";
        Double riskScore = 0.1;

        if (item.isAnomalous() || duration >= (sla * 2)) {
            priorityLabel = "HIGH";
            recommendedAction = "ESCALATE";
            riskScore = 0.85;
        } else if (duration >= sla) {
            priorityLabel = "MEDIUM";
            if (item.getWorkerActiveLoad() >= 3 || item.getDepartmentActiveLoad() >= 5) {
                recommendedAction = "REASSIGN";
            } else {
                recommendedAction = "MONITOR";
            }
        } else if (item.getReworkCount() > 0) {
            riskScore = 0.35; // base risk
            riskScore = Math.min(1.0, riskScore + 0.20);
            if (riskScore > 0.65) {
                priorityLabel = "HIGH";
                recommendedAction = "ESCALATE";
            } else {
                priorityLabel = "MEDIUM";
                recommendedAction = "MONITOR";
            }
        } else if (item.isBottleneck()) {
            priorityLabel = "MEDIUM";
            if (item.getWorkerActiveLoad() >= 3 || item.getDepartmentActiveLoad() >= 5) {
                recommendedAction = "REASSIGN";
            } else {
                recommendedAction = "MONITOR";
            }
        }

        return DeepLearningPredictResponse.builder()
                .riskScore(riskScore)
                .bottleneckScore(bottleneckScore)
                .priorityScore(riskScore * 100.0)
                .priorityLabel(priorityLabel)
                .recommendedAction(recommendedAction)
                .modelUsed(false)
                .bottleneckDelayHours(delayHours)
                .bottleneckRatio(ratio)
                .slaExceeded(slaExceeded)
                .build();
    }
}
