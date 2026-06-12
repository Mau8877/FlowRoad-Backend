package sw1.backend.flowroad.dtos.client;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ClientAgentChatResponse {
    private String chatId;
    private String reply;
    private String conversationState;
    private List<ClientOrganizationResponse> availableOrganizations;
    private List<ClientWorkflowResponse> availableWorkflows;
    private String selectedOrganizationId;
    private String selectedWorkflowId;
    private ClientWorkflowStartRequirementsResponse startRequirements;
    private boolean readyToStart;
    private String processInstanceId;
    private String trackingCode;
    private boolean needsHumanHelp;
}
