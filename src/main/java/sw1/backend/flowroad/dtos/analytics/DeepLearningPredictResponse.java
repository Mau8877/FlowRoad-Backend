package sw1.backend.flowroad.dtos.analytics;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class DeepLearningPredictResponse {
    private Double riskScore;
    private Double bottleneckScore;
    private Double priorityScore;
    private String priorityLabel;
    private String recommendedAction;
    private Boolean modelUsed;
    private Double bottleneckDelayHours;
    private Double bottleneckRatio;
    private Boolean slaExceeded;
}
