package sw1.backend.flowroad.services.report;

import java.text.Normalizer;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.stream.Collectors;

import org.springframework.stereotype.Service;

import sw1.backend.flowroad.dtos.report.ReportQuerySpec;
import sw1.backend.flowroad.dtos.report.ReportSortSpec;

@Service
public class ReportQueryBuilderService {
    public static final Set<String> ALLOWED_GROUPINGS = Set.of(
            "department", "workflow", "status", "assignedUser", "organization", "day", "week", "month");
    public static final Set<String> ALLOWED_METRICS = Set.of(
            "count", "percentage", "completedCount", "pendingCount", "avgDurationHours", "maxDurationHours",
            "slaExceededCount", "slaExceededRate", "avgPriorityScore", "avgRiskScore", "avgBottleneckScore",
            "operationalRiskLevel");
    public static final Set<String> ALLOWED_DATASETS = Set.of("PROCESS_ANALYTICS");
    public static final Set<String> ALLOWED_CHARTS = Set.of("BAR", "LINE", "PIE", "TABLE");
    public static final Set<String> ALLOWED_INTENTS = Set.of(
            "BASIC_BY_STATUS", "BASIC_BY_DEPARTMENT", "BASIC_BY_WORKFLOW", "BASIC_BY_ORGANIZATION",
            "CREATED_LAST_7_DAYS", "RECENT_PROCESSES", "AVG_TIME_BY_DEPARTMENT", "AVG_TIME_BY_WORKFLOW",
            "SLA_EXCEEDED", "SLA_EXCEEDED_BY_DEPARTMENT", "SLA_EXCEEDED_BY_WORKFLOW", "TOP_RISK_PROCESSES",
            "RISK_BY_DEPARTMENT", "BOTTLENECK_BY_DEPARTMENT", "BOTTLENECK_BY_WORKFLOW", "PRIORITY_PROCESSES",
            "MONTHLY_EVOLUTION", "WEEKLY_EVOLUTION", "UNKNOWN");

    public ReportQuerySpec normalize(ReportQuerySpec raw, String prompt, List<String> warnings) {
        ReportQuerySpec spec = raw != null ? raw : new ReportQuerySpec();
        String intent = inferReportIntent(prompt, spec.getReportIntent());

        spec.setReportIntent(intent);
        spec.setDataset(ALLOWED_DATASETS.contains(nullToDefault(spec.getDataset(), "")) ? spec.getDataset()
                : "PROCESS_ANALYTICS");
        spec.setDateRange(normalizeDateRange(spec.getDateRange(), prompt, intent));
        applyIntentShape(spec, intent, prompt, warnings);
        spec.setGroupBy(filterAllowed(spec.getGroupBy(), ALLOWED_GROUPINGS));
        spec.setMetrics(filterAllowed(spec.getMetrics(), ALLOWED_METRICS));
        if (spec.getGroupBy().isEmpty()) {
            spec.setGroupBy(new ArrayList<>(List.of("status")));
            warnings.add("No se recibio una agrupacion valida; se uso estado como agrupacion segura.");
        }
        if (spec.getMetrics().isEmpty()) {
            spec.setMetrics(new ArrayList<>(List.of("count")));
        }

        String chart = nullToDefault(spec.getChartType(), defaultChart(intent)).toUpperCase(Locale.ROOT);
        spec.setChartType(ALLOWED_CHARTS.contains(chart) ? chart : defaultChart(intent));
        spec.setLimit(clampLimit(spec.getLimit()));
        spec.setTitle(titleFor(intent));
        spec.setSummaryIntent(nullToDefault(spec.getSummaryIntent(), summaryIntentFor(intent)));
        spec.setSort(normalizeSort(spec.getSort(), spec.getMetrics(), spec.getGroupBy(), intent));
        spec.setFilters(List.of());
        return spec;
    }

    public ReportQuerySpec buildFallback(String prompt) {
        ReportQuerySpec spec = ReportQuerySpec.builder()
                .dataset("PROCESS_ANALYTICS")
                .limit(50)
                .build();
        return normalize(spec, prompt, new ArrayList<>());
    }

    public List<String> availableFields() {
        return new ArrayList<>(ALLOWED_GROUPINGS);
    }

    public List<String> availableMetrics() {
        return new ArrayList<>(ALLOWED_METRICS);
    }

    public List<String> availableGroupings() {
        return new ArrayList<>(ALLOWED_GROUPINGS);
    }

    public boolean isPredictiveIntent(String intent) {
        return Set.of("TOP_RISK_PROCESSES", "RISK_BY_DEPARTMENT", "BOTTLENECK_BY_DEPARTMENT",
                "BOTTLENECK_BY_WORKFLOW", "PRIORITY_PROCESSES").contains(intent);
    }

    public boolean isPredictivePrompt(String prompt) {
        String normalized = normalizeText(prompt);
        return normalized.contains("riesgo")
                || normalized.contains("prioridad")
                || normalized.contains("cuello de botella")
                || normalized.contains("cuellos de botella")
                || normalized.contains("bottleneck");
    }

    public String dateRangeLabel(ReportQuerySpec spec) {
        if (spec == null || spec.getDateRange() == null) {
            return "Todo el historial";
        }
        return switch (nullToDefault(spec.getDateRange().getPreset(), "ALL_TIME")) {
            case "LAST_7_DAYS" -> "Últimos 7 días";
            case "LAST_30_DAYS" -> "Último mes";
            case "CUSTOM" -> "Rango personalizado";
            default -> "Todo el historial";
        };
    }

    private void applyIntentShape(ReportQuerySpec spec, String intent, String prompt, List<String> warnings) {
        spec.setGroupBy(new ArrayList<>(switch (intent) {
            case "CREATED_LAST_7_DAYS" -> List.of("day");
            case "BASIC_BY_STATUS" -> List.of("status");
            case "BASIC_BY_DEPARTMENT", "AVG_TIME_BY_DEPARTMENT", "SLA_EXCEEDED_BY_DEPARTMENT",
                    "RISK_BY_DEPARTMENT", "BOTTLENECK_BY_DEPARTMENT" -> List.of("department");
            case "BASIC_BY_WORKFLOW", "AVG_TIME_BY_WORKFLOW", "SLA_EXCEEDED_BY_WORKFLOW",
                    "TOP_RISK_PROCESSES", "PRIORITY_PROCESSES", "BOTTLENECK_BY_WORKFLOW" -> List.of("workflow");
            case "BASIC_BY_ORGANIZATION" -> List.of("organization");
            case "MONTHLY_EVOLUTION" -> List.of("month");
            case "WEEKLY_EVOLUTION" -> List.of("week");
            case "SLA_EXCEEDED" -> List.of(inferExplicitGrouping(prompt, "workflow"));
            default -> List.of(inferExplicitGrouping(prompt, "status"));
        }));

        spec.setMetrics(new ArrayList<>(switch (intent) {
            case "BASIC_BY_STATUS" -> List.of("count", "percentage");
            case "CREATED_LAST_7_DAYS" -> List.of("count");
            case "BASIC_BY_DEPARTMENT", "BASIC_BY_WORKFLOW" -> List.of("count", "avgDurationHours", "slaExceededCount");
            case "BASIC_BY_ORGANIZATION", "MONTHLY_EVOLUTION", "WEEKLY_EVOLUTION" -> List.of("count");
            case "AVG_TIME_BY_DEPARTMENT", "AVG_TIME_BY_WORKFLOW" -> List.of("count", "avgDurationHours", "maxDurationHours");
            case "SLA_EXCEEDED", "SLA_EXCEEDED_BY_DEPARTMENT", "SLA_EXCEEDED_BY_WORKFLOW" ->
                    List.of("count", "slaExceededCount", "slaExceededRate");
            case "TOP_RISK_PROCESSES", "RISK_BY_DEPARTMENT", "BOTTLENECK_BY_DEPARTMENT",
                    "BOTTLENECK_BY_WORKFLOW", "PRIORITY_PROCESSES" ->
                    List.of("count", "avgRiskScore", "avgPriorityScore", "avgBottleneckScore",
                            "slaExceededCount", "slaExceededRate");
            default -> List.of("count");
        }));

        if ("UNKNOWN".equals(intent)) {
            warnings.add("No se reconocio una intencion especifica; se genero un reporte basico operativo.");
        }
    }

    private String inferReportIntent(String prompt, String aiIntent) {
        String normalized = normalizeText(prompt);
        String cleanAiIntent = nullToDefault(aiIntent, "").toUpperCase(Locale.ROOT);
        if (ALLOWED_INTENTS.contains(cleanAiIntent) && !"UNKNOWN".equals(cleanAiIntent)) {
            return cleanAiIntent;
        }
        boolean asksDepartment = normalized.contains("departamento");
        boolean asksWorkflow = normalized.contains("workflow") || normalized.contains("tramite")
                || normalized.contains("tipo de tramite");

        if ((normalized.contains("creados") || normalized.contains("creado")) && containsLast7Days(normalized)) {
            return "CREATED_LAST_7_DAYS";
        }
        if (containsAny(normalized, "mensual", "por mes")) {
            return "MONTHLY_EVOLUTION";
        }
        if (containsAny(normalized, "semanal", "por semana")) {
            return "WEEKLY_EVOLUTION";
        }
        if (containsAny(normalized, "mayor riesgo", "mas riesgo")) {
            return "TOP_RISK_PROCESSES";
        }
        if (normalized.contains("riesgo")) {
            return asksDepartment || !asksWorkflow ? "RISK_BY_DEPARTMENT" : "TOP_RISK_PROCESSES";
        }
        if (normalized.contains("cuello de botella") || normalized.contains("cuellos de botella")) {
            return asksWorkflow ? "BOTTLENECK_BY_WORKFLOW" : "BOTTLENECK_BY_DEPARTMENT";
        }
        if (normalized.contains("prioridad")) {
            return "PRIORITY_PROCESSES";
        }
        if (containsAny(normalized, "sla vencido", "atrasados", "vencidos")) {
            if (asksDepartment) {
                return "SLA_EXCEEDED_BY_DEPARTMENT";
            }
            if (asksWorkflow) {
                return "SLA_EXCEEDED_BY_WORKFLOW";
            }
            return "SLA_EXCEEDED";
        }
        if (containsAny(normalized, "tiempo promedio", "duracion promedio", "demora promedio", "mayor tiempo")) {
            return asksWorkflow ? "AVG_TIME_BY_WORKFLOW" : "AVG_TIME_BY_DEPARTMENT";
        }
        if (containsAny(normalized, "organizacion", "empresa")) {
            return "BASIC_BY_ORGANIZATION";
        }
        if (normalized.contains("estado")) {
            return "BASIC_BY_STATUS";
        }
        if (asksDepartment) {
            return "BASIC_BY_DEPARTMENT";
        }
        if (asksWorkflow) {
            return "BASIC_BY_WORKFLOW";
        }
        if (normalized.contains("por dia") || normalized.contains("por fecha")) {
            return "RECENT_PROCESSES";
        }
        return "UNKNOWN";
    }

    private String inferExplicitGrouping(String prompt, String fallback) {
        String normalized = normalizeText(prompt);
        if (normalized.contains("departamento")) {
            return "department";
        }
        if (normalized.contains("estado")) {
            return "status";
        }
        if (normalized.contains("workflow") || normalized.contains("tramite") || normalized.contains("tipo de tramite")) {
            return "workflow";
        }
        if (normalized.contains("organizacion") || normalized.contains("empresa")) {
            return "organization";
        }
        if (normalized.contains("por dia")) {
            return "day";
        }
        if (normalized.contains("por semana")) {
            return "week";
        }
        if (normalized.contains("por mes")) {
            return "month";
        }
        return fallback;
    }

    private List<String> filterAllowed(List<String> values, Set<String> allowed) {
        if (values == null) {
            return new ArrayList<>();
        }
        return values.stream()
                .filter(allowed::contains)
                .distinct()
                .collect(Collectors.toCollection(ArrayList::new));
    }

    private List<ReportSortSpec> normalizeSort(
            List<ReportSortSpec> sort,
            List<String> metrics,
            List<String> groupBy,
            String intent) {
        if (sort == null || sort.isEmpty()) {
            String defaultField = switch (intent) {
                case "TOP_RISK_PROCESSES", "RISK_BY_DEPARTMENT" -> "avgRiskScore";
                case "PRIORITY_PROCESSES" -> "avgPriorityScore";
                case "BOTTLENECK_BY_DEPARTMENT", "BOTTLENECK_BY_WORKFLOW", "AVG_TIME_BY_DEPARTMENT",
                        "AVG_TIME_BY_WORKFLOW" -> "avgDurationHours";
                case "SLA_EXCEEDED", "SLA_EXCEEDED_BY_DEPARTMENT", "SLA_EXCEEDED_BY_WORKFLOW" -> "slaExceededRate";
                default -> "count";
            };
            return List.of(ReportSortSpec.builder().field(defaultField).direction("DESC").build());
        }
        List<String> sortable = new ArrayList<>();
        sortable.addAll(metrics);
        sortable.addAll(groupBy);
        return sort.stream()
                .filter(s -> s.getField() != null && sortable.contains(s.getField()))
                .map(s -> ReportSortSpec.builder()
                        .field(s.getField())
                        .direction("ASC".equalsIgnoreCase(s.getDirection()) ? "ASC" : "DESC")
                        .build())
                .toList();
    }

    private ReportQuerySpec.DateRangeSpec normalizeDateRange(
            ReportQuerySpec.DateRangeSpec raw,
            String prompt,
            String intent) {
        if ("CREATED_LAST_7_DAYS".equals(intent)) {
            return presetToRange("LAST_7_DAYS");
        }
        if (raw != null && raw.getPreset() != null) {
            String preset = raw.getPreset().toUpperCase(Locale.ROOT);
            if (Set.of("LAST_7_DAYS", "LAST_30_DAYS", "ALL_TIME", "CUSTOM").contains(preset)) {
                if ("CUSTOM".equals(preset) && raw.getFrom() != null && raw.getTo() != null) {
                    return raw;
                }
                return presetToRange(preset);
            }
        }
        String normalized = normalizeText(prompt);
        if (containsLast7Days(normalized)) {
            return presetToRange("LAST_7_DAYS");
        }
        if (normalized.contains("ultimo mes") || normalized.contains("ultimos 30") || normalized.contains("mes")) {
            return presetToRange("LAST_30_DAYS");
        }
        return presetToRange("ALL_TIME");
    }

    private ReportQuerySpec.DateRangeSpec presetToRange(String preset) {
        LocalDateTime now = LocalDateTime.now();
        LocalDateTime from = switch (preset) {
            case "LAST_7_DAYS" -> now.minusDays(7);
            case "LAST_30_DAYS" -> now.minusDays(30);
            default -> null;
        };
        return ReportQuerySpec.DateRangeSpec.builder().preset(preset).from(from).to(now).build();
    }

    private String titleFor(String intent) {
        return switch (intent) {
            case "BASIC_BY_STATUS" -> "Trámites por estado";
            case "BASIC_BY_DEPARTMENT" -> "Trámites por departamento";
            case "BASIC_BY_WORKFLOW" -> "Trámites por tipo de trámite";
            case "BASIC_BY_ORGANIZATION" -> "Trámites por empresa";
            case "CREATED_LAST_7_DAYS" -> "Trámites creados en los últimos 7 días";
            case "AVG_TIME_BY_DEPARTMENT" -> "Tiempo promedio por departamento";
            case "AVG_TIME_BY_WORKFLOW" -> "Tiempo promedio por tipo de trámite";
            case "SLA_EXCEEDED" -> "Trámites con SLA vencido";
            case "SLA_EXCEEDED_BY_DEPARTMENT" -> "SLA vencido por departamento";
            case "SLA_EXCEEDED_BY_WORKFLOW" -> "SLA vencido por trámite";
            case "TOP_RISK_PROCESSES" -> "Trámites con mayor riesgo";
            case "RISK_BY_DEPARTMENT" -> "Reporte predictivo de riesgo, prioridad y cuello de botella";
            case "BOTTLENECK_BY_DEPARTMENT" -> "Cuellos de botella por departamento";
            case "BOTTLENECK_BY_WORKFLOW" -> "Cuellos de botella por trámite";
            case "PRIORITY_PROCESSES" -> "Prioridad de trámites según IA";
            case "MONTHLY_EVOLUTION" -> "Evolución mensual de trámites";
            case "WEEKLY_EVOLUTION" -> "Evolución semanal de trámites";
            default -> "Reporte inteligente operativo";
        };
    }

    private String summaryIntentFor(String intent) {
        return switch (intent) {
            case "CREATED_LAST_7_DAYS" -> "Reporte generado por día para el periodo de los últimos 7 días.";
            case "RISK_BY_DEPARTMENT", "TOP_RISK_PROCESSES", "BOTTLENECK_BY_DEPARTMENT", "BOTTLENECK_BY_WORKFLOW",
                    "PRIORITY_PROCESSES" -> "Analiza riesgo, prioridad y cuellos de botella.";
            default -> "Reporte administrativo generado desde lenguaje natural.";
        };
    }

    private String defaultChart(String intent) {
        return switch (intent) {
            case "BASIC_BY_STATUS" -> "PIE";
            case "CREATED_LAST_7_DAYS", "MONTHLY_EVOLUTION", "WEEKLY_EVOLUTION" -> "LINE";
            default -> "BAR";
        };
    }

    private boolean containsLast7Days(String normalized) {
        return containsAny(normalized, "ultimos 7 dias", "ultimo 7 dias", "7 dias", "siete dias", "ultima semana");
    }

    private boolean containsAny(String text, String... needles) {
        for (String needle : needles) {
            if (text.contains(needle)) {
                return true;
            }
        }
        return false;
    }

    private int clampLimit(Integer limit) {
        if (limit == null) {
            return 50;
        }
        return Math.max(1, Math.min(limit, 200));
    }

    private String nullToDefault(String value, String fallback) {
        return value == null || value.isBlank() ? fallback : value;
    }

    private String normalizeText(String text) {
        if (text == null) {
            return "";
        }
        return Normalizer.normalize(text, Normalizer.Form.NFD)
                .replaceAll("\\p{M}", "")
                .toLowerCase(Locale.ROOT);
    }
}
