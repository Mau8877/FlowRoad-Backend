package sw1.backend.flowroad.repository.process;

import java.util.List;
import java.util.Optional;

import org.springframework.data.mongodb.repository.MongoRepository;

import sw1.backend.flowroad.models.process.ProcessInstance;

public interface ProcessInstanceRepository extends MongoRepository<ProcessInstance, String> {

    List<ProcessInstance> findAllByOrgIdOrderByStartedAtDesc(String orgId);

    List<ProcessInstance> findByClientIdOrderByUpdatedAtDesc(String clientId);

    Optional<ProcessInstance> findByIdAndOrgId(String id, String orgId);

    Optional<ProcessInstance> findByCode(String code);

    List<ProcessInstance> findAllByCode(String code);

    List<ProcessInstance> findAllByCodeStartingWith(String codePrefix);

    long deleteByIdIn(List<String> ids);
}
