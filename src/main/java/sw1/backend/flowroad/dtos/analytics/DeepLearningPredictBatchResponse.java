package sw1.backend.flowroad.dtos.analytics;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class DeepLearningPredictBatchResponse {
    private Integer totalItems;
    private List<DeepLearningPredictResponse> predictions;
}
