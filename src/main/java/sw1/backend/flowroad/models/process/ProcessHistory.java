package sw1.backend.flowroad.models.process;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.mapping.Document;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Document(collection = "process_history")
public class ProcessHistory {

    @Id
    private String id;

    private String processInstanceId;
    private String assignmentId;

    private String fromNodeId;
    private String fromNodeName;
    private String toNodeId;
    private String toNodeName;
    private String transitionLabel;

    private String performedByUserId;
    private String performedByUserName;
    private LocalDateTime performedAt;

    private String templateDocumentId;
    private String templateName;
    private Map<String, Object> templateResponseData;
    private List<Map<String, Object>> attachments;
    private String comment;
}

