package sw1.backend.flowroad.dtos.diagram;

import java.time.LocalDateTime;

import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class DiagramSummaryResponse {
    private String id;
    private String name;
    private String description;
    private Integer version;
    private Boolean isActive;
    private LocalDateTime updatedAt;
}
