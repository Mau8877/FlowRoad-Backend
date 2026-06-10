package sw1.backend.flowroad.repository.document;

import java.util.List;
import java.util.Optional;

import org.springframework.data.mongodb.repository.MongoRepository;

import sw1.backend.flowroad.models.document.DocumentFile;
import sw1.backend.flowroad.models.document.DocumentFile.DocumentFileStatus;

public interface DocumentFileRepository extends MongoRepository<DocumentFile, String> {

    List<DocumentFile> findByOrgIdAndProcessInstanceId(String orgId, String processInstanceId);

    Optional<DocumentFile> findByIdAndOrgId(String id, String orgId);

    List<DocumentFile> findByOrgIdAndProcessInstanceIdAndDocumentRequirementIdAndStatus(
            String orgId,
            String processInstanceId,
            String documentRequirementId,
            DocumentFileStatus status);

    Optional<DocumentFile> findTopByOrgIdAndProcessInstanceIdAndDocumentRequirementIdOrderByVersionDesc(
            String orgId,
            String processInstanceId,
            String documentRequirementId);
}
