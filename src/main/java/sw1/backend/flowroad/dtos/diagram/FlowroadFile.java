package sw1.backend.flowroad.dtos.diagram;

import java.time.LocalDateTime;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class FlowroadFile {
    private Integer schemaVersion;
    private String app;
    private LocalDateTime exportedAt;
    private FlowroadDiagramData diagram;
}