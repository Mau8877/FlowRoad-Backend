package sw1.backend.flowroad.repository.templates;

import java.util.List;
import java.util.Optional;

import org.springframework.data.mongodb.repository.MongoRepository;
import org.springframework.stereotype.Repository;

import sw1.backend.flowroad.models.templates.Template;

@Repository
public interface TemplateRepository extends MongoRepository<Template, String> {

    // 1. Obtener todas las plantillas de una organización (Útil para la tabla del
    // ADMIN/DESIGNER)
    List<Template> findAllByOrgId(String orgId);

    // 2. Obtener solo las plantillas ACTIVAS de una organización (Para que los
    // trabajadores las usen)
    List<Template> findAllByOrgIdAndIsActiveTrue(String orgId);

    // 3. Obtener plantillas activas filtradas por Departamento (Ej: Mostrar solo
    // las de "Taller")
    List<Template> findAllByOrgIdAndDepartmentIdAndIsActiveTrue(String orgId, String departmentId);

    // 4. Seguridad: Buscar una plantilla asegurando que pertenece a la organización
    // actual
    Optional<Template> findByIdAndOrgId(String id, String orgId);

    // 5. Validar que no se dupliquen nombres de plantillas en la misma organización
    boolean existsByOrgIdAndName(String orgId, String name);
}