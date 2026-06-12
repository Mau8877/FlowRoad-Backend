package sw1.backend.flowroad.dtos.analytics;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import java.time.LocalDateTime;
import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class DeepLearningCurrentPredictionsResponse {
    private Integer totalItems;
    private LocalDateTime generatedAt;
    private Boolean modelUsed;
    private DeepLearningPredictionSummary summary;
    private List<DeepLearningPredictedItemResponse> items;
}
