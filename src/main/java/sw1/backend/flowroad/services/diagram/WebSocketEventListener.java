package sw1.backend.flowroad.services.diagram;

import java.security.Principal;
import java.util.Map;

import org.springframework.context.event.EventListener;
import org.springframework.messaging.simp.stomp.StompHeaderAccessor;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.messaging.SessionDisconnectEvent;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import sw1.backend.flowroad.models.user.User;

@Component
@RequiredArgsConstructor
@Slf4j
public class WebSocketEventListener {

    private final DesignSessionService sessionService;

    @EventListener
    public void handleWebSocketDisconnectListener(SessionDisconnectEvent event) {
        StompHeaderAccessor headerAccessor = StompHeaderAccessor.wrap(event.getMessage());

        String userId = null;

        Principal principal = headerAccessor.getUser();
        if (principal instanceof UsernamePasswordAuthenticationToken auth) {
            Object principalObject = auth.getPrincipal();

            if (principalObject instanceof User user) {
                userId = user.getId();
            }
        }

        if (userId == null) {
            Map<String, Object> sessionAttributes = headerAccessor.getSessionAttributes();

            if (sessionAttributes != null) {
                Object storedUserId = sessionAttributes.get("userId");
                if (storedUserId != null) {
                    userId = storedUserId.toString();
                }
            }
        }

        if (userId != null) {
            log.info("🔌 Limpiando sesión para usuario real: {}", userId);
            sessionService.handleUserDisconnection(userId);
        } else {
            log.warn("⚠️ No se pudo identificar al usuario en DISCONNECT. SessionId del socket: {}",
                    headerAccessor.getSessionId());

            sessionService.cleanupEmptySessions();
        }
    }
}