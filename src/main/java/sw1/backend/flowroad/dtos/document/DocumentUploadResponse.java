package sw1.backend.flowroad.dtos.document;

import lombok.Data;

@Data
public class DocumentUploadResponse {
    private DocumentFileResponse documentFile;
    private String contentType;
    private Long size;
    private String originalFileName;
}
