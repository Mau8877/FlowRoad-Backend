package sw1.backend.flowroad.models.organization;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.mapping.Document;
import org.springframework.data.mongodb.core.mapping.Field;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Document(collection = "cargos")
public class Cargo {
    @Id
    private String id;

    @Field("org_id")
    private String orgId;

    private String name;
    private Integer level;

    private Boolean isActive;
}