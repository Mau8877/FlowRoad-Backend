package sw1.backend.flowroad.config;

import org.springframework.context.annotation.Configuration;
import org.springframework.messaging.Message;
import org.springframework.messaging.MessageChannel;
import org.springframework.messaging.simp.config.ChannelRegistration;
import org.springframework.messaging.simp.config.MessageBrokerRegistry;
import org.springframework.messaging.simp.stomp.StompCommand;
import org.springframework.messaging.simp.stomp.StompHeaderAccessor;
import org.springframework.messaging.support.ChannelInterceptor;
import org.springframework.messaging.support.MessageHeaderAccessor;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.web.socket.config.annotation.EnableWebSocketMessageBroker;
import org.springframework.web.socket.config.annotation.StompEndpointRegistry;
import org.springframework.web.socket.config.annotation.WebSocketMessageBrokerConfigurer;

import lombok.RequiredArgsConstructor;
import sw1.backend.flowroad.security.JwtService;

@Configuration
@EnableWebSocketMessageBroker
@RequiredArgsConstructor
public class WebSocketConfig implements WebSocketMessageBrokerConfigurer {

    private final JwtService jwtService;
    private final UserDetailsService userDetailsService;

    /**
     * Este método intercepta los mensajes que entran antes de llegar al Controller.
     * Es vital para capturar el Token durante el comando CONNECT de STOMP.
     */
    @Override
    public void configureClientInboundChannel(ChannelRegistration registration) {
        registration.interceptors(new ChannelInterceptor() {
            @Override
            public Message<?> preSend(Message<?> message, MessageChannel channel) {
                StompHeaderAccessor accessor = MessageHeaderAccessor.getAccessor(message, StompHeaderAccessor.class);

                // Solo actuamos si el cliente está intentando conectar
                if (accessor != null && StompCommand.CONNECT.equals(accessor.getCommand())) {
                    // Extraemos el header "Authorization" que mandamos desde Angular
                    String authHeader = accessor.getFirstNativeHeader("Authorization");

                    if (authHeader != null && authHeader.startsWith("Bearer ")) {
                        String jwt = authHeader.substring(7);
                        try {
                            String userEmail = jwtService.extractUsername(jwt);

                            if (userEmail != null) {
                                UserDetails userDetails = userDetailsService.loadUserByUsername(userEmail);

                                if (jwtService.isTokenValid(jwt, userDetails)) {
                                    UsernamePasswordAuthenticationToken auth = new UsernamePasswordAuthenticationToken(
                                            userDetails, null, userDetails.getAuthorities());

                                    // ¡ESTO ES LO MÁS IMPORTANTE!
                                    // Vinculamos al usuario con la sesión del WebSocket.
                                    accessor.setUser(auth);
                                }
                            }
                        } catch (Exception e) {
                            System.err.println("[WS-AUTH-ERROR] Error validando token en WebSocket: " + e.getMessage());
                        }
                    }
                }
                return message;
            }
        });
    }

    @Override
    public void configureMessageBroker(MessageBrokerRegistry config) {
        // Los mensajes que el servidor envía a los clientes (vía rápida)
        config.enableSimpleBroker("/topic");

        // Prefijo para los mensajes que van del cliente al servidor (@MessageMapping)
        config.setApplicationDestinationPrefixes("/app");
    }

    @Override
    public void registerStompEndpoints(StompEndpointRegistry registry) {
        registry.addEndpoint("/ws-flowroad")
                // Permitimos el origen de tu Angular
                .setAllowedOriginPatterns("http://localhost:4200", "http://127.0.0.1:4200")
                .withSockJS(); // Soporte para navegadores antiguos y estabilidad
    }
}