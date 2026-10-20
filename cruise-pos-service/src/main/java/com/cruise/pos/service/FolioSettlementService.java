package com.cruise.pos.service;

import com.cruise.pos.dto.SettlementLineItemDto;
import com.cruise.pos.dto.SettlementPreviewResponse;
import com.cruise.pos.dto.SettlementResponse;
import com.cruise.pos.entity.CabinFolioAccount;
import com.cruise.pos.entity.FolioSettlement;
import com.cruise.pos.entity.PosAuditLog;
import com.cruise.pos.entity.PosTransaction;
import com.cruise.pos.enums.FolioStatus;
import com.cruise.pos.enums.PaymentMethod;
import com.cruise.pos.enums.SettlementStatus;
import com.cruise.pos.enums.TransactionStatus;
import com.cruise.pos.enums.TransactionType;
import com.cruise.pos.exception.FolioAlreadySettledException;
import com.cruise.pos.exception.FolioNotActiveException;
import com.cruise.pos.exception.IdempotencyKeyRequiredException;
import com.cruise.pos.exception.InsufficientTenderException;
import com.cruise.pos.exception.OpenDisputesPendingException;
import com.cruise.pos.exception.ResourceNotFoundException;
import com.cruise.pos.repository.CabinFolioAccountRepository;
import com.cruise.pos.repository.FolioSettlementRepository;
import com.cruise.pos.repository.PosAuditLogRepository;
import com.cruise.pos.repository.PosTransactionRepository;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

/**
 * Lõi nghiệp vụ tất toán folio cabin: xem trước số dư cuối cùng, thu tiền và
 * chuyển folio sang trạng thái SETTLED với đảm bảo idempotency + khoá bi quan.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class FolioSettlementService {

    private static final int MONEY_SCALE = 2;
    private static final Set<String> MANAGER_ROLES = Set.of("OPERATIONS_MANAGER", "SYSTEM_ADMIN");
    private static final String AUDIT_ACTION_SETTLE = "SETTLE_FOLIO";

    private final CabinFolioAccountRepository folioRepository;
    private final PosTransactionRepository transactionRepository;
    private final FolioSettlementRepository settlementRepository;
    private final PosAuditLogRepository auditLogRepository;
    private final DisputeHoldProvider disputeHoldProvider;
    private final InvoiceNumberAllocator invoiceNumberAllocator;
    private final PlatformTransactionManager transactionManager;
    private final ObjectMapper objectMapper;

    // ---------------------------------------------------------------------
    // Public API
    // ---------------------------------------------------------------------

    public SettlementPreviewResponse preview(String cabinNumber) {
        TransactionTemplate template = new TransactionTemplate(transactionManager);
        template.setReadOnly(true);
        return template.execute(status -> buildPreview(cabinNumber));
    }

    public FolioSettlement getSettlement(String settlementUuid) {
        return settlementRepository.findBySettlementUuid(settlementUuid)
                .orElseThrow(() -> new ResourceNotFoundException("Không tìm thấy bản ghi tất toán: " + settlementUuid));
    }

    /**
     * Tất toán folio. Không đánh dấu {@code @Transactional} ở đây để có thể xử lý
     * {@link DataIntegrityViolationException} của unique key ngoài ranh giới transaction
     * (tránh lỗi rollback-only) rồi trả lại kết quả đã ghi trước đó.
     */
    public SettleOutcome settle(String cabinNumber,
                                     String idempotencyKey,
                                     PaymentMethod paymentMethod,
                                     BigDecimal amountTendered,
                                     String notes,
                                     boolean forceWithHold,
                                     String cashierId,
                                     Set<String> roles) {

        validateIdempotencyKey(idempotencyKey);

        String fingerprint = fingerprint(cabinNumber, paymentMethod, amountTendered, forceWithHold);

        Optional<FolioSettlement> replayed = settlementRepository.findByIdempotencyKey(idempotencyKey);
        if (replayed.isPresent()) {
            return new SettleOutcome(replay(replayed.get(), fingerprint), true);
        }

        TransactionTemplate template = new TransactionTemplate(transactionManager);
        try {
            SettlementResponse response = template.execute(status -> doSettle(cabinNumber, idempotencyKey,
                    paymentMethod, amountTendered, notes, forceWithHold, cashierId, roles, fingerprint));
            return new SettleOutcome(response, false);
        } catch (DataIntegrityViolationException conflict) {
            log.info("Idempotency race detected for key {}, falling back to replay", idempotencyKey);
            FolioSettlement existing = settlementRepository.findByIdempotencyKey(idempotencyKey)
                    .orElseThrow(() -> conflict);
            return new SettleOutcome(replay(existing, fingerprint), true);
        }
    }

    // ---------------------------------------------------------------------
    // Preview
    // ---------------------------------------------------------------------

    private SettlementPreviewResponse buildPreview(String cabinNumber) {
        CabinFolioAccount folio = folioRepository.findByCabinNumber(cabinNumber)
                .orElseThrow(() -> new ResourceNotFoundException(
                        "Không tìm thấy tài khoản Folio cho cabin: " + cabinNumber));

        List<PosTransaction> transactions = transactionRepository
                .findByCabinNumberOrderByCreatedAtAsc(cabinNumber);

        Totals totals = computeTotals(transactions);
        DisputeHold hold = disputeHoldProvider.resolve(cabinNumber);

        BigDecimal netAmountDue = money(folio.getCurrentBalance().subtract(hold.heldAmount()));
        if (netAmountDue.signum() < 0) {
            netAmountDue = BigDecimal.ZERO.setScale(MONEY_SCALE, RoundingMode.HALF_UP);
        }

        List<String> blockingReasons = new ArrayList<>();
        if (folio.getStatus() == FolioStatus.SETTLED || folio.getStatus() == FolioStatus.CLOSED) {
            blockingReasons.add("FOLIO_ALREADY_SETTLED");
        }
        if (folio.getStatus() == FolioStatus.LOCKED) {
            blockingReasons.add("FOLIO_LOCKED");
        }
        if (hold.openDisputeCount() > 0) {
            blockingReasons.add("OPEN_DISPUTES_PENDING");
        }

        return SettlementPreviewResponse.builder()
                .cabinNumber(folio.getCabinNumber())
                .primaryGuestName(folio.getPrimaryGuestName())
                .currency("USD")
                .subtotal(totals.subtotal())
                .reversalsTotal(totals.reversals())
                .discountTotal(totals.discount())
                .taxTotal(totals.tax())
                .gratuityTotal(totals.gratuity())
                .netAmountDue(netAmountDue)
                .heldDisputeAmount(money(hold.heldAmount()))
                .openDisputeCount(hold.openDisputeCount())
                .eligibleForSettlement(blockingReasons.isEmpty())
                .blockingReasons(blockingReasons)
                .lineItems(buildLineItems(transactions))
                .build();
    }

    private Totals computeTotals(List<PosTransaction> transactions) {
        BigDecimal subtotal = BigDecimal.ZERO;
        BigDecimal tax = BigDecimal.ZERO;
        BigDecimal gratuity = BigDecimal.ZERO;
        BigDecimal discount = BigDecimal.ZERO;
        BigDecimal reversals = BigDecimal.ZERO;

        for (PosTransaction tx : transactions) {
            if (tx.getType() == TransactionType.CHARGE) {
                if (tx.getStatus() == TransactionStatus.COMPLETED
                        || tx.getStatus() == TransactionStatus.PARTIALLY_REFUNDED
                        || tx.getStatus() == TransactionStatus.FULLY_REFUNDED
                        || tx.getStatus() == TransactionStatus.VOIDED) {
                    subtotal = subtotal.add(nullSafe(tx.getSubtotal()));
                    tax = tax.add(nullSafe(tx.getTax()));
                    gratuity = gratuity.add(nullSafe(tx.getGratuity()));
                    discount = discount.add(nullSafe(tx.getDiscount()));
                }
                if (tx.getStatus() == TransactionStatus.VOIDED) {
                    reversals = reversals.add(nullSafe(tx.getTotalAmount()));
                }
            } else if (tx.getStatus() == TransactionStatus.COMPLETED) {
                reversals = reversals.add(nullSafe(tx.getTotalAmount()));
            }
            reversals = reversals.add(nullSafe(tx.getRefundedAmount()));
        }

        return new Totals(money(subtotal), money(tax), money(gratuity), money(discount), money(reversals));
    }

    private List<SettlementLineItemDto> buildLineItems(List<PosTransaction> transactions) {
        List<SettlementLineItemDto> items = new ArrayList<>();

        for (PosTransaction tx : transactions) {
            if (tx.getType() != TransactionType.CHARGE) {
                if (tx.getStatus() == TransactionStatus.COMPLETED) {
                    items.add(SettlementLineItemDto.builder()
                            .description(describeReversal(tx))
                            .serviceArea("POS")
                            .postedAt(tx.getCreatedAt())
                            .amount(money(nullSafe(tx.getTotalAmount()).negate()))
                            .isReversal(true)
                            .build());
                }
                continue;
            }

            if (tx.getStatus() == TransactionStatus.PENDING || tx.getStatus() == TransactionStatus.FAILED) {
                continue;
            }

            tx.getItems().forEach(item -> items.add(SettlementLineItemDto.builder()
                    .description(item.getQuantity() + " × " + item.getItemName())
                    .serviceArea(item.getCategory())
                    .postedAt(tx.getCreatedAt())
                    .amount(money(nullSafe(item.getTotalPrice())))
                    .isReversal(false)
                    .build()));

            if (tx.getTax() != null && tx.getTax().signum() > 0) {
                items.add(extraRow("Thuế hàng hải", tx, money(tx.getTax())));
            }
            if (tx.getGratuity() != null && tx.getGratuity().signum() > 0) {
                items.add(extraRow("Phí phục vụ", tx, money(tx.getGratuity())));
            }
            if (tx.getDiscount() != null && tx.getDiscount().signum() > 0) {
                items.add(extraRow("Giảm giá", tx, money(tx.getDiscount()).negate()));
            }
            if (tx.getStatus() == TransactionStatus.VOIDED) {
                items.add(SettlementLineItemDto.builder()
                        .description(describeReversal(tx))
                        .serviceArea("POS")
                        .postedAt(tx.getCreatedAt())
                        .amount(money(nullSafe(tx.getTotalAmount()).negate()))
                        .isReversal(true)
                        .build());
            }
            if (tx.getRefundedAmount() != null && tx.getRefundedAmount().signum() > 0) {
                items.add(SettlementLineItemDto.builder()
                        .description("Hoàn tiền giao dịch " + tx.getTransactionUuid())
                        .serviceArea("POS")
                        .postedAt(tx.getCreatedAt())
                        .amount(money(tx.getRefundedAmount()).negate())
                        .isReversal(true)
                        .build());
            }
        }

        items.sort(Comparator.comparing(SettlementLineItemDto::getPostedAt,
                Comparator.nullsLast(Comparator.naturalOrder())));
        return items;
    }

    private SettlementLineItemDto extraRow(String label, PosTransaction tx, BigDecimal amount) {
        return SettlementLineItemDto.builder()
                .description(label)
                .serviceArea("POS")
                .postedAt(tx.getCreatedAt())
                .amount(amount)
                .isReversal(false)
                .build();
    }

    private String describeReversal(PosTransaction tx) {
        return switch (tx.getType()) {
            case REFUND -> "Hoàn tiền giao dịch " + tx.getTransactionUuid();
            case VOID -> "Huỷ giao dịch " + tx.getTransactionUuid();
            default -> "Đảo giao dịch " + tx.getTransactionUuid();
        };
    }

    // ---------------------------------------------------------------------
    // Settle
    // ---------------------------------------------------------------------

    private SettlementResponse doSettle(String cabinNumber,
                                        String idempotencyKey,
                                        PaymentMethod paymentMethod,
                                        BigDecimal amountTendered,
                                        String notes,
                                        boolean forceWithHold,
                                        String cashierId,
                                        Set<String> roles,
                                        String fingerprint) {

        CabinFolioAccount folio = folioRepository.findByCabinNumberWithLock(cabinNumber)
                .orElseThrow(() -> new ResourceNotFoundException(
                        "Không tìm thấy tài khoản Folio cho cabin: " + cabinNumber));

        // Kiểm tra lại idempotency SAU khi đã giữ khoá folio: request song song cùng key
        // sẽ thấy bản ghi của request thắng thay vì báo folio đã tất toán.
        Optional<FolioSettlement> concurrent = settlementRepository.findByIdempotencyKey(idempotencyKey);
        if (concurrent.isPresent()) {
            return replay(concurrent.get(), fingerprint);
        }

        if (folio.getStatus() == FolioStatus.SETTLED || folio.getStatus() == FolioStatus.CLOSED) {
            throw new FolioAlreadySettledException(
                    "FOLIO_ALREADY_SETTLED: Cabin " + cabinNumber + " đã được tất toán trước đó");
        }
        if (folio.getStatus() == FolioStatus.LOCKED) {
            throw new FolioNotActiveException(
                    "FOLIO_LOCKED: Folio cabin " + cabinNumber + " đang bị khoá để điều tra");
        }
        if (paymentMethod == PaymentMethod.CABIN_FOLIO) {
            throw new com.cruise.pos.exception.BusinessRuleException(
                    "Không thể tất toán folio bằng phương thức CABIN_FOLIO");
        }

        DisputeHold hold = disputeHoldProvider.resolve(cabinNumber);
        BigDecimal netAmount = money(folio.getCurrentBalance().subtract(hold.heldAmount()));
        if (netAmount.signum() < 0) {
            netAmount = BigDecimal.ZERO.setScale(MONEY_SCALE, RoundingMode.HALF_UP);
        }

        if (hold.openDisputeCount() > 0 && !forceWithHold) {
            throw new OpenDisputesPendingException(
                    "OPEN_DISPUTES_PENDING: Cabin " + cabinNumber + " còn "
                            + hold.openDisputeCount() + " khiếu nại chưa xử lý");
        }
        if (forceWithHold && roles.stream().noneMatch(MANAGER_ROLES::contains)) {
            throw new AccessDeniedException(
                    "Chỉ quản lý vận hành mới được tất toán khi còn khiếu nại mở");
        }

        BigDecimal tendered = money(amountTendered);
        if (tendered.compareTo(netAmount) < 0) {
            throw new InsufficientTenderException(String.format(Locale.ROOT,
                    "INSUFFICIENT_TENDER: Số tiền nhận %.2f nhỏ hơn số tiền phải thu %.2f",
                    tendered, netAmount));
        }

        Instant settledAt = Instant.now();
        List<PosTransaction> transactions = transactionRepository.findByCabinNumberOrderByCreatedAtAsc(cabinNumber);
        Totals totals = computeTotals(transactions);
        List<SettlementLineItemDto> lineItems = buildLineItems(transactions);

        String invoiceNumber = invoiceNumberAllocator.allocate(cabinNumber);

        FolioSettlement settlement = FolioSettlement.builder()
                .settlementUuid(UUID.randomUUID().toString())
                .folioId(folio.getId())
                .cabinNumber(cabinNumber)
                .primaryGuestName(folio.getPrimaryGuestName())
                .idempotencyKey(idempotencyKey)
                .invoiceNumber(invoiceNumber)
                .subtotal(totals.subtotal())
                .reversalsTotal(totals.reversals())
                .tax(totals.tax())
                .gratuity(totals.gratuity())
                .discount(totals.discount())
                .netAmount(netAmount)
                .amountTendered(tendered)
                .changeGiven(money(tendered.subtract(netAmount)))
                .heldDisputeAmount(money(hold.heldAmount()))
                .currency("USD")
                .paymentMethod(paymentMethod)
                .cashierId(cashierId == null || cashierId.isBlank() ? "system" : cashierId)
                .forceWithHold(forceWithHold)
                .status(SettlementStatus.COMPLETED)
                .notes(notes)
                .requestFingerprint(fingerprint)
                .lineItemsJson(serializeLineItems(lineItems))
                .settledAt(settledAt)
                .build();

        FolioSettlement saved = settlementRepository.saveAndFlush(settlement);

        folio.markSettled(saved.getId(), settledAt);
        folioRepository.saveAndFlush(folio);

        writeAuditLog(saved, folio);

        log.info("Settled folio {} (settlement {}, invoice {}), net={}, tendered={}",
                cabinNumber, saved.getSettlementUuid(), invoiceNumber, netAmount, tendered);

        return toResponse(saved, folio.getStatus());
    }

    private void writeAuditLog(FolioSettlement settlement, CabinFolioAccount folio) {
        String details;
        try {
            Map<String, Object> map = new LinkedHashMap<>();
            map.put("cabinNumber", settlement.getCabinNumber());
            map.put("settlementUuid", settlement.getSettlementUuid());
            map.put("invoiceNumber", settlement.getInvoiceNumber());
            map.put("netAmount", settlement.getNetAmount());
            map.put("amountTendered", settlement.getAmountTendered());
            map.put("heldDisputeAmount", settlement.getHeldDisputeAmount());
            map.put("balanceBefore", folio.getCurrentBalance());
            map.put("idempotencyKey", settlement.getIdempotencyKey());
            details = objectMapper.writeValueAsString(map);
        } catch (Exception ex) {
            log.warn("Không thể tuần tự hoá audit details cho settlement {}: {}",
                    settlement.getSettlementUuid(), ex.getMessage());
            details = "{}";
        }

        auditLogRepository.save(PosAuditLog.builder()
                .transactionId(null)
                .action(AUDIT_ACTION_SETTLE)
                .performedBy(settlement.getCashierId())
                .reason(settlement.getNotes())
                .details(details.length() > 1024 ? details.substring(0, 1024) : details)
                .build());
    }

    // ---------------------------------------------------------------------
    // Helpers
    // ---------------------------------------------------------------------

    private SettlementResponse replay(FolioSettlement settlement, String fingerprint) {
        if (!settlement.getRequestFingerprint().equals(fingerprint)) {
            throw new com.cruise.pos.exception.IdempotencyConflictException(
                    "IDEMPOTENCY_KEY_REUSED: Idempotency-Key đã được dùng với payload khác");
        }
        String status = settlement.getStatus() == SettlementStatus.COMPLETED
                ? FolioStatus.SETTLED.name()
                : FolioStatus.CLOSED.name();
        return toResponse(settlement, FolioStatus.valueOf(status));
    }

    public SettlementResponse toResponse(FolioSettlement settlement, FolioStatus folioStatus) {
        return SettlementResponse.builder()
                .settlementUuid(settlement.getSettlementUuid())
                .invoiceNumber(settlement.getInvoiceNumber())
                .cabinNumber(settlement.getCabinNumber())
                .netAmount(settlement.getNetAmount())
                .amountTendered(settlement.getAmountTendered())
                .changeGiven(settlement.getChangeGiven())
                .paymentMethod(settlement.getPaymentMethod())
                .currency(settlement.getCurrency())
                .settledAt(settlement.getSettledAt())
                .folioStatus(folioStatus)
                .build();
    }

    private void validateIdempotencyKey(String idempotencyKey) {
        if (idempotencyKey == null || idempotencyKey.isBlank()) {
            throw new IdempotencyKeyRequiredException(
                    "IDEMPOTENCY_KEY_REQUIRED: Thiếu header Idempotency-Key");
        }
        if (idempotencyKey.length() < 8 || idempotencyKey.length() > 128) {
            throw new IdempotencyKeyRequiredException(
                    "IDEMPOTENCY_KEY_INVALID: Idempotency-Key phải dài từ 8 đến 128 ký tự");
        }
    }

    private String fingerprint(String cabinNumber, PaymentMethod paymentMethod,
                               BigDecimal amountTendered, boolean forceWithHold) {
        String raw = cabinNumber + "|" + paymentMethod + "|"
                + money(amountTendered).toPlainString() + "|" + forceWithHold;
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            return HexFormat.of().formatHex(digest.digest(raw.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException ex) {
            throw new IllegalStateException("SHA-256 không khả dụng", ex);
        }
    }

    private String serializeLineItems(List<SettlementLineItemDto> lineItems) {
        try {
            return objectMapper.writeValueAsString(lineItems);
        } catch (JsonProcessingException ex) {
            throw new IllegalStateException("Không thể lưu bảng kê tất toán", ex);
        }
    }

    private static BigDecimal nullSafe(BigDecimal value) {
        return value == null ? BigDecimal.ZERO : value;
    }

    private static BigDecimal money(BigDecimal value) {
        return nullSafe(value).setScale(MONEY_SCALE, RoundingMode.HALF_UP);
    }

    private record Totals(BigDecimal subtotal,
                          BigDecimal tax,
                          BigDecimal gratuity,
                          BigDecimal discount,
                          BigDecimal reversals) {
    }
}
