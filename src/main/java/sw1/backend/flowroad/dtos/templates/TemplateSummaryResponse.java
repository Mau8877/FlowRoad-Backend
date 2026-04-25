package sw1.backend.flowroad.dtos.templates;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class TemplateSummaryResponse {
    private String id;
    private String name;
    private String departmentId;
    private String departmentName;
    private Boolean isActive;
}