package sw1.backend.flowroad.dtos.diagram;

import java.util.List;

import lombok.Builder;
import lombok.Data;
import sw1.backend.flowroad.models.diagram.DesignSession.ActiveUser;

@Data
@Builder
public class JoinSessionResponse {
    private String sessionToken;
    private String diagramId;
    private String snapshot;
    private String lanesSnapshot;
    private List<ActiveUser> currentUsers;
}