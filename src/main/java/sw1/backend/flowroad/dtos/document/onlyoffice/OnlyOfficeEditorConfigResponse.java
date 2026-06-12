package sw1.backend.flowroad.dtos.document.onlyoffice;

import java.util.Map;

import lombok.AllArgsConstructor;
import lombok.Data;

@Data
@AllArgsConstructor
public class OnlyOfficeEditorConfigResponse {

    private String documentServerUrl;
    private Map<String, Object> config;
}
