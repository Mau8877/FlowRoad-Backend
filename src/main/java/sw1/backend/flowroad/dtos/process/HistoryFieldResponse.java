package sw1.backend.flowroad.dtos.process;

import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class HistoryFieldResponse {
    private String fieldId;
    private String label;
    private Object value;
}