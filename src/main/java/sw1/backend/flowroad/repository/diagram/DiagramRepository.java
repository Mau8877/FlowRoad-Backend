package sw1.backend.flowroad.repository.diagram;

import java.util.List;
import java.util.Optional;

import org.springframework.data.mongodb.repository.MongoRepository;
import org.springframework.stereotype.Repository;

import sw1.backend.flowroad.models.diagram.Diagram;

@Repository
public interface DiagramRepository extends MongoRepository<Diagram, String> {

    // 1. Obtener todos los diagramas de una organización (Para el listado general)
    List<Diagram> findAllByOrgId(String orgId);

    // 2. Obtener solo los diagramas ACTIVOS de una organización
    List<Diagram> findAllByOrgIdAndIsActiveTrue(String orgId);

    // 3. Seguridad: Buscar un diagrama asegurando que pertenece a la organización
    // del usuario
    Optional<Diagram> findByIdAndOrgId(String id, String orgId);

    // 4. Validar que no haya dos diagramas con el mismo nombre en la misma
    // organización
    boolean existsByOrgIdAndName(String orgId, String name);
}