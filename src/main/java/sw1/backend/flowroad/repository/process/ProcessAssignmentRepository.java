package sw1.backend.flowroad.repository.process;

import java.util.List;
import java.util.Optional;

import org.springframework.data.mongodb.repository.MongoRepository;
import org.springframework.stereotype.Repository;

import sw1.backend.flowroad.models.process.ProcessAssignment;
import sw1.backend.flowroad.models.process.ProcessAssignment.ProcessAssignmentStatus;

@Repository
public interface ProcessAssignmentRepository extends MongoRepository<ProcessAssignment, String> {

        List<ProcessAssignment> findByProcessInstanceId(String processInstanceId);

        List<ProcessAssignment> findByProcessInstanceIdAndStatus(String processInstanceId,
                        ProcessAssignmentStatus status);

        List<ProcessAssignment> findByProcessInstanceIdIn(List<String> processInstanceIds);

        List<ProcessAssignment> findByProcessInstanceIdInAndStatus(List<String> processInstanceIds,
                        ProcessAssignmentStatus status);

        Optional<ProcessAssignment> findByIdAndProcessInstanceId(String id, String processInstanceId);

        long countByProcessInstanceIdAndStatus(String processInstanceId, ProcessAssignmentStatus status);

        List<ProcessAssignment> findByAssignedDepartmentIdAndAssignedUserIdInOrderByAssignedAtDesc(
                        String assignedDepartmentId,
                        List<String> assignedUserIds);

        List<ProcessAssignment> findByAssignedDepartmentIdAndAssignedCargoIdAndAssignedUserIdInOrderByAssignedAtDesc(
                        String assignedDepartmentId,
                        String assignedCargoId,
                        List<String> assignedUserIds);

        List<ProcessAssignment> findByAssignedUserIdOrderByAssignedAtDesc(String assignedUserId);

        List<ProcessAssignment> findByAssignedUserIdAndStatusOrderByAssignedAtDesc(
                        String assignedUserId,
                        ProcessAssignmentStatus status);

}
