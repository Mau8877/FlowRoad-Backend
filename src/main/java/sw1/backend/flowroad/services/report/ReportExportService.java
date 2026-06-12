package sw1.backend.flowroad.services.report;

import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

import com.openhtmltopdf.pdfboxout.PdfRendererBuilder;
import org.springframework.stereotype.Service;

import sw1.backend.flowroad.dtos.report.ReportPreviewResponse;

@Service
public class ReportExportService {
    public ExportedReport export(ReportPreviewResponse report, String format) {
        String normalized = format == null ? "HTML" : format.trim().toUpperCase();
        return switch (normalized) {
            case "PDF" -> new ExportedReport(toPdf(report), "application/pdf", "reporte-inteligente.pdf");
            case "EXCEL", "XLSX" -> new ExportedReport(
                    toXlsx(report),
                    "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet",
                    "reporte-inteligente.xlsx");
            default -> new ExportedReport(toHtml(report).getBytes(StandardCharsets.UTF_8), "text/html; charset=UTF-8",
                    "reporte-inteligente.html");
        };
    }

    public String toHtml(ReportPreviewResponse report) {
        StringBuilder html = new StringBuilder();
        html.append("<!doctype html><html><head><meta charset=\"UTF-8\"/><title>")
                .append(escape(report.getTitle()))
                .append("</title><style>")
                .append("@page{margin:24mm 18mm}*{box-sizing:border-box}")
                .append("body{font-family:Arial,'Liberation Sans',sans-serif;color:#1f2937;margin:0;background:#f7f1ea}")
                .append(".page{background:#fff;max-width:1040px;margin:0 auto;padding:32px}")
                .append(".brand{color:#cc9e61;font-size:12px;font-weight:800;letter-spacing:.08em;text-transform:uppercase}")
                .append("h1{color:#541f14;font-size:28px;margin:8px 0 12px}")
                .append(".meta{display:flex;gap:16px;flex-wrap:wrap;color:#626266;font-size:12px;margin-bottom:18px}")
                .append(".box{background:#f7f1ea;border-left:5px solid #cc9e61;border-radius:8px;padding:14px 16px;margin:14px 0}")
                .append(".prompt{background:#fff;border:1px solid #e7d7c3;border-radius:8px;padding:12px;margin:12px 0}")
                .append(".warnings{background:#fff3cd;border:1px solid #f0d98a;border-radius:8px;padding:12px;margin:12px 0;color:#654500}")
                .append("table{border-collapse:collapse;width:100%;margin-top:18px;font-size:12px}")
                .append("th,td{border:1px solid #e5e7eb;padding:9px;text-align:left;vertical-align:top}")
                .append("th{background:#cc9e61;color:#020304;font-weight:800}")
                .append("tr:nth-child(even) td{background:#fbf7f1}")
                .append(".empty{padding:18px;text-align:center;color:#626266}")
                .append("footer{margin-top:28px;padding-top:12px;border-top:1px solid #e5e7eb;color:#938172;font-size:12px;text-align:center}")
                .append("</style></head><body>");
        html.append("<main class=\"page\">");
        html.append("<div class=\"brand\">FlowRoad - Reporte Inteligente</div>");
        html.append("<h1>").append(escape(report.getTitle())).append("</h1>");
        html.append("<div class=\"meta\"><span><strong>Generado:</strong> ")
                .append(escape(report.getGeneratedAt()))
                .append("</span><span><strong>Tipo:</strong> ")
                .append(escape(report.getReportIntent()))
                .append("</span><span><strong>Rango:</strong> ")
                .append(escape(report.getDateRangeLabel()))
                .append("</span><span><strong>Fuente:</strong> ")
                .append(escape(report.getDataSource()))
                .append("</span><span><strong>Visualización:</strong> ")
                .append(escape(chartLabel(report.getChartType())))
                .append("</span></div>");
        html.append("<section class=\"box\"><strong>Resumen</strong><br/>")
                .append(escape(report.getSummary()))
                .append("</section>");
        html.append("<section class=\"prompt\"><strong>Prompt original</strong><br/>")
                .append(escape(report.getPrompt()))
                .append("</section>");
        appendWarnings(html, report);
        appendHtmlTable(html, report);
        html.append("<footer>FlowRoad - Reporte Inteligente</footer>");
        html.append("</main></body></html>");
        return html.toString();
    }

    private void appendWarnings(StringBuilder html, ReportPreviewResponse report) {
        if (report.getWarnings() == null || report.getWarnings().isEmpty()) {
            return;
        }
        html.append("<section class=\"warnings\"><strong>Advertencias</strong><ul>");
        for (String warning : report.getWarnings()) {
            html.append("<li>").append(escape(warning)).append("</li>");
        }
        html.append("</ul></section>");
    }

    private void appendHtmlTable(StringBuilder html, ReportPreviewResponse report) {
        html.append("<table><thead><tr>");
        for (String column : safeList(report.getColumns())) {
            html.append("<th>").append(escape(column)).append("</th>");
        }
        html.append("</tr></thead><tbody>");
        if (report.getRows() == null || report.getRows().isEmpty()) {
            int colspan = report.getColumns() == null || report.getColumns().isEmpty() ? 1 : report.getColumns().size();
            html.append("<tr><td class=\"empty\" colspan=\"").append(colspan)
                    .append("\">No se encontraron datos para este reporte.</td></tr>");
        } else {
            for (Map<String, Object> row : report.getRows()) {
                html.append("<tr>");
                for (String column : safeList(report.getColumns())) {
                    html.append("<td>").append(escape(String.valueOf(row.getOrDefault(column, "")))).append("</td>");
                }
                html.append("</tr>");
            }
        }
        html.append("</tbody></table>");
    }

    private byte[] toXlsx(ReportPreviewResponse report) {
        try {
            ByteArrayOutputStream out = new ByteArrayOutputStream();
            try (ZipOutputStream zip = new ZipOutputStream(out, StandardCharsets.UTF_8)) {
                put(zip, "[Content_Types].xml", """
                        <?xml version="1.0" encoding="UTF-8"?>
                        <Types xmlns="http://schemas.openxmlformats.org/package/2006/content-types">
                        <Default Extension="rels" ContentType="application/vnd.openxmlformats-package.relationships+xml"/>
                        <Default Extension="xml" ContentType="application/xml"/>
                        <Override PartName="/xl/workbook.xml" ContentType="application/vnd.openxmlformats-officedocument.spreadsheetml.sheet.main+xml"/>
                        <Override PartName="/xl/worksheets/sheet1.xml" ContentType="application/vnd.openxmlformats-officedocument.spreadsheetml.worksheet+xml"/>
                        </Types>
                        """);
                put(zip, "_rels/.rels", """
                        <?xml version="1.0" encoding="UTF-8"?>
                        <Relationships xmlns="http://schemas.openxmlformats.org/package/2006/relationships">
                        <Relationship Id="rId1" Type="http://schemas.openxmlformats.org/officeDocument/2006/relationships/officeDocument" Target="xl/workbook.xml"/>
                        </Relationships>
                        """);
                put(zip, "xl/workbook.xml", """
                        <?xml version="1.0" encoding="UTF-8"?>
                        <workbook xmlns="http://schemas.openxmlformats.org/spreadsheetml/2006/main" xmlns:r="http://schemas.openxmlformats.org/officeDocument/2006/relationships">
                        <sheets><sheet name="Reporte" sheetId="1" r:id="rId1"/></sheets>
                        </workbook>
                        """);
                put(zip, "xl/_rels/workbook.xml.rels", """
                        <?xml version="1.0" encoding="UTF-8"?>
                        <Relationships xmlns="http://schemas.openxmlformats.org/package/2006/relationships">
                        <Relationship Id="rId1" Type="http://schemas.openxmlformats.org/officeDocument/2006/relationships/worksheet" Target="worksheets/sheet1.xml"/>
                        </Relationships>
                        """);
                put(zip, "xl/worksheets/sheet1.xml", sheetXml(report));
            }
            return out.toByteArray();
        } catch (Exception ex) {
            throw new IllegalStateException("No se pudo generar Excel.", ex);
        }
    }

    private String sheetXml(ReportPreviewResponse report) {
        StringBuilder sheet = new StringBuilder();
        sheet.append("<?xml version=\"1.0\" encoding=\"UTF-8\"?>");
        sheet.append("<worksheet xmlns=\"http://schemas.openxmlformats.org/spreadsheetml/2006/main\"><sheetData>");
        int rowIndex = 1;
        rowIndex = appendMetadataRow(sheet, rowIndex, "Título", report.getTitle());
        rowIndex = appendMetadataRow(sheet, rowIndex, "Fuente de datos", report.getDataSource());
        rowIndex = appendMetadataRow(sheet, rowIndex, "Rango", report.getDateRangeLabel());
        rowIndex = appendMetadataRow(sheet, rowIndex, "Tipo de reporte", report.getReportIntent());
        rowIndex = appendMetadataRow(sheet, rowIndex, "Prompt original", report.getPrompt());
        rowIndex = appendMetadataRow(sheet, rowIndex, "Resumen", report.getSummary());
        if (report.getWarnings() != null && !report.getWarnings().isEmpty()) {
            rowIndex = appendMetadataRow(sheet, rowIndex, "Advertencias", String.join(" | ", report.getWarnings()));
        }
        rowIndex++;
        sheet.append("<row r=\"").append(rowIndex++).append("\">");
        int colIndex = 1;
        for (String column : safeList(report.getColumns())) {
            appendCell(sheet, colIndex++, rowIndex - 1, column);
        }
        sheet.append("</row>");
        for (Map<String, Object> row : safeRows(report.getRows())) {
            sheet.append("<row r=\"").append(rowIndex).append("\">");
            colIndex = 1;
            for (String column : safeList(report.getColumns())) {
                appendCell(sheet, colIndex++, rowIndex, String.valueOf(row.getOrDefault(column, "")));
            }
            sheet.append("</row>");
            rowIndex++;
        }
        sheet.append("</sheetData></worksheet>");
        return sheet.toString();
    }

    private int appendMetadataRow(StringBuilder sheet, int rowIndex, String label, String value) {
        sheet.append("<row r=\"").append(rowIndex).append("\">");
        appendCell(sheet, 1, rowIndex, label);
        appendCell(sheet, 2, rowIndex, value);
        sheet.append("</row>");
        return rowIndex + 1;
    }

    private void appendCell(StringBuilder sheet, int col, int row, String value) {
        sheet.append("<c r=\"").append(columnName(col)).append(row).append("\" t=\"inlineStr\"><is><t>")
                .append(escape(value))
                .append("</t></is></c>");
    }

    private String columnName(int col) {
        StringBuilder name = new StringBuilder();
        int value = col;
        while (value > 0) {
            int remainder = (value - 1) % 26;
            name.insert(0, (char) ('A' + remainder));
            value = (value - 1) / 26;
        }
        return name.toString();
    }

    private byte[] toPdf(ReportPreviewResponse report) {
        try {
            ByteArrayOutputStream out = new ByteArrayOutputStream();
            PdfRendererBuilder builder = new PdfRendererBuilder();
            builder.useFastMode();
            builder.withHtmlContent(toHtml(report), null);
            builder.toStream(out);
            builder.run();
            return out.toByteArray();
        } catch (Exception ex) {
            throw new IllegalStateException("No se pudo generar PDF.", ex);
        }
    }

    private void put(ZipOutputStream zip, String path, String content) throws java.io.IOException {
        zip.putNextEntry(new ZipEntry(path));
        zip.write(content.getBytes(StandardCharsets.UTF_8));
        zip.closeEntry();
    }

    private List<String> safeList(List<String> values) {
        return values == null ? List.of() : values;
    }

    private List<Map<String, Object>> safeRows(List<Map<String, Object>> rows) {
        return rows == null ? List.of() : rows;
    }

    private String chartLabel(String chartType) {
        return switch (chartType == null ? "TABLE" : chartType.toUpperCase()) {
            case "BAR" -> "barras";
            case "LINE" -> "líneas";
            case "PIE" -> "circular";
            default -> "tabla";
        };
    }

    private String escape(String value) {
        if (value == null) {
            return "";
        }
        return value
                .replace("&", "&amp;")
                .replace("<", "&lt;")
                .replace(">", "&gt;")
                .replace("\"", "&quot;");
    }

    public record ExportedReport(byte[] content, String contentType, String filename) {
    }
}
