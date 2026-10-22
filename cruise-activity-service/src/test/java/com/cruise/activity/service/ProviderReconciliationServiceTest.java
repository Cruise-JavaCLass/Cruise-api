package com.cruise.activity.service;

import com.cruise.activity.dto.*;
import com.cruise.activity.enums.AdjustmentType;
import com.cruise.activity.enums.ExcursionPassengerStatus;
import com.cruise.activity.enums.SettlementStatus;
import com.cruise.activity.exception.BusinessRuleException;
import com.cruise.activity.exception.ResourceNotFoundException;
import com.cruise.activity.repository.ExcursionPassengerRepository;
import com.cruise.activity.repository.ProviderSettlementAdjustmentRepository;
import com.cruise.activity.repository.ProviderSettlementRepository;
import com.cruise.activity.repository.ShoreExcursionRepository;
import com.cruise.activity.entity.ExcursionPassenger;
import com.cruise.activity.entity.ProviderSettlement;
import com.cruise.activity.entity.ProviderSettlementAdjustment;
import com.cruise.activity.entity.ShoreExcursion;
import com.cruise.activity.engine.SettlementCalculationEngine;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Spy;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class ProviderReconciliationServiceTest {

    @Mock
    private ProviderSettlementRepository settlementRepository;

    @Mock
    private ProviderSettlementAdjustmentRepository adjustmentRepository;

    @Mock
    private ShoreExcursionRepository excursionRepository;

    @Mock
    private ExcursionPassengerRepository excursionPassengerRepository;

    @Spy
    private SettlementCalculationEngine calculationEngine = new SettlementCalculationEngine();

    @InjectMocks
    private ProviderReconciliationService reconciliationService;

    private ShoreExcursion mockExcursion;

    @BeforeEach
    void setUp() {
        mockExcursion = ShoreExcursion.builder()
                .tourId(101L)
                .portId(201L)
                .portName("Phuket Deep Sea Port")
                .title("Phuket Explorer Tour")
                .localProvider("Phuket Island Tours")
                .price(BigDecimal.valueOf(120.0))
                .currency("USD")
                .build();
        mockExcursion.setId(1L);
    }

    @Test
    @DisplayName("Khởi tạo quyết toán tính toán đúng theo Headcount Pax & Min Guarantee")
    void testInitializeOrRecalculateSettlement_success() {
        when(excursionRepository.findById(1L)).thenReturn(Optional.of(mockExcursion));
        when(settlementRepository.findByExcursionIdAndProviderIdWithLock(eq(1L), anyString()))
                .thenReturn(Optional.empty());

        List<ExcursionPassenger> passengers = List.of(
                ExcursionPassenger.builder().status(ExcursionPassengerStatus.CHECKED_IN).build(),
                ExcursionPassenger.builder().status(ExcursionPassengerStatus.ON_TOUR).build(),
                ExcursionPassenger.builder().status(ExcursionPassengerStatus.RETURNED_SAFE).build(),
                ExcursionPassenger.builder().status(ExcursionPassengerStatus.REGISTERED).build() // No-show
        );
        when(excursionPassengerRepository.findByExcursionIdAndActiveTrue(1L)).thenReturn(passengers);

        when(settlementRepository.save(any(ProviderSettlement.class))).thenAnswer(invocation -> {
            ProviderSettlement s = invocation.getArgument(0);
            if (s.getId() == null) s.setId(99L);
            return s;
        });

        InitializeSettlementRequest req = new InitializeSettlementRequest(
                1L, null, BigDecimal.valueOf(100.0), 10, "Initial test"
        );

        SettlementDetailDto dto = reconciliationService.initializeOrRecalculateSettlement(req);

        assertThat(dto).isNotNull();
        assertThat(dto.registeredPax()).isEqualTo(4);
        assertThat(dto.actualPax()).isEqualTo(3);
        assertThat(dto.noShowPax()).isEqualTo(1);
        assertThat(dto.minGuaranteePax()).isEqualTo(10);
        assertThat(dto.billablePax()).isEqualTo(10);
        assertThat(dto.baseAmount()).isEqualByComparingTo("1000.00");
        assertThat(dto.totalAmount()).isEqualByComparingTo("1000.00");
    }

    @Test
    @DisplayName("Khởi tạo quyết toán ném BusinessRuleException khi gặp xung đột DataIntegrityViolationException")
    void testInitializeSettlement_recalculateExistingAndConflictHandling() {
        when(excursionRepository.findById(1L)).thenReturn(Optional.of(mockExcursion));
        ProviderSettlement existing = ProviderSettlement.builder()
                .providerId("phuket-island-tours")
                .providerName("Phuket Island Tours")
                .excursionId(1L)
                .excursionTitle("Phuket Explorer Tour")
                .voyageId(101L)
                .status(SettlementStatus.DRAFT)
                .agreedUnitRate(BigDecimal.valueOf(120.0))
                .build();
        existing.setId(50L);

        when(settlementRepository.findByExcursionIdAndProviderIdWithLock(eq(1L), anyString()))
                .thenReturn(Optional.of(existing));
        when(excursionPassengerRepository.findByExcursionIdAndActiveTrue(1L)).thenReturn(List.of());

        when(settlementRepository.save(any(ProviderSettlement.class)))
                .thenThrow(new DataIntegrityViolationException("Duplicate key uk_ps_excursion_provider"));

        InitializeSettlementRequest req = new InitializeSettlementRequest(
                1L, "custom-provider", null, null, "Updated note"
        );

        assertThatThrownBy(() -> reconciliationService.initializeOrRecalculateSettlement(req))
                .isInstanceOf(BusinessRuleException.class)
                .hasMessageContaining("Xung đột dữ liệu khi khởi tạo quyết toán");
    }

    @Test
    @DisplayName("Thêm điều chỉnh Dispute / Penalty: giảm trừ vào tổng tiền quyết toán")
    void testAddAdjustment_penalty() {
        ProviderSettlement settlement = ProviderSettlement.builder()
                .providerId("phuket-island-tours")
                .providerName("Phuket Island Tours")
                .excursionId(1L)
                .excursionTitle("Phuket Explorer Tour")
                .voyageId(101L)
                .actualPax(15)
                .minGuaranteePax(10)
                .agreedUnitRate(BigDecimal.valueOf(100.0))
                .status(SettlementStatus.DRAFT)
                .adjustments(new ArrayList<>())
                .build();
        settlement.setId(10L);

        when(settlementRepository.findByIdWithLock(10L)).thenReturn(Optional.of(settlement));
        when(settlementRepository.save(any(ProviderSettlement.class))).thenAnswer(inv -> inv.getArgument(0));

        CreateSettlementAdjustmentRequest adjReq = new CreateSettlementAdjustmentRequest(
                AdjustmentType.PENALTY_VEHICLE_DEFECT,
                BigDecimal.valueOf(200.0),
                "INV-PEN-01",
                "Xe bus hỏng máy lạnh nửa hành trình",
                "MANAGER_01"
        );

        SettlementDetailDto result = reconciliationService.addAdjustment(10L, adjReq);

        assertThat(result.adjustedAmount()).isEqualByComparingTo("-200.00");
        assertThat(result.baseAmount()).isEqualByComparingTo("1500.00");
        assertThat(result.totalAmount()).isEqualByComparingTo("1300.00");
    }

    @Test
    @DisplayName("Thêm điều chỉnh Extra Service: cộng tiền vào quyết toán")
    void testAddAdjustment_extraService() {
        ProviderSettlement settlement = ProviderSettlement.builder()
                .providerId("phuket-island-tours")
                .status(SettlementStatus.DRAFT)
                .agreedUnitRate(BigDecimal.valueOf(50.0))
                .actualPax(10)
                .adjustments(new ArrayList<>())
                .build();
        settlement.setId(12L);

        when(settlementRepository.findByIdWithLock(12L)).thenReturn(Optional.of(settlement));
        when(settlementRepository.save(any(ProviderSettlement.class))).thenAnswer(inv -> inv.getArgument(0));

        CreateSettlementAdjustmentRequest adjReq = new CreateSettlementAdjustmentRequest(
                AdjustmentType.EXTRA_SERVICE,
                BigDecimal.valueOf(150.0),
                "INV-EXTRA-01",
                "Thêm đồ uống nhẹ",
                "MANAGER"
        );

        SettlementDetailDto result = reconciliationService.addAdjustment(12L, adjReq);
        assertThat(result.adjustedAmount()).isEqualByComparingTo("150.00");
    }

    @Test
    @DisplayName("Bảo vệ sổ cái bất biến: Không cho phép sửa khi trạng thái đã APPROVED hoặc PAID")
    void testImmutableLedgerLock_whenApproved() {
        ProviderSettlement lockedSettlement = ProviderSettlement.builder()
                .status(SettlementStatus.APPROVED)
                .build();
        lockedSettlement.setId(20L);

        when(settlementRepository.findByIdWithLock(20L)).thenReturn(Optional.of(lockedSettlement));

        CreateSettlementAdjustmentRequest adjReq = new CreateSettlementAdjustmentRequest(
                AdjustmentType.EXTRA_SERVICE,
                BigDecimal.valueOf(100.0),
                "INV-EXTRA-01",
                "Thêm nước ngọt",
                "STAFF"
        );

        assertThatThrownBy(() -> reconciliationService.addAdjustment(20L, adjReq))
                .isInstanceOf(BusinessRuleException.class)
                .hasMessageContaining("Không thể thêm điều chỉnh vào quyết toán đã khóa");
    }

    @Test
    @DisplayName("Cập nhật trạng thái chuyển giao hợp lệ (DRAFT -> PENDING_VERIFICATION -> APPROVED -> PAID -> RECONCILED)")
    void testUpdateStatus_validLifecycle() {
        ProviderSettlement settlement = ProviderSettlement.builder()
                .status(SettlementStatus.DRAFT)
                .build();
        settlement.setId(30L);

        when(settlementRepository.findByIdWithLock(30L)).thenReturn(Optional.of(settlement));
        when(settlementRepository.save(any(ProviderSettlement.class))).thenAnswer(inv -> inv.getArgument(0));

        SettlementDetailDto step1 = reconciliationService.updateStatus(
                30L, new UpdateSettlementStatusRequest(SettlementStatus.PENDING_VERIFICATION, "MANAGER", "Gửi đối soát")
        );
        assertThat(step1.status()).isEqualTo(SettlementStatus.PENDING_VERIFICATION);

        SettlementDetailDto step2 = reconciliationService.updateStatus(
                30L, new UpdateSettlementStatusRequest(SettlementStatus.APPROVED, "FINANCE_DIRECTOR", "Đã duyệt thanh toán")
        );
        assertThat(step2.status()).isEqualTo(SettlementStatus.APPROVED);
        assertThat(step2.approvedBy()).isEqualTo("FINANCE_DIRECTOR");
        assertThat(step2.approvedAt()).isNotNull();

        SettlementDetailDto step3 = reconciliationService.updateStatus(
                30L, new UpdateSettlementStatusRequest(SettlementStatus.PAID, "ACCOUNTANT", "Đã chi tiền")
        );
        assertThat(step3.status()).isEqualTo(SettlementStatus.PAID);

        SettlementDetailDto step4 = reconciliationService.updateStatus(
                30L, new UpdateSettlementStatusRequest(SettlementStatus.RECONCILED, "AUDITOR", "Khóa sổ")
        );
        assertThat(step4.status()).isEqualTo(SettlementStatus.RECONCILED);
    }

    @Test
    @DisplayName("Cập nhật trạng thái giữ nguyên (target == current) không thực hiện mutation")
    void testUpdateStatus_sameStatusNoOp() {
        ProviderSettlement settlement = ProviderSettlement.builder()
                .status(SettlementStatus.PENDING_VERIFICATION)
                .notes("Original note")
                .adjustments(new ArrayList<>())
                .build();
        settlement.setId(31L);

        when(settlementRepository.findByIdWithLock(31L)).thenReturn(Optional.of(settlement));

        SettlementDetailDto result = reconciliationService.updateStatus(
                31L, new UpdateSettlementStatusRequest(SettlementStatus.PENDING_VERIFICATION, "MANAGER", "Should not append")
        );

        assertThat(result.status()).isEqualTo(SettlementStatus.PENDING_VERIFICATION);
        assertThat(result.notes()).isEqualTo("Original note");
        verify(settlementRepository, never()).save(any());
    }

    @Test
    @DisplayName("Kiểm tra chuyển đổi trạng thái không hợp lệ ném BusinessRuleException")
    void testUpdateStatus_invalidTransitionThrows() {
        ProviderSettlement settlement = ProviderSettlement.builder()
                .status(SettlementStatus.DRAFT)
                .build();
        settlement.setId(32L);

        when(settlementRepository.findByIdWithLock(32L)).thenReturn(Optional.of(settlement));

        UpdateSettlementStatusRequest invalidReq = new UpdateSettlementStatusRequest(
                SettlementStatus.RECONCILED, "AUDITOR", "Bỏ qua các bước"
        );

        assertThatThrownBy(() -> reconciliationService.updateStatus(32L, invalidReq))
                .isInstanceOf(BusinessRuleException.class)
                .hasMessageContaining("Không thể chuyển trạng thái quyết toán");
    }

    @Test
    @DisplayName("Tra cứu chi tiết quyết toán theo ID và ngoại lệ khi không tìm thấy")
    void testGetSettlementById() {
        ProviderSettlement settlement = ProviderSettlement.builder()
                .providerId("provider-01")
                .status(SettlementStatus.DRAFT)
                .build();
        settlement.setId(40L);

        when(settlementRepository.findById(40L)).thenReturn(Optional.of(settlement));
        when(settlementRepository.findById(999L)).thenReturn(Optional.empty());

        SettlementDetailDto dto = reconciliationService.getSettlementById(40L);
        assertThat(dto).isNotNull();
        assertThat(dto.id()).isEqualTo(40L);

        assertThatThrownBy(() -> reconciliationService.getSettlementById(999L))
                .isInstanceOf(ResourceNotFoundException.class);
    }

    @Test
    @DisplayName("Tìm kiếm quyết toán phân trang (searchSettlements)")
    void testSearchSettlements() {
        ProviderSettlement settlement = ProviderSettlement.builder()
                .providerId("provider-01")
                .status(SettlementStatus.DRAFT)
                .build();
        settlement.setId(41L);

        Page<ProviderSettlement> page = new PageImpl<>(List.of(settlement));
        when(settlementRepository.searchSettlements(eq("provider-01"), isNull(), eq(SettlementStatus.DRAFT), any()))
                .thenReturn(page);

        Page<SettlementDetailDto> result = reconciliationService.searchSettlements(
                "provider-01", null, SettlementStatus.DRAFT, PageRequest.of(0, 10)
        );

        assertThat(result.getContent()).hasSize(1);
        assertThat(result.getContent().get(0).id()).isEqualTo(41L);
    }

    @Test
    @DisplayName("Xuất báo cáo tổng kết đối soát nhà cung cấp (getSettlementSummary)")
    void testGetSettlementSummary() {
        ProviderSettlement s1 = ProviderSettlement.builder()
                .providerId("p-1")
                .providerName("Provider One")
                .voyageId(100L)
                .registeredPax(10)
                .actualPax(8)
                .noShowPax(2)
                .billablePax(10)
                .baseAmount(BigDecimal.valueOf(1000.0))
                .adjustedAmount(BigDecimal.valueOf(-50.0))
                .totalAmount(BigDecimal.valueOf(950.0))
                .currency("USD")
                .status(SettlementStatus.APPROVED)
                .build();
        s1.setId(101L);

        when(settlementRepository.findByProviderIdAndVoyageId("p-1", 100L)).thenReturn(List.of(s1));
        when(settlementRepository.findByProviderId("p-1")).thenReturn(List.of(s1));

        ProviderSettlementSummaryResponse summaryWithVoyage = reconciliationService.getSettlementSummary("p-1", 100L);
        assertThat(summaryWithVoyage.totalExcursions()).isEqualTo(1);
        assertThat(summaryWithVoyage.totalRegisteredPax()).isEqualTo(10);
        assertThat(summaryWithVoyage.totalActualPax()).isEqualTo(8);
        assertThat(summaryWithVoyage.grandTotalAmount()).isEqualByComparingTo("950.0");

        ProviderSettlementSummaryResponse summaryNoVoyage = reconciliationService.getSettlementSummary("p-1", null);
        assertThat(summaryNoVoyage.providerName()).isEqualTo("Provider One");
    }
}
