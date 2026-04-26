package sw1.backend.flowroad.repository.user;

import java.util.List;
import java.util.Optional;

import org.springframework.data.mongodb.repository.MongoRepository;
import org.springframework.stereotype.Repository;

import sw1.backend.flowroad.models.user.Roles;
import sw1.backend.flowroad.models.user.User;

@Repository
public interface UserRepository extends MongoRepository<User, String> {

        Optional<User> findByEmail(String email);

        Boolean existsByEmail(String email);

        List<User> findAllByOrgIdAndIsActiveTrue(String orgId);

        List<User> findAllByOrgId(String orgId);

        Optional<User> findFirstByOrgIdAndDepartmentIdAndRoleAndIsActiveTrueOrderByWorkloadAsc(
                        String orgId,
                        String departmentId,
                        Roles role);

        List<User> findByDepartmentIdAndIsActiveTrue(String departmentId);

        List<User> findByOrgIdAndCargoIdAndIsActiveTrue(String orgId, String cargoId);

        List<User> findByOrgIdAndDepartmentIdAndRoleAndIsActiveTrue(
                        String orgId,
                        String departmentId,
                        Roles role);

        List<User> findByOrgIdAndDepartmentIdAndCargoIdAndRoleAndIsActiveTrue(
                        String orgId,
                        String departmentId,
                        String cargoId,
                        Roles role);

        List<User> findByProfileNombreContainingIgnoreCaseOrProfileApellidoContainingIgnoreCase(
                        String nombre,
                        String apellido);

        boolean existsByIdAndRole(String id, String role);

        List<User> findByRole(Roles role);

        List<User> findByOrgIdAndDepartmentIdAndRoleInAndIsActiveTrue(
                        String orgId,
                        String departmentId,
                        List<Roles> roles);

        List<User> findByOrgIdAndDepartmentIdAndCargoIdAndRoleInAndIsActiveTrue(
                        String orgId,
                        String departmentId,
                        String cargoId,
                        List<Roles> roles);
}
