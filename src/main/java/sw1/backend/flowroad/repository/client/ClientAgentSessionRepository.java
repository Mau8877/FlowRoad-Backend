package sw1.backend.flowroad.repository.client;

import org.springframework.data.mongodb.repository.MongoRepository;
import org.springframework.stereotype.Repository;
import sw1.backend.flowroad.models.client.ClientAgentSession;

import java.util.Optional;

@Repository
public interface ClientAgentSessionRepository extends MongoRepository<ClientAgentSession, String> {
    Optional<ClientAgentSession> findByIdAndClientId(String id, String clientId);
    Optional<ClientAgentSession> findFirstByClientIdOrderByUpdatedAtDesc(String clientId);
}
