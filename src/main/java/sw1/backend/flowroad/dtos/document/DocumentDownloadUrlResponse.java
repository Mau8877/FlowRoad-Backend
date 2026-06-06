package sw1.backend.flowroad.dtos.document;

import lombok.Data;

@Data
public class DocumentDownloadUrlResponse {
    private String documentFileId;
    private String originalFileName;
    private String contentType;
    private Long expiresInSeconds;
    private String downloadUrl;
}
