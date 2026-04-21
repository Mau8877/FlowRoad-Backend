package sw1.backend.flowroad.dtos.diagram;

import java.util.Map;

import lombok.Data;

@Data
public class SocketOperationMessage {

    /**
     * Operaciones esperadas:
     * MOVE_LIVE
     * MOVE_COMMIT
     * CURSOR
     * CREATE_NODE
     * UPDATE_NODE
     * DELETE_CELL
     * CREATE_LINK
     * UPDATE_LINK
     * DELETE_LINK
     * LOCK_CELL
     * UNLOCK_CELL
     * LOCK_REJECTED
     */
    private String opType;

    /**
     * ID general de la celda del diagrama.
     */
    private String cellId;

    /**
     * Datos variables de la operación.
     */
    private Map<String, Object> delta;

    /**
     * Usuario que emite la operación.
     */
    private String userId;

    /**
     * Identificador del drag actual.
     * Sirve para distinguir mensajes viejos de arrastres nuevos.
     */
    private String dragId;
}