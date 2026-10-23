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
import com.cruise.pos.enums.PaymentMethod;
import com.cruise.pos.enums.ReportDimension;
import com.cruise.pos.enums.TransactionStatus;
import com.cruise.pos.repository.PosOutletRepository;
import com.cruise.pos.repository.PosTransactionRepository;
import com.cruise.pos.repository.ReportExportJobRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@DisplayName("OperationalReportService Unit Tests")
class OperationalReportServiceTest {

    @Mock
    private PosTransactionRepository transactionRepository;

    @Mock
    private ReportExportJobRepository exportJobRepository;

    @Mock
    private PosOutletRepository outletRepository;

    @InjectMocks
    private OperationalReportService reportService;

    private PosTransaction tx1;
    private PosTransaction tx2;

    @BeforeEach
    void setUp() {
        PosTransactionItem item1 = PosTransactionItem.builder()
                .category("BEVERAGE")
                .itemName("Cocktail")
                .quantity(2)
                .totalPrice(BigDecimal.valueOf(150.00))
                .build();

        tx1 = PosTransaction.builder()
                .stationId("BAR-DECK-9")
                .totalAmount(BigDecimal.valueOf(150.00))
                .discount(BigDecimal.valueOf(10.00))
                .tax(BigDecimal.valueOf(14.00))
                .paymentMethod(PaymentMethod.CABIN_FOLIO)
                .status(TransactionStatus.COMPLETED)
                .items(List.of(item1))
                .build();
        tx1.setCreatedAt(Instant.parse("2026-10-20T10:00:00Z"));

        PosTransactionItem item2 = PosTransactionItem.builder()
                .category("RETAIL")
                .itemName("Watch")
                .quantity(1)
                .totalPrice(BigDecimal.valueOf(250.00))
                .build();

        tx2 = PosTransaction.builder()
                .stationId("RETAIL-PROMENADE")
                .totalAmount(BigDecimal.valueOf(250.00))
                .discount(BigDecimal.ZERO)
                .tax(BigDecimal.valueOf(20.00))
                .paymentMethod(PaymentMethod.CREDIT_CARD)
                .status(TransactionStatus.COMPLETED)
                .items(List.of(item2))
                .build();
        tx2.setCreatedAt(Instant.parse("2026-10-21T15:00:00Z"));
    }

    @Test
    @DisplayName("generateOperationalReport aggregates revenues and groups by OUTLET dimension")
    void testGenerateOperationalReportOutletDimension() {
        when(transactionRepository.findTransactionsForReport(isNull(), eq(TransactionStatus.COMPLETED), isNull(), isNull(), isNull()))
                .thenReturn(List.of(tx1, tx2));

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
    @DisplayName("generateOperationalReport groups by DATE_RANGE and filters by outletId")
    void testGenerateOperationalReportDateRangeDimensionAndOutletFilter() {
        PosOutlet outlet = PosOutlet.builder().code("BAR-DECK-9").name("Pool Bar").build();
        when(outletRepository.findById(1L)).thenReturn(Optional.of(outlet));
        when(transactionRepository.findTransactionsForReport(eq("BAR-DECK-9"), eq(TransactionStatus.COMPLETED), isNull(), isNull(), isNull()))
                .thenReturn(List.of(tx1));

        OperationalReportQuery query = OperationalReportQuery.builder()
                .outletId(1L)
                .dimension(ReportDimension.DATE_RANGE)
                .build();

        OperationalReportResponse response = reportService.generateOperationalReport(query);

        assertThat(response.getTotalGrossRevenue()).isEqualByComparingTo(BigDecimal.valueOf(150.00));
        assertThat(response.getBreakdown()).hasSize(1);
        assertThat(response.getBreakdown().get(0).getLabel()).isEqualTo("2026-10-20");
    }

    @Test
    @DisplayName("generateOperationalReport groups by CATEGORY and filters by category name")
    void testGenerateOperationalReportCategoryDimension() {
        when(transactionRepository.findTransactionsForReport(isNull(), eq(TransactionStatus.COMPLETED), isNull(), isNull(), isNull()))
                .thenReturn(List.of(tx1, tx2));

        OperationalReportQuery query = OperationalReportQuery.builder()
                .category("BEVERAGE")
                .dimension(ReportDimension.CATEGORY)
                .build();

        OperationalReportResponse response = reportService.generateOperationalReport(query);

        assertThat(response.getTotalGrossRevenue()).isEqualByComparingTo(BigDecimal.valueOf(150.00));
        assertThat(response.getBreakdown()).hasSize(1);
        assertThat(response.getBreakdown().get(0).getLabel()).isEqualTo("BEVERAGE");
    }

    @Test
    @DisplayName("generateOperationalReport groups by CATEGORY with multiple items in same category and counts transaction once")
    void testGenerateOperationalReportCategoryDimensionMultipleItems() {
        PosTransactionItem itemA = PosTransactionItem.builder()
                .category("BEVERAGE")
                .itemName("Cocktail")
                .quantity(1)
                .totalPrice(BigDecimal.valueOf(50.00))
                .build();
        PosTransactionItem itemB = PosTransactionItem.builder()
                .category("BEVERAGE")
                .itemName("Beer")
                .quantity(2)
                .totalPrice(BigDecimal.valueOf(70.00))
                .build();

        PosTransaction multiItemTx = PosTransaction.builder()
                .stationId("BAR-DECK-9")
                .totalAmount(BigDecimal.valueOf(120.00))
                .status(TransactionStatus.COMPLETED)
                .items(List.of(itemA, itemB))
                .build();
        multiItemTx.setId(99L);

        when(transactionRepository.findTransactionsForReport(isNull(), eq(TransactionStatus.COMPLETED), isNull(), isNull(), isNull()))
                .thenReturn(List.of(multiItemTx));

        OperationalReportQuery query = OperationalReportQuery.builder()
                .dimension(ReportDimension.CATEGORY)
                .build();

        OperationalReportResponse response = reportService.generateOperationalReport(query);

        assertThat(response.getBreakdown()).hasSize(1);
        assertThat(response.getBreakdown().get(0).getLabel()).isEqualTo("BEVERAGE");
        assertThat(response.getBreakdown().get(0).getTransactionCount()).isEqualTo(1L);
        assertThat(response.getBreakdown().get(0).getGrossRevenue()).isEqualByComparingTo(BigDecimal.valueOf(120.00));
    }

    @Test
    @DisplayName("createExportJob generates CSV artifact, computes actual file size, and marks COMPLETED")
    void testCreateExportJobCsv() {
        when(transactionRepository.findTransactionsForReport(any(), any(), any(), any(), any()))
                .thenReturn(List.of(tx1, tx2));

        CreateExportJobRequest request = CreateExportJobRequest.builder()
                .format(ExportFormat.CSV)
                .reportTitle("Daily Revenue Report")
                .build();

        ReportExportJob job = reportService.createExportJob(request);

        assertThat(job).isNotNull();
        assertThat(job.getStatus()).isEqualTo(ExportJobStatus.COMPLETED);
        assertThat(job.getDownloadUrl()).contains(".csv");
        assertThat(job.getReportTitle()).isEqualTo("Daily Revenue Report");
        assertThat(job.getFileSizeBytes()).isGreaterThan(0L);
        assertThat(job.getFileData()).isNotNull().isNotEmpty();

        verify(exportJobRepository, times(2)).save(any(ReportExportJob.class));
    }

    @Test
    @DisplayName("createExportJob generates EXCEL artifact successfully")
    void testCreateExportJobExcel() {
        when(transactionRepository.findTransactionsForReport(any(), any(), any(), any(), any()))
                .thenReturn(List.of(tx1));

        CreateExportJobRequest request = CreateExportJobRequest.builder()
                .format(ExportFormat.EXCEL)
                .reportTitle("Excel Revenue Report")
                .build();

        ReportExportJob job = reportService.createExportJob(request);

        assertThat(job.getStatus()).isEqualTo(ExportJobStatus.COMPLETED);
        assertThat(job.getDownloadUrl()).contains(".xlsx");
        assertThat(job.getFileData()).isNotNull().isNotEmpty();
    }

    @Test
    @DisplayName("createExportJob generates PDF artifact successfully")
    void testCreateExportJobPdf() {
        when(transactionRepository.findTransactionsForReport(any(), any(), any(), any(), any()))
                .thenReturn(List.of(tx1));

        CreateExportJobRequest request = CreateExportJobRequest.builder()
                .format(ExportFormat.PDF)
                .reportTitle("PDF Audit Report")
                .build();

        ReportExportJob job = reportService.createExportJob(request);

        assertThat(job.getStatus()).isEqualTo(ExportJobStatus.COMPLETED);
        assertThat(job.getDownloadUrl()).contains(".pdf");
        assertThat(job.getFileData()).isNotNull().isNotEmpty();
    }

    @Test
    @DisplayName("getExportJobs delegates to repository ordering by createdAt and id descending")
    void testGetExportJobs() {
        PageRequest pageable = PageRequest.of(0, 10);
        when(exportJobRepository.findAllByOrderByCreatedAtDescIdDesc(pageable))
                .thenReturn(new PageImpl<>(List.of()));

        Page<ReportExportJob> page = reportService.getExportJobs(pageable);
        assertThat(page).isNotNull();
        verify(exportJobRepository).findAllByOrderByCreatedAtDescIdDesc(pageable);
    }
}
