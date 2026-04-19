package sw1.backend.flowroad.services.templates;

import java.time.LocalDateTime;
import java.util.List;
import java.util.stream.Collectors;

import org.springframework.stereotype.Service;

import lombok.RequiredArgsConstructor;
import sw1.backend.flowroad.dtos.templates.CreateFormTelemetryRequest;
import sw1.backend.flowroad.dtos.templates.FormTelemetryResponse;
import sw1.backend.flowroad.models.templates.FormTelemetry;
import sw1.backend.flowroad.repository.templates.FormTelemetryRepository;

@Service
@RequiredArgsConstructor
public class FormTelemetryService {

    private final FormTelemetryRepository repository;

    // 1. CREAR (Append-Only: Solo registrar, nunca modificar)
    // Nota: orgId y workerId vendrán del Token de seguridad en el Controlador
    public FormTelemetryResponse create(CreateFormTelemetryRequest request, String orgId, String workerId) {

        FormTelemetry telemetry = FormTelemetry.builder()
                .orgId(orgId)
                .templateId(request.templateId())
                .templateVersion(request.templateVersion())
                .ticketId(request.ticketId())
                .workerId(workerId)
                .fieldMetrics(request.fieldMetrics())
                .createdAt(LocalDateTime.now())
                .build();

        FormTelemetry savedTelemetry = repository.save(telemetry);
        return convertToResponseDTO(savedTelemetry);
    }

    // 2. LECTURA: Obtener toda la telemetría de una Organización (Para el ADMIN)
    public List<FormTelemetryResponse> getAllByOrganization(String orgId) {
        return repository.findAllByOrgId(orgId)
                .stream()
                .map(this::convertToResponseDTO)
                .collect(Collectors.toList());
    }

    // 3. LECTURA: Obtener métricas de un Ticket específico (Para Auditoría del
    // flujo)
    public List<FormTelemetryResponse> getByTicketId(String ticketId) {
        return repository.findAllByTicketId(ticketId)
                .stream()
                .map(this::convertToResponseDTO)
                .collect(Collectors.toList());
    }

    // 4. LECTURA: Obtener métricas de una Plantilla para analizar Cuellos de
    // Botella (Para IA)
    public List<FormTelemetryResponse> getByTemplateId(String templateId) {
        return repository.findAllByTemplateId(templateId)
                .stream()
                .map(this::convertToResponseDTO)
                .collect(Collectors.toList());
    }

    // --- MAPPERS ---
    private FormTelemetryResponse convertToResponseDTO(FormTelemetry telemetry) {
        FormTelemetryResponse dto = new FormTelemetryResponse();
        dto.setId(telemetry.getId());
        dto.setTemplateId(telemetry.getTemplateId());
        dto.setTemplateVersion(telemetry.getTemplateVersion());
        dto.setTicketId(telemetry.getTicketId());

        // TODO: En el futuro, aquí podríamos inyectar el UserService para buscar el
        // nombre real del trabajador usando el workerId, por ahora devolvemos el ID.
        dto.setWorkerName(telemetry.getWorkerId());

        dto.setFieldMetrics(telemetry.getFieldMetrics());
        dto.setCreatedAt(telemetry.getCreatedAt());
        return dto;
    }
}