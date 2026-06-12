package sw1.backend.flowroad.services.client;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import sw1.backend.flowroad.dtos.client.*;
import sw1.backend.flowroad.exceptions.ResourceNotFoundException;
import sw1.backend.flowroad.models.client.ClientAgentSession;
import sw1.backend.flowroad.models.client.ClientAgentSession.ChatMessage;
import sw1.backend.flowroad.models.diagram.Diagram;
import sw1.backend.flowroad.models.document.DocumentRequirement;
import sw1.backend.flowroad.models.organization.Organization;
import sw1.backend.flowroad.models.user.User;
import sw1.backend.flowroad.repository.client.ClientAgentSessionRepository;
import sw1.backend.flowroad.repository.diagram.DiagramRepository;
import sw1.backend.flowroad.repository.document.DocumentRequirementRepository;
import sw1.backend.flowroad.repository.organization.OrganizationRepository;
import sw1.backend.flowroad.services.process.ProcessInstanceService;
import sw1.backend.flowroad.dtos.process.ProcessInstanceSummaryResponse;

import java.time.LocalDateTime;
import java.util.*;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Slf4j
public class ClientAgentChatService {

    private final ClientAgentSessionRepository sessionRepository;
    private final ClientAgentAiClient aiClient;
    private final OrganizationRepository organizationRepository;
    private final DiagramRepository diagramRepository;
    private final DocumentRequirementRepository documentRequirementRepository;
    private final ProcessInstanceService processInstanceService;

    private static final Set<String> CONFIRM_KEYWORDS = Set.of(
            "si", "sí", "confirmar", "confirmo", "iniciar", "aceptar", "dale", "proceder", "ok", "claro", "por supuesto"
    );

    @Transactional
    public ClientAgentChatResponse processMessage(ClientAgentChatRequest request, User currentUser) {
        // 1. Obtener o crear sesión (haciéndola final para usarla en lambdas)
        final ClientAgentSession session = getOrCreateSession(request.getChatId(), currentUser);

        // Normalizar entrada del usuario
        String userMsg = request.getMessage() != null ? request.getMessage() : "";
        String normMsg = normalize(userMsg);

        // Cargar listas reales de BD para el matching
        List<ClientOrganizationResponse> allOrgs = organizationRepository.findAllByIsActiveTrue()
                .stream()
                .map(org -> ClientOrganizationResponse.builder()
                        .id(org.getId())
                        .name(org.getName())
                        .code(org.getCode())
                        .build())
                .collect(Collectors.toList());

        List<ClientWorkflowResponse> allWfs = new ArrayList<>();
        if (session.getSelectedOrganizationId() != null) {
            allWfs = diagramRepository.findAllByOrgIdAndIsActiveTrue(session.getSelectedOrganizationId())
                    .stream()
                    .map(diag -> ClientWorkflowResponse.builder()
                            .id(diag.getId())
                            .name(diag.getName())
                            .description(diag.getDescription())
                            .organizationId(diag.getOrgId())
                            .build())
                    .collect(Collectors.toList());
        }

        // Banderas para reportar en la generación de la respuesta determinista
        boolean isInvalidOrgSelection = false;
        boolean isInvalidWfSelection = false;
        boolean isInvalidConfirmationInput = false;
        boolean autoSelectedOrg = false;

        // Verificar si el usuario solicita cancelar/volver
        boolean userWantsCancel = isCancel(normMsg);

        // 2. Máquina de estados determinista y Matching Flexible Local
        if ("COMPLETED".equals(session.getConversationState())) {
            // Ya completado
        } else if (userWantsCancel) {
            handleCancellation(session, allOrgs);
            // Recargar workflows si el estado regresó a SELECTING_WORKFLOW
            if (session.getSelectedOrganizationId() != null) {
                allWfs = diagramRepository.findAllByOrgIdAndIsActiveTrue(session.getSelectedOrganizationId())
                        .stream()
                        .map(diag -> ClientWorkflowResponse.builder()
                                .id(diag.getId())
                                .name(diag.getName())
                                .description(diag.getDescription())
                                .organizationId(diag.getOrgId())
                                .build())
                        .collect(Collectors.toList());
            }
        } else {
            // Procesar según el estado actual
            switch (session.getConversationState()) {
                case "START":
                case "SELECTING_ORGANIZATION":
                    Optional<ClientOrganizationResponse> matchedOrg = matchOrganization(normMsg, allOrgs);
                    if (matchedOrg.isPresent()) {
                        session.setSelectedOrganizationId(matchedOrg.get().getId());
                        session.setSelectedOrganizationName(matchedOrg.get().getName());
                        session.setConversationState("SELECTING_WORKFLOW");
                        // Cargar workflows de la nueva organización seleccionada
                        allWfs = diagramRepository.findAllByOrgIdAndIsActiveTrue(matchedOrg.get().getId())
                                .stream()
                                .map(diag -> ClientWorkflowResponse.builder()
                                        .id(diag.getId())
                                        .name(diag.getName())
                                        .description(diag.getDescription())
                                        .organizationId(diag.getOrgId())
                                        .build())
                                .collect(Collectors.toList());
                    } else if (allOrgs.size() == 1 && "START".equals(session.getConversationState())) {
                        // Auto-seleccionar si solo hay una organización disponible al inicio
                        ClientOrganizationResponse singleOrg = allOrgs.get(0);
                        session.setSelectedOrganizationId(singleOrg.getId());
                        session.setSelectedOrganizationName(singleOrg.getName());
                        session.setConversationState("SELECTING_WORKFLOW");
                        autoSelectedOrg = true;
                        allWfs = diagramRepository.findAllByOrgIdAndIsActiveTrue(singleOrg.getId())
                                .stream()
                                .map(diag -> ClientWorkflowResponse.builder()
                                        .id(diag.getId())
                                        .name(diag.getName())
                                        .description(diag.getDescription())
                                        .organizationId(diag.getOrgId())
                                        .build())
                                .collect(Collectors.toList());
                    } else {
                        // Si no es el mensaje de inicio vacío y no coincide nada, es una selección inválida
                        if (!"START".equals(session.getConversationState()) || !normMsg.isEmpty()) {
                            isInvalidOrgSelection = true;
                        }
                        session.setConversationState("SELECTING_ORGANIZATION");
                    }
                    break;

                case "SELECTING_WORKFLOW":
                    if (session.getSelectedOrganizationId() == null) {
                        session.setConversationState("SELECTING_ORGANIZATION");
                    } else {
                        Optional<ClientWorkflowResponse> matchedWf = matchWorkflow(normMsg, allWfs);
                        if (matchedWf.isPresent()) {
                            session.setSelectedWorkflowId(matchedWf.get().getId());
                            session.setSelectedWorkflowName(matchedWf.get().getName());
                            // Cargar requisitos iniciales
                            ClientWorkflowStartRequirementsResponse reqs = getWorkflowStartRequirements(matchedWf.get().getId());
                            session.setStartRequirements(reqs);
                            session.setConversationState("CONFIRMATION");
                            session.setReadyToStart(true);
                        } else {
                            isInvalidWfSelection = true;
                            session.setConversationState("SELECTING_WORKFLOW");
                        }
                    }
                    break;

                case "CONFIRMATION":
                    if (session.getSelectedWorkflowId() == null) {
                        session.setConversationState("SELECTING_WORKFLOW");
                    } else if (isConfirm(normMsg)) {
                        try {
                            ProcessInstanceSummaryResponse summary = processInstanceService.createClientProcessInstance(
                                    session.getSelectedWorkflowId(),
                                    session.getSelectedOrganizationId(),
                                    currentUser,
                                    session.getCollectedData()
                            );
                            session.setProcessInstanceId(summary.getId());
                            session.setTrackingCode(summary.getCode());
                            session.setConversationState("COMPLETED");
                            session.setReadyToStart(false);
                        } catch (Exception e) {
                            log.error("Error al iniciar el trámite conversacional para workflowId: {}, error: {}", session.getSelectedWorkflowId(), e.getMessage(), e);
                            session.setConversationState("ERROR");
                        }
                    } else {
                        isInvalidConfirmationInput = true;
                        session.setConversationState("CONFIRMATION");
                    }
                    break;

                default:
                    // Auto-corregir si se entra en un estado desconocido
                    sanitizeEffectiveState(session);
                    break;
            }
        }

        // Auto-corrección de seguridad adicional (Effective State Guard)
        sanitizeEffectiveState(session);

        // Cargar listas actualizadas según el nuevo estado determinista
        List<ClientOrganizationResponse> availableOrgs = new ArrayList<>();
        if ("START".equals(session.getConversationState()) || "SELECTING_ORGANIZATION".equals(session.getConversationState())) {
            availableOrgs = allOrgs;
        }

        List<ClientWorkflowResponse> availableWorkflows = new ArrayList<>();
        if (session.getSelectedOrganizationId() != null) {
            availableWorkflows = diagramRepository.findAllByOrgIdAndIsActiveTrue(session.getSelectedOrganizationId())
                    .stream()
                    .map(diag -> ClientWorkflowResponse.builder()
                            .id(diag.getId())
                            .name(diag.getName())
                            .description(diag.getDescription())
                            .organizationId(diag.getOrgId())
                            .build())
                    .collect(Collectors.toList());
        }

        // 3. Preparar contexto para llamar a FastAPI
        ClientOrganizationResponse selectedOrgDto = null;
        if (session.getSelectedOrganizationId() != null) {
            selectedOrgDto = organizationRepository.findById(session.getSelectedOrganizationId())
                    .map(org -> ClientOrganizationResponse.builder()
                            .id(org.getId())
                            .name(org.getName())
                            .code(org.getCode())
                            .build())
                    .orElse(null);
        }

        ClientWorkflowResponse selectedWfDto = null;
        if (session.getSelectedWorkflowId() != null) {
            selectedWfDto = diagramRepository.findById(session.getSelectedWorkflowId())
                    .map(diag -> ClientWorkflowResponse.builder()
                            .id(diag.getId())
                            .name(diag.getName())
                            .description(diag.getDescription())
                            .organizationId(diag.getOrgId())
                            .build())
                    .orElse(null);
        }

        List<String> missingDocuments = new ArrayList<>();
        if (session.getStartRequirements() != null && session.getStartRequirements().getRequiredDocuments() != null) {
            for (ClientWorkflowStartRequirementsResponse.ClientRequiredDocumentResponse doc : session.getStartRequirements().getRequiredDocuments()) {
                if (doc.isRequired() && !session.getUploadedDocumentIds().contains(doc.getId())) {
                    missingDocuments.add(doc.getName());
                }
            }
        }

        ClientAgentAiClient.FastAPIContext apiContext = ClientAgentAiClient.FastAPIContext.builder()
                .availableOrganizations(availableOrgs)
                .availableWorkflows(availableWorkflows)
                .selectedOrganization(selectedOrgDto)
                .selectedWorkflow(selectedWfDto)
                .startRequirements(session.getStartRequirements())
                .missingData(new ArrayList<>())
                .missingDocuments(missingDocuments)
                .build();

        ClientAgentAiClient.FastAPIRequest apiRequest = ClientAgentAiClient.FastAPIRequest.builder()
                .message(request.getMessage())
                .conversationState(session.getConversationState())
                .context(apiContext)
                .build();

        // 4. Llamar a FastAPI IA (para logging y compatibilidad con el pipeline conversacional)
        boolean needsHumanHelp = false;
        try {
            ClientAgentAiClient.FastAPIResponse apiResponse = aiClient.respond(apiRequest);
            needsHumanHelp = apiResponse.isNeedsHumanHelp();
        } catch (Exception e) {
            log.warn("FastAPI IA falló o está inactivo: {}", e.getMessage());
            needsHumanHelp = true;
        }

        // 5. Construcción determinista del reply según el estado real final de Spring Boot
        String replyText = buildDeterministicReply(
                session.getConversationState(),
                session,
                availableOrgs,
                availableWorkflows,
                isInvalidOrgSelection,
                isInvalidWfSelection,
                isInvalidConfirmationInput,
                autoSelectedOrg
        );

        // 6. Guardar historial de mensajes
        session.getMessagesHistory().add(ChatMessage.builder()
                .sender("USER")
                .text(request.getMessage())
                .timestamp(LocalDateTime.now())
                .build());

        session.getMessagesHistory().add(ChatMessage.builder()
                .sender("AGENT")
                .text(replyText)
                .timestamp(LocalDateTime.now())
                .build());

        session.setUpdatedAt(LocalDateTime.now());
        sessionRepository.save(session);

        // 7. Construir respuesta final DTO
        return ClientAgentChatResponse.builder()
                .chatId(session.getId())
                .reply(replyText)
                .conversationState(session.getConversationState())
                .availableOrganizations(availableOrgs)
                .availableWorkflows(availableWorkflows)
                .selectedOrganizationId(session.getSelectedOrganizationId())
                .selectedWorkflowId(session.getSelectedWorkflowId())
                .startRequirements(session.getStartRequirements())
                .readyToStart(Boolean.TRUE.equals(session.getReadyToStart()))
                .processInstanceId(session.getProcessInstanceId())
                .trackingCode(session.getTrackingCode())
                .needsHumanHelp(needsHumanHelp)
                .build();
    }

    private String buildDeterministicReply(
            String state,
            ClientAgentSession session,
            List<ClientOrganizationResponse> orgs,
            List<ClientWorkflowResponse> wfs,
            boolean isInvalidOrg,
            boolean isInvalidWf,
            boolean isInvalidConfirm,
            boolean autoSelectedOrg) {

        switch (state) {
            case "START":
            case "SELECTING_ORGANIZATION":
                if (orgs.isEmpty()) {
                    return "Claro, puedo ayudarte a iniciar un trámite. En este momento no hay empresas disponibles.";
                }
                String orgNames = orgs.stream()
                        .map(ClientOrganizationResponse::getName)
                        .collect(Collectors.joining(", "));
                if (isInvalidOrg) {
                    return "No encontré esa empresa entre las opciones disponibles. Primero elige la empresa disponible. Las opciones son: " + orgNames + ".";
                }
                return "Claro, puedo ayudarte a iniciar un trámite. Primero elige la empresa disponible.";

            case "SELECTING_WORKFLOW":
                if (wfs.isEmpty()) {
                    return "He seleccionado " + session.getSelectedOrganizationName() + ", pero no tiene trámites disponibles en este momento.";
                }
                String wfNames = wfs.stream()
                        .map(ClientWorkflowResponse::getName)
                        .collect(Collectors.joining(", "));

                if (isInvalidWf) {
                    return "No encontré ese trámite en la organización. Los trámites disponibles son: " + wfNames + ". ¿Cuál deseas iniciar?";
                }

                if (autoSelectedOrg) {
                    return "Perfecto, usaré " + session.getSelectedOrganizationName() + ". Estos son los trámites disponibles: " + wfNames + ". ¿Cuál deseas iniciar?";
                }

                return "Perfecto, he seleccionado " + session.getSelectedOrganizationName() + ". Estos son los trámites disponibles: " + wfNames + ". ¿Cuál deseas iniciar?";

            case "CONFIRMATION":
                String docNames = "";
                if (session.getStartRequirements() != null && session.getStartRequirements().getRequiredDocuments() != null && !session.getStartRequirements().getRequiredDocuments().isEmpty()) {
                    docNames = session.getStartRequirements().getRequiredDocuments().stream()
                            .map(doc -> doc.getName() + " en formato " + String.join(", ", doc.getAllowedTypes()).toUpperCase())
                            .collect(Collectors.joining(", "));
                }

                String docMessage = "";
                if (!docNames.isEmpty()) {
                    docMessage = "Este trámite requiere adjuntar " + docNames + ". Primero iniciaré la solicitud y luego podrás adjuntar el documento.";
                } else {
                    docMessage = "Este trámite no requiere documentos iniciales.";
                }

                if (isInvalidConfirm) {
                    return "No entendí tu respuesta. " + docMessage + " ¿Confirmas iniciar el trámite?";
                }

                return "Perfecto, seleccioné " + session.getSelectedWorkflowName() + ". " + docMessage + " ¿Confirmas iniciar el trámite?";

            case "COMPLETED":
                String completedDocNames = "";
                if (session.getStartRequirements() != null && session.getStartRequirements().getRequiredDocuments() != null && !session.getStartRequirements().getRequiredDocuments().isEmpty()) {
                    completedDocNames = session.getStartRequirements().getRequiredDocuments().stream()
                            .map(doc -> doc.getName() + " en formato " + String.join(", ", doc.getAllowedTypes()).toUpperCase())
                            .collect(Collectors.joining(", "));
                }

                String completedDocMessage = "";
                if (!completedDocNames.isEmpty()) {
                    completedDocMessage = " Ahora debes adjuntar la " + completedDocNames + ".";
                }

                return "Tu trámite fue iniciado correctamente. Tu código de seguimiento es " + session.getTrackingCode() + "." + completedDocMessage;

            case "ERROR":
                return "Ocurrió un error inesperado al procesar tu trámite. Por favor, contacta a soporte técnico.";

            default:
                return "Hola, ¿en qué puedo ayudarte hoy?";
        }
    }

    private String normalize(String text) {
        if (text == null) {
            return "";
        }
        String normalized = text.trim().toLowerCase();
        // Quitar acentos
        normalized = normalized.replace("á", "a")
                               .replace("é", "e")
                               .replace("í", "i")
                               .replace("ó", "o")
                               .replace("ú", "u")
                               .replace("ñ", "n");
        // Quitar puntuación y signos
        normalized = normalized.replaceAll("[.,/#!$%^&*;:{}=\\-_`~()?¿¡]", "");
        return normalized.replaceAll("\\s+", " ").trim();
    }

    private boolean isConfirm(String normalizedMsg) {
        return CONFIRM_KEYWORDS.contains(normalizedMsg)
                || normalizedMsg.contains("confirmar")
                || normalizedMsg.contains("confirmo")
                || normalizedMsg.contains("dale")
                || normalizedMsg.contains("proceder")
                || normalizedMsg.contains("iniciar");
    }

    private boolean isCancel(String normalizedMsg) {
        return normalizedMsg.equals("cancelar")
                || normalizedMsg.equals("no")
                || normalizedMsg.equals("volver")
                || normalizedMsg.equals("salir")
                || normalizedMsg.contains("cancelar")
                || normalizedMsg.contains("volver atras")
                || normalizedMsg.contains("regresar");
    }

    private Optional<ClientOrganizationResponse> matchOrganization(String normalizedMsg, List<ClientOrganizationResponse> orgs) {
        // 1. Check exact code match first
        for (ClientOrganizationResponse org : orgs) {
            String normCode = normalize(org.getCode());
            if (!normCode.isEmpty() && normalizedMsg.contains(normCode)) {
                return Optional.of(org);
            }
        }
        // 2. Check name matches or is contained
        for (ClientOrganizationResponse org : orgs) {
            String normName = normalize(org.getName());
            if (normalizedMsg.contains(normName) || normName.contains(normalizedMsg)) {
                return Optional.of(org);
            }
        }
        // 3. Check partial tokens
        String[] tokens = normalizedMsg.split(" ");
        for (String token : tokens) {
            if (token.length() < 3) continue;
            for (ClientOrganizationResponse org : orgs) {
                String normName = normalize(org.getName());
                if (normName.contains(token)) {
                    return Optional.of(org);
                }
            }
        }
        return Optional.empty();
    }

    private Optional<ClientWorkflowResponse> matchWorkflow(String normalizedMsg, List<ClientWorkflowResponse> wfs) {
        // 1. Direct matching: check if normalizedMsg contains any normalized workflow name
        for (ClientWorkflowResponse wf : wfs) {
            String normName = normalize(wf.getName());
            if (normalizedMsg.contains(normName) || normName.contains(normalizedMsg)) {
                return Optional.of(wf);
            }
        }

        // 2. Semantic/fuzzy mappings specified by requirements:
        // Apertura de Cuenta Bancaria mappings:
        if (normalizedMsg.contains("apertura de cuenta") || normalizedMsg.contains("abrir cuenta") || normalizedMsg.contains("cuenta bancaria")) {
            return findWorkflowByName(wfs, "Apertura de Cuenta Bancaria");
        }
        // Solicitud de Prestamo Personal mappings:
        if (normalizedMsg.contains("prestamo personal") || normalizedMsg.contains("prestamo") || normalizedMsg.contains("solicitud de prestamo")) {
            return findWorkflowByName(wfs, "Solicitud de Prestamo Personal");
        }
        // Solicitud de Credito Hipotecario mappings:
        if (normalizedMsg.contains("credito hipotecario") || normalizedMsg.contains("hipotecario")) {
            return findWorkflowByName(wfs, "Solicitud de Credito Hipotecario");
        }
        // Reclamo por Transaccion No Reconocida mappings:
        if (normalizedMsg.contains("reclamo transaccion") || normalizedMsg.contains("transaccion no reconocida") || normalizedMsg.contains("reclamo")) {
            return findWorkflowByName(wfs, "Reclamo por Transaccion No Reconocida");
        }

        // 3. Token-based matching fallback
        String[] tokens = normalizedMsg.split(" ");
        for (String token : tokens) {
            if (token.length() < 4) continue;
            for (ClientWorkflowResponse wf : wfs) {
                String normName = normalize(wf.getName());
                if (normName.contains(token)) {
                    return Optional.of(wf);
                }
            }
        }
        return Optional.empty();
    }

    private Optional<ClientWorkflowResponse> findWorkflowByName(List<ClientWorkflowResponse> wfs, String targetName) {
        String normalizedTarget = normalize(targetName);
        return wfs.stream()
                .filter(w -> normalize(w.getName()).contains(normalizedTarget) || normalizedTarget.contains(normalize(w.getName())))
                .findFirst();
    }

    private void handleCancellation(ClientAgentSession session, List<ClientOrganizationResponse> allOrgs) {
        if ("CONFIRMATION".equals(session.getConversationState())) {
            session.setSelectedWorkflowId(null);
            session.setSelectedWorkflowName(null);
            session.setStartRequirements(null);
            session.setConversationState("SELECTING_WORKFLOW");
        } else if ("SELECTING_WORKFLOW".equals(session.getConversationState())) {
            session.setSelectedOrganizationId(null);
            session.setSelectedOrganizationName(null);
            session.setConversationState("SELECTING_ORGANIZATION");
        } else {
            session.setConversationState("SELECTING_ORGANIZATION");
        }
        session.setReadyToStart(false);
    }

    private void sanitizeEffectiveState(ClientAgentSession session) {
        if (session.getProcessInstanceId() != null) {
            session.setConversationState("COMPLETED");
            session.setReadyToStart(false);
        } else if (session.getSelectedWorkflowId() != null) {
            session.setConversationState("CONFIRMATION");
            session.setReadyToStart(true);
        } else if (session.getSelectedOrganizationId() != null) {
            session.setConversationState("SELECTING_WORKFLOW");
            session.setReadyToStart(false);
        } else {
            session.setConversationState("SELECTING_ORGANIZATION");
            session.setReadyToStart(false);
        }
    }

    private ClientWorkflowStartRequirementsResponse getWorkflowStartRequirements(String workflowId) {
        Optional<Diagram> diagramOpt = diagramRepository.findById(workflowId);
        if (diagramOpt.isEmpty()) {
            return null;
        }
        Diagram diagram = diagramOpt.get();
        if (!Boolean.TRUE.equals(diagram.getIsActive())) {
            return null;
        }

        // Identificar el nodo INITIAL
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
            return null;
        }

        // Buscar salidas del nodo INITIAL
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

        if (initialOutgoing.isEmpty() || initialOutgoing.size() > 1) {
            return null;
        }

        Diagram.DiagramCell outgoingLink = initialOutgoing.get(0);
        String targetNodeId = (outgoingLink.getTarget() != null) ? outgoingLink.getTarget().getId() : null;
        if (targetNodeId == null || targetNodeId.isBlank()) {
            return null;
        }

        // Obtener requisitos documentales asociados al nodo destino real
        List<DocumentRequirement> requirements = documentRequirementRepository
                .findByOrgIdAndDiagramIdAndNodeIdAndStatus(
                        diagram.getOrgId(),
                        diagram.getId(),
                        targetNodeId,
                        DocumentRequirement.DocumentRequirementStatus.ACTIVE);

        List<ClientWorkflowStartRequirementsResponse.ClientRequiredDocumentResponse> requiredDocuments = requirements.stream()
                .map(req -> ClientWorkflowStartRequirementsResponse.ClientRequiredDocumentResponse.builder()
                        .id(req.getId())
                        .name(req.getName())
                        .required(Boolean.TRUE.equals(req.getRequired()))
                        .allowedTypes(req.getAllowedFileTypes())
                        .build())
                .collect(Collectors.toList());

        return ClientWorkflowStartRequirementsResponse.builder()
                .workflowId(diagram.getId())
                .workflowName(diagram.getName())
                .initialNodeId(targetNodeId)
                .requiredData(new ArrayList<>())
                .requiredDocuments(requiredDocuments)
                .build();
    }

    private ClientAgentSession getOrCreateSession(String chatId, User currentUser) {
        if (chatId != null && !chatId.isBlank()) {
            return sessionRepository.findByIdAndClientId(chatId, currentUser.getId())
                    .orElseThrow(() -> new ResourceNotFoundException("Sesión no encontrada o no pertenece al cliente especificado."));
        } else {
            ClientAgentSession newSession = ClientAgentSession.builder()
                    .clientId(currentUser.getId())
                    .conversationState("START")
                    .createdAt(LocalDateTime.now())
                    .updatedAt(LocalDateTime.now())
                    .build();
            return sessionRepository.save(newSession);
        }
    }
}
