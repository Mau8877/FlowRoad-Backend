package sw1.backend.flowroad.controllers.client;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import sw1.backend.flowroad.dtos.client.ClientAgentChatRequest;
import sw1.backend.flowroad.dtos.client.ClientAgentChatResponse;
import sw1.backend.flowroad.models.user.User;
import sw1.backend.flowroad.services.client.ClientAgentChatService;

@RestController
@RequestMapping("/client/agent")
@RequiredArgsConstructor
@PreAuthorize("hasAuthority('CLIENT')")
public class ClientAgentChatController {

    private final ClientAgentChatService clientAgentChatService;

    @PostMapping("/chat")
    public ResponseEntity<ClientAgentChatResponse> chat(
            @Valid @RequestBody ClientAgentChatRequest request,
            @AuthenticationPrincipal User currentUser) {
        
        ClientAgentChatResponse response = clientAgentChatService.processMessage(request, currentUser);
        return ResponseEntity.ok(response);
    }
}
