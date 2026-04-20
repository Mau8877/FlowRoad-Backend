package sw1.backend.flowroad.repository.diagram;

import java.util.Optional;

import org.springframework.data.mongodb.repository.MongoRepository;
import org.springframework.stereotype.Repository;

import sw1.backend.flowroad.models.diagram.DesignSession;

@Repository
public interface DesignSessionRepository extends MongoRepository<DesignSession, String> {

    // 1. Encontrar la sesión activa usando el Token (Para cuando alguien entra por
    // URL o Socket)
    Optional<DesignSession> findBySessionToken(String sessionToken);

    // 2. Buscar si un diagrama ya tiene una sesión abierta
    // (Para no crear dos salas diferentes para el mismo diagrama)
    Optional<DesignSession> findByDiagramId(String diagramId);

    // 3. Verificar si el token ya existe (Para el generador de tokens aleatorios)
    boolean existsBySessionToken(String sessionToken);
}