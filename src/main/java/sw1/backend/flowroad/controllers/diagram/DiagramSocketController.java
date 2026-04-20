package sw1.backend.flowroad.controllers.diagram;

import org.springframework.messaging.handler.annotation.DestinationVariable;
import org.springframework.messaging.handler.annotation.MessageMapping;
import org.springframework.messaging.simp.SimpMessagingTemplate; // IMPORTANTE
import org.springframework.stereotype.Controller;

import lombok.RequiredArgsConstructor;
import sw1.backend.flowroad.dtos.diagram.SocketOperationMessage;
import sw1.backend.flowroad.models.diagram.DesignSession.OperationLog;
import sw1.backend.flowroad.services.diagram.DesignSessionService;

@Controller
@RequiredArgsConstructor
public class DiagramSocketController {

    private final DesignSessionService sessionService;
    private final SimpMessagingTemplate messagingTemplate; // El encargado de reenviar mensajes

    @MessageMapping("/session/{sessionToken}/operacion")
    public void registrarOperacion(
            @DestinationVariable String sessionToken,
            SocketOperationMessage message) {

        OperationLog op = OperationLog.builder()
                .opType(message.getOpType())
                .nodeId(message.getNodeId())
                .delta(message.getDelta())
                .userId(message.getUserId())
                .build();

        // 1. Guardamos en la base de datos (Atlas)
        sessionService.recordOperation(sessionToken, op);

        // 2. RETRANSMISIÓN: Le avisamos a todos los que están en la sala
        // para que Angular actualice el lienzo oficialmente.
        String destination = "/topic/session/" + sessionToken + "/cambios";
        messagingTemplate.convertAndSend(destination, message);
    }

    @MessageMapping("/session/{sessionToken}/ping")
    public void recibirPing(
            @DestinationVariable String sessionToken,
            SocketOperationMessage message) {

        try {
            double x = Double.parseDouble(message.getDelta().get("x").toString());
            double y = Double.parseDouble(message.getDelta().get("y").toString());
            sessionService.pingUser(sessionToken, message.getUserId(), x, y);

            // Opcional: Reenviar el ping si quieres mostrar cursores en tiempo real
            // messagingTemplate.convertAndSend("/topic/session/" + sessionToken +
            // "/cambios", message);
        } catch (Exception e) {
            System.err.println("Error en ping: " + e.getMessage());
        }
    }
}