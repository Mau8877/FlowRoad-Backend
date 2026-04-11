package sw1.backend.flowroad.dtos.organization;

import lombok.Data;
import java.time.LocalDateTime;
import java.util.List;

@Data
public class DepartmentResponse {
        private String id;
        private String orgId;
        private String managerId;
        private String name;
        private String code;
        private Integer slaHours;
        private Boolean isActive;
        private LocalDateTime createdAt;
        private List<String> cargoIds;
}