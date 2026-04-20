package sw1.backend.flowroad.services.diagram;

import org.springframework.context.event.EventListener;
import org.springframework.messaging.simp.stomp.StompHeaderAccessor;
import org.springframework.security.core.Authentication;
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

        // Intentar obtener el usuario de varias formas
        String userId = null;

        // Forma 1: Directamente del Principal de Spring Security
        Authentication auth = (Authentication) headerAccessor.getUser();
        if (auth != null && auth.getPrincipal() instanceof User user) {
            userId = user.getId();
        }

        // Forma 2: Si la forma 1 falló (que es tu caso), el SessionId de STOMP nos
        // puede ayudar
        // Pero para que la forma 1 funcione, el token debe estar bien vinculado.

        if (userId != null) {
            log.info("🔌 Limpiando sesión para usuario real: {}", userId);
            sessionService.handleUserDisconnection(userId);
        } else {
            // INFO: Si llegamos aquí, el socket se cerró sin que Spring supiera quién era.
            // Vamos a forzar una limpieza "por fuerza bruta" usando el SessionId del socket
            // solo si lo guardamos al inicio, pero mejor arreglemos la identificación.
            log.warn("⚠️ Sigue saliendo anónimo. SessionId del socket: {}", headerAccessor.getSessionId());

            // HACK PROVISIONAL: Limpiar CUALQUIER sesión que esté vacía o colgada
            sessionService.cleanupEmptySessions();
        }
    }
}
