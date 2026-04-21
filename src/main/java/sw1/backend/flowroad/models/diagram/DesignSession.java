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

    @Indexed
    private String diagramId;

    @Indexed(unique = true)
    private String sessionToken;

    private String snapshot;

    private List<ActiveUser> activeUsers;
    private List<OperationLog> opsLog;
    private List<CellLock> activeLocks;

    private LocalDateTime startedAt;

    @Indexed(expireAfterSeconds = 3600)
    private LocalDateTime lastActivity;

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class ActiveUser {
        private String userId;
        private String nombre;
        private String color;
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
        private String opType;
        private String cellId;
        private Map<String, Object> delta;
        private String userId;
        private String dragId;
        private LocalDateTime timestamp;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class CellLock {
        private String cellId;
        private String userId;
        private String username;
        private String dragId;
        private LocalDateTime lockedAt;
    }
}