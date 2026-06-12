package sw1.backend.flowroad.dtos.analytics;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class DeepLearningPredictionSummary {
    private Integer normalCount;
    private Integer mediumCount;
    private Integer highCount;
    private Integer bottleneckCount;
    private Integer slaExceededCount;
    private Double averageRiskScore;
    private Double averageBottleneckScore;
}
