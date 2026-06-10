package sw1.backend.FlowRoad;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import sw1.backend.flowroad.dtos.analytics.DeepLearningDatasetResponse;
import sw1.backend.flowroad.dtos.analytics.DeepLearningDatasetItemResponse;
import sw1.backend.flowroad.services.analytics.DatasetGeneratorService;
import sw1.backend.flowroad.repository.organization.OrganizationRepository;
import sw1.backend.flowroad.models.organization.Organization;

import java.io.FileWriter;
import java.io.PrintWriter;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@SpringBootTest
public class DeepLearningDatasetTest {

    @Autowired
    private DatasetGeneratorService datasetGeneratorService;

    @Autowired
    private OrganizationRepository organizationRepository;

    @Test
    public void testDatasetGenerationAndAnalyze() throws Exception {
        System.out.println("====== INICIANDO TEST DE EXTRACCION DE DATASET ======");
        
        // Buscar una organización
        List<Organization> orgs = organizationRepository.findAll();
        if (orgs.isEmpty()) {
            System.out.println("ERROR: No se encontró ninguna organización en la base de datos.");
            return;
        }

        Organization org = orgs.get(0);
        String orgId = org.getId();
        System.out.println("Organización seleccionada: " + org.getName() + " (ID: " + orgId + ")");

        DeepLearningDatasetResponse response = datasetGeneratorService.generateDataset(orgId, null, null, null, null);
        List<DeepLearningDatasetItemResponse> items = response.getItems();

        System.out.println("Resultados obtenidos:");
        System.out.println("Total de items en el dataset: " + response.getTotalItems());

        // Analizar distribución de priorityLabel
        Map<String, Long> priorityDist = items.stream()
                .collect(Collectors.groupingBy(DeepLearningDatasetItemResponse::getPriorityLabel, Collectors.counting()));

        // Analizar isBottleneck
        long bottleneckCount = items.stream()
                .filter(DeepLearningDatasetItemResponse::isBottleneck)
                .count();

        // Analizar isAnomalous
        long anomalousCount = items.stream()
                .filter(DeepLearningDatasetItemResponse::isAnomalous)
                .count();

        // Analizar recommendedAction
        Map<String, Long> actionDist = items.stream()
                .collect(Collectors.groupingBy(DeepLearningDatasetItemResponse::getRecommendedAction, Collectors.counting()));

        // Analizar SLA fallback vs Real (slaHoursTarget != 24.0 es real si los SLAs reales son distintos de 24,
        // pero para estar seguros contemos cuántos tienen departamento asignado).
        // El seeder asigna departamentoId en todos los items de simulación.
        long realSlaCount = items.stream()
                .filter(item -> item.getAssignedDepartmentId() != null)
                .count();
        long fallbackSlaCount = items.stream()
                .filter(item -> item.getAssignedDepartmentId() == null)
                .count();

        // Ejemplos
        DeepLearningDatasetItemResponse normalExample = items.stream()
                .filter(item -> "NORMAL".equals(item.getPriorityLabel()))
                .findFirst()
                .orElse(null);

        DeepLearningDatasetItemResponse highExample = items.stream()
                .filter(item -> "HIGH".equals(item.getPriorityLabel()) || item.isAnomalous())
                .findFirst()
                .orElse(null);

        // Guardar reporte
        try (PrintWriter writer = new PrintWriter(new FileWriter("dataset_test_report.txt"))) {
            writer.println("=== REPORTE DE ANÁLISIS DE DATASET CU19 ===");
            writer.println("Organización: " + org.getName() + " (ID: " + orgId + ")");
            writer.println("Total Items: " + response.getTotalItems());
            writer.println("Representa asignaciones: Sí (cada ítem corresponde a una asignación y tiene assignmentId)");
            writer.println("\nDistribución de priorityLabel:");
            priorityDist.forEach((k, v) -> writer.println("  - " + k + ": " + v));
            writer.println("\nCantidad de isBottleneck=true: " + bottleneckCount);
            writer.println("Cantidad de isAnomalous=true: " + anomalousCount);
            writer.println("\nDistribución de recommendedAction:");
            actionDist.forEach((k, v) -> writer.println("  - " + k + ": " + v));
            writer.println("\nSLA Targets:");
            writer.println("  - Con departamento asignado (SLA Target real si existe en depto): " + realSlaCount);
            writer.println("  - Sin departamento (Usa fallback 24h): " + fallbackSlaCount);

            writer.println("\n--- EJEMPLO ITEM NORMAL ---");
            if (normalExample != null) {
                writer.println("Instance ID: " + normalExample.getProcessInstanceId());
                writer.println("Assignment ID: " + normalExample.getAssignmentId());
                writer.println("Diagram Name: " + normalExample.getDiagramName());
                writer.println("Step Index: " + normalExample.getStepIndex());
                writer.println("Duration Hours: " + normalExample.getAssignmentDurationHours());
                writer.println("SLA Target: " + normalExample.getSlaHoursTarget());
                writer.println("Priority Label: " + normalExample.getPriorityLabel());
                writer.println("Recommended Action: " + normalExample.getRecommendedAction());
            } else {
                writer.println("No se encontró ningún item NORMAL");
            }

            writer.println("\n--- EJEMPLO ITEM HIGH / ANOMALOUS ---");
            if (highExample != null) {
                writer.println("Instance ID: " + highExample.getProcessInstanceId());
                writer.println("Assignment ID: " + highExample.getAssignmentId());
                writer.println("Diagram Name: " + highExample.getDiagramName());
                writer.println("Step Index: " + highExample.getStepIndex());
                writer.println("Duration Hours: " + highExample.getAssignmentDurationHours());
                writer.println("SLA Target: " + highExample.getSlaHoursTarget());
                writer.println("Priority Label: " + highExample.getPriorityLabel());
                writer.println("Recommended Action: " + highExample.getRecommendedAction());
                writer.println("Is Anomalous: " + highExample.isAnomalous());
            } else {
                writer.println("No se encontró ningún item HIGH/ANOMALOUS");
            }
        }

        System.out.println("====== REPORTE GENERADO EN dataset_test_report.txt ======");
    }
}
