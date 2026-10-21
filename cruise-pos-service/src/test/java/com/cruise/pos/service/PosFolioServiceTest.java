package com.cruise.pos.service;

import com.cruise.pos.dto.*;
import com.cruise.pos.entity.CabinFolioAccount;
import com.cruise.pos.entity.GuestFolioLedger;
import com.cruise.pos.enums.FolioStatus;
import com.cruise.pos.exception.BusinessRuleException;
import com.cruise.pos.exception.DuplicateResourceException;
import com.cruise.pos.exception.InsufficientFolioBalanceException;
import com.cruise.pos.exception.ResourceNotFoundException;
import com.cruise.pos.repository.CabinFolioAccountRepository;
import com.cruise.pos.repository.GuestFolioLedgerRepository;
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
import org.springframework.data.domain.Pageable;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Collections;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@DisplayName("PosFolioService Unit Tests")
class PosFolioServiceTest {

    @Mock
    private CabinFolioAccountRepository folioRepository;

    @Mock
    private GuestFolioLedgerRepository guestFolioLedgerRepository;

    @InjectMocks
    private PosFolioService folioService;

    private CabinFolioAccount folio;

    @BeforeEach
    void setUp() {
        folio = CabinFolioAccount.builder()
                .cabinNumber("CABIN-7012")
                .primaryGuestName("Nguyễn Văn An")
                .passengerId(101L)
                .creditLimit(BigDecimal.valueOf(1500.00))
                .currentBalance(BigDecimal.valueOf(200.00))
                .status(FolioStatus.ACTIVE)
                .build();
    }

    @Test
    @DisplayName("getFolioByCabin returns account when found")
    void testGetFolioByCabinSuccess() {
        when(folioRepository.findByCabinNumber("CABIN-7012")).thenReturn(Optional.of(folio));

        FolioAccountResponse res = folioService.getFolioByCabin("CABIN-7012");
        assertThat(res.getCabinNumber()).isEqualTo("CABIN-7012");
        assertThat(res.getAvailableLimit()).isEqualByComparingTo(BigDecimal.valueOf(1300.00));
    }

    @Test
    @DisplayName("getFolioByCabin throws ResourceNotFoundException when missing")
    void testGetFolioByCabinNotFound() {
        when(folioRepository.findByCabinNumber("CABIN-404")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> folioService.getFolioByCabin("CABIN-404"))
                .isInstanceOf(ResourceNotFoundException.class)
                .hasMessageContaining("CABIN-404");
    }

    @Test
    @DisplayName("createFolio successfully saves new account")
    void testCreateFolioSuccess() {
        CreateFolioRequest req = CreateFolioRequest.builder()
                .cabinNumber("CABIN-8888")
                .primaryGuestName("Hoàng Kim")
                .passengerId(202L)
                .creditLimit(BigDecimal.valueOf(2500.00))
                .build();

        when(folioRepository.findByCabinNumber("CABIN-8888")).thenReturn(Optional.empty());
        when(folioRepository.saveAndFlush(any(CabinFolioAccount.class))).thenAnswer(inv -> inv.getArgument(0));

        FolioAccountResponse resp = folioService.createFolio(req);
        assertThat(resp.getCabinNumber()).isEqualTo("CABIN-8888");
        assertThat(resp.getPrimaryGuestName()).isEqualTo("Hoàng Kim");
        assertThat(resp.getCreditLimit()).isEqualByComparingTo(BigDecimal.valueOf(2500.00));
        assertThat(resp.getStatus()).isEqualTo(FolioStatus.ACTIVE);
    }

    @Test
    @DisplayName("createFolio throws DuplicateResourceException if cabin already exists")
    void testCreateFolioDuplicate() {
        CreateFolioRequest req = CreateFolioRequest.builder()
                .cabinNumber("CABIN-7012")
                .primaryGuestName("Trùng Cabin")
                .creditLimit(BigDecimal.valueOf(1000.00))
                .build();

        when(folioRepository.findByCabinNumber("CABIN-7012")).thenReturn(Optional.of(folio));

        assertThatThrownBy(() -> folioService.createFolio(req))
                .isInstanceOf(DuplicateResourceException.class)
                .hasMessageContaining("CABIN-7012");
    }

    @Test
    @DisplayName("getFolios returns paginated results with filter")
    void testGetFoliosPaginated() {
        Pageable pageable = PageRequest.of(0, 10);
        when(folioRepository.findWithFilter("7012", FolioStatus.ACTIVE, pageable))
                .thenReturn(new PageImpl<>(List.of(folio), pageable, 1));

        Page<FolioAccountResponse> result = folioService.getFolios("7012", FolioStatus.ACTIVE, pageable);
        assertThat(result.getTotalElements()).isEqualTo(1);
        assertThat(result.getContent().get(0).getCabinNumber()).isEqualTo("CABIN-7012");
    }

    @Test
    @DisplayName("updateCreditLimit updates limit when new limit >= current balance")
    void testUpdateCreditLimitSuccess() {
        UpdateCreditLimitRequest req = UpdateCreditLimitRequest.builder()
                .creditLimit(BigDecimal.valueOf(3000.00))
                .reason("Khách VIP nâng hạn mức")
                .build();

        when(folioRepository.findByCabinNumberWithLock("CABIN-7012")).thenReturn(Optional.of(folio));
        when(folioRepository.save(any(CabinFolioAccount.class))).thenAnswer(inv -> inv.getArgument(0));

        FolioAccountResponse resp = folioService.updateCreditLimit("CABIN-7012", req);
        assertThat(resp.getCreditLimit()).isEqualByComparingTo(BigDecimal.valueOf(3000.00));
    }

    @Test
    @DisplayName("updateCreditLimit throws BusinessRuleException when new limit < current balance")
    void testUpdateCreditLimitBelowBalance() {
        UpdateCreditLimitRequest req = UpdateCreditLimitRequest.builder()
                .creditLimit(BigDecimal.valueOf(100.00)) // currentBalance is 200.00
                .reason("Giảm hạn mức")
                .build();

        when(folioRepository.findByCabinNumberWithLock("CABIN-7012")).thenReturn(Optional.of(folio));

        assertThatThrownBy(() -> folioService.updateCreditLimit("CABIN-7012", req))
                .isInstanceOf(BusinessRuleException.class)
                .hasMessageContaining("không thể nhỏ hơn số dư nợ hiện tại");
    }

    @Test
    @DisplayName("updateStatus updates folio status")
    void testUpdateStatusSuccess() {
        UpdateFolioStatusRequest req = UpdateFolioStatusRequest.builder()
                .status(FolioStatus.FROZEN)
                .reason("Tạm khóa do nghi ngờ giao dịch")
                .build();

        when(folioRepository.findByCabinNumberWithLock("CABIN-7012")).thenReturn(Optional.of(folio));
        when(folioRepository.save(any(CabinFolioAccount.class))).thenAnswer(inv -> inv.getArgument(0));

        FolioAccountResponse resp = folioService.updateStatus("CABIN-7012", req);
        assertThat(resp.getStatus()).isEqualTo(FolioStatus.FROZEN);
    }

    @Test
    @DisplayName("postExpense debits folio and saves ledger entry")
    void testPostExpenseChargeSuccess() {
        PostExpenseRequest req = PostExpenseRequest.builder()
                .transactionId("TX-EXP-101")
                .description("Sky Lounge Cocktails")
                .serviceArea("Sky Bar")
                .amount(BigDecimal.valueOf(50.00))
                .isReversal(false)
                .build();

        when(folioRepository.findByCabinNumberWithLock("CABIN-7012")).thenReturn(Optional.of(folio));
        when(guestFolioLedgerRepository.existsByTransactionId("TX-EXP-101")).thenReturn(false);
        when(guestFolioLedgerRepository.saveAndFlush(any(GuestFolioLedger.class))).thenAnswer(inv -> inv.getArgument(0));
        when(folioRepository.save(any(CabinFolioAccount.class))).thenAnswer(inv -> inv.getArgument(0));

        FolioLedgerItemDto item = folioService.postExpense("CABIN-7012", req);
        assertThat(item.getTransactionId()).isEqualTo("TX-EXP-101");
        assertThat(item.getAmount()).isEqualByComparingTo(BigDecimal.valueOf(50.00));
        assertThat(item.getRunningBalance()).isEqualByComparingTo(BigDecimal.valueOf(250.00));
    }

    @Test
    @DisplayName("postExpense throws InsufficientFolioBalanceException if debit exceeds limit")
    void testPostExpenseExceedsLimit() {
        PostExpenseRequest req = PostExpenseRequest.builder()
                .transactionId("TX-EXP-TOO-HIGH")
                .description("Luxury Watch Gift Shop")
                .serviceArea("Gift Shop")
                .amount(BigDecimal.valueOf(2000.00)) // Available is 1300.00
                .build();

        when(folioRepository.findByCabinNumberWithLock("CABIN-7012")).thenReturn(Optional.of(folio));
        when(guestFolioLedgerRepository.existsByTransactionId("TX-EXP-TOO-HIGH")).thenReturn(false);

        assertThatThrownBy(() -> folioService.postExpense("CABIN-7012", req))
                .isInstanceOf(InsufficientFolioBalanceException.class)
                .hasMessageContaining("không đủ hạn mức tín dụng");
    }

    @Test
    @DisplayName("postExpense throws DuplicateResourceException if transactionId exists")
    void testPostExpenseDuplicateTx() {
        PostExpenseRequest req = PostExpenseRequest.builder()
                .transactionId("TX-ALREADY-EXISTS")
                .description("Room Service")
                .serviceArea("Dining")
                .amount(BigDecimal.valueOf(25.00))
                .build();

        when(folioRepository.findByCabinNumberWithLock("CABIN-7012")).thenReturn(Optional.of(folio));
        when(guestFolioLedgerRepository.existsByTransactionId("TX-ALREADY-EXISTS")).thenReturn(true);

        assertThatThrownBy(() -> folioService.postExpense("CABIN-7012", req))
                .isInstanceOf(DuplicateResourceException.class)
                .hasMessageContaining("TX-ALREADY-EXISTS");
    }

    @Test
    @DisplayName("postExpense with reversal credits balance")
    void testPostExpenseReversalSuccess() {
        PostExpenseRequest req = PostExpenseRequest.builder()
                .transactionId("TX-REV-101")
                .description("Refund for canceled excursion")
                .serviceArea("Excursion Desk")
                .amount(BigDecimal.valueOf(80.00))
                .isReversal(true)
                .build();

        when(folioRepository.findByCabinNumberWithLock("CABIN-7012")).thenReturn(Optional.of(folio));
        when(guestFolioLedgerRepository.existsByTransactionId("TX-REV-101")).thenReturn(false);
        when(guestFolioLedgerRepository.saveAndFlush(any(GuestFolioLedger.class))).thenAnswer(inv -> inv.getArgument(0));

        FolioLedgerItemDto item = folioService.postExpense("CABIN-7012", req);
        assertThat(item.getIsReversal()).isTrue();
        assertThat(item.getRunningBalance()).isEqualByComparingTo(BigDecimal.valueOf(120.00)); // 200 - 80 = 120
    }

    @Test
    @DisplayName("postExpense with reversal throws BusinessRuleException if folio is not ACTIVE")
    void testPostExpenseReversalNotActive() {
        folio.setStatus(FolioStatus.FROZEN);
        PostExpenseRequest req = PostExpenseRequest.builder()
                .transactionId("TX-REV-FROZEN")
                .description("Refund for canceled excursion")
                .serviceArea("Excursion Desk")
                .amount(BigDecimal.valueOf(50.00))
                .isReversal(true)
                .build();

        when(folioRepository.findByCabinNumberWithLock("CABIN-7012")).thenReturn(Optional.of(folio));
        when(guestFolioLedgerRepository.existsByTransactionId("TX-REV-FROZEN")).thenReturn(false);

        assertThatThrownBy(() -> folioService.postExpense("CABIN-7012", req))
                .isInstanceOf(BusinessRuleException.class)
                .hasMessageContaining("chỉ áp dụng khi ACTIVE");
    }

    @Test
    @DisplayName("postExpense with reversal throws BusinessRuleException if amount exceeds current balance")
    void testPostExpenseReversalExceedsBalance() {
        PostExpenseRequest req = PostExpenseRequest.builder()
                .transactionId("TX-REV-TOO-MUCH")
                .description("Refund exceeding balance")
                .serviceArea("Excursion Desk")
                .amount(BigDecimal.valueOf(500.00)) // Current balance is 200.00
                .isReversal(true)
                .build();

        when(folioRepository.findByCabinNumberWithLock("CABIN-7012")).thenReturn(Optional.of(folio));
        when(guestFolioLedgerRepository.existsByTransactionId("TX-REV-TOO-MUCH")).thenReturn(false);

        assertThatThrownBy(() -> folioService.postExpense("CABIN-7012", req))
                .isInstanceOf(BusinessRuleException.class)
                .hasMessageContaining("vượt quá số dư nợ hiện tại");
    }


    @Test
    @DisplayName("getFolioStatement calculates cumulative running balance correctly")
    void testGetFolioStatementRunningBalance() {
        when(folioRepository.findByCabinNumber("CABIN-7012")).thenReturn(Optional.of(folio));

        GuestFolioLedger tx1 = GuestFolioLedger.builder()
                .transactionId("TX-1")
                .description("Welcome Dinner")
                .serviceArea("Restaurant")
                .amount(BigDecimal.valueOf(100.00))
                .isReversal(false)
                .postedAt(Instant.parse("2026-11-01T18:00:00Z"))
                .build();
        tx1.setId(1L);

        GuestFolioLedger tx2 = GuestFolioLedger.builder()
                .transactionId("TX-2")
                .description("Cocktail Lounge")
                .serviceArea("Sky Bar")
                .amount(BigDecimal.valueOf(40.00))
                .isReversal(false)
                .postedAt(Instant.parse("2026-11-01T20:00:00Z"))
                .build();
        tx2.setId(2L);

        GuestFolioLedger tx3 = GuestFolioLedger.builder()
                .transactionId("TX-3")
                .description("Discount adjustment")
                .serviceArea("Sky Bar")
                .amount(BigDecimal.valueOf(10.00))
                .isReversal(true)
                .postedAt(Instant.parse("2026-11-01T20:30:00Z"))
                .build();
        tx3.setId(3L);

        when(guestFolioLedgerRepository.findByCabinNumberOrderByPostedAtAsc("CABIN-7012"))
                .thenReturn(List.of(tx1, tx2, tx3));

        FolioStatementResponse statement = folioService.getFolioStatement("CABIN-7012", null);

        assertThat(statement.getTotalCharges()).isEqualByComparingTo(BigDecimal.valueOf(140.00));
        assertThat(statement.getTotalCredits()).isEqualByComparingTo(BigDecimal.valueOf(10.00));
        assertThat(statement.getItems()).hasSize(3);

        // Check running balances: 100 -> 140 -> 130
        assertThat(statement.getItems().get(0).getRunningBalance()).isEqualByComparingTo(BigDecimal.valueOf(100.00));
        assertThat(statement.getItems().get(1).getRunningBalance()).isEqualByComparingTo(BigDecimal.valueOf(140.00));
        assertThat(statement.getItems().get(2).getRunningBalance()).isEqualByComparingTo(BigDecimal.valueOf(130.00));
    }

    @Test
    @DisplayName("getFolioStatement filters by service area when provided")
    void testGetFolioStatementFilterServiceArea() {
        when(folioRepository.findByCabinNumber("CABIN-7012")).thenReturn(Optional.of(folio));

        GuestFolioLedger tx = GuestFolioLedger.builder()
                .transactionId("TX-BAR-1")
                .description("Cocktail")
                .serviceArea("Sky Bar")
                .amount(BigDecimal.valueOf(30.00))
                .isReversal(false)
                .postedAt(Instant.now())
                .build();
        tx.setId(1L);

        when(guestFolioLedgerRepository.findByCabinNumberAndServiceAreaOrderByPostedAtAsc("CABIN-7012", "Sky Bar"))
                .thenReturn(List.of(tx));

        FolioStatementResponse statement = folioService.getFolioStatement("CABIN-7012", "Sky Bar");
        assertThat(statement.getItems()).hasSize(1);
        assertThat(statement.getItems().get(0).getServiceArea()).isEqualTo("Sky Bar");
    }

    @Test
    @DisplayName("chargeFolio debits balance successfully")
    void testChargeFolioSuccess() {
        when(folioRepository.findByCabinNumberWithLock("CABIN-7012")).thenReturn(Optional.of(folio));

        folioService.chargeFolio("CABIN-7012", BigDecimal.valueOf(300.00));
        assertThat(folio.getCurrentBalance()).isEqualByComparingTo(BigDecimal.valueOf(500.00));
    }

    @Test
    @DisplayName("refundFolio credits balance successfully")
    void testRefundFolioSuccess() {
        when(folioRepository.findByCabinNumberWithLock("CABIN-7012")).thenReturn(Optional.of(folio));

        folioService.refundFolio("CABIN-7012", BigDecimal.valueOf(50.00));
        assertThat(folio.getCurrentBalance()).isEqualByComparingTo(BigDecimal.valueOf(150.00));
    }

    @Test
    @DisplayName("seedDefaultFolios creates entries when not present")
    void testSeedDefaultFolios() {
        when(folioRepository.findByCabinNumber(anyString())).thenReturn(Optional.empty());

        folioService.seedDefaultFolios();
        verify(folioRepository, times(3)).save(any(CabinFolioAccount.class));
    }
}
