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
public class DeepLearningDatasetResponse {
    private int totalItems;
    private LocalDateTime generatedAt;
    private List<DeepLearningDatasetItemResponse> items;
}
