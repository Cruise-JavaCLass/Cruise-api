package com.cruise.pos.service;

import com.cruise.pos.dto.*;
import com.cruise.pos.entity.PosAuditLog;
import com.cruise.pos.entity.VoyageFinancialClosure;
import com.cruise.pos.enums.VoyageFinancialStatus;
import com.cruise.pos.exception.BusinessRuleException;
import com.cruise.pos.exception.DuplicateResourceException;
import com.cruise.pos.repository.PosAuditLogRepository;
import com.cruise.pos.repository.VoyageFinancialClosureRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class VoyageAnalyticsServiceTest {

    @Mock
    private VoyageFinancialClosureRepository closureRepository;

    @Mock
    private PosAuditLogRepository auditLogRepository;

    @InjectMocks
    private VoyageAnalyticsService voyageAnalyticsService;

    private Instant startDate;
    private Instant endDate;

    @BeforeEach
    void setUp() {
        startDate = Instant.now().minus(7, ChronoUnit.DAYS);
        endDate = Instant.now();
    }

    @Test
    @DisplayName("initiateVoyageClosure: correctly computes RevPCD, occupancy, and budget variance")
    void testInitiateVoyageClosureFormulas() {
        InitiateVoyageClosureRequest req = new InitiateVoyageClosureRequest(
                "VOY-2026-MED-01",
                "SHIP-CELEBRATION",
                "Mediterranean Discovery 7-Day",
                startDate,
                endDate,
                2000, // Total Capacity
                1800, // Actual Attendance (90% occupancy)
                BigDecimal.valueOf(1000000.00), // Budgeted Gross
                BigDecimal.valueOf(450000.00), // Bars
                BigDecimal.valueOf(320000.00), // Dining
                BigDecimal.valueOf(250000.00), // Excursions
                BigDecimal.valueOf(110000.00), // Spa
                BigDecimal.valueOf(70000.00),  // Retail
                BigDecimal.valueOf(50000.00),  // Discounts/OBC
                "Successful Mediterranean voyage"
        );

        when(closureRepository.findByVoyageNumber("VOY-2026-MED-01")).thenReturn(Optional.empty());
        when(closureRepository.save(any(VoyageFinancialClosure.class))).thenAnswer(inv -> {
            VoyageFinancialClosure v = inv.getArgument(0);
            v.setId(1L);
            return v;
        });

        VoyageFinancialClosureDto dto = voyageAnalyticsService.initiateVoyageClosure(req, "EXECUTIVE_CONTROLLER");

        assertThat(dto).isNotNull();
        // Gross = 450k + 320k + 250k + 110k + 70k = 1,200,000.00
        assertThat(dto.actualGrossRevenue()).isEqualByComparingTo(BigDecimal.valueOf(1200000.00));
        // Net = 1.2M - 50k = 1,150,000.00
        assertThat(dto.actualNetRevenue()).isEqualByComparingTo(BigDecimal.valueOf(1150000.00));
        // Occupancy = (1800 / 2000) * 100 = 90.00%
        assertThat(dto.occupancyPercentage()).isEqualByComparingTo(BigDecimal.valueOf(90.00));
        // APCD = 1800 passengers * 7 days = 12,600
        // RevPCD = 1,200,000 / 12,600 = 95.24
        assertThat(dto.revenuePerPassengerCruiseDay()).isEqualByComparingTo(BigDecimal.valueOf(95.24));
        // Budget Variance = ((1.2M - 1.0M) / 1.0M) * 100 = +20.00%
        assertThat(dto.budgetVariancePercentage()).isEqualByComparingTo(BigDecimal.valueOf(20.00));
        assertThat(dto.status()).isEqualTo(VoyageFinancialStatus.CLOSURE_IN_PROGRESS);

        verify(auditLogRepository).save(any(PosAuditLog.class));
    }

    @Test
    @DisplayName("initiateVoyageClosure: throws DuplicateResourceException if already exists")
    void testInitiateVoyageClosureDuplicateThrows() {
        InitiateVoyageClosureRequest req = new InitiateVoyageClosureRequest(
                "VOY-DUPLICATE",
                "SHIP-1",
                "Caribbean Cruise",
                startDate,
                endDate,
                1000,
                800,
                BigDecimal.valueOf(500000.00),
                null, null, null, null, null, null, null
        );

        when(closureRepository.findByVoyageNumber("VOY-DUPLICATE")).thenReturn(Optional.of(new VoyageFinancialClosure()));

        assertThatThrownBy(() -> voyageAnalyticsService.initiateVoyageClosure(req, "ADMIN"))
                .isInstanceOf(DuplicateResourceException.class)
                .hasMessageContaining("already exists");
    }

    @Test
    @DisplayName("finalizeVoyageClosure: marks status as FINANCIALLY_CLOSED and records closing timestamp")
    void testFinalizeVoyageClosureSuccess() {
        VoyageFinancialClosure existing = VoyageFinancialClosure.builder()
                .voyageNumber("VOY-2026-MED-01")
                .status(VoyageFinancialStatus.CLOSURE_IN_PROGRESS)
                .actualNetRevenue(BigDecimal.valueOf(1150000.00))
                .build();
        existing.setId(1L);

        when(closureRepository.findByVoyageNumber("VOY-2026-MED-01")).thenReturn(Optional.of(existing));
        when(closureRepository.save(any(VoyageFinancialClosure.class))).thenAnswer(inv -> inv.getArgument(0));

        FinalizeVoyageClosureRequest req = new FinalizeVoyageClosureRequest("All bank settlements reconciled and verified");
        VoyageFinancialClosureDto dto = voyageAnalyticsService.finalizeVoyageClosure("VOY-2026-MED-01", req, "DIRECTOR_FINANCE");

        assertThat(dto.status()).isEqualTo(VoyageFinancialStatus.FINANCIALLY_CLOSED);
        assertThat(dto.closedBy()).isEqualTo("DIRECTOR_FINANCE");
        assertThat(dto.closedAt()).isNotNull();
        assertThat(dto.closureAuditNotes()).contains("verified");
    }

    @Test
    @DisplayName("finalizeVoyageClosure: throws exception if already finalized")
    void testFinalizeVoyageClosureAlreadyClosedThrows() {
        VoyageFinancialClosure existing = VoyageFinancialClosure.builder()
                .voyageNumber("VOY-ALREADY-CLOSED")
                .status(VoyageFinancialStatus.FINANCIALLY_CLOSED)
                .build();

        when(closureRepository.findByVoyageNumber("VOY-ALREADY-CLOSED")).thenReturn(Optional.of(existing));

        assertThatThrownBy(() -> voyageAnalyticsService.finalizeVoyageClosure("VOY-ALREADY-CLOSED", new FinalizeVoyageClosureRequest("audit note"), "USER"))
                .isInstanceOf(BusinessRuleException.class)
                .hasMessageContaining("already financially closed");
    }

    @Test
    @DisplayName("initiateVoyageClosure: throws exception when voyageEndDate is before or equal to voyageStartDate")
    void testInitiateVoyageClosureInvalidDatesThrows() {
        InitiateVoyageClosureRequest req = new InitiateVoyageClosureRequest(
                "VOY-INVALID-DATE", "SHIP-1", "Itinerary",
                endDate, startDate, // End date is before start date
                1000, 800, BigDecimal.valueOf(500000.00),
                null, null, null, null, null, null, null
        );

        when(closureRepository.findByVoyageNumber("VOY-INVALID-DATE")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> voyageAnalyticsService.initiateVoyageClosure(req, "ADMIN"))
                .isInstanceOf(BusinessRuleException.class)
                .hasMessageContaining("voyageEndDate must be after voyageStartDate");
    }

    @Test
    @DisplayName("initiateVoyageClosure: throws exception when attendance exceeds total capacity")
    void testInitiateVoyageClosureOverCapacityThrows() {
        InitiateVoyageClosureRequest req = new InitiateVoyageClosureRequest(
                "VOY-OVER-CAPACITY", "SHIP-1", "Itinerary",
                startDate, endDate,
                1000, 1200, // Attendance > Capacity
                BigDecimal.valueOf(500000.00),
                null, null, null, null, null, null, null
        );

        when(closureRepository.findByVoyageNumber("VOY-OVER-CAPACITY")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> voyageAnalyticsService.initiateVoyageClosure(req, "ADMIN"))
                .isInstanceOf(BusinessRuleException.class)
                .hasMessageContaining("actualPassengerAttendance cannot exceed totalPassengerCapacity");
    }

    @Test
    @DisplayName("initiateVoyageClosure: handles zero passenger attendance with zero RevPCD")
    void testInitiateVoyageClosureZeroAttendance() {
        InitiateVoyageClosureRequest req = new InitiateVoyageClosureRequest(
                "VOY-ZERO-ATTENDANCE", "SHIP-1", "Itinerary",
                startDate, endDate,
                1000, 0, // Zero attendance
                BigDecimal.valueOf(500000.00),
                null, null, null, null, null, null, null
        );

        when(closureRepository.findByVoyageNumber("VOY-ZERO-ATTENDANCE")).thenReturn(Optional.empty());
        when(closureRepository.save(any(VoyageFinancialClosure.class))).thenAnswer(inv -> inv.getArgument(0));

        VoyageFinancialClosureDto dto = voyageAnalyticsService.initiateVoyageClosure(req, "ADMIN");

        assertThat(dto.actualPassengerAttendance()).isEqualTo(0);
        assertThat(dto.passengerCruiseDays()).isEqualTo(0);
        assertThat(dto.revenuePerPassengerCruiseDay()).isEqualByComparingTo(BigDecimal.ZERO);
    }

    @Test
    @DisplayName("generateExecutivePdfBooklet: generates valid PDFBox document")
    void testGenerateExecutivePdfBooklet() {
        VoyageFinancialClosure closure = VoyageFinancialClosure.builder()
                .voyageNumber("VOY-PDF-01")
                .shipId("SHIP-1")
                .itineraryName("Caribbean Tour")
                .voyageDurationDays(7)
                .totalPassengerCapacity(2000)
                .actualPassengerAttendance(1800)
                .occupancyPercentage(BigDecimal.valueOf(90.00))
                .revenuePerPassengerCruiseDay(BigDecimal.valueOf(95.24))
                .budgetedGrossRevenue(BigDecimal.valueOf(1000000.00))
                .actualGrossRevenue(BigDecimal.valueOf(1200000.00))
                .actualNetRevenue(BigDecimal.valueOf(1150000.00))
                .budgetVariancePercentage(BigDecimal.valueOf(20.00))
                .profitabilityIndex(BigDecimal.valueOf(1.04))
                .revenueBeverageBars(BigDecimal.valueOf(450000.00))
                .revenueSpecialtyDining(BigDecimal.valueOf(320000.00))
                .revenueShoreExcursions(BigDecimal.valueOf(250000.00))
                .revenueSpaWellness(BigDecimal.valueOf(110000.00))
                .revenueRetailBoutiques(BigDecimal.valueOf(70000.00))
                .status(VoyageFinancialStatus.FINANCIALLY_CLOSED)
                .closedBy("DIRECTOR")
                .build();

        when(closureRepository.findByVoyageNumber("VOY-PDF-01")).thenReturn(Optional.of(closure));

        byte[] pdfBytes = voyageAnalyticsService.generateExecutivePdfBooklet("VOY-PDF-01");

        assertThat(pdfBytes).isNotEmpty();
        String header = new String(pdfBytes, 0, Math.min(pdfBytes.length, 10));
        assertThat(header).startsWith("%PDF-");
    }

    @Test
    @DisplayName("dispatchExecutiveEmail: records audit log and confirms email queued")
    void testDispatchExecutiveEmail() {
        VoyageFinancialClosure closure = VoyageFinancialClosure.builder()
                .voyageNumber("VOY-EMAIL-01")
                .build();
        when(closureRepository.findByVoyageNumber("VOY-EMAIL-01")).thenReturn(Optional.of(closure));

        DispatchExecutiveSummaryEmailRequest request = new DispatchExecutiveSummaryEmailRequest(
                "executive@cruise.com",
                "Executive briefing note",
                java.util.List.of("controller@cruise.com")
        );

        voyageAnalyticsService.dispatchExecutiveEmail("VOY-EMAIL-01", request, "EXECUTIVE_CONTROLLER");

        verify(auditLogRepository).save(any(PosAuditLog.class));
    }

    @Test
    @DisplayName("getExecutiveSummary: correctly averages RevPCD and Occupancy across closed voyages")
    void testGetExecutiveSummary() {
        VoyageFinancialClosure v1 = VoyageFinancialClosure.builder()
                .voyageNumber("VOY-1")
                .shipId("SHIP-1")
                .revenuePerPassengerCruiseDay(BigDecimal.valueOf(100.00))
                .occupancyPercentage(BigDecimal.valueOf(90.00))
                .actualGrossRevenue(BigDecimal.valueOf(500000.00))
                .status(VoyageFinancialStatus.FINANCIALLY_CLOSED)
                .build();

        VoyageFinancialClosure v2 = VoyageFinancialClosure.builder()
                .voyageNumber("VOY-2")
                .shipId("SHIP-1")
                .revenuePerPassengerCruiseDay(BigDecimal.valueOf(120.00))
                .occupancyPercentage(BigDecimal.valueOf(95.00))
                .actualGrossRevenue(BigDecimal.valueOf(600000.00))
                .status(VoyageFinancialStatus.AUDITED)
                .build();

        when(closureRepository.findRecentClosedVoyages(
                java.util.List.of(VoyageFinancialStatus.FINANCIALLY_CLOSED, VoyageFinancialStatus.AUDITED),
                org.springframework.data.domain.PageRequest.of(0, 10)
        )).thenReturn(java.util.List.of(v1, v2));

        ExecutiveVoyageAnalyticsSummary summary = voyageAnalyticsService.getExecutiveSummary();

        assertThat(summary).isNotNull();
        assertThat(summary.fleetAverageRevPCD()).isEqualByComparingTo(BigDecimal.valueOf(110.00));
        assertThat(summary.fleetAverageOccupancy()).isEqualByComparingTo(BigDecimal.valueOf(92.50));
        assertThat(summary.fleetTotalGrossRevenue()).isEqualByComparingTo(BigDecimal.valueOf(1100000.00));
    }

    @Test
    @DisplayName("getExecutiveSummary: returns zeros when no closed voyages exist")
    void testGetExecutiveSummaryEmpty() {
        when(closureRepository.findRecentClosedVoyages(
                java.util.List.of(VoyageFinancialStatus.FINANCIALLY_CLOSED, VoyageFinancialStatus.AUDITED),
                org.springframework.data.domain.PageRequest.of(0, 10)
        )).thenReturn(java.util.List.of());

        ExecutiveVoyageAnalyticsSummary summary = voyageAnalyticsService.getExecutiveSummary();

        assertThat(summary).isNotNull();
        assertThat(summary.latestVoyage()).isNull();
        assertThat(summary.fleetAverageRevPCD()).isEqualByComparingTo(BigDecimal.ZERO);
    }
}
