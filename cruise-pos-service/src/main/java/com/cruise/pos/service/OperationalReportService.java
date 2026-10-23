package com.cruise.pos.service;

import com.cruise.pos.dto.CreateExportJobRequest;
import com.cruise.pos.dto.OperationalReportQuery;
import com.cruise.pos.dto.OperationalReportResponse;
import com.cruise.pos.entity.PosOutlet;
import com.cruise.pos.entity.PosTransaction;
import com.cruise.pos.entity.PosTransactionItem;
import com.cruise.pos.entity.ReportExportJob;
import com.cruise.pos.enums.ExportFormat;
import com.cruise.pos.enums.ExportJobStatus;
import com.cruise.pos.enums.ReportDimension;
import com.cruise.pos.enums.TransactionStatus;
import com.cruise.pos.exception.ResourceNotFoundException;
import com.cruise.pos.repository.PosOutletRepository;
import com.cruise.pos.repository.PosTransactionRepository;
import com.cruise.pos.repository.ReportExportJobRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPage;
import org.apache.pdfbox.pdmodel.PDPageContentStream;
import org.apache.pdfbox.pdmodel.common.PDRectangle;
import org.apache.pdfbox.pdmodel.font.PDType0Font;
import org.springframework.core.io.ClassPathResource;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.*;
import java.util.stream.Collectors;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

@Service
@RequiredArgsConstructor
@Slf4j
public class OperationalReportService {

    private final PosTransactionRepository transactionRepository;
    private final ReportExportJobRepository exportJobRepository;
    private final PosOutletRepository outletRepository;

    @Transactional(readOnly = true)
    public OperationalReportResponse generateOperationalReport(OperationalReportQuery query) {
        final OperationalReportQuery q = (query != null) ? query : new OperationalReportQuery();
        String stationFilter = null;
        if (q.getOutletId() != null) {
            Optional<PosOutlet> outletOpt = outletRepository.findById(q.getOutletId());
            stationFilter = outletOpt.map(PosOutlet::getCode).orElseGet(() -> String.valueOf(q.getOutletId()));
        }

        List<PosTransaction> transactions = transactionRepository.findTransactionsForReport(
                stationFilter,
                TransactionStatus.COMPLETED,
                q.getPaymentMethod(),
                q.getStartDate(),
                q.getEndDate()
        );

        if (q.getCategory() != null && !q.getCategory().isBlank()) {
            transactions = transactions.stream()
                    .filter(t -> t.getItems() != null && t.getItems().stream()
                            .anyMatch(i -> q.getCategory().equalsIgnoreCase(i.getCategory())))
                    .collect(Collectors.toList());
        }

        BigDecimal totalGross = transactions.stream()
                .map(t -> t.getTotalAmount() != null ? t.getTotalAmount() : BigDecimal.ZERO)
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        BigDecimal totalDiscount = transactions.stream()
                .map(t -> t.getDiscount() != null ? t.getDiscount() : BigDecimal.ZERO)
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        BigDecimal totalTax = transactions.stream()
                .map(t -> t.getTax() != null ? t.getTax() : BigDecimal.ZERO)
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        BigDecimal totalNet = totalGross.subtract(totalDiscount).max(BigDecimal.ZERO);
        long count = transactions.size();
        BigDecimal avgTicket = count > 0 ? totalGross.divide(BigDecimal.valueOf(count), 2, RoundingMode.HALF_UP) : BigDecimal.ZERO;

        // Payment method breakdown
        Map<String, BigDecimal> byPayment = transactions.stream()
                .collect(Collectors.groupingBy(
                        t -> t.getPaymentMethod() != null ? t.getPaymentMethod().name() : "UNKNOWN",
                        Collectors.reducing(BigDecimal.ZERO,
                                t -> t.getTotalAmount() != null ? t.getTotalAmount() : BigDecimal.ZERO,
                                BigDecimal::add)
                ));

        // Dimension breakdown
        ReportDimension dim = q.getDimension() != null ? q.getDimension() : ReportDimension.OUTLET;
        List<OperationalReportResponse.DimensionBreakdownDto> breakdownList = computeDimensionBreakdown(transactions, dim, totalGross);

        return OperationalReportResponse.builder()
                .totalGrossRevenue(totalGross)
                .totalNetRevenue(totalNet)
                .totalDiscountAmount(totalDiscount)
                .totalTaxAmount(totalTax)
                .totalTransactionCount(count)
                .averageTicketSize(avgTicket)
                .revenueByPaymentMethod(byPayment)
                .breakdown(breakdownList)
                .build();
    }

    private List<OperationalReportResponse.DimensionBreakdownDto> computeDimensionBreakdown(
            List<PosTransaction> transactions,
            ReportDimension dimension,
            BigDecimal totalGross
    ) {
        if (dimension == ReportDimension.CATEGORY) {
            Map<String, List<PosTransactionItem>> categoryItems = new HashMap<>();
            Map<String, Set<Long>> categoryTxIds = new HashMap<>();

            for (PosTransaction t : transactions) {
                Long txId = t.getId() != null ? t.getId() : (long) t.hashCode();
                if (t.getItems() != null && !t.getItems().isEmpty()) {
                    for (PosTransactionItem item : t.getItems()) {
                        String cat = (item.getCategory() != null && !item.getCategory().isBlank())
                                ? item.getCategory().trim()
                                : "Uncategorized";
                        categoryItems.computeIfAbsent(cat, k -> new ArrayList<>()).add(item);
                        categoryTxIds.computeIfAbsent(cat, k -> new HashSet<>()).add(txId);
                    }
                } else {
                    categoryTxIds.computeIfAbsent("Uncategorized", k -> new HashSet<>()).add(txId);
                }
            }

            List<OperationalReportResponse.DimensionBreakdownDto> result = new ArrayList<>();
            for (Map.Entry<String, Set<Long>> entry : categoryTxIds.entrySet()) {
                String cat = entry.getKey();
                long txCount = entry.getValue().size();
                List<PosTransactionItem> items = categoryItems.getOrDefault(cat, Collections.emptyList());
                BigDecimal gross = items.stream()
                        .map(i -> i.getTotalPrice() != null ? i.getTotalPrice() : BigDecimal.ZERO)
                        .reduce(BigDecimal.ZERO, BigDecimal::add);

                BigDecimal net = gross;
                BigDecimal pct = totalGross.compareTo(BigDecimal.ZERO) > 0
                        ? gross.multiply(BigDecimal.valueOf(100)).divide(totalGross, 2, RoundingMode.HALF_UP)
                        : BigDecimal.ZERO;

                result.add(OperationalReportResponse.DimensionBreakdownDto.builder()
                        .label(cat)
                        .transactionCount(txCount)
                        .grossRevenue(gross)
                        .netRevenue(net)
                        .revenuePercentage(pct.doubleValue())
                        .build());
            }

            result.sort((a, b) -> {
                int cmp = b.getGrossRevenue().compareTo(a.getGrossRevenue());
                return cmp != 0 ? cmp : a.getLabel().compareTo(b.getLabel());
            });
            return result;
        }

        Map<String, List<PosTransaction>> grouped;
        if (dimension == ReportDimension.PAYMENT_METHOD) {
            grouped = transactions.stream().collect(Collectors.groupingBy(
                    t -> t.getPaymentMethod() != null ? t.getPaymentMethod().name() : "UNKNOWN"
            ));
        } else if (dimension == ReportDimension.DATE_RANGE) {
            grouped = transactions.stream().collect(Collectors.groupingBy(
                    t -> t.getCreatedAt() != null
                            ? LocalDate.ofInstant(t.getCreatedAt(), ZoneOffset.UTC).toString()
                            : "Undated"
            ));
        } else {
            // Default station / outlet identifier
            grouped = transactions.stream().collect(Collectors.groupingBy(
                    t -> t.getStationId() != null ? t.getStationId() : "General Outlet"
            ));
        }

        List<OperationalReportResponse.DimensionBreakdownDto> result = new ArrayList<>();
        for (Map.Entry<String, List<PosTransaction>> entry : grouped.entrySet()) {
            List<PosTransaction> txs = entry.getValue();
            BigDecimal gross = txs.stream()
                    .map(t -> t.getTotalAmount() != null ? t.getTotalAmount() : BigDecimal.ZERO)
                    .reduce(BigDecimal.ZERO, BigDecimal::add);

            BigDecimal discount = txs.stream()
                    .map(t -> t.getDiscount() != null ? t.getDiscount() : BigDecimal.ZERO)
                    .reduce(BigDecimal.ZERO, BigDecimal::add);

            BigDecimal net = gross.subtract(discount).max(BigDecimal.ZERO);
            BigDecimal pct = totalGross.compareTo(BigDecimal.ZERO) > 0
                    ? gross.multiply(BigDecimal.valueOf(100)).divide(totalGross, 2, RoundingMode.HALF_UP)
                    : BigDecimal.ZERO;

            result.add(OperationalReportResponse.DimensionBreakdownDto.builder()
                    .label(entry.getKey())
                    .transactionCount((long) txs.size())
                    .grossRevenue(gross)
                    .netRevenue(net)
                    .revenuePercentage(pct.doubleValue())
                    .build());
        }

        result.sort((a, b) -> {
            int cmp = b.getGrossRevenue().compareTo(a.getGrossRevenue());
            return cmp != 0 ? cmp : a.getLabel().compareTo(b.getLabel());
        });
        return result;
    }

    @Transactional
    public ReportExportJob createExportJob(CreateExportJobRequest request) {
        String jobId = "JOB-" + System.currentTimeMillis() + "-" + UUID.randomUUID().toString().substring(0, 6).toUpperCase();
        String title = request.getReportTitle() != null ? request.getReportTitle() : "Financial & Operational Report";

        String ext = request.getFormat() == ExportFormat.CSV ? ".csv" : (request.getFormat() == ExportFormat.EXCEL ? ".xlsx" : ".pdf");
        String downloadUrl = "/api/pos/reports/exports/" + jobId + "/download" + ext;

        ReportExportJob job = ReportExportJob.builder()
                .jobId(jobId)
                .format(request.getFormat())
                .status(ExportJobStatus.PENDING)
                .reportTitle(title)
                .downloadUrl(downloadUrl)
                .build();

        exportJobRepository.save(job);
        log.info("Started report export job: {} format: {} title: {}", jobId, request.getFormat(), title);

        try {
            OperationalReportQuery query = request.getQuery() != null ? request.getQuery() : new OperationalReportQuery();
            OperationalReportResponse report = generateOperationalReport(query);

            byte[] fileBytes;
            if (request.getFormat() == ExportFormat.CSV) {
                fileBytes = renderCsv(report);
            } else if (request.getFormat() == ExportFormat.EXCEL) {
                fileBytes = renderExcel(report);
            } else {
                fileBytes = renderPdf(title, report);
            }

            job.setFileSizeBytes((long) fileBytes.length);
            job.setFileData(fileBytes);
            job.setStatus(ExportJobStatus.COMPLETED);
            job.setCompletedAt(Instant.now());
            exportJobRepository.save(job);
            log.info("Completed export job: {} with size {} bytes", jobId, fileBytes.length);
        } catch (Exception e) {
            log.error("Failed to generate export job: {}", jobId, e);
            job.setStatus(ExportJobStatus.FAILED);
            job.setErrorMessage(e.getMessage() != null ? e.getMessage() : "Export rendering error");
            exportJobRepository.save(job);
        }

        return job;
    }

    private byte[] renderCsv(OperationalReportResponse report) {
        StringBuilder sb = new StringBuilder("Dimension,Transactions,Gross Revenue,Net Revenue,Revenue %\n");
        if (report.getBreakdown() != null) {
            for (var b : report.getBreakdown()) {
                sb.append(String.format(Locale.US, "%s,%d,%.2f,%.2f,%.2f%%\n",
                        escapeCsv(b.getLabel()),
                        b.getTransactionCount(),
                        b.getGrossRevenue(),
                        b.getNetRevenue(),
                        b.getRevenuePercentage()));
            }
        }
        return sb.toString().getBytes(StandardCharsets.UTF_8);
    }

    private String escapeCsv(String value) {
        if (value == null) return "\"\"";
        String sanitized = value;
        if (sanitized.startsWith("=") || sanitized.startsWith("+") || sanitized.startsWith("-") || sanitized.startsWith("@")) {
            sanitized = "'" + sanitized;
        }
        return "\"" + sanitized.replace("\"", "\"\"") + "\"";
    }

    private byte[] renderExcel(OperationalReportResponse report) throws IOException {
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        try (ZipOutputStream zos = new ZipOutputStream(baos)) {
            // [Content_Types].xml
            addZipEntry(zos, "[Content_Types].xml",
                    "<?xml version=\"1.0\" encoding=\"UTF-8\" standalone=\"yes\"?>\n" +
                    "<Types xmlns=\"http://schemas.openxmlformats.org/package/2006/content-types\">\n" +
                    "  <Default Extension=\"rels\" ContentType=\"application/vnd.openxmlformats-package.relationships+xml\"/>\n" +
                    "  <Default Extension=\"xml\" ContentType=\"application/xml\"/>\n" +
                    "  <Override PartName=\"/xl/workbook.xml\" ContentType=\"application/vnd.openxmlformats-officedocument.spreadsheetml.sheet.main+xml\"/>\n" +
                    "  <Override PartName=\"/xl/worksheets/sheet1.xml\" ContentType=\"application/vnd.openxmlformats-officedocument.spreadsheetml.worksheet+xml\"/>\n" +
                    "</Types>");

            // _rels/.rels
            addZipEntry(zos, "_rels/.rels",
                    "<?xml version=\"1.0\" encoding=\"UTF-8\" standalone=\"yes\"?>\n" +
                    "<Relationships xmlns=\"http://schemas.openxmlformats.org/package/2006/relationships\">\n" +
                    "  <Relationship Id=\"rId1\" Type=\"http://schemas.openxmlformats.org/officeDocument/2006/relationships/officeDocument\" Target=\"xl/workbook.xml\"/>\n" +
                    "</Relationships>");

            // xl/_rels/workbook.xml.rels
            addZipEntry(zos, "xl/_rels/workbook.xml.rels",
                    "<?xml version=\"1.0\" encoding=\"UTF-8\" standalone=\"yes\"?>\n" +
                    "<Relationships xmlns=\"http://schemas.openxmlformats.org/package/2006/relationships\">\n" +
                    "  <Relationship Id=\"rId1\" Type=\"http://schemas.openxmlformats.org/officeDocument/2006/relationships/worksheet\" Target=\"worksheets/sheet1.xml\"/>\n" +
                    "</Relationships>");

            // xl/workbook.xml
            addZipEntry(zos, "xl/workbook.xml",
                    "<?xml version=\"1.0\" encoding=\"UTF-8\" standalone=\"yes\"?>\n" +
                    "<workbook xmlns=\"http://schemas.openxmlformats.org/spreadsheetml/2006/main\" xmlns:r=\"http://schemas.openxmlformats.org/officeDocument/2006/relationships\">\n" +
                    "  <sheets>\n" +
                    "    <sheet name=\"Report\" sheetId=\"1\" r:id=\"rId1\"/>\n" +
                    "  </sheets>\n" +
                    "</workbook>");

            // xl/worksheets/sheet1.xml
            StringBuilder sheetXml = new StringBuilder();
            sheetXml.append("<?xml version=\"1.0\" encoding=\"UTF-8\" standalone=\"yes\"?>\n");
            sheetXml.append("<worksheet xmlns=\"http://schemas.openxmlformats.org/spreadsheetml/2006/main\">\n");
            sheetXml.append("  <sheetData>\n");
            sheetXml.append("    <row r=\"1\">\n");
            sheetXml.append("      <c r=\"A1\" t=\"inlineStr\"><is><t>Dimension</t></is></c>\n");
            sheetXml.append("      <c r=\"B1\" t=\"inlineStr\"><is><t>Transactions</t></is></c>\n");
            sheetXml.append("      <c r=\"C1\" t=\"inlineStr\"><is><t>Gross Revenue</t></is></c>\n");
            sheetXml.append("      <c r=\"D1\" t=\"inlineStr\"><is><t>Net Revenue</t></is></c>\n");
            sheetXml.append("      <c r=\"E1\" t=\"inlineStr\"><is><t>Revenue %</t></is></c>\n");
            sheetXml.append("    </row>\n");

            int rowIdx = 2;
            if (report.getBreakdown() != null) {
                for (var b : report.getBreakdown()) {
                    sheetXml.append(String.format(Locale.US,
                            "    <row r=\"%d\">\n" +
                            "      <c r=\"A%d\" t=\"inlineStr\"><is><t>%s</t></is></c>\n" +
                            "      <c r=\"B%d\"><v>%d</v></c>\n" +
                            "      <c r=\"C%d\"><v>%.2f</v></c>\n" +
                            "      <c r=\"D%d\"><v>%.2f</v></c>\n" +
                            "      <c r=\"E%d\"><v>%.2f</v></c>\n" +
                            "    </row>\n",
                            rowIdx,
                            rowIdx, escapeXml(b.getLabel()),
                            rowIdx, b.getTransactionCount(),
                            rowIdx, b.getGrossRevenue(),
                            rowIdx, b.getNetRevenue(),
                            rowIdx, b.getRevenuePercentage()));
                    rowIdx++;
                }
            }

            sheetXml.append("  </sheetData>\n");
            sheetXml.append("</worksheet>");
            addZipEntry(zos, "xl/worksheets/sheet1.xml", sheetXml.toString());
        }
        return baos.toByteArray();
    }

    private void addZipEntry(ZipOutputStream zos, String path, String content) throws IOException {
        ZipEntry entry = new ZipEntry(path);
        zos.putNextEntry(entry);
        zos.write(content.getBytes(StandardCharsets.UTF_8));
        zos.closeEntry();
    }

    private String escapeXml(String text) {
        if (text == null) return "";
        return text.replace("&", "&amp;")
                .replace("<", "&lt;")
                .replace(">", "&gt;")
                .replace("\"", "&quot;")
                .replace("'", "&apos;");
    }

    private byte[] renderPdf(String title, OperationalReportResponse report) throws IOException {
        try (PDDocument doc = new PDDocument()) {
            PDPage page = new PDPage(PDRectangle.A4);
            doc.addPage(page);

            PDType0Font font = null;
            try (InputStream fontStream = new ClassPathResource("fonts/NotoSans-Regular.ttf").getInputStream()) {
                font = PDType0Font.load(doc, fontStream, true);
            } catch (Exception e) {
                log.warn("Could not load custom font, using default", e);
            }

            try (PDPageContentStream stream = new PDPageContentStream(doc, page)) {
                stream.beginText();
                if (font != null) {
                    stream.setFont(font, 14);
                }
                stream.newLineAtOffset(50, 750);
                String safeTitle = title != null ? title.replaceAll("[\\r\\n]", " ") : "Operational Report";
                stream.showText("Report: " + safeTitle);
                stream.newLineAtOffset(0, -20);
                if (font != null) {
                    stream.setFont(font, 10);
                }
                stream.showText(String.format(Locale.US, "Total Gross Revenue: $%.2f | Total Transactions: %d",
                        report.getTotalGrossRevenue() != null ? report.getTotalGrossRevenue() : BigDecimal.ZERO,
                        report.getTotalTransactionCount() != null ? report.getTotalTransactionCount() : 0));
                stream.newLineAtOffset(0, -25);
                stream.showText("Dimension | Transactions | Gross Revenue | Net Revenue | Revenue %");
                stream.newLineAtOffset(0, -15);
                if (report.getBreakdown() != null) {
                    for (var b : report.getBreakdown()) {
                        stream.showText(String.format(Locale.US, "%s | %d | $%.2f | $%.2f | %.2f%%",
                                b.getLabel(), b.getTransactionCount(), b.getGrossRevenue(), b.getNetRevenue(), b.getRevenuePercentage()));
                        stream.newLineAtOffset(0, -15);
                    }
                }
                stream.endText();
            }

            ByteArrayOutputStream baos = new ByteArrayOutputStream();
            doc.save(baos);
            return baos.toByteArray();
        }
    }

    @Transactional(readOnly = true)
    public ReportExportJob getExportJobById(String jobId) {
        return exportJobRepository.findByJobId(jobId)
                .orElseThrow(() -> new ResourceNotFoundException("Không tìm thấy tác vụ xuất báo cáo: " + jobId));
    }

    @Transactional(readOnly = true)
    public Page<ReportExportJob> getExportJobs(Pageable pageable) {
        return exportJobRepository.findAllByOrderByCreatedAtDescIdDesc(pageable);
    }
}
