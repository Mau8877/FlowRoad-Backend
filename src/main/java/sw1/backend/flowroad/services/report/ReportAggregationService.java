package sw1.backend.flowroad.services.report;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Duration;
import java.time.LocalDateTime;
import java.time.temporal.WeekFields;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.stream.Collectors;

import org.springframework.stereotype.Service;

import lombok.RequiredArgsConstructor;
import sw1.backend.flowroad.dtos.report.ReportQuerySpec;
import sw1.backend.flowroad.dtos.report.ReportSortSpec;
import sw1.backend.flowroad.models.process.ProcessAssignment;
import sw1.backend.flowroad.models.process.ProcessInstance;
import sw1.backend.flowroad.models.process.ProcessInstance.ProcessInstanceStatus;
import sw1.backend.flowroad.repository.process.ProcessAssignmentRepository;
import sw1.backend.flowroad.repository.process.ProcessInstanceRepository;

@Service
@RequiredArgsConstructor
public class ReportAggregationService {
    private static final double SLA_HOURS = 24.0;

    private final ProcessInstanceRepository processInstanceRepository;
    private final ProcessAssignmentRepository processAssignmentRepository;
    private final ReportQueryBuilderService queryBuilderService;

    public ReportAggregationResult execute(String orgId, ReportQuerySpec spec) {
        List<ProcessInstance> instances = processInstanceRepository.findAllByOrgIdOrderByStartedAtDesc(orgId).stream()
                .filter(instance -> isInsideDateRange(instance, spec.getDateRange()))
                .toList();
        boolean predictiveIntent = queryBuilderService.isPredictiveIntent(spec.getReportIntent());
        boolean predictiveMetricsAvailable = predictiveIntent && hasPredictiveMetrics(instances);
        List<String> effectiveMetrics = effectiveMetrics(spec, predictiveIntent, predictiveMetricsAvailable);

        if (instances.isEmpty()) {
            return new ReportAggregationResult(
                    buildColumns(spec, effectiveMetrics),
                    List.of(),
                    List.of(),
                    predictiveMetricsAvailable,
                    dataSource(predictiveIntent, predictiveMetricsAvailable));
        }

        List<String> instanceIds = instances.stream().map(ProcessInstance::getId).filter(Objects::nonNull).toList();
        Map<String, List<ProcessAssignment>> assignmentsByInstance = instanceIds.isEmpty()
                ? Map.of()
                : processAssignmentRepository.findByProcessInstanceIdIn(instanceIds).stream()
                        .collect(Collectors.groupingBy(ProcessAssignment::getProcessInstanceId));

        Map<String, Bucket> buckets = new LinkedHashMap<>();
        for (ProcessInstance instance : instances) {
            String key = groupKey(instance, assignmentsByInstance.getOrDefault(instance.getId(), List.of()), spec);
            Bucket bucket = buckets.computeIfAbsent(key, Bucket::new);
            bucket.add(instance);
        }

        int total = instances.size();
        List<Map<String, Object>> rows = buckets.values().stream()
                .map(bucket -> toRow(bucket, spec, effectiveMetrics, total))
                .collect(Collectors.toCollection(ArrayList::new));

        applySort(rows, spec.getSort(), predictiveMetricsAvailable);
        int limit = spec.getLimit() != null ? spec.getLimit() : 50;
        if (rows.size() > limit) {
            rows = new ArrayList<>(rows.subList(0, limit));
        }

        return new ReportAggregationResult(
                buildColumns(spec, effectiveMetrics),
                rows,
                rows,
                predictiveMetricsAvailable,
                dataSource(predictiveIntent, predictiveMetricsAvailable));
    }

    private List<String> effectiveMetrics(
            ReportQuerySpec spec,
            boolean predictiveIntent,
            boolean predictiveMetricsAvailable) {
        if (predictiveIntent && !predictiveMetricsAvailable) {
            return List.of("count", "avgDurationHours", "maxDurationHours", "slaExceededCount",
                    "slaExceededRate", "operationalRiskLevel");
        }
        return spec.getMetrics() == null || spec.getMetrics().isEmpty() ? List.of("count") : spec.getMetrics();
    }

    private boolean isInsideDateRange(ProcessInstance instance, ReportQuerySpec.DateRangeSpec dateRange) {
        if (dateRange == null || dateRange.getFrom() == null) {
            return true;
        }
        LocalDateTime date = instance.getStartedAt() != null ? instance.getStartedAt() : instance.getUpdatedAt();
        if (date == null) {
            return false;
        }
        boolean afterFrom = !date.isBefore(dateRange.getFrom());
        boolean beforeTo = dateRange.getTo() == null || !date.isAfter(dateRange.getTo());
        return afterFrom && beforeTo;
    }

    private String groupKey(ProcessInstance instance, List<ProcessAssignment> assignments, ReportQuerySpec spec) {
        String groupBy = spec.getGroupBy().isEmpty() ? "status" : spec.getGroupBy().get(0);
        return switch (groupBy) {
            case "department" -> assignments.stream()
                    .map(ProcessAssignment::getAssignedDepartmentName)
                    .filter(name -> name != null && !name.isBlank())
                    .findFirst()
                    .orElse("Sin departamento");
            case "workflow" -> safe(instance.getDiagramName(), "Proceso sin nombre");
            case "assignedUser" -> assignments.stream()
                    .map(ProcessAssignment::getAssignedUserName)
                    .filter(name -> name != null && !name.isBlank())
                    .findFirst()
                    .orElse("Sin usuario");
            case "organization" -> safe(instance.getOrgId(), "Organizacion");
            case "day" -> formatDatePart(instance, "day");
            case "week" -> formatDatePart(instance, "week");
            case "month" -> formatDatePart(instance, "month");
            default -> instance.getStatus() != null ? instance.getStatus().name() : "Sin estado";
        };
    }

    private String formatDatePart(ProcessInstance instance, String part) {
        LocalDateTime date = instance.getStartedAt() != null ? instance.getStartedAt() : instance.getUpdatedAt();
        if (date == null) {
            return "Sin fecha";
        }
        return switch (part) {
            case "day" -> date.toLocalDate().toString();
            case "week" -> date.getYear() + "-W" + date.get(WeekFields.ISO.weekOfWeekBasedYear());
            default -> date.getYear() + "-" + String.format("%02d", date.getMonthValue());
        };
    }

    private Map<String, Object> toRow(Bucket bucket, ReportQuerySpec spec, List<String> metrics, int total) {
        Map<String, Object> row = new LinkedHashMap<>();
        String groupBy = spec.getGroupBy().isEmpty() ? "status" : spec.getGroupBy().get(0);
        row.put(labelFor(groupBy), bucket.key);

        for (String metric : metrics) {
            switch (metric) {
                case "completedCount" -> row.put("Completados", bucket.completedCount);
                case "pendingCount" -> row.put("Pendientes", bucket.pendingCount);
                case "percentage" -> row.put("Porcentaje", round(total == 0 ? 0.0 : (bucket.count * 100.0) / total));
                case "avgDurationHours" -> row.put("Tiempo promedio", round(bucket.avgDurationHours()));
                case "maxDurationHours" -> row.put("Tiempo máximo", round(bucket.maxDurationHours()));
                case "slaExceededCount" -> row.put("SLA vencido", bucket.slaExceededCount());
                case "slaExceededRate" -> row.put("% SLA vencido", round(bucket.slaExceededRate()));
                case "avgPriorityScore" -> bucket.avgPriorityScore()
                        .ifPresent(value -> row.put("Prioridad promedio", round(value)));
                case "avgRiskScore" -> bucket.avgRiskScore()
                        .ifPresent(value -> row.put("Riesgo promedio", round(value)));
                case "avgBottleneckScore" -> bucket.avgBottleneckScore()
                        .ifPresent(value -> row.put("Cuello botella promedio", round(value)));
                case "operationalRiskLevel" -> row.put("Nivel operativo de riesgo", bucket.operationalRiskLevel());
                default -> row.put("Cantidad", bucket.count);
            }
        }
        return row;
    }

    private List<String> buildColumns(ReportQuerySpec spec, List<String> metrics) {
        List<String> columns = new ArrayList<>();
        String groupBy = spec.getGroupBy().isEmpty() ? "status" : spec.getGroupBy().get(0);
        columns.add(labelFor(groupBy));
        for (String metric : metrics) {
            String label = switch (metric) {
                case "completedCount" -> "Completados";
                case "pendingCount" -> "Pendientes";
                case "percentage" -> "Porcentaje";
                case "avgDurationHours" -> "Tiempo promedio";
                case "maxDurationHours" -> "Tiempo máximo";
                case "slaExceededCount" -> "SLA vencido";
                case "slaExceededRate" -> "% SLA vencido";
                case "avgPriorityScore" -> "Prioridad promedio";
                case "avgRiskScore" -> "Riesgo promedio";
                case "avgBottleneckScore" -> "Cuello botella promedio";
                case "operationalRiskLevel" -> "Nivel operativo de riesgo";
                default -> "Cantidad";
            };
            if (!columns.contains(label)) {
                columns.add(label);
            }
        }
        return columns;
    }

    private void applySort(List<Map<String, Object>> rows, List<ReportSortSpec> sort, boolean predictiveMetricsAvailable) {
        if (sort == null || sort.isEmpty()) {
            return;
        }
        ReportSortSpec firstSort = sort.get(0);
        String column = metricColumn(firstSort.getField(), predictiveMetricsAvailable);
        Comparator<Map<String, Object>> comparator = (left, right) -> compareValues(left.get(column), right.get(column));
        if (!"ASC".equalsIgnoreCase(firstSort.getDirection())) {
            comparator = comparator.reversed();
        }
        rows.sort(comparator);
    }

    private int compareValues(Object left, Object right) {
        if (left instanceof Number leftNumber && right instanceof Number rightNumber) {
            return Double.compare(leftNumber.doubleValue(), rightNumber.doubleValue());
        }
        String leftText = left == null ? "" : left.toString();
        String rightText = right == null ? "" : right.toString();
        return leftText.compareToIgnoreCase(rightText);
    }

    private String metricColumn(String metric, boolean predictiveMetricsAvailable) {
        return switch (metric) {
            case "completedCount" -> "Completados";
            case "pendingCount" -> "Pendientes";
            case "percentage" -> "Porcentaje";
            case "avgDurationHours" -> "Tiempo promedio";
            case "maxDurationHours" -> "Tiempo máximo";
            case "slaExceededCount" -> "SLA vencido";
            case "slaExceededRate" -> "% SLA vencido";
            case "avgRiskScore" -> predictiveMetricsAvailable ? "Riesgo promedio" : "% SLA vencido";
            case "avgPriorityScore" -> predictiveMetricsAvailable ? "Prioridad promedio" : "% SLA vencido";
            case "avgBottleneckScore" -> predictiveMetricsAvailable ? "Cuello botella promedio" : "Tiempo promedio";
            default -> "Cantidad";
        };
    }

    private String labelFor(String groupBy) {
        return switch (groupBy) {
            case "department" -> "Departamento";
            case "workflow" -> "Trámite";
            case "assignedUser" -> "Usuario asignado";
            case "organization" -> "Organización";
            case "day" -> "Día";
            case "week" -> "Semana";
            case "month" -> "Mes";
            default -> "Estado";
        };
    }

    private boolean hasPredictiveMetrics(List<ProcessInstance> instances) {
        return instances.stream().anyMatch(instance ->
                readPredictiveNumber(instance, "riskScore").isPresent()
                        || readPredictiveNumber(instance, "priorityScore").isPresent()
                        || readPredictiveNumber(instance, "bottleneckScore").isPresent());
    }

    @SuppressWarnings("unchecked")
    private Optional<Double> readPredictiveNumber(ProcessInstance instance, String key) {
        Map<String, Object> data = instance.getRequestData();
        if (data == null || data.isEmpty()) {
            return Optional.empty();
        }
        Object value = data.get(key);
        if (value == null && data.get("predictiveMetrics") instanceof Map<?, ?> nested) {
            value = ((Map<String, Object>) nested).get(key);
        }
        if (value == null && data.get("cu19Metrics") instanceof Map<?, ?> nested) {
            value = ((Map<String, Object>) nested).get(key);
        }
        if (value instanceof Number number) {
            return Optional.of(number.doubleValue());
        }
        if (value instanceof String text && !text.isBlank()) {
            try {
                return Optional.of(Double.parseDouble(text));
            } catch (NumberFormatException ignored) {
                return Optional.empty();
            }
        }
        return Optional.empty();
    }

    private double durationHours(ProcessInstance instance) {
        LocalDateTime start = instance.getStartedAt();
        LocalDateTime end = instance.getFinishedAt() != null ? instance.getFinishedAt() : instance.getUpdatedAt();
        if (start == null || end == null || end.isBefore(start)) {
            return 0.0;
        }
        return Duration.between(start, end).toMinutes() / 60.0;
    }

    private double round(double value) {
        return BigDecimal.valueOf(value).setScale(2, RoundingMode.HALF_UP).doubleValue();
    }

    private String safe(String value, String fallback) {
        return value == null || value.isBlank() ? fallback : value;
    }

    private String dataSource(boolean predictiveIntent, boolean predictiveMetricsAvailable) {
        if (predictiveIntent && predictiveMetricsAvailable) {
            return "Datos predictivos CU19";
        }
        if (predictiveIntent) {
            return "Datos operativos con aproximación de riesgo";
        }
        return "Datos operativos";
    }

    public record ReportAggregationResult(
            List<String> columns,
            List<Map<String, Object>> rows,
            List<Map<String, Object>> chartData,
            boolean predictiveMetricsAvailable,
            String dataSource) {
    }

    private class Bucket {
        private final String key;
        private int count;
        private int completedCount;
        private int pendingCount;
        private final List<Double> durations = new ArrayList<>();
        private final List<Double> riskScores = new ArrayList<>();
        private final List<Double> priorityScores = new ArrayList<>();
        private final List<Double> bottleneckScores = new ArrayList<>();

        private Bucket(String key) {
            this.key = key;
        }

        private void add(ProcessInstance instance) {
            count++;
            if (instance.getStatus() == ProcessInstanceStatus.COMPLETED) {
                completedCount++;
            }
            if (instance.getStatus() == ProcessInstanceStatus.PENDING_ASSIGNMENT
                    || instance.getStatus() == ProcessInstanceStatus.RUNNING) {
                pendingCount++;
            }
            durations.add(durationHours(instance));
            readPredictiveNumber(instance, "riskScore").ifPresent(riskScores::add);
            readPredictiveNumber(instance, "priorityScore").ifPresent(priorityScores::add);
            readPredictiveNumber(instance, "bottleneckScore").ifPresent(bottleneckScores::add);
        }

        private double avgDurationHours() {
            return durations.stream().mapToDouble(Double::doubleValue).average().orElse(0.0);
        }

        private double maxDurationHours() {
            return durations.stream().mapToDouble(Double::doubleValue).max().orElse(0.0);
        }

        private long slaExceededCount() {
            return durations.stream().filter(value -> value > SLA_HOURS).count();
        }

        private double slaExceededRate() {
            return count == 0 ? 0.0 : (slaExceededCount() * 100.0) / count;
        }

        private Optional<Double> avgRiskScore() {
            return average(riskScores);
        }

        private Optional<Double> avgPriorityScore() {
            return average(priorityScores);
        }

        private Optional<Double> avgBottleneckScore() {
            return average(bottleneckScores);
        }

        private Optional<Double> average(List<Double> values) {
            return values.isEmpty()
                    ? Optional.empty()
                    : Optional.of(values.stream().mapToDouble(Double::doubleValue).average().orElse(0.0));
        }

        private String operationalRiskLevel() {
            if (slaExceededRate() >= 60.0 || avgDurationHours() >= SLA_HOURS * 2) {
                return "ALTO";
            }
            if (slaExceededRate() >= 25.0 || avgDurationHours() >= SLA_HOURS) {
                return "MEDIO";
            }
            return "BAJO";
        }
    }
}
