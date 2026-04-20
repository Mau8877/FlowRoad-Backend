package sw1.backend.flowroad.controllers.diagram;

import java.util.HashMap;
import java.util.Map;

import org.springframework.messaging.handler.annotation.DestinationVariable;
import org.springframework.messaging.handler.annotation.MessageMapping;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Controller;

import lombok.RequiredArgsConstructor;
import sw1.backend.flowroad.dtos.diagram.SocketOperationMessage;
import sw1.backend.flowroad.models.diagram.DesignSession.OperationLog;
import sw1.backend.flowroad.services.diagram.DesignSessionService;

@Controller
@RequiredArgsConstructor
public class DiagramSocketController {

    private final DesignSessionService sessionService;
    private final SimpMessagingTemplate messagingTemplate;

    @MessageMapping("/session/{sessionToken}/operacion")
    public void registrarOperacion(
            @DestinationVariable String sessionToken,
            SocketOperationMessage message) {

        String opType = message.getOpType();
        String cellId = message.getCellId();
        String userId = message.getUserId();

        if ("LOCK_CELL".equals(opType)) {
            boolean locked = sessionService.lockCell(sessionToken, cellId, userId);
            if (locked) {
                messagingTemplate.convertAndSend("/topic/session/" + sessionToken + "/cambios", message);
            } else {
                SocketOperationMessage rejectedMessage = new SocketOperationMessage();
                rejectedMessage.setOpType("LOCK_REJECTED");
                rejectedMessage.setCellId(cellId);
                rejectedMessage.setUserId(userId);

                Map<String, Object> delta = new HashMap<>();
                delta.put("reason", "La celda ya está bloqueada por otro usuario.");
                rejectedMessage.setDelta(delta);

                messagingTemplate.convertAndSend("/topic/session/" + sessionToken + "/cambios", rejectedMessage);
            }
            return;
        }

        if ("UNLOCK_CELL".equals(opType)) {
            boolean unlocked = sessionService.unlockCell(sessionToken, cellId, userId);
            if (unlocked) {
                messagingTemplate.convertAndSend("/topic/session/" + sessionToken + "/cambios", message);
            }
            return;
        }

        boolean requiresLock = "MOVE_LIVE".equals(opType) ||
                "MOVE_COMMIT".equals(opType) ||
                "UPDATE_NODE".equals(opType) ||
                "DELETE_CELL".equals(opType) ||
                "UPDATE_LINK".equals(opType) ||
                "DELETE_LINK".equals(opType);

        if (requiresLock) {
            boolean allowed = sessionService.canOperateOnCell(sessionToken, cellId, userId);
            if (!allowed) {
                return;
            }
        }

        OperationLog op = OperationLog.builder()
                .opType(opType)
                .cellId(cellId)
                .delta(message.getDelta())
                .userId(userId)
                .build();

        sessionService.recordOperation(sessionToken, op);

        String destination = "/topic/session/" + sessionToken + "/cambios";
        messagingTemplate.convertAndSend(destination, message);
    }

    @MessageMapping("/session/{sessionToken}/ping")
    public void recibirPing(
            @DestinationVariable String sessionToken,
            SocketOperationMessage message) {

        double x = Double.parseDouble(message.getDelta().get("x").toString());
        double y = Double.parseDouble(message.getDelta().get("y").toString());

        sessionService.pingUser(sessionToken, message.getUserId(), x, y);

        message.setOpType("CURSOR");

        messagingTemplate.convertAndSend(
                "/topic/session/" + sessionToken + "/cambios",
                message);
    }
}