package sw1.backend.flowroad.dtos.document;

import java.util.ArrayList;
import java.util.List;

import lombok.Data;

@Data
public class ClientDocumentExpedientResponse {
    private String processInstanceId;
    private String processCode;
    private String diagramId;
    private String diagramName;
    private String processStatus;
    private List<ClientDocumentExpedientItemResponse> items = new ArrayList<>();
}
