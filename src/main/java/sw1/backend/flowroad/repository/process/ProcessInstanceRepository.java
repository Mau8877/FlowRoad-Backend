package sw1.backend.flowroad.repository.process;

import java.util.List;
import java.util.Optional;

import org.springframework.data.mongodb.repository.MongoRepository;
import org.springframework.stereotype.Repository;

import sw1.backend.flowroad.models.process.ProcessInstance;

@Repository
public interface ProcessInstanceRepository extends MongoRepository<ProcessInstance, String> {

    List<ProcessInstance> findAllByOrgIdOrderByStartedAtDesc(String orgId);

    List<ProcessInstance> findByClientIdOrderByUpdatedAtDesc(String clientId);

    Optional<ProcessInstance> findByIdAndOrgId(String id, String orgId);

    Optional<ProcessInstance> findByCode(String code);
}
