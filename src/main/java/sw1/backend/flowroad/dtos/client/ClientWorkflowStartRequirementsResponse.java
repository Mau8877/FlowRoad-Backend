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
public class ClientWorkflowStartRequirementsResponse {
    private String workflowId;
    private String workflowName;
    private String initialNodeId;
    private List<RequiredDataField> requiredData;
    private List<ClientRequiredDocumentResponse> requiredDocuments;

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class RequiredDataField {
        private String key;
        private String label;
        private boolean required;
        private String type;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class ClientRequiredDocumentResponse {
        private String id;
        private String name;
        private boolean required;
        private List<String> allowedTypes;
    }
}
