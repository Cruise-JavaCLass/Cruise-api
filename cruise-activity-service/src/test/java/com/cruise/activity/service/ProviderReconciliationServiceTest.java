package com.cruise.activity.service;

import com.cruise.activity.dto.CreateSettlementAdjustmentRequest;
import com.cruise.activity.dto.InitializeSettlementRequest;
import com.cruise.activity.dto.SettlementDetailDto;
import com.cruise.activity.dto.UpdateSettlementStatusRequest;
import com.cruise.activity.engine.SettlementCalculationEngine;
import com.cruise.activity.entity.ExcursionPassenger;
import com.cruise.activity.entity.ProviderSettlement;
import com.cruise.activity.entity.ShoreExcursion;
import com.cruise.activity.enums.AdjustmentType;
import com.cruise.activity.enums.ExcursionPassengerStatus;
import com.cruise.activity.enums.SettlementStatus;
import com.cruise.activity.repository.ExcursionPassengerRepository;
import com.cruise.activity.repository.ProviderSettlementAdjustmentRepository;
import com.cruise.activity.repository.ProviderSettlementRepository;
import com.cruise.activity.repository.ShoreExcursionRepository;
import com.cruise.activity.exception.BusinessRuleException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Spy;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
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
        assertThat(dto.billablePax()).isEqualTo(10); // MAX(3, 10) = 10
        assertThat(dto.baseAmount()).isEqualByComparingTo("1000.00"); // 10 * 100
        assertThat(dto.totalAmount()).isEqualByComparingTo("1000.00");
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
        assertThat(result.baseAmount()).isEqualByComparingTo("1500.00"); // 15 * 100
        assertThat(result.totalAmount()).isEqualByComparingTo("1300.00"); // 1500 - 200
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
    @DisplayName("Cập nhật trạng thái chuyển giao hợp lệ (DRAFT -> PENDING_VERIFICATION -> APPROVED)")
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
    }

    @Test
    @DisplayName("Cập nhật trạng thái giữ nguyên (target == current) không thực hiện mutation")
    void testUpdateStatus_sameStatusNoOp() {
        ProviderSettlement settlement = ProviderSettlement.builder()
                .status(SettlementStatus.PENDING_VERIFICATION)
                .notes("Original note")
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
}