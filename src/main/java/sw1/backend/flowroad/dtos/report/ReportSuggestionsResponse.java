package sw1.backend.flowroad.dtos.report;

import java.util.List;

import lombok.AllArgsConstructor;
import lombok.Data;

@Data
@AllArgsConstructor
public class ReportSuggestionsResponse {
    private List<String> suggestions;
}
