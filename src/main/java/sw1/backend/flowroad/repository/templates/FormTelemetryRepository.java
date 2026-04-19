package sw1.backend.flowroad.repository.templates;

import java.util.List;

import org.springframework.data.mongodb.repository.MongoRepository;
import org.springframework.stereotype.Repository;

import sw1.backend.flowroad.models.templates.FormTelemetry;

@Repository
public interface FormTelemetryRepository extends MongoRepository<FormTelemetry, String> {

    // 1. Historial completo de la organización
    List<FormTelemetry> findAllByOrgId(String orgId);

    // 2. Para Analítica IA: Traer toda la telemetría de UNA plantilla específica
    // (Para ver cuellos de botella)
    List<FormTelemetry> findAllByTemplateId(String templateId);

    // 3. Traer la telemetría pero filtrada por una versión exacta de la plantilla
    List<FormTelemetry> findAllByTemplateIdAndTemplateVersion(String templateId, Integer templateVersion);

    // 4. Para Auditoría de RRHH: Ver el rendimiento o comportamiento de un
    // mecánico/trabajador en específico
    List<FormTelemetry> findAllByWorkerId(String workerId);

    // 5. Para Trazabilidad: Ver todo lo que pasó mientras se llenaba un Ticket
    // específico
    List<FormTelemetry> findAllByTicketId(String ticketId);
}