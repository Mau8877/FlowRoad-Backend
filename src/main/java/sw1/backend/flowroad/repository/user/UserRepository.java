package sw1.backend.flowroad.repository.user;

import java.util.List;
import java.util.Optional;

import org.springframework.data.mongodb.repository.MongoRepository;
import org.springframework.stereotype.Repository;

import sw1.backend.flowroad.models.user.Roles;
import sw1.backend.flowroad.models.user.User;

@Repository
public interface UserRepository extends MongoRepository<User, String> {

        // 1. CRUCIAL PARA EL LOGIN
        // Spring Security usará este método para cargar al usuario durante la
        // autenticación.
        Optional<User> findByEmail(String email);

        // 2. VALIDACIÓN DE REGISTRO
        // Para verificar si el correo ya existe antes de crear un nuevo usuario.
        Boolean existsByEmail(String email);

        // 3. CONSULTA MULTI-TENANT
        // Obtiene todos los usuarios activos de una organización específica (ej:
        // Nissan).
        List<User> findAllByOrgIdAndIsActiveTrue(String orgId);

        List<User> findAllByOrgId(String orgId);

        // 4. EL MOTOR DEL ROUND ROBIN (Lógica de Tesis)
        // Busca al trabajador (WORKER) activo con la menor carga de trabajo (workload)
        // en un departamento específico de una organización.
        Optional<User> findFirstByOrgIdAndDepartmentIdAndRoleAndIsActiveTrueOrderByWorkloadAsc(
                        String orgId,
                        String departmentId,
                        Roles role);

        // 5. CONSULTA POR DEPARTAMENTO
        // Lista a todos los empleados de un área específica.
        List<User> findByDepartmentIdAndIsActiveTrue(String departmentId);

        // 6. CONSULTA POR CARGO
        // Útil para saber quiénes ocupan un puesto específico en la empresa.
        List<User> findByOrgIdAndCargoIdAndIsActiveTrue(String orgId, String cargoId);

        // 7. BÚSQUEDA POR NOMBRE O APELLIDO (Buscador del Frontend)
        // Busca coincidencias ignorando mayúsculas/minúsculas dentro del objeto
        // embebido UserProfile.
        List<User> findByProfileNombreContainingIgnoreCaseOrProfileApellidoContainingIgnoreCase(
                        String nombre,
                        String apellido);

        // 8. SEGURIDAD Y ROLES
        // Verifica si un ID de usuario específico tiene un rol determinado.
        boolean existsByIdAndRole(String id, String role);

        List<User> findByRole(Roles role);
}