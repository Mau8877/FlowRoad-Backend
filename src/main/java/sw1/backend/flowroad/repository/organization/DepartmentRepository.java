package sw1.backend.flowroad.repository.organization;

import org.springframework.data.mongodb.repository.MongoRepository;
import org.springframework.stereotype.Repository;
import sw1.backend.flowroad.models.organization.Department;

import java.util.List;
import java.util.Optional;

@Repository
public interface DepartmentRepository extends MongoRepository<Department, String> {

    // 1. Buscar todos los departamentos de una organización específica
    List<Department> findByOrgId(String orgId);

    // 2. Buscar solo los activos de una organización (para el flujo de trabajo
    // real)
    List<Department> findByOrgIdAndIsActiveTrue(String orgId);

    // 3. Buscar por código dentro de una organización (ej. "ADM" en Nissan)
    Optional<Department> findByCodeAndOrgId(String code, String orgId);

    // 4. Verificar si ya existe un código en esa organización para evitar
    // duplicados
    boolean existsByCodeAndOrgId(String code, String orgId);

    // 5. Buscar departamentos que tengan asignado un cargo específico
    // MongoDB buscará automáticamente dentro de la lista 'cargoIds'
    List<Department> findByCargoIdsContaining(String cargoId);
}