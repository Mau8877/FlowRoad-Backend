package sw1.backend.flowroad.models.organization;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.mapping.Document;
import org.springframework.data.mongodb.core.mapping.Field;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Document(collection = "departments")
public class Department {
    @Id
    private String id;

    @Field("org_id")
    private String orgId;

    @Field("manager_id")
    private String managerId;

    private String name;
    private String code;

    @Field("sla_hours")
    private Integer slaHours;

    @Field("is_active")
    private Boolean isActive;

    private LocalDateTime createdAt = LocalDateTime.now();

    private List<String> cargoIds = new ArrayList<>();
}