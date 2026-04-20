package sw1.backend.flowroad.dtos.diagram;

import java.util.Map;

import lombok.Data;

@Data
public class SocketOperationMessage {
    private String opType; // "MOVE", "RENAME", "DELETE"
    private String nodeId; // "act-1"
    private Map<String, Object> delta; // { "x": 100, "y": 200 }
    private String userId;
}
