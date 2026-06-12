package sw1.backend.flowroad.models.client;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.mapping.Document;
import sw1.backend.flowroad.dtos.client.ClientWorkflowStartRequirementsResponse;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Document(collection = "client_agent_sessions")
public class ClientAgentSession {

    @Id
    private String id;
    private String clientId;
    
    @Builder.Default
    private String conversationState = "START";
    
    private String selectedOrganizationId;
    private String selectedWorkflowId;
    private String selectedOrganizationName;
    private String selectedWorkflowName;
    
    private ClientWorkflowStartRequirementsResponse startRequirements;
    
    @Builder.Default
    private Map<String, Object> collectedData = new HashMap<>();
    
    @Builder.Default
    private List<String> missingDocuments = new ArrayList<>();
    
    @Builder.Default
    private List<String> uploadedDocumentIds = new ArrayList<>();
    
    @Builder.Default
    private Boolean readyToStart = false;
    
    private String processInstanceId;
    private String trackingCode;
    
    @Builder.Default
    private List<ChatMessage> messagesHistory = new ArrayList<>();
    
    @Builder.Default
    private LocalDateTime createdAt = LocalDateTime.now();
    
    @Builder.Default
    private LocalDateTime updatedAt = LocalDateTime.now();

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class ChatMessage {
        private String sender; // "USER" or "AGENT"
        private String text;
        private LocalDateTime timestamp;
    }
}
