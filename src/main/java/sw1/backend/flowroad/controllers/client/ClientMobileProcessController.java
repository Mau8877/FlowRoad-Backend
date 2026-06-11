package sw1.backend.flowroad.controllers.client;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Collectors;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import sw1.backend.flowroad.dtos.client.ClientOrganizationResponse;
import sw1.backend.flowroad.dtos.client.ClientStartProcessInstanceRequest;
import sw1.backend.flowroad.dtos.client.ClientStartProcessInstanceResponse;
import sw1.backend.flowroad.dtos.client.ClientWorkflowResponse;
import sw1.backend.flowroad.dtos.client.ClientWorkflowStartRequirementsResponse;
import sw1.backend.flowroad.dtos.process.ProcessInstanceSummaryResponse;
import sw1.backend.flowroad.exceptions.ResourceNotFoundException;
import sw1.backend.flowroad.models.diagram.Diagram;
import sw1.backend.flowroad.models.document.DocumentRequirement;
import sw1.backend.flowroad.models.document.DocumentRequirement.DocumentRequirementStatus;
import sw1.backend.flowroad.models.organization.Organization;
import sw1.backend.flowroad.models.user.User;
import sw1.backend.flowroad.repository.diagram.DiagramRepository;
import sw1.backend.flowroad.repository.document.DocumentRequirementRepository;
import sw1.backend.flowroad.repository.organization.OrganizationRepository;
import sw1.backend.flowroad.services.process.ProcessInstanceService;

@RestController
@RequestMapping("/client")
@RequiredArgsConstructor
@PreAuthorize("hasAuthority('CLIENT')")
public class ClientMobileProcessController {

    private final OrganizationRepository organizationRepository;
    private final DiagramRepository diagramRepository;
    private final DocumentRequirementRepository documentRequirementRepository;
    private final ProcessInstanceService processInstanceService;

    @GetMapping("/organizations")
    public ResponseEntity<List<ClientOrganizationResponse>> getActiveOrganizations() {
        List<ClientOrganizationResponse> list = organizationRepository.findAllByIsActiveTrue()
                .stream()
                .map(org -> ClientOrganizationResponse.builder()
                        .id(org.getId())
                        .name(org.getName())
                        .code(org.getCode())
                        .build())
                .collect(Collectors.toList());
        return ResponseEntity.ok(list);
    }

    @GetMapping("/organizations/{organizationId}/workflows")
    public ResponseEntity<List<ClientWorkflowResponse>> getWorkflowsByOrganization(
            @PathVariable String organizationId) {
        
        Organization org = organizationRepository.findById(organizationId)
                .orElseThrow(() -> new ResourceNotFoundException("Organizacion no encontrada con ID: " + organizationId));

        if (!Boolean.TRUE.equals(org.getIsActive())) {
            throw new IllegalArgumentException("La organizacion seleccionada esta inactiva.");
        }

        List<ClientWorkflowResponse> list = diagramRepository.findAllByOrgIdAndIsActiveTrue(organizationId)
                .stream()
                .map(d -> ClientWorkflowResponse.builder()
                        .id(d.getId())
                        .name(d.getName())
                        .description(d.getDescription())
                        .organizationId(d.getOrgId())
                        .build())
                .collect(Collectors.toList());

        return ResponseEntity.ok(list);
    }

    @GetMapping("/workflows/{workflowId}/start-requirements")
    public ResponseEntity<ClientWorkflowStartRequirementsResponse> getStartRequirements(
            @PathVariable String workflowId) {

        Diagram diagram = diagramRepository.findById(workflowId)
                .orElseThrow(() -> new ResourceNotFoundException("Workflow no encontrado con ID: " + workflowId));

        if (!Boolean.TRUE.equals(diagram.getIsActive())) {
            throw new IllegalArgumentException("El workflow seleccionado esta inactivo.");
        }

        // 1. Identificar el nodo técnico INITIAL
        Diagram.DiagramCell initialNode = null;
        if (diagram.getCells() != null) {
            for (Diagram.DiagramCell cell : diagram.getCells()) {
                if (cell.getCustomData() != null) {
                    Object tipoObj = cell.getCustomData().get("tipo");
                    if (tipoObj != null) {
                        String tipo = tipoObj.toString().trim().toUpperCase();
                        if ("INITIAL".equals(tipo) || "INICIO".equals(tipo) || "START".equals(tipo)) {
                            initialNode = cell;
                            break;
                        }
                    }
                }
            }
        }

        if (initialNode == null) {
            throw new IllegalArgumentException("El diagrama no contiene un nodo INITIAL.");
        }

        // 2. Buscar las salidas de ese nodo inicial
        final String initialNodeId = initialNode.getId();
        List<Diagram.DiagramCell> initialOutgoing = new ArrayList<>();
        if (diagram.getCells() != null) {
            for (Diagram.DiagramCell cell : diagram.getCells()) {
                if ("standard.Link".equals(cell.getType())) {
                    if (cell.getSource() != null && initialNodeId.equals(cell.getSource().getId())) {
                        initialOutgoing.add(cell);
                    }
                }
            }
        }

        if (initialOutgoing.isEmpty()) {
            throw new IllegalArgumentException("El nodo INITIAL no tiene una salida configurada.");
        }

        if (initialOutgoing.size() > 1) {
            throw new IllegalArgumentException("El nodo INITIAL no puede tener más de una salida en esta versión.");
        }

        // 3. Obtener el nodo destino de esa salida
        Diagram.DiagramCell outgoingLink = initialOutgoing.get(0);
        String targetNodeId = (outgoingLink.getTarget() != null) ? outgoingLink.getTarget().getId() : null;

        if (targetNodeId == null || targetNodeId.isBlank()) {
            throw new IllegalArgumentException("La transición del nodo INITIAL no apunta a un nodo válido.");
        }

        // Verificar que el nodo destino exista en el diagrama
        boolean targetNodeExists = false;
        if (diagram.getCells() != null) {
            for (Diagram.DiagramCell cell : diagram.getCells()) {
                if (cell.getId() != null && cell.getId().equals(targetNodeId) && !"standard.Link".equals(cell.getType())) {
                    targetNodeExists = true;
                    break;
                }
            }
        }

        if (!targetNodeExists) {
            throw new IllegalArgumentException("El nodo destino '" + targetNodeId + "' no existe en el diagrama.");
        }

        // 4. Obtener requisitos documentales asociados al nodo destino real
        List<ClientWorkflowStartRequirementsResponse.ClientRequiredDocumentResponse> requiredDocuments = new ArrayList<>();
        List<DocumentRequirement> requirements = documentRequirementRepository
                .findByOrgIdAndDiagramIdAndNodeIdAndStatus(
                        diagram.getOrgId(),
                        diagram.getId(),
                        targetNodeId,
                        DocumentRequirementStatus.ACTIVE);

        requiredDocuments = requirements.stream()
                .map(req -> ClientWorkflowStartRequirementsResponse.ClientRequiredDocumentResponse.builder()
                        .id(req.getId())
                        .name(req.getName())
                        .required(Boolean.TRUE.equals(req.getRequired()))
                        .allowedTypes(req.getAllowedFileTypes())
                        .build())
                .collect(Collectors.toList());

        ClientWorkflowStartRequirementsResponse response = ClientWorkflowStartRequirementsResponse.builder()
                .workflowId(diagram.getId())
                .workflowName(diagram.getName())
                .initialNodeId(targetNodeId)
                .requiredData(new ArrayList<>())
                .requiredDocuments(requiredDocuments)
                .build();

        return ResponseEntity.ok(response);
    }

    @PostMapping("/process-instances/start")
    public ResponseEntity<ClientStartProcessInstanceResponse> startProcessInstance(
            @Valid @RequestBody ClientStartProcessInstanceRequest request,
            @AuthenticationPrincipal User currentUser) {

        Organization org = organizationRepository.findById(request.getOrganizationId())
                .orElseThrow(() -> new ResourceNotFoundException("Organizacion no encontrada con ID: " + request.getOrganizationId()));

        if (!Boolean.TRUE.equals(org.getIsActive())) {
            throw new IllegalArgumentException("La organizacion seleccionada esta inactiva.");
        }

        ProcessInstanceSummaryResponse summary = processInstanceService.createClientProcessInstance(
                request.getWorkflowId(),
                request.getOrganizationId(),
                currentUser,
                request.getInitialData());

        ClientStartProcessInstanceResponse response = ClientStartProcessInstanceResponse.builder()
                .processInstanceId(summary.getId())
                .trackingCode(summary.getCode())
                .workflowName(summary.getDiagramName())
                .status("STARTED")
                .message("Tramite iniciado correctamente.")
                .build();

        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }
}
