package com.cruise.pos.service;

import com.cruise.pos.dto.CreateExportJobRequest;
import com.cruise.pos.dto.OperationalReportQuery;
import com.cruise.pos.dto.OperationalReportResponse;
import com.cruise.pos.entity.PosOutlet;
import com.cruise.pos.entity.PosTransaction;
import com.cruise.pos.entity.ReportExportJob;
import com.cruise.pos.enums.ExportFormat;
import com.cruise.pos.enums.ExportJobStatus;
import com.cruise.pos.enums.ReportDimension;
import com.cruise.pos.enums.TransactionStatus;
import com.cruise.pos.repository.PosOutletRepository;
import com.cruise.pos.repository.PosTransactionRepository;
import com.cruise.pos.repository.ReportExportJobRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.*;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Slf4j
public class OperationalReportService {

    private final PosTransactionRepository transactionRepository;
    private final ReportExportJobRepository exportJobRepository;
    private final PosOutletRepository outletRepository;

    @Transactional(readOnly = true)
    public OperationalReportResponse generateOperationalReport(OperationalReportQuery query) {
        String stationFilter = null;
        if (query.getOutletId() != null) {
            Optional<PosOutlet> outletOpt = outletRepository.findById(query.getOutletId());
            stationFilter = outletOpt.map(PosOutlet::getCode).orElseGet(() -> String.valueOf(query.getOutletId()));
        }

        List<PosTransaction> transactions = transactionRepository.findTransactionsForReport(
                stationFilter,
                TransactionStatus.COMPLETED,
                query.getPaymentMethod(),
                query.getStartDate(),
                query.getEndDate()
        );

        if (query.getCategory() != null && !query.getCategory().isBlank()) {
            transactions = transactions.stream()
                    .filter(t -> t.getItems() != null && t.getItems().stream()
                            .anyMatch(i -> query.getCategory().equalsIgnoreCase(i.getCategory())))
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
        ReportDimension dim = query.getDimension() != null ? query.getDimension() : ReportDimension.OUTLET;
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
        } else if (dimension == ReportDimension.CATEGORY) {
            grouped = new HashMap<>();
            for (PosTransaction t : transactions) {
                if (t.getItems() != null && !t.getItems().isEmpty()) {
                    for (var item : t.getItems()) {
                        String cat = item.getCategory() != null ? item.getCategory() : "Uncategorized";
                        grouped.computeIfAbsent(cat, k -> new ArrayList<>()).add(t);
                    }
                } else {
                    grouped.computeIfAbsent("Uncategorized", k -> new ArrayList<>()).add(t);
                }
            }
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

        result.sort((a, b) -> b.getGrossRevenue().compareTo(a.getGrossRevenue()));
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
                StringBuilder sb = new StringBuilder("Dimension,Transactions,Gross Revenue,Net Revenue,Revenue %\n");
                for (var b : report.getBreakdown()) {
                    sb.append(String.format(Locale.US, "\"%s\",%d,%.2f,%.2f,%.2f%%\n",
                            b.getLabel(), b.getTransactionCount(), b.getGrossRevenue(), b.getNetRevenue(), b.getRevenuePercentage()));
                }
                fileBytes = sb.toString().getBytes(StandardCharsets.UTF_8);
            } else if (request.getFormat() == ExportFormat.EXCEL) {
                StringBuilder sb = new StringBuilder("Dimension\tTransactions\tGross Revenue\tNet Revenue\tRevenue %\n");
                for (var b : report.getBreakdown()) {
                    sb.append(String.format(Locale.US, "%s\t%d\t%.2f\t%.2f\t%.2f%%\n",
                            b.getLabel(), b.getTransactionCount(), b.getGrossRevenue(), b.getNetRevenue(), b.getRevenuePercentage()));
                }
                fileBytes = sb.toString().getBytes(StandardCharsets.UTF_8);
            } else {
                String pdf = "%PDF-1.4\n1 0 obj\n<< /Title (" + title + ") >>\nendobj\ntrailer\n<< /Root 1 0 R >>\n%%EOF";
                fileBytes = pdf.getBytes(StandardCharsets.UTF_8);
            }

            job.setFileSizeBytes((long) fileBytes.length);
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

    @Transactional(readOnly = true)
    public Page<ReportExportJob> getExportJobs(Pageable pageable) {
        return exportJobRepository.findAllByOrderByCreatedAtDescIdDesc(pageable);
    }
}
