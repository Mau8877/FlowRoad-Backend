package sw1.backend.flowroad.dtos.diagram;

import java.util.List;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import sw1.backend.flowroad.models.diagram.Diagram;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class FlowroadDiagramData {
    private String name;
    private String description;
    private List<Diagram.DiagramCell> cells;
    private List<Diagram.DiagramLane> lanes;
}