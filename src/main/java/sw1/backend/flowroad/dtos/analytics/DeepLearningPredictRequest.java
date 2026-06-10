package sw1.backend.flowroad.dtos.analytics;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class DeepLearningPredictRequest {
    private DeepLearningDatasetItemResponse item;
}
