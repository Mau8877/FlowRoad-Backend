package sw1.backend.flowroad.dtos.process;

import java.util.List;
import java.util.Map;

public record CompleteAssignmentRequest(
        String transitionLabel,
        String targetNodeId,
        Map<String, Object> templateResponseData,
        List<Map<String, Object>> attachments,
        String comment) {
}

