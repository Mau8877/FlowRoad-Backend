package sw1.backend.flowroad.dtos.diagram;

import java.util.List;

import lombok.Builder;
import lombok.Data;
import sw1.backend.flowroad.models.diagram.DesignSession.ActiveUser;

@Data
@Builder
public class JoinSessionResponse {
    private String sessionToken; // El ticket para el WebSocket
    private String diagramId;
    private String snapshot; // El JSON inicial del diagrama
    private List<ActiveUser> currentUsers; // Quiénes ya están adentro
}
