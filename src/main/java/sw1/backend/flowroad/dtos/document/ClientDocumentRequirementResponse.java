package sw1.backend.flowroad.dtos.document;

import java.util.List;

import lombok.Data;

@Data
public class ClientDocumentRequirementResponse {
    private String id;
    private String nodeId;
    private String name;
    private String description;
    private Boolean required;
    private List<String> allowedFileTypes;
    private Integer maxFileSizeMb;
    private Boolean clientCanRead;
    private Boolean clientCanUpload;
    private Boolean clientCanReplace;
}
