package sw1.backend.flowroad.services.analytics;

import org.springframework.stereotype.Service;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import sw1.backend.flowroad.dtos.analytics.*;

import org.springframework.beans.factory.annotation.Value;
import java.util.ArrayList;
import java.util.List;

@Service
@RequiredArgsConstructor
@Slf4j
public class DeepLearningPredictionService {

    private final DeepLearningPredictionClient predictionClient;

    @Value("${flowroad.ia.batch-size:50}")
    private int batchSize;

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

    public DeepLearningCurrentPredictionsResponse getCurrentPredictions(List<DeepLearningDatasetItemResponse> datasetItems) {
        if (datasetItems == null || datasetItems.isEmpty()) {
            return DeepLearningCurrentPredictionsResponse.builder()
                    .totalItems(0)
                    .generatedAt(java.time.LocalDateTime.now())
                    .modelUsed(false)
                    .summary(DeepLearningPredictionSummary.builder()
                            .normalCount(0)
                            .mediumCount(0)
                            .highCount(0)
                            .bottleneckCount(0)
                            .slaExceededCount(0)
                            .averageRiskScore(0.0)
                            .averageBottleneckScore(0.0)
                            .build())
                    .items(new ArrayList<>())
                    .build();
        }

        // 1. Segmentar en chunks y llamar a predictBatch por cada chunk
        List<DeepLearningPredictResponse> allPredictions = new ArrayList<>();
        boolean anyFallbackUsed = false;
        
        int totalDatasetSize = datasetItems.size();
        for (int i = 0; i < totalDatasetSize; i += batchSize) {
            int end = Math.min(totalDatasetSize, i + batchSize);
            List<DeepLearningDatasetItemResponse> chunkItems = datasetItems.subList(i, end);
            
            try {
                DeepLearningPredictBatchRequest batchRequest = DeepLearningPredictBatchRequest.builder()
                        .items(chunkItems)
                        .build();
                DeepLearningPredictBatchResponse batchResponse = predictBatch(batchRequest);
                
                List<DeepLearningPredictResponse> chunkPredictions = batchResponse.getPredictions();
                if (chunkPredictions == null || chunkPredictions.isEmpty()) {
                    log.warn("Respuesta nula o vacía de predictBatch para chunk [{}-{}]. Usando fallback.", i, end - 1);
                    anyFallbackUsed = true;
                    for (DeepLearningDatasetItemResponse item : chunkItems) {
                        allPredictions.add(buildFallbackPredict(item));
                    }
                } else if (chunkPredictions.size() != chunkItems.size()) {
                    log.warn("Mismatch de tamaño en predicción del chunk [{}-{}]. Esperado: {}, Recibido: {}. Usando fallback.", 
                            i, end - 1, chunkItems.size(), chunkPredictions.size());
                    anyFallbackUsed = true;
                    for (DeepLearningDatasetItemResponse item : chunkItems) {
                        allPredictions.add(buildFallbackPredict(item));
                    }
                } else {
                    for (DeepLearningPredictResponse pred : chunkPredictions) {
                        allPredictions.add(pred);
                        if (pred == null || !Boolean.TRUE.equals(pred.getModelUsed())) {
                            anyFallbackUsed = true;
                        }
                    }
                }
            } catch (Exception e) {
                log.warn("Excepción al procesar chunk [{}-{}]: {}. Usando fallback para este chunk.", i, end - 1, e.getMessage());
                anyFallbackUsed = true;
                for (DeepLearningDatasetItemResponse item : chunkItems) {
                    allPredictions.add(buildFallbackPredict(item));
                }
            }
        }
        
        boolean modelUsed = !anyFallbackUsed && !allPredictions.isEmpty();

        List<DeepLearningPredictedItemResponse> predictedItems = new ArrayList<>();
        int normalCount = 0;
        int mediumCount = 0;
        int highCount = 0;
        int bottleneckCount = 0;
        int slaExceededCount = 0;
        double totalRiskScore = 0.0;
        double totalBottleneckScore = 0.0;

        for (int i = 0; i < datasetItems.size(); i++) {
            DeepLearningDatasetItemResponse item = datasetItems.get(i);
            DeepLearningPredictResponse prediction = null;

            if (i < allPredictions.size()) {
                prediction = allPredictions.get(i);
            } else {
                prediction = buildFallbackPredict(item);
            }

            // Mapear DTO de item con predicción
            DeepLearningPredictedItemResponse predictedItem = DeepLearningPredictedItemResponse.builder()
                    .processInstanceId(item.getProcessInstanceId())
                    .assignmentId(item.getAssignmentId())
                    .diagramId(item.getDiagramId())
                    .diagramName(item.getDiagramName())
                    .stepIndex(item.getStepIndex())
                    .nodeId(item.getNodeId())
                    .assignedDepartmentId(item.getAssignedDepartmentId())
                    .assignedDepartmentName(item.getAssignedDepartmentName())
                    .assignedCargoId(item.getAssignedCargoId())
                    .assignedUserId(item.getAssignedUserId())
                    .workerActiveLoad(item.getWorkerActiveLoad())
                    .departmentActiveLoad(item.getDepartmentActiveLoad())
                    .assignmentDurationHours(item.getAssignmentDurationHours())
                    .currentStepDurationHours(item.getCurrentStepDurationHours())
                    .accumulatedDurationHours(item.getAccumulatedDurationHours())
                    .reworkCount(item.getReworkCount())
                    .slaHoursTarget(item.getSlaHoursTarget())
                    .nodeActivationCount(item.getNodeActivationCount())
                    .originalPriorityLabel(item.getPriorityLabel())
                    .originalRecommendedAction(item.getRecommendedAction())
                    .originalBottleneck(item.isBottleneck())
                    .originalAnomalous(item.isAnomalous())
                    .prediction(prediction)
                    .build();

            predictedItems.add(predictedItem);

            // Acumular estadísticas del resumen
            String pLabel = prediction.getPriorityLabel();
            if ("HIGH".equalsIgnoreCase(pLabel)) {
                highCount++;
            } else if ("MEDIUM".equalsIgnoreCase(pLabel)) {
                mediumCount++;
            } else {
                normalCount++;
            }

            if (prediction.getBottleneckScore() != null && prediction.getBottleneckScore() > 0.0) {
                bottleneckCount++;
            }
            if (Boolean.TRUE.equals(prediction.getSlaExceeded())) {
                slaExceededCount++;
            }

            totalRiskScore += prediction.getRiskScore() != null ? prediction.getRiskScore() : 0.0;
            totalBottleneckScore += prediction.getBottleneckScore() != null ? prediction.getBottleneckScore() : 0.0;
        }

        int count = datasetItems.size();
        double avgRisk = totalRiskScore / count;
        double avgBottleneck = totalBottleneckScore / count;

        DeepLearningPredictionSummary summary = DeepLearningPredictionSummary.builder()
                .normalCount(normalCount)
                .mediumCount(mediumCount)
                .highCount(highCount)
                .bottleneckCount(bottleneckCount)
                .slaExceededCount(slaExceededCount)
                .averageRiskScore(Math.round(avgRisk * 100.0) / 100.0)
                .averageBottleneckScore(Math.round(avgBottleneck * 100.0) / 100.0)
                .build();

        // Ordenamiento recomendado de items más críticos primero:
        // 1. priorityLabel = HIGH
        // 2. Mayor bottleneckScore
        // 3. Mayor riskScore
        // 4. slaExceeded = true
        predictedItems.sort((a, b) -> {
            String aPriority = a.getPrediction().getPriorityLabel();
            String bPriority = b.getPrediction().getPriorityLabel();
            
            int aPriorityWeight = "HIGH".equalsIgnoreCase(aPriority) ? 3 : ("MEDIUM".equalsIgnoreCase(aPriority) ? 2 : 1);
            int bPriorityWeight = "HIGH".equalsIgnoreCase(bPriority) ? 3 : ("MEDIUM".equalsIgnoreCase(bPriority) ? 2 : 1);
            
            if (aPriorityWeight != bPriorityWeight) {
                return Integer.compare(bPriorityWeight, aPriorityWeight);
            }
            
            Double aBScore = a.getPrediction().getBottleneckScore() != null ? a.getPrediction().getBottleneckScore() : 0.0;
            Double bBScore = b.getPrediction().getBottleneckScore() != null ? b.getPrediction().getBottleneckScore() : 0.0;
            if (!aBScore.equals(bBScore)) {
                return Double.compare(bBScore, aBScore);
            }
            
            Double aRScore = a.getPrediction().getRiskScore() != null ? a.getPrediction().getRiskScore() : 0.0;
            Double bRScore = b.getPrediction().getRiskScore() != null ? b.getPrediction().getRiskScore() : 0.0;
            if (!aRScore.equals(bRScore)) {
                return Double.compare(bRScore, aRScore);
            }
            
            boolean aSla = Boolean.TRUE.equals(a.getPrediction().getSlaExceeded());
            boolean bSla = Boolean.TRUE.equals(b.getPrediction().getSlaExceeded());
            return Boolean.compare(bSla, aSla);
        });

        return DeepLearningCurrentPredictionsResponse.builder()
                .totalItems(count)
                .generatedAt(java.time.LocalDateTime.now())
                .modelUsed(modelUsed)
                .summary(summary)
                .items(predictedItems)
                .build();
    }
}
