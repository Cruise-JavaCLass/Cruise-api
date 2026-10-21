package com.cruise.pos.service;

import com.cruise.pos.dto.SettlementLineItemDto;
import com.cruise.pos.dto.SettlementPreviewResponse;
import com.cruise.pos.dto.SettlementResponse;
import com.cruise.pos.entity.CabinFolioAccount;
import com.cruise.pos.entity.FolioSettlement;
import com.cruise.pos.enums.FolioStatus;
import com.cruise.pos.enums.PaymentMethod;
import com.cruise.pos.enums.TransactionStatus;
import com.cruise.pos.exception.BusinessRuleException;
import com.cruise.pos.exception.FolioAlreadySettledException;
import com.cruise.pos.exception.FolioNotActiveException;
import com.cruise.pos.exception.IdempotencyConflictException;
import com.cruise.pos.exception.IdempotencyKeyRequiredException;
import com.cruise.pos.exception.InsufficientTenderException;
import com.cruise.pos.exception.OpenDisputesPendingException;
import com.cruise.pos.exception.ResourceNotFoundException;
import com.cruise.pos.repository.CabinFolioAccountRepository;
import com.cruise.pos.repository.FolioSettlementRepository;
import com.cruise.pos.repository.PosAuditLogRepository;
import com.cruise.pos.repository.PosTransactionRepository;
import com.cruise.pos.support.SettlementTestData;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.List;
import java.util.Set;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.reset;

@SpringBootTest
@ActiveProfiles("test")
@Transactional
@DisplayName("Folio settlement engine")
class FolioSettlementServiceTest {

    private static final Set<String> FRONT_DESK = Set.of("FINANCE_FRONT_DESK");
    private static final Set<String> MANAGER = Set.of("OPERATIONS_MANAGER");

    @Autowired
    private FolioSettlementService settlementService;

    @Autowired
    private PosFolioService folioService;

    @Autowired
    private CabinFolioAccountRepository folioRepository;

    @Autowired
    private PosTransactionRepository transactionRepository;

    @Autowired
    private FolioSettlementRepository settlementRepository;

    @Autowired
    private PosAuditLogRepository auditLogRepository;

    @MockitoBean
    private DisputeHoldProvider disputeHoldProvider;

    private String cabin;
    private CabinFolioAccount folio;

    @BeforeEach
    void setUp() {
        reset(disputeHoldProvider);
        given(disputeHoldProvider.resolve(anyString())).willReturn(DisputeHold.none());

        cabin = SettlementTestData.uniqueCabin("CABIN-SETTLE");
        folio = SettlementTestData.newFolio(folioRepository, cabin, "Nguyễn Văn An", new BigDecimal("120.00"));

        SettlementTestData.saveCharge(transactionRepository, cabin,
                new BigDecimal("80.00"), new BigDecimal("8.00"), BigDecimal.ZERO, BigDecimal.ZERO,
                "Sky Bar Cocktail", "BEVERAGE", 2, new BigDecimal("40.00"), TransactionStatus.COMPLETED);
        SettlementTestData.saveCharge(transactionRepository, cabin,
                new BigDecimal("20.00"), new BigDecimal("2.00"), new BigDecimal("10.00"), BigDecimal.ZERO,
                "Spa Massage", "SPA", 1, new BigDecimal("20.00"), TransactionStatus.COMPLETED);
    }

    @Test
    @DisplayName("Preview cộng đúng subtotal, thuế, gratuity và số dư phải thu")
    void previewComputesTotals() {
        SettlementPreviewResponse preview = settlementService.preview(cabin);

        assertThat(preview.getCabinNumber()).isEqualTo(cabin);
        assertThat(preview.getPrimaryGuestName()).isEqualTo("Nguyễn Văn An");
        assertThat(preview.getSubtotal()).isEqualByComparingTo("100.00");
        assertThat(preview.getTaxTotal()).isEqualByComparingTo("10.00");
        assertThat(preview.getGratuityTotal()).isEqualByComparingTo("10.00");
        assertThat(preview.getNetAmountDue()).isEqualByComparingTo("120.00");
        assertThat(preview.isEligibleForSettlement()).isTrue();
        assertThat(preview.getBlockingReasons()).isEmpty();
        assertThat(preview.getLineItems()).isNotEmpty();
    }

    @Test
    @DisplayName("Line items có tổng đúng bằng số dư folio")
    void lineItemsSumMatchesBalance() {
        SettlementPreviewResponse preview = settlementService.preview(cabin);
        BigDecimal sum = preview.getLineItems().stream()
                .map(SettlementLineItemDto::getAmount)
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        assertThat(sum).isEqualByComparingTo(preview.getNetAmountDue());
    }

    @Test
    @DisplayName("Preview cabin không tồn tại trả ResourceNotFoundException")
    void previewUnknownCabin() {
        assertThatThrownBy(() -> settlementService.preview("CABIN-KHONG-TON-TAI"))
                .isInstanceOf(ResourceNotFoundException.class);
    }

    @Test
    @DisplayName("Tất toán thành công chuyển folio sang SETTLED và ghi audit log")
    void settleSuccess() {
        String key = UUID.randomUUID().toString();

        SettleOutcome outcome = settlementService.settle(cabin, key, PaymentMethod.CASH,
                new BigDecimal("150.00"), "Khách trả tiền mặt", false, "cashier-9", FRONT_DESK);

        SettlementResponse response = outcome.response();
        assertThat(outcome.replayed()).isFalse();
        assertThat(response.getSettlementUuid()).isNotBlank();
        assertThat(response.getInvoiceNumber()).startsWith("INV-").endsWith("00001");
        assertThat(response.getNetAmount()).isEqualByComparingTo("120.00");
        assertThat(response.getChangeGiven()).isEqualByComparingTo("30.00");
        assertThat(response.getFolioStatus()).isEqualTo(FolioStatus.SETTLED);

        CabinFolioAccount reloaded = folioRepository.findByCabinNumber(cabin).orElseThrow();
        assertThat(reloaded.getStatus()).isEqualTo(FolioStatus.SETTLED);
        assertThat(reloaded.getSettlementId()).isEqualTo(settlementRepository
                .findBySettlementUuid(response.getSettlementUuid()).orElseThrow().getId());
        assertThat(reloaded.getSettledAt()).isNotNull();

        assertThat(auditLogRepository.findAll().stream()
                .filter(log -> "SETTLE_FOLIO".equals(log.getAction()))
                .anyMatch(log -> log.getDetails().contains(cabin))).isTrue();
    }

    @Test
    @DisplayName("Replay cùng Idempotency-Key trả lại đúng settlement cũ, không tạo bản ghi mới")
    void settleReplayIsIdempotent() {
        String key = UUID.randomUUID().toString();
        BigDecimal tendered = new BigDecimal("120.00");

        SettleOutcome first = settlementService.settle(cabin, key, PaymentMethod.CASH, tendered,
                null, false, "cashier-9", FRONT_DESK);
        SettleOutcome second = settlementService.settle(cabin, key, PaymentMethod.CASH, tendered,
                null, false, "cashier-9", FRONT_DESK);

        assertThat(second.replayed()).isTrue();
        assertThat(second.response().getSettlementUuid()).isEqualTo(first.response().getSettlementUuid());
        assertThat(second.response().getInvoiceNumber()).isEqualTo(first.response().getInvoiceNumber());
        assertThat(settlementRepository.countByCabinNumber(cabin)).isEqualTo(1);
    }

    @Test
    @DisplayName("Cùng key nhưng payload khác trả lỗi xung đột idempotency")
    void sameKeyDifferentPayloadConflicts() {
        String key = UUID.randomUUID().toString();
        settlementService.settle(cabin, key, PaymentMethod.CASH, new BigDecimal("120.00"),
                null, false, "cashier-9", FRONT_DESK);

        assertThatThrownBy(() -> settlementService.settle(cabin, key, PaymentMethod.CASH,
                new BigDecimal("130.00"), null, false, "cashier-9", FRONT_DESK))
                .isInstanceOf(IdempotencyConflictException.class)
                .hasMessageContaining("IDEMPOTENCY_KEY_REUSED");
    }

    @Test
    @DisplayName("Số tiền khách đưa nhỏ hơn số phải thu bị từ chối 422")
    void insufficientTenderRejected() {
        assertThatThrownBy(() -> settlementService.settle(cabin, UUID.randomUUID().toString(),
                PaymentMethod.CASH, new BigDecimal("119.99"), null, false, "cashier-9", FRONT_DESK))
                .isInstanceOf(InsufficientTenderException.class)
                .hasMessageContaining("INSUFFICIENT_TENDER");
    }

    @Test
    @DisplayName("Không cho tất toán bằng chính folio")
    void settleWithFolioPaymentRejected() {
        assertThatThrownBy(() -> settlementService.settle(cabin, UUID.randomUUID().toString(),
                PaymentMethod.CABIN_FOLIO, new BigDecimal("120.00"), null, false, "cashier-9", FRONT_DESK))
                .isInstanceOf(BusinessRuleException.class);
    }

    @Test
    @DisplayName("Tất toán lần hai trên folio đã SETTLED trả 409")
    void settleTwiceConflicts() {
        settlementService.settle(cabin, UUID.randomUUID().toString(), PaymentMethod.CASH,
                new BigDecimal("120.00"), null, false, "cashier-9", FRONT_DESK);

        assertThatThrownBy(() -> settlementService.settle(cabin, UUID.randomUUID().toString(),
                PaymentMethod.CASH, new BigDecimal("120.00"), null, false, "cashier-9", FRONT_DESK))
                .isInstanceOf(FolioAlreadySettledException.class)
                .hasMessageContaining("FOLIO_ALREADY_SETTLED");
    }

    @Test
    @DisplayName("Sau khi tất toán, folio không thể ghi nợ thêm")
    void chargeAfterSettleRejected() {
        settlementService.settle(cabin, UUID.randomUUID().toString(), PaymentMethod.CASH,
                new BigDecimal("120.00"), null, false, "cashier-9", FRONT_DESK);

        assertThatThrownBy(() -> folioService.chargeFolio(cabin, new BigDecimal("10.00")))
                .isInstanceOf(FolioNotActiveException.class)
                .hasMessageContaining("FOLIO_NOT_ACTIVE");
    }

    @Test
    @DisplayName("Thiếu hoặc sai định dạng Idempotency-Key bị từ chối 400")
    void idempotencyKeyValidated() {
        assertThatThrownBy(() -> settlementService.settle(cabin, null, PaymentMethod.CASH,
                new BigDecimal("120.00"), null, false, "cashier-9", FRONT_DESK))
                .isInstanceOf(IdempotencyKeyRequiredException.class);

        assertThatThrownBy(() -> settlementService.settle(cabin, "short", PaymentMethod.CASH,
                new BigDecimal("120.00"), null, false, "cashier-9", FRONT_DESK))
                .isInstanceOf(IdempotencyKeyRequiredException.class);
    }

    @Test
    @DisplayName("Còn khiếu nại mở thì chặn tất toán")
    void openDisputesBlockSettlement() {
        given(disputeHoldProvider.resolve(cabin))
                .willReturn(new DisputeHold(new BigDecimal("15.00"), 2));

        assertThatThrownBy(() -> settlementService.settle(cabin, UUID.randomUUID().toString(),
                PaymentMethod.CASH, new BigDecimal("120.00"), null, false, "cashier-9", FRONT_DESK))
                .isInstanceOf(OpenDisputesPendingException.class)
                .hasMessageContaining("OPEN_DISPUTES_PENDING");
    }

    @Test
    @DisplayName("forceWithHold yêu cầu role quản lý")
    void forceWithHoldRequiresManagerRole() {
        given(disputeHoldProvider.resolve(cabin))
                .willReturn(new DisputeHold(new BigDecimal("15.00"), 1));

        assertThatThrownBy(() -> settlementService.settle(cabin, UUID.randomUUID().toString(),
                PaymentMethod.CASH, new BigDecimal("120.00"), null, true, "cashier-9", FRONT_DESK))
                .isInstanceOf(AccessDeniedException.class);
    }

    @Test
    @DisplayName("Quản lý dùng forceWithHold: khoản khiếu nại được giữ lại, không thu")
    void managerCanForceWithHold() {
        given(disputeHoldProvider.resolve(cabin))
                .willReturn(new DisputeHold(new BigDecimal("15.00"), 1));

        SettleOutcome outcome = settlementService.settle(cabin, UUID.randomUUID().toString(),
                PaymentMethod.CASH, new BigDecimal("105.00"), null, true, "manager-1", MANAGER);

        assertThat(outcome.response().getNetAmount()).isEqualByComparingTo("105.00");
        FolioSettlement settlement = settlementRepository
                .findBySettlementUuid(outcome.response().getSettlementUuid()).orElseThrow();
        assertThat(settlement.getHeldDisputeAmount()).isEqualByComparingTo("15.00");
        assertThat(settlement.getForceWithHold()).isTrue();
    }

    @Test
    @DisplayName("Tra cứu settlement theo uuid")
    void getSettlementByUuid() {
        SettleOutcome outcome = settlementService.settle(cabin, UUID.randomUUID().toString(),
                PaymentMethod.CASH, new BigDecimal("120.00"), null, false, "cashier-9", FRONT_DESK);

        FolioSettlement settlement = settlementService.getSettlement(outcome.response().getSettlementUuid());
        assertThat(settlement.getCabinNumber()).isEqualTo(cabin);
        assertThat(settlement.getLineItemsJson()).contains("Sky Bar Cocktail");

        assertThatThrownBy(() -> settlementService.getSettlement("khong-ton-tai"))
                .isInstanceOf(ResourceNotFoundException.class);
    }

    @Test
    @DisplayName("Số hoá đơn tăng đơn điệu theo từng cabin")
    void invoiceNumbersAreSequential() {
        SettlementTestData.newFolio(folioRepository, cabin + "-B", "Khách B", new BigDecimal("10.00"));

        SettleOutcome first = settlementService.settle(cabin, UUID.randomUUID().toString(),
                PaymentMethod.CASH, new BigDecimal("120.00"), null, false, "cashier-9", FRONT_DESK);
        SettleOutcome second = settlementService.settle(cabin + "-B", UUID.randomUUID().toString(),
                PaymentMethod.CASH, new BigDecimal("10.00"), null, false, "cashier-9", FRONT_DESK);

        assertThat(first.response().getInvoiceNumber()).endsWith("00001");
        assertThat(second.response().getInvoiceNumber()).endsWith("00001");
        assertThat(first.response().getInvoiceNumber()).isNotEqualTo(second.response().getInvoiceNumber());
    }

    @Test
    @DisplayName("Preview báo FOLIO_ALREADY_SETTLED sau khi tất toán")
    void previewAfterSettlementIsBlocked() {
        settlementService.settle(cabin, UUID.randomUUID().toString(), PaymentMethod.CASH,
                new BigDecimal("120.00"), null, false, "cashier-9", FRONT_DESK);

        SettlementPreviewResponse preview = settlementService.preview(cabin);
        assertThat(preview.isEligibleForSettlement()).isFalse();
        assertThat(preview.getBlockingReasons()).contains("FOLIO_ALREADY_SETTLED");
    }

    @Test
    @DisplayName("Line items phản ánh bút toán đảo")
    void voidedTransactionsAppearAsReversal() {
        SettlementTestData.saveCharge(transactionRepository, cabin,
                new BigDecimal("25.00"), BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO,
                "Voided Item", "BEVERAGE", 1, new BigDecimal("25.00"), TransactionStatus.VOIDED);

        List<SettlementLineItemDto> items = settlementService.preview(cabin).getLineItems();
        assertThat(items)
                .filteredOn(SettlementLineItemDto::isReversal)
                .isNotEmpty();
    }
}
