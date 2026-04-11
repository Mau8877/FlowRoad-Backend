package sw1.backend.flowroad.repository.organization;

import org.springframework.data.mongodb.repository.MongoRepository;
import org.springframework.stereotype.Repository;

import sw1.backend.flowroad.models.organization.Organization;

import java.util.List;
import java.util.Optional;

@Repository
public interface OrganizationRepository extends MongoRepository<Organization, String> {

    // Buscar solo las que están activas (Para el uso diario)
    List<Organization> findAllByIsActiveTrue();

    // Buscar por nombre solo si está activa
    Optional<Organization> findByNameAndIsActiveTrue(String name);

    // Buscar por código (activo o no) para procesos de reactivación
    Optional<Organization> findByCode(String code);

    // Query personalizada para ver "Eliminados" (Opcional para el Admin)
    List<Organization> findAllByIsActiveFalse();
}