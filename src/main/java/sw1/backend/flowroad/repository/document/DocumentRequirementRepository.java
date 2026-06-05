package sw1.backend.flowroad.repository.document;

import java.util.List;
import java.util.Optional;

import org.springframework.data.mongodb.repository.MongoRepository;
import org.springframework.stereotype.Repository;

import sw1.backend.flowroad.models.document.DocumentRequirement;
import sw1.backend.flowroad.models.document.DocumentRequirement.DocumentRequirementStatus;

@Repository
public interface DocumentRequirementRepository extends MongoRepository<DocumentRequirement, String> {

    List<DocumentRequirement> findByOrgIdAndDiagramIdAndNodeIdAndStatus(
            String orgId,
            String diagramId,
            String nodeId,
            DocumentRequirementStatus status);

    Optional<DocumentRequirement> findByIdAndOrgId(String id, String orgId);

    boolean existsByOrgIdAndDiagramIdAndNodeIdAndNameAndStatus(
            String orgId,
            String diagramId,
            String nodeId,
            String name,
            DocumentRequirementStatus status);

    Optional<DocumentRequirement> findByOrgIdAndDiagramIdAndNodeIdAndNameAndStatus(
            String orgId,
            String diagramId,
            String nodeId,
            String name,
            DocumentRequirementStatus status);
}
