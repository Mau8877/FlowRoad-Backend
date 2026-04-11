package sw1.backend.flowroad.models.organization;

import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.AllArgsConstructor;

import java.time.LocalDateTime;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.mapping.Document;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Document(collection = "organizations")
public class Organization {
    @Id
    private String id;
    private String name;
    private String code;
    private Boolean isActive;
    private LocalDateTime createdAt = LocalDateTime.now();
}