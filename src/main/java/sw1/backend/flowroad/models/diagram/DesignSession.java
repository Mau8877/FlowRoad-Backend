package sw1.backend.flowroad.models.diagram;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.index.Indexed;
import org.springframework.data.mongodb.core.mapping.Document;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Document(collection = "design_sessions")
public class DesignSession {

    @Id
    private String id;

    // FK -> Referencia al Diagram.java
    @Indexed
    private String diagramId;

    // Token de la "sala" (Ej: "WS-REQ-2026")
    @Indexed(unique = true)
    private String sessionToken;

    // El estado base (JSON stringificado para cargar rápido)
    private String snapshot;

    private List<ActiveUser> activeUsers;
    private List<OperationLog> opsLog;

    private LocalDateTime startedAt;

    // TTL Index: Mongo destruye el documento en 1 hora (3600 seg) si nadie lo
    // actualiza
    @Indexed(expireAfterSeconds = 3600)
    private LocalDateTime lastActivity;

    // --- SUB-DOCUMENTOS ---

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class ActiveUser {
        private String userId;
        private String nombre;
        private String color; // Ej: "#FF5733"
        private CursorPosition cursor;
        private LocalDateTime lastPing;
    }

    @Data
    @AllArgsConstructor
    @NoArgsConstructor
    public static class CursorPosition {
        private double x;
        private double y;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class OperationLog {
        private String opType; // Ej: "MOVE", "RENAME"
        private String nodeId;
        private Map<String, Object> delta; // Solo lo que cambió {x: 10, y: 50}
        private String userId;
        private LocalDateTime timestamp;
    }
}