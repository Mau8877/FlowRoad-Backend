package sw1.backend.flowroad.repository.organization;

import org.springframework.data.mongodb.repository.MongoRepository;
import org.springframework.stereotype.Repository;
import sw1.backend.flowroad.models.organization.Cargo;

import java.util.List;

@Repository
public interface CargoRepository extends MongoRepository<Cargo, String> {

    // Buscar todos los cargos de una empresa específica
    List<Cargo> findByOrgId(String orgId);

    // Buscar solo los cargos activos de una empresa (útil para el frontend)
    List<Cargo> findByOrgIdAndIsActiveTrue(String orgId);

    // Por si necesitas buscar un cargo por nombre dentro de una organización
    boolean existsByNameAndOrgId(String name, String orgId);

    // Método para listar cargos
    List<Cargo> findAllByOrgId(String orgId);
}