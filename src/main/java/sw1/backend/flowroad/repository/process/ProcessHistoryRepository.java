package sw1.backend.flowroad.repository.process;

import java.util.List;

import org.springframework.data.mongodb.repository.MongoRepository;
import org.springframework.stereotype.Repository;

import sw1.backend.flowroad.models.process.ProcessHistory;

@Repository
public interface ProcessHistoryRepository extends MongoRepository<ProcessHistory, String> {

    List<ProcessHistory> findByProcessInstanceIdOrderByPerformedAtAsc(String processInstanceId);
}

