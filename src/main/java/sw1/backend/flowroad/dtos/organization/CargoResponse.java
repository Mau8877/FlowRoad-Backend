package sw1.backend.flowroad.dtos.organization;

import lombok.Data;

@Data
public class CargoResponse {
    private String id;
    private String orgId;
    private String name;
    private Integer level;
    private Boolean isActive;
}