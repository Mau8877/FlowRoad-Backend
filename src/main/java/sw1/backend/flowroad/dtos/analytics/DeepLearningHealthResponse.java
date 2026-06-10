package sw1.backend.flowroad.dtos.analytics;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class DeepLearningHealthResponse {
    private String status;
    private String module;
    private Boolean modelLoaded;
    private Boolean hasTensorFlow;
}
