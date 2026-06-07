package sw1.backend.flowroad.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

import lombok.Data;

@Data
@Component
@ConfigurationProperties(prefix = "onlyoffice")
public class OnlyOfficeProperties {

    private String documentServerUrl;
    private String jwtSecret;
    private String callbackBaseUrl;
}
