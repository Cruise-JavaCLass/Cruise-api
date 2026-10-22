package com.cruise.pos.service;

import com.cruise.pos.dto.CreateExportJobRequest;
import com.cruise.pos.dto.OperationalReportQuery;
import com.cruise.pos.dto.OperationalReportResponse;
import com.cruise.pos.entity.PosTransaction;
import com.cruise.pos.entity.ReportExportJob;
import com.cruise.pos.enums.ExportFormat;
import com.cruise.pos.enums.ExportJobStatus;
import com.cruise.pos.enums.ReportDimension;
import com.cruise.pos.enums.TransactionStatus;
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
import java.time.Instant;
import java.util.*;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Slf4j
public class OperationalReportService {

    private final PosTransactionRepository transactionRepository;
    private final ReportExportJobRepository exportJobRepository;

    @Transactional(readOnly = true)
    public OperationalReportResponse generateOperationalReport(OperationalReportQuery query) {
        List<PosTransaction> transactions = transactionRepository.findAll().stream()
                .filter(t -> t.getStatus() == TransactionStatus.COMPLETED)
                .filter(t -> query.getPaymentMethod() == null || query.getPaymentMethod() == t.getPaymentMethod())
                .filter(t -> query.getStartDate() == null || (t.getCreatedAt() != null && !t.getCreatedAt().isBefore(query.getStartDate())))
                .filter(t -> query.getEndDate() == null || (t.getCreatedAt() != null && !t.getCreatedAt().isAfter(query.getEndDate())))
                .collect(Collectors.toList());

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
            double pct = totalGross.compareTo(BigDecimal.ZERO) > 0
                    ? gross.multiply(BigDecimal.valueOf(100)).divide(totalGross, 1, RoundingMode.HALF_UP).doubleValue()
                    : 0.0;

            result.add(OperationalReportResponse.DimensionBreakdownDto.builder()
                    .key(entry.getKey())
                    .label(entry.getKey())
                    .transactionCount((long) txs.size())
                    .grossRevenue(gross)
                    .netRevenue(net)
                    .revenuePercentage(pct)
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
                .status(ExportJobStatus.COMPLETED)
                .reportTitle(title)
                .downloadUrl(downloadUrl)
                .fileSizeBytes(24576L)
                .completedAt(Instant.now())
                .build();

        exportJobRepository.save(job);
        log.info("Created export job: {} format: {} title: {}", jobId, request.getFormat(), title);
        return job;
    }

    @Transactional(readOnly = true)
    public Page<ReportExportJob> getExportJobs(Pageable pageable) {
        return exportJobRepository.findAllByOrderByCreatedAtDesc(pageable);
    }
}
