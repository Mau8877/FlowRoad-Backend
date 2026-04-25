package sw1.backend.flowroad.controllers.diagram;

import java.util.HashMap;
import java.util.Map;

import org.springframework.messaging.handler.annotation.DestinationVariable;
import org.springframework.messaging.handler.annotation.MessageMapping;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Controller;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import sw1.backend.flowroad.dtos.diagram.SocketOperationMessage;
import sw1.backend.flowroad.models.diagram.DesignSession.OperationLog;
import sw1.backend.flowroad.services.diagram.DesignSessionService;

@Controller
@RequiredArgsConstructor
@Slf4j
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
        String dragId = message.getDragId();

        log.info("[WS][RECV] session={} opType={} cellId={} userId={} dragId={}",
                sessionToken, opType, cellId, userId, dragId);

        if ("LOCK_CELL".equals(opType)) {
            boolean locked = sessionService.lockCell(sessionToken, cellId, userId, dragId);
            log.info("[WS][LOCK_RESULT] cellId={} userId={} dragId={} locked={}",
                    cellId, userId, dragId, locked);

            if (locked) {
                messagingTemplate.convertAndSend("/topic/session/" + sessionToken + "/cambios", message);
            } else {
                SocketOperationMessage rejectedMessage = new SocketOperationMessage();
                rejectedMessage.setOpType("LOCK_REJECTED");
                rejectedMessage.setCellId(cellId);
                rejectedMessage.setUserId(userId);
                rejectedMessage.setDragId(dragId);

                Map<String, Object> delta = new HashMap<>();
                delta.put("reason", "La celda ya está bloqueada por otro usuario o drag distinto.");
                rejectedMessage.setDelta(delta);

                messagingTemplate.convertAndSend("/topic/session/" + sessionToken + "/cambios", rejectedMessage);
            }
            return;
        }

        if ("UNLOCK_CELL".equals(opType)) {
            boolean unlocked = sessionService.unlockCell(sessionToken, cellId, userId, dragId);
            log.info("[WS][UNLOCK_RESULT] cellId={} userId={} dragId={} unlocked={}",
                    cellId, userId, dragId, unlocked);

            if (unlocked) {
                messagingTemplate.convertAndSend("/topic/session/" + sessionToken + "/cambios", message);
            }
            return;
        }

        boolean requiresLock = "MOVE_LIVE".equals(opType) ||
                "MOVE_COMMIT".equals(opType) ||
                "UPDATE_LINK".equals(opType) ||
                "DELETE_LINK".equals(opType);

        if (requiresLock) {
            boolean allowed = sessionService.canOperateOnCell(sessionToken, cellId, userId, dragId);
            log.info("[WS][LOCK_CHECK] opType={} cellId={} userId={} dragId={} allowed={}",
                    opType, cellId, userId, dragId, allowed);

            if (!allowed) {
                return;
            }
        }

        OperationLog op = OperationLog.builder()
                .opType(opType)
                .cellId(cellId)
                .delta(message.getDelta())
                .userId(userId)
                .dragId(dragId)
                .build();

        sessionService.recordOperation(sessionToken, op);

        String destination = "/topic/session/" + sessionToken + "/cambios";
        messagingTemplate.convertAndSend(destination, message);
        log.info("[WS][BROADCAST] opType={} cellId={} userId={} dragId={}",
                opType, cellId, userId, dragId);

        if ("MOVE_COMMIT".equals(opType)) {
            boolean unlocked = sessionService.unlockCell(sessionToken, cellId, userId, dragId);
            log.info("[WS][AUTO_UNLOCK_AFTER_COMMIT] cellId={} userId={} dragId={} unlocked={}",
                    cellId, userId, dragId, unlocked);

            SocketOperationMessage unlockMessage = new SocketOperationMessage();
            unlockMessage.setOpType("UNLOCK_CELL");
            unlockMessage.setCellId(cellId);
            unlockMessage.setUserId(userId);
            unlockMessage.setDragId(dragId);
            unlockMessage.setDelta(new HashMap<>());

            messagingTemplate.convertAndSend(destination, unlockMessage);
            log.info("[WS][BROADCAST_AUTO_UNLOCK] cellId={} userId={} dragId={}",
                    cellId, userId, dragId);
        }
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