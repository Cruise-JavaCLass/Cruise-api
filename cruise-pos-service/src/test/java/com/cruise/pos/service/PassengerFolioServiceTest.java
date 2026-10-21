package com.cruise.pos.service;

import com.cruise.pos.dto.CreateFolioDisputeRequest;
import com.cruise.pos.dto.DisputeResponse;
import com.cruise.pos.dto.FolioTransactionDto;
import com.cruise.pos.dto.PassengerFolioSummaryResponse;
import com.cruise.pos.entity.*;
import com.cruise.pos.enums.DisputeReason;
import com.cruise.pos.enums.DisputeStatus;
import com.cruise.pos.enums.FolioStatus;
import com.cruise.pos.exception.BusinessRuleException;
import com.cruise.pos.exception.ResourceNotFoundException;
import com.cruise.pos.repository.CabinFolioAccountRepository;
import com.cruise.pos.repository.DisputeCaseRepository;
import com.cruise.pos.repository.DisputeHistoryRepository;
import com.cruise.pos.repository.PosTransactionRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class PassengerFolioServiceTest {

    @Mock
    private CabinFolioAccountRepository cabinFolioAccountRepository;

    @Mock
    private PosTransactionRepository posTransactionRepository;

    @Mock
    private DisputeCaseRepository disputeCaseRepository;

    @Mock
    private DisputeHistoryRepository disputeHistoryRepository;

    @InjectMocks
    private PassengerFolioService passengerFolioService;

    private CabinFolioAccount sampleAccount;
    private PosTransaction sampleTx;

    @BeforeEach
    void setUp() {
        sampleAccount = CabinFolioAccount.builder()
                .passengerId(101L)
                .cabinNumber("CABIN-7012")
                .primaryGuestName("Nguyễn Văn An")
                .creditLimit(BigDecimal.valueOf(1000.00))
                .currentBalance(BigDecimal.valueOf(450.00))
                .status(FolioStatus.ACTIVE)
                .build();

        PosTransactionItem item1 = PosTransactionItem.builder()
                .itemCode("COCKTAIL-01")
                .itemName("Mojito Cruise")
                .category("Beverage Bar")
                .quantity(2)
                .unitPrice(BigDecimal.valueOf(15.00))
                .totalPrice(BigDecimal.valueOf(30.00))
                .build();

        PosTransactionItem item2 = PosTransactionItem.builder()
                .itemCode("SPA-01")
                .itemName("Aroma Massage")
                .category("Spa & Wellness")
                .quantity(1)
                .unitPrice(BigDecimal.valueOf(120.00))
                .totalPrice(BigDecimal.valueOf(120.00))
                .build();

        PosTransactionItem complimentaryItem = PosTransactionItem.builder()
                .itemCode("TOUR-COMP")
                .itemName("Welcome Excursion Tour")
                .category("Shore Excursion")
                .quantity(1)
                .unitPrice(BigDecimal.ZERO)
                .totalPrice(BigDecimal.ZERO)
                .build();

        sampleTx = PosTransaction.builder()
                .transactionUuid("TX-UUID-999")
                .cabinNumber("CABIN-7012")
                .passengerId(101L)
                .stationId("BAR-DECK-9")
                .cashierId("POS-001")
                .guestName("Nguyễn Văn An")
                .totalAmount(BigDecimal.valueOf(150.00))
                .currency("USD")
                .items(List.of(item1, item2, complimentaryItem))
                .build();
        sampleTx.setCreatedAt(Instant.now());
    }

    @Test
    @DisplayName("Should return accurate folio summary with 5 standard categories and spending thresholds")
    void testGetFolioSummarySuccess() {
        PosTransaction txNotes = PosTransaction.builder()
                .transactionUuid("TX-NOTES-1")
                .cabinNumber("CABIN-7012")
                .passengerId(101L)
                .stationId("RETAIL-01")
                .cashierId("POS-002")
                .guestName("Nguyễn Văn An")
                .totalAmount(BigDecimal.valueOf(70.00))
                .notes("Retail Souvenir Gift")
                .items(List.of())
                .build();

        when(cabinFolioAccountRepository.findByPassengerId(101L)).thenReturn(Optional.of(sampleAccount));
        when(posTransactionRepository.findByCabinNumber("CABIN-7012")).thenReturn(List.of(sampleTx, txNotes));

        PassengerFolioSummaryResponse summary = passengerFolioService.getFolioSummary(101L);

        assertThat(summary).isNotNull();
        assertThat(summary.getPassengerId()).isEqualTo(101L);
        assertThat(summary.getCabinNumber()).isEqualTo("CABIN-7012");
        assertThat(summary.getCreditLimit()).isEqualByComparingTo(BigDecimal.valueOf(1000.00));
        assertThat(summary.getCurrentBalance()).isEqualByComparingTo(BigDecimal.valueOf(450.00));
        assertThat(summary.getSpendingPercentage()).isEqualByComparingTo(BigDecimal.valueOf(45.00));
        assertThat(summary.isWarningThresholdReached()).isFalse();
        assertThat(summary.isLimitExceeded()).isFalse();

        // Check categories breakdown
        assertThat(summary.getSpendingByCategory()).containsKeys(
                PassengerFolioService.CAT_FOOD_AND_BEVERAGE,
                PassengerFolioService.CAT_SHORE_EXCURSIONS,
                PassengerFolioService.CAT_SPA_AND_WELLNESS,
                PassengerFolioService.CAT_RETAIL_SHOPPING,
                PassengerFolioService.CAT_SERVICE_FEES
        );
        assertThat(summary.getSpendingByCategory().get(PassengerFolioService.CAT_FOOD_AND_BEVERAGE))
                .isEqualByComparingTo(BigDecimal.valueOf(30.00));
        assertThat(summary.getSpendingByCategory().get(PassengerFolioService.CAT_SPA_AND_WELLNESS))
                .isEqualByComparingTo(BigDecimal.valueOf(120.00));
        assertThat(summary.getSpendingByCategory().get(PassengerFolioService.CAT_RETAIL_SHOPPING))
                .isEqualByComparingTo(BigDecimal.valueOf(70.00));
        assertThat(summary.getSpendingByCategory().get(PassengerFolioService.CAT_SHORE_EXCURSIONS))
                .isEqualByComparingTo(BigDecimal.ZERO);
    }

    @Test
    @DisplayName("Should flag 80% warning and 100% exceeded thresholds accurately")
    void testSpendingThresholds() {
        sampleAccount.setCurrentBalance(BigDecimal.valueOf(850.00)); // 85%
        when(cabinFolioAccountRepository.findByPassengerId(101L)).thenReturn(Optional.of(sampleAccount));
        when(posTransactionRepository.findByCabinNumber("CABIN-7012")).thenReturn(List.of());

        PassengerFolioSummaryResponse summary85 = passengerFolioService.getFolioSummary(101L);
        assertThat(summary85.isWarningThresholdReached()).isTrue();
        assertThat(summary85.isLimitExceeded()).isFalse();

        sampleAccount.setCurrentBalance(BigDecimal.valueOf(1050.00)); // 105%
        PassengerFolioSummaryResponse summary105 = passengerFolioService.getFolioSummary(101L);
        assertThat(summary105.isWarningThresholdReached()).isTrue();
        assertThat(summary105.isLimitExceeded()).isTrue();
    }

    @Test
    @DisplayName("Should list transactions and distinguish complimentary packages ($0.00)")
    void testGetTransactionsWithComplimentary() {
        PosTransaction freeTx = PosTransaction.builder()
                .transactionUuid("TX-FREE-01")
                .cabinNumber("CABIN-7012")
                .totalAmount(BigDecimal.ZERO)
                .items(List.of())
                .build();
        freeTx.setCreatedAt(Instant.now().minusSeconds(60));

        when(cabinFolioAccountRepository.findByPassengerId(101L)).thenReturn(Optional.of(sampleAccount));
        when(posTransactionRepository.findByCabinNumber("CABIN-7012")).thenReturn(List.of(sampleTx, freeTx));

        Page<FolioTransactionDto> page = passengerFolioService.getTransactions(101L, "ALL", PageRequest.of(0, 10));

        assertThat(page.getTotalElements()).isEqualTo(2);
        FolioTransactionDto txDto = page.getContent().get(0);
        assertThat(txDto.getTransactionUuid()).isEqualTo("TX-UUID-999");
        assertThat(txDto.getLocationCounter()).isEqualTo("BAR-DECK-9");
        assertThat(txDto.getPosCode()).isEqualTo("POS-001");
        assertThat(txDto.getSwipedBy()).isEqualTo("Nguyễn Văn An");
        assertThat(txDto.isComplimentary()).isFalse(); // sampleTx total is $150

        assertThat(txDto.getItems()).hasSize(3);
        assertThat(txDto.getItems().get(2).isComplimentary()).isTrue();
        assertThat(txDto.getItems().get(2).getTotalPrice()).isEqualByComparingTo(BigDecimal.ZERO);

        FolioTransactionDto freeDto = page.getContent().get(1);
        assertThat(freeDto.isComplimentary()).isTrue();
    }

    @Test
    @DisplayName("Should filter transactions by category and handle out of bound pages")
    void testGetTransactionsCategoryFilterAndOutOfBoundPage() {
        when(cabinFolioAccountRepository.findByPassengerId(101L)).thenReturn(Optional.of(sampleAccount));
        when(posTransactionRepository.findByCabinNumber("CABIN-7012")).thenReturn(List.of(sampleTx));

        Page<FolioTransactionDto> pageFood = passengerFolioService.getTransactions(101L, PassengerFolioService.CAT_FOOD_AND_BEVERAGE, PageRequest.of(0, 10));
        assertThat(pageFood.getContent()).hasSize(1);

        Page<FolioTransactionDto> pageOutOfBound = passengerFolioService.getTransactions(101L, "ALL", PageRequest.of(5, 10));
        assertThat(pageOutOfBound.getContent()).isEmpty();
    }

    @Test
    @DisplayName("Should successfully create a dispute for passenger transaction and capture entities")
    void testDisputeTransactionSuccess() {
        when(cabinFolioAccountRepository.findByPassengerId(101L)).thenReturn(Optional.of(sampleAccount));
        sampleTx.setId(555L);
        when(posTransactionRepository.findById(555L)).thenReturn(Optional.of(sampleTx));
        when(disputeCaseRepository.existsByTransactionIdAndStatusIn(any(), any())).thenReturn(false);

        DisputeCase savedCase = DisputeCase.builder()
                .disputeNumber("DISP-12345678")
                .transactionId("TX-UUID-999")
                .cabinNumber("CABIN-7012")
                .passengerId("101")
                .reason(DisputeReason.INCORRECT_AMOUNT)
                .disputeAmount(BigDecimal.valueOf(50.00))
                .status(DisputeStatus.SUBMITTED)
                .passengerNote("Sai đơn giá đồ uống")
                .build();
        savedCase.setId(1L);
        savedCase.setCreatedAt(Instant.now());
        when(disputeCaseRepository.save(any(DisputeCase.class))).thenReturn(savedCase);

        CreateFolioDisputeRequest request = CreateFolioDisputeRequest.builder()
                .reason(DisputeReason.INCORRECT_AMOUNT)
                .disputeAmount(BigDecimal.valueOf(50.00))
                .disputeNote("Sai đơn giá đồ uống")
                .build();

        DisputeResponse response = passengerFolioService.disputeTransaction(101L, 555L, request);

        assertThat(response).isNotNull();
        assertThat(response.getDisputeNumber()).isEqualTo("DISP-12345678");
        assertThat(response.getStatus()).isEqualTo(DisputeStatus.SUBMITTED);

        ArgumentCaptor<DisputeCase> disputeCaptor = ArgumentCaptor.forClass(DisputeCase.class);
        verify(disputeCaseRepository).save(disputeCaptor.capture());
        DisputeCase capturedCase = disputeCaptor.getValue();
        assertThat(capturedCase.getTransactionId()).isEqualTo("TX-UUID-999");
        assertThat(capturedCase.getCabinNumber()).isEqualTo("CABIN-7012");
        assertThat(capturedCase.getReason()).isEqualTo(DisputeReason.INCORRECT_AMOUNT);
        assertThat(capturedCase.getDisputeAmount()).isEqualByComparingTo(BigDecimal.valueOf(50.00));

        ArgumentCaptor<DisputeHistory> historyCaptor = ArgumentCaptor.forClass(DisputeHistory.class);
        verify(disputeHistoryRepository).save(historyCaptor.capture());
        DisputeHistory capturedHistory = historyCaptor.getValue();
        assertThat(capturedHistory.getDisputeId()).isEqualTo(1L);
        assertThat(capturedHistory.getNewStatus()).isEqualTo(DisputeStatus.SUBMITTED);
        assertThat(capturedHistory.getActionBy()).isEqualTo("PASSENGER-101");
    }

    @Test
    @DisplayName("Should throw exception if dispute amount exceeds transaction total or is invalid")
    void testDisputeInvalidAmountThrowsException() {
        when(cabinFolioAccountRepository.findByPassengerId(101L)).thenReturn(Optional.of(sampleAccount));
        sampleTx.setId(555L);
        when(posTransactionRepository.findById(555L)).thenReturn(Optional.of(sampleTx));

        CreateFolioDisputeRequest reqOver = CreateFolioDisputeRequest.builder()
                .reason(DisputeReason.INCORRECT_AMOUNT)
                .disputeAmount(BigDecimal.valueOf(999.00))
                .disputeNote("Quá cao")
                .build();

        assertThatThrownBy(() -> passengerFolioService.disputeTransaction(101L, 555L, reqOver))
                .isInstanceOf(BusinessRuleException.class)
                .hasMessageContaining("vượt quá");

        CreateFolioDisputeRequest reqZero = CreateFolioDisputeRequest.builder()
                .reason(DisputeReason.INCORRECT_AMOUNT)
                .disputeAmount(BigDecimal.ZERO)
                .disputeNote("Bằng 0")
                .build();

        assertThatThrownBy(() -> passengerFolioService.disputeTransaction(101L, 555L, reqZero))
                .isInstanceOf(BusinessRuleException.class)
                .hasMessageContaining("lớn hơn 0");
    }

    @Test
    @DisplayName("Should throw exception if transaction total amount is null")
    void testDisputeNullTotalAmountThrowsException() {
        when(cabinFolioAccountRepository.findByPassengerId(101L)).thenReturn(Optional.of(sampleAccount));
        sampleTx.setId(555L);
        sampleTx.setTotalAmount(null);
        when(posTransactionRepository.findById(555L)).thenReturn(Optional.of(sampleTx));

        CreateFolioDisputeRequest req = CreateFolioDisputeRequest.builder()
                .reason(DisputeReason.INCORRECT_AMOUNT)
                .disputeAmount(BigDecimal.valueOf(50.00))
                .disputeNote("Giao dịch không có tổng tiền")
                .build();

        assertThatThrownBy(() -> passengerFolioService.disputeTransaction(101L, 555L, req))
                .isInstanceOf(BusinessRuleException.class)
                .hasMessageContaining("không có tổng tiền hợp lệ");
    }

    @Test
    @DisplayName("Should throw exception if transaction belongs to another cabin")
    void testDisputeOtherCabinThrowsException() {
        when(cabinFolioAccountRepository.findByPassengerId(101L)).thenReturn(Optional.of(sampleAccount));
        sampleTx.setId(555L);
        sampleTx.setCabinNumber("CABIN-9999");
        when(posTransactionRepository.findById(555L)).thenReturn(Optional.of(sampleTx));

        CreateFolioDisputeRequest req = CreateFolioDisputeRequest.builder()
                .reason(DisputeReason.INCORRECT_AMOUNT)
                .disputeAmount(BigDecimal.valueOf(50.00))
                .disputeNote("Sai cabin")
                .build();

        assertThatThrownBy(() -> passengerFolioService.disputeTransaction(101L, 555L, req))
                .isInstanceOf(BusinessRuleException.class)
                .hasMessageContaining("không thuộc về tài khoản cabin");

        verify(disputeCaseRepository, never()).save(any());
    }

    @Test
    @DisplayName("Should throw exception if transaction has an active dispute already")
    void testDisputeAlreadyActiveThrowsException() {
        when(cabinFolioAccountRepository.findByPassengerId(101L)).thenReturn(Optional.of(sampleAccount));
        sampleTx.setId(555L);
        when(posTransactionRepository.findById(555L)).thenReturn(Optional.of(sampleTx));
        when(disputeCaseRepository.existsByTransactionIdAndStatusIn(any(), any())).thenReturn(true);

        CreateFolioDisputeRequest req = CreateFolioDisputeRequest.builder()
                .reason(DisputeReason.DUPLICATE_BILLING)
                .disputeAmount(BigDecimal.valueOf(50.00))
                .disputeNote("Trùng lặp")
                .build();

        assertThatThrownBy(() -> passengerFolioService.disputeTransaction(101L, 555L, req))
                .isInstanceOf(BusinessRuleException.class)
                .hasMessageContaining("đang có khiếu nại đang xử lý");

        verify(disputeCaseRepository, never()).save(any());
    }

    @Test
    @DisplayName("Should throw exception if passenger folio account or transaction not found")
    void testNotFoundExceptions() {
        when(cabinFolioAccountRepository.findByPassengerId(999L)).thenReturn(Optional.empty());
        when(cabinFolioAccountRepository.findByCabinNumber("CABIN-999")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> passengerFolioService.getFolioSummary(999L))
                .isInstanceOf(ResourceNotFoundException.class);

        when(cabinFolioAccountRepository.findByPassengerId(101L)).thenReturn(Optional.of(sampleAccount));
        when(posTransactionRepository.findById(888L)).thenReturn(Optional.empty());

        CreateFolioDisputeRequest req = CreateFolioDisputeRequest.builder()
                .reason(DisputeReason.INCORRECT_AMOUNT)
                .disputeAmount(BigDecimal.valueOf(50.00))
                .disputeNote("Không tìm thấy")
                .build();

        assertThatThrownBy(() -> passengerFolioService.disputeTransaction(101L, 888L, req))
                .isInstanceOf(ResourceNotFoundException.class);
    }

    @Test
    @DisplayName("Should correctly map category for fees and fallbacks")
    void testCategoryMappingEdgeCases() {
        assertThat(PassengerFolioService.mapToStandardCategory(null))
                .isEqualTo(PassengerFolioService.CAT_FOOD_AND_BEVERAGE);
        assertThat(PassengerFolioService.mapToStandardCategory("   "))
                .isEqualTo(PassengerFolioService.CAT_FOOD_AND_BEVERAGE);
        assertThat(PassengerFolioService.mapToStandardCategory("Service Gratuity Fee"))
                .isEqualTo(PassengerFolioService.CAT_SERVICE_FEES);
        assertThat(PassengerFolioService.mapToStandardCategory("Unknown Category XYZ"))
                .isEqualTo(PassengerFolioService.CAT_FOOD_AND_BEVERAGE);
    }
}
