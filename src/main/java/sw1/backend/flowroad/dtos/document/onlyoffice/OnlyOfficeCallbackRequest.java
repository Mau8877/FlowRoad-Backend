package sw1.backend.flowroad.dtos.document.onlyoffice;

import java.util.List;
import java.util.Map;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

import lombok.Data;

@Data
@JsonIgnoreProperties(ignoreUnknown = true)
public class OnlyOfficeCallbackRequest {

    private Integer status;
    private String url;
    private String key;
    private List<String> users;
    private List<Map<String, Object>> actions;
    private String changesurl;
    private Map<String, Object> history;
    private String token;
    private Integer error;
    private Integer forcesavetype;
}

