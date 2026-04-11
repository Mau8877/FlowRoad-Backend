package sw1.backend.flowroad.dtos.organization;

import lombok.Data;
import java.time.LocalDateTime;

@Data
public class OrganizationResponse {
    private String id;
    private String name;
    private String code;
    private LocalDateTime createdAt;
}