package sw1.backend.flowroad.models.diagram;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.mapping.Document;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Document(collection = "diagrams")
public class Diagram {

    @Id
    private String id;
    private String orgId;
    private String name;
    private String description;
    private Integer version;
    private Boolean isActive;

    // Aquí guardamos los elementos (nodos) y las flechas (enlaces)
    private List<DiagramCell> cells;

    private LocalDateTime createdAt;
    private String createdBy;
    private LocalDateTime updatedAt;

    // --- SUB-DOCUMENTOS ---

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class DiagramCell {
        private String id; // Ej: "act-1", "link-1"
        private String type; // Ej: "standard.Rectangle", "standard.Link"

        // Posición y tamaño (nulos si es un enlace)
        private Position position;
        private Size size;

        // Referencias de conexión (nulos si es un nodo)
        private CellReference source;
        private CellReference target;

        // Propiedades visuales (colores, texto) que maneja JointJS
        private Map<String, Object> attrs;

        // ¡Clave! Aquí puedes guardar datos de negocio de FlowRoad (Ej: roles,
        // validaciones)
        private Map<String, Object> customData;
    }

    @Data
    @AllArgsConstructor
    @NoArgsConstructor
    public static class Position {
        private double x;
        private double y;
    }

    @Data
    @AllArgsConstructor
    @NoArgsConstructor
    public static class Size {
        private double width;
        private double height;
    }

    @Data
    @AllArgsConstructor
    @NoArgsConstructor
    public static class CellReference {
        private String id; // ID del nodo al que se conecta
        private String port; // ID del puerto (Ej: "p-out")
    }
}