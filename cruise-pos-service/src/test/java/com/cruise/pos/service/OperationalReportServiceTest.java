package com.cruise.pos.service;

import com.cruise.pos.dto.CreateExportJobRequest;
import com.cruise.pos.dto.OperationalReportQuery;
import com.cruise.pos.dto.OperationalReportResponse;
import com.cruise.pos.entity.PosTransaction;
import com.cruise.pos.entity.ReportExportJob;
import com.cruise.pos.enums.ExportFormat;
import com.cruise.pos.enums.ExportJobStatus;
import com.cruise.pos.enums.PaymentMethod;
import com.cruise.pos.enums.ReportDimension;
import com.cruise.pos.enums.TransactionStatus;
import com.cruise.pos.repository.PosTransactionRepository;
import com.cruise.pos.repository.ReportExportJobRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class OperationalReportServiceTest {

    @Mock
    private PosTransactionRepository transactionRepository;

    @Mock
    private ReportExportJobRepository exportJobRepository;

    @InjectMocks
    private OperationalReportService reportService;

    private PosTransaction tx1;
    private PosTransaction tx2;

    @BeforeEach
    void setUp() {
        tx1 = PosTransaction.builder()
                .stationId("BAR-DECK-9")
                .totalAmount(BigDecimal.valueOf(150.00))
                .discount(BigDecimal.valueOf(10.00))
                .tax(BigDecimal.valueOf(14.00))
                .paymentMethod(PaymentMethod.CABIN_FOLIO)
                .status(TransactionStatus.COMPLETED)
                .build();
        tx1.setCreatedAt(Instant.now());

        tx2 = PosTransaction.builder()
                .stationId("RETAIL-PROMENADE")
                .totalAmount(BigDecimal.valueOf(250.00))
                .discount(BigDecimal.ZERO)
                .tax(BigDecimal.valueOf(20.00))
                .paymentMethod(PaymentMethod.CREDIT_CARD)
                .status(TransactionStatus.COMPLETED)
                .build();
        tx2.setCreatedAt(Instant.now());
    }

    @Test
    @DisplayName("generateOperationalReport aggregates revenues, discounts, taxes and average ticket accurately")
    void testGenerateOperationalReport() {
        when(transactionRepository.findAll()).thenReturn(List.of(tx1, tx2));

        OperationalReportQuery query = OperationalReportQuery.builder()
                .dimension(ReportDimension.OUTLET)
                .build();

        OperationalReportResponse response = reportService.generateOperationalReport(query);

        assertThat(response).isNotNull();
        assertThat(response.getTotalGrossRevenue()).isEqualByComparingTo(BigDecimal.valueOf(400.00));
        assertThat(response.getTotalDiscountAmount()).isEqualByComparingTo(BigDecimal.valueOf(10.00));
        assertThat(response.getTotalNetRevenue()).isEqualByComparingTo(BigDecimal.valueOf(390.00));
        assertThat(response.getTotalTaxAmount()).isEqualByComparingTo(BigDecimal.valueOf(34.00));
        assertThat(response.getTotalTransactionCount()).isEqualTo(2L);
        assertThat(response.getAverageTicketSize()).isEqualByComparingTo(BigDecimal.valueOf(200.00));

        assertThat(response.getRevenueByPaymentMethod()).containsEntry("CABIN_FOLIO", BigDecimal.valueOf(150.00));
        assertThat(response.getRevenueByPaymentMethod()).containsEntry("CREDIT_CARD", BigDecimal.valueOf(250.00));

        assertThat(response.getBreakdown()).hasSize(2);
    }

    @Test
    @DisplayName("createExportJob initializes job with download URL and completed status")
    void testCreateExportJob() {
        CreateExportJobRequest request = CreateExportJobRequest.builder()
                .format(ExportFormat.CSV)
                .reportTitle("Daily Revenue Report")
                .build();

        ReportExportJob job = reportService.createExportJob(request);

        assertThat(job).isNotNull();
        assertThat(job.getStatus()).isEqualTo(ExportJobStatus.COMPLETED);
        assertThat(job.getDownloadUrl()).contains(".csv");
        assertThat(job.getReportTitle()).isEqualTo("Daily Revenue Report");

        verify(exportJobRepository).save(any(ReportExportJob.class));
    }
}
