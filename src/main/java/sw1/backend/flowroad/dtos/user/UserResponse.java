package sw1.backend.flowroad.dtos.user;

import lombok.Data;
import java.time.LocalDateTime;

import sw1.backend.flowroad.dtos.organization.CargoSummaryResponse;
import sw1.backend.flowroad.dtos.organization.DepartmentSummaryResponse;
import sw1.backend.flowroad.models.user.Roles;

@Data
public class UserResponse {
    private String id;
    private String email;
    private Roles role;
    private String orgId;
    private DepartmentSummaryResponse department;
    private CargoSummaryResponse cargo;
    private Integer workload;
    private Boolean isActive;
    private UserProfileResponse profile;
    private LocalDateTime createdAt;
}