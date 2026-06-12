package sw1.backend.flowroad.repository.process;

import java.util.List;

import org.springframework.data.mongodb.repository.MongoRepository;

import sw1.backend.flowroad.models.process.ProcessHistory;

public interface ProcessHistoryRepository extends MongoRepository<ProcessHistory, String> {

    List<ProcessHistory> findByProcessInstanceIdOrderByPerformedAtAsc(String processInstanceId);

    List<ProcessHistory> findByProcessInstanceIdIn(List<String> processInstanceIds);

    long deleteByProcessInstanceIdIn(List<String> processInstanceIds);
}
