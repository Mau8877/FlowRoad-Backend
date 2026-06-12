package sw1.backend.flowroad.services.report;

import java.time.LocalDateTime;
import java.util.List;

import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;

import lombok.RequiredArgsConstructor;
import sw1.backend.flowroad.dtos.report.GeneratedReportHistoryPageResponse;
import sw1.backend.flowroad.dtos.report.GeneratedReportHistoryResponse;
import sw1.backend.flowroad.dtos.report.ReportPreviewResponse;
import sw1.backend.flowroad.dtos.report.ReportQuerySpec;
import sw1.backend.flowroad.dtos.report.ReportSuggestionsResponse;
import sw1.backend.flowroad.models.report.GeneratedReport;
import sw1.backend.flowroad.models.user.User;
import sw1.backend.flowroad.repository.report.GeneratedReportRepository;
import sw1.backend.flowroad.services.report.ReportAggregationService.ReportAggregationResult;

@Service
@RequiredArgsConstructor
public class IntelligentReportService {
    private final ReportAiClient reportAiClient;
    private final ReportQueryBuilderService queryBuilderService;
    private final ReportAggregationService aggregationService;
    private final GeneratedReportRepository generatedReportRepository;

    public ReportPreviewResponse preview(String prompt, User user) {
        List<String> warnings = new java.util.ArrayList<>();
        ReportQuerySpec interpreted = reportAiClient.interpret(prompt);
        ReportQuerySpec spec = queryBuilderService.normalize(interpreted, prompt, warnings);
        ReportAggregationResult result = aggregationService.execute(user.getOrgId(), spec);

        String title = buildTitle(spec, result);
        String summary = buildSummary(spec, result);
        if (result.rows().isEmpty()) {
            warnings.add("No se encontraron datos para los filtros solicitados.");
        }
        if (queryBuilderService.isPredictiveIntent(spec.getReportIntent()) && !result.predictiveMetricsAvailable()) {
            warnings.add("No se encontraron métricas predictivas reales del CU19 para este periodo. "
                    + "Se usaron SLA vencido, tiempos de atención y volumen como aproximación operativa.");
        }

        GeneratedReport saved = generatedReportRepository.save(GeneratedReport.builder()
                .orgId(user.getOrgId())
                .generatedByUserId(user.getId())
                .title(title)
                .prompt(prompt)
                .summary(summary)
                .chartType(spec.getChartType())
                .columns(result.columns())
                .rows(result.rows())
                .querySpec(spec)
                .generatedAt(LocalDateTime.now())
                .build());

        return ReportPreviewResponse.builder()
                .reportId(saved.getId())
                .title(title)
                .summary(summary)
                .prompt(prompt)
                .generatedAt(saved.getGeneratedAt().toString())
                .reportIntent(spec.getReportIntent())
                .dateRangeLabel(queryBuilderService.dateRangeLabel(spec))
                .dataSource(result.dataSource())
                .columns(result.columns())
                .rows(result.rows())
                .chartType(spec.getChartType())
                .chartData(result.chartData())
                .querySpec(spec)
                .warnings(warnings)
                .build();
    }

    public List<GeneratedReportHistoryResponse> history(User user) {
        return generatedReportRepository
                .findTop10ByOrgIdAndGeneratedByUserIdOrderByGeneratedAtDesc(user.getOrgId(), user.getId())
                .stream()
                .map(this::toHistoryResponse)
                .toList();
    }

    public GeneratedReportHistoryPageResponse historyPage(User user, int page, int size) {
        int safePage = Math.max(0, page);
        int safeSize = Math.max(1, Math.min(size, 20));
        var pageResult = generatedReportRepository.findByOrgIdAndGeneratedByUserIdOrderByGeneratedAtDesc(
                user.getOrgId(),
                user.getId(),
                PageRequest.of(safePage, safeSize, Sort.by(Sort.Direction.DESC, "generatedAt")));
        return GeneratedReportHistoryPageResponse.builder()
                .content(pageResult.getContent().stream().map(this::toHistoryResponse).toList())
                .page(pageResult.getNumber())
                .size(pageResult.getSize())
                .totalElements(pageResult.getTotalElements())
                .totalPages(pageResult.getTotalPages())
                .first(pageResult.isFirst())
                .last(pageResult.isLast())
                .build();
    }

    public ReportSuggestionsResponse suggestions() {
        return new ReportSuggestionsResponse(List.of(
                "Muéstrame los trámites por estado del último mes",
                "Agrupa los trámites por departamento",
                "Dame la cantidad de trámites por empresa",
                "Muéstrame los trámites por tipo de trámite",
                "Tiempo promedio por departamento",
                "Tiempo promedio por tipo de trámite",
                "Trámites con mayor tiempo de atención",
                "Trámites creados en los últimos 7 días",
                "Trámites con SLA vencido",
                "SLA vencido por departamento",
                "Porcentaje de SLA vencido por trámite",
                "Departamentos con más trámites atrasados",
                "Dame los trámites con mayor riesgo",
                "Riesgo promedio por departamento",
                "Cuellos de botella por departamento",
                "Prioridad de trámites según IA",
                "Riesgo, prioridad y cuello de botella del último mes",
                "Trámites por día",
                "Trámites por semana",
                "Evolución mensual de trámites por estado",
                "Duración mensual de trámites"));
    }

    private String buildTitle(ReportQuerySpec spec, ReportAggregationResult result) {
        if (queryBuilderService.isPredictiveIntent(spec.getReportIntent()) && !result.predictiveMetricsAvailable()) {
            return "Reporte operativo de riesgo y atrasos";
        }
        return spec.getTitle();
    }

    private GeneratedReportHistoryResponse toHistoryResponse(GeneratedReport report) {
        return GeneratedReportHistoryResponse.builder()
                .id(report.getId())
                .title(report.getTitle())
                .prompt(report.getPrompt())
                .chartType(report.getChartType())
                .rowCount(report.getRows() != null ? report.getRows().size() : 0)
                .generatedAt(report.getGeneratedAt())
                .build();
    }

    private String buildSummary(ReportQuerySpec spec, ReportAggregationResult result) {
        String grouping = spec.getGroupBy().isEmpty() ? "status" : spec.getGroupBy().get(0);
        String label = switch (grouping) {
            case "department" -> "departamento";
            case "workflow" -> "tipo de trámite";
            case "day" -> "día";
            case "week" -> "semana";
            case "month" -> "mes";
            case "organization" -> "empresa";
            default -> "estado";
        };
        if (queryBuilderService.isPredictiveIntent(spec.getReportIntent())) {
            if (result.predictiveMetricsAvailable()) {
                return "Reporte generado con métricas predictivas del CU19, agrupado por " + label + ".";
            }
            return "Reporte generado con métricas operativas porque no se encontraron datos predictivos disponibles para el periodo.";
        }
        if ("CREATED_LAST_7_DAYS".equals(spec.getReportIntent())) {
            return "Reporte generado por día para el periodo de los últimos 7 días.";
        }
        return "Reporte operativo generado para " + result.rows().size() + " grupo(s), agrupado por " + label + ".";
    }
}
