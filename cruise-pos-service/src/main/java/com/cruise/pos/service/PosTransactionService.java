package com.cruise.pos.service;

import com.cruise.pos.dto.*;
import com.cruise.pos.entity.*;
import com.cruise.pos.enums.*;
import com.cruise.pos.exception.*;
import com.cruise.pos.repository.*;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.*;

@Service
@RequiredArgsConstructor
@Slf4j
public class PosTransactionService {

    private final PosTransactionRepository transactionRepository;
    private final PosShiftRepository shiftRepository;
    private final PosIdempotencyRepository idempotencyRepository;
    private final PosAuditLogRepository auditLogRepository;
    private final PosFolioService folioService;
    private final PosShiftService shiftService;
    private final ObjectMapper objectMapper;
    private final PosManagerAuthService managerAuthService;
    @org.springframework.lang.Nullable
    private final TransactionTemplate transactionTemplate;

    @Transactional
    public TransactionResponse processChargeTransaction(String idempotencyKey, CreateTransactionRequest request) {
        return doProcessChargeTransaction(idempotencyKey, request);
    }

    private TransactionResponse doProcessChargeTransaction(String idempotencyKey, CreateTransactionRequest request) {
        // 1. Idempotency Check
        PosIdempotencyRecord idempotencyRecord = null;
        if (idempotencyKey != null && !idempotencyKey.isBlank()) {
            Optional<PosIdempotencyRecord> existingRecord = idempotencyRepository.findByIdempotencyKey(idempotencyKey);
            if (existingRecord.isPresent()) {
                PosIdempotencyRecord record = existingRecord.get();
                if (record.getStatus() == IdempotencyStatus.COMPLETED) {
                    if (record.getResponseBody() != null) {
                        try {
                            log.info("Idempotent request detected for key {}. Returning cached response.", idempotencyKey);
                            return objectMapper.readValue(record.getResponseBody(), TransactionResponse.class);
                        } catch (JsonProcessingException e) {
                            log.error("Failed to deserialize cached idempotency response: {}", e.getMessage());
                        }
                    }
                    return transactionRepository.findByIdempotencyKey(idempotencyKey)
                            .map(this::toResponse)
                            .orElseThrow(() -> new IdempotencyConflictException("Idempotency Key đã được sử dụng: " + idempotencyKey));
                }
                throw new IdempotencyConflictException("Giao dịch đang được xử lý song song với Idempotency Key: " + idempotencyKey);
            } else {
                idempotencyRecord = PosIdempotencyRecord.builder()
                        .idempotencyKey(idempotencyKey)
                        .requestPath("/api/pos/transactions")
                        .status(IdempotencyStatus.IN_PROGRESS)
                        .build();
                try {
                    idempotencyRecord = idempotencyRepository.saveAndFlush(idempotencyRecord);
                } catch (DataIntegrityViolationException ex) {
                    throw new IdempotencyConflictException("Giao dịch đang được xử lý song song với Idempotency Key: " + idempotencyKey);
                }
            }
        }

        // 2. Resolve Active Shift
        PosShift shift;
        if (request.getShiftId() != null) {
            shift = shiftRepository.findByIdWithLock(request.getShiftId())
                    .orElseThrow(() -> new BusinessRuleException("Không tìm thấy ca làm việc ID: " + request.getShiftId()));
            if (shift.getStatus() != ShiftStatus.OPEN || !shift.getStationId().equals(request.getStationId())) {
                throw new BusinessRuleException("Ca làm việc không còn mở hoặc không thuộc trạm này!");
            }
        } else {
            shift = shiftService.openShift(request.getStationId(), request.getCashierId(), BigDecimal.ZERO);
        }

        // 3. Calculate Item totals and Subtotal
        BigDecimal subtotal = BigDecimal.ZERO;
        List<PosTransactionItem> items = new ArrayList<>();
        for (TransactionItemRequest itemReq : request.getItems()) {
            BigDecimal lineTotal = itemReq.getUnitPrice().multiply(BigDecimal.valueOf(itemReq.getQuantity()));
            subtotal = subtotal.add(lineTotal);

            PosTransactionItem item = PosTransactionItem.builder()
                    .itemCode(itemReq.getItemCode())
                    .itemName(itemReq.getItemName())
                    .category(itemReq.getCategory())
                    .quantity(itemReq.getQuantity())
                    .unitPrice(itemReq.getUnitPrice())
                    .totalPrice(lineTotal)
                    .build();
            items.add(item);
        }

        BigDecimal tax = request.getTax() != null ? request.getTax() : BigDecimal.ZERO;
        BigDecimal gratuity = request.getGratuity() != null ? request.getGratuity() : BigDecimal.ZERO;
        BigDecimal discount = request.getDiscount() != null ? request.getDiscount() : BigDecimal.ZERO;
        BigDecimal totalAmount = subtotal.add(tax).add(gratuity).subtract(discount);
        if (totalAmount.compareTo(BigDecimal.ZERO) < 0) {
            totalAmount = BigDecimal.ZERO;
        }

        // 4. Folio Balance Check & Debit if CABIN_FOLIO
        if (request.getPaymentMethod() == PaymentMethod.CABIN_FOLIO) {
            if (request.getCabinNumber() == null || request.getCabinNumber().isBlank()) {
                throw new BusinessRuleException("Thanh toán Folio cabin yêu cầu số phòng (cabinNumber)!");
            }
            folioService.chargeFolio(request.getCabinNumber(), totalAmount);
        }

        // 5. Build and Save Transaction
        String txUuid = (request.getTransactionUuid() != null && !request.getTransactionUuid().isBlank())
                ? request.getTransactionUuid()
                : UUID.randomUUID().toString();

        PosTransaction transaction = PosTransaction.builder()
                .transactionUuid(txUuid)
                .idempotencyKey(idempotencyKey)
                .shiftId(shift.getId())
                .stationId(request.getStationId())
                .cashierId(request.getCashierId())
                .passengerId(request.getPassengerId())
                .cabinNumber(request.getCabinNumber())
                .guestName(request.getGuestName())
                .paymentMethod(request.getPaymentMethod())
                .type(TransactionType.CHARGE)
                .status(TransactionStatus.COMPLETED)
                .subtotal(subtotal)
                .tax(tax)
                .gratuity(gratuity)
                .discount(discount)
                .totalAmount(totalAmount)
                .refundedAmount(BigDecimal.ZERO)
                .currency("USD")
                .settled(false)
                .notes(request.getNotes())
                .build();

        for (PosTransactionItem item : items) {
            transaction.addItem(item);
        }

        PosTransaction savedTx = transactionRepository.save(transaction);

        // 6. Update Shift Metrics
        shift.addSale(totalAmount);
        shiftRepository.save(shift);

        // 7. Audit Log
        PosAuditLog auditLog = PosAuditLog.builder()
                .transactionId(savedTx.getId())
                .action("CHARGE")
                .performedBy(request.getCashierId())
                .reason("Khởi tạo thanh toán POS")
                .details(String.format("Station: %s, Total: $%.2f, Method: %s",
                        request.getStationId(), totalAmount, request.getPaymentMethod()))
                .build();
        auditLogRepository.save(auditLog);

        TransactionResponse response = toResponse(savedTx);

        // 8. Update Idempotency Record
        if (idempotencyRecord != null) {
            try {
                String body = objectMapper.writeValueAsString(response);
                idempotencyRecord.setResponseBody(body);
                idempotencyRecord.setStatusCode(201);
                idempotencyRecord.setTransactionId(savedTx.getId());
                idempotencyRecord.setStatus(IdempotencyStatus.COMPLETED);
                idempotencyRepository.save(idempotencyRecord);
            } catch (JsonProcessingException e) {
                log.error("Failed to serialize idempotency response: {}", e.getMessage());
            }
        }

        return response;
    }

    @Transactional
    public TransactionResponse voidTransaction(Long transactionId, VoidTransactionRequest request) {
        PosTransaction tx = transactionRepository.findByIdWithLock(transactionId)
                .orElseThrow(() -> new ResourceNotFoundException("Không tìm thấy giao dịch ID: " + transactionId));

        if (tx.getStatus() != TransactionStatus.COMPLETED) {
            throw new BusinessRuleException("Chỉ giao dịch ở trạng thái COMPLETED mới được phép Hủy (Void). Trạng thái hiện tại: " + tx.getStatus());
        }

        if (Boolean.TRUE.equals(tx.getSettled())) {
            throw new BusinessRuleException("Giao dịch đã được chốt quyết toán ca (Z-Report). Không thể Void, vui lòng thực hiện Hoàn tiền (Refund)!");
        }

        // Release cabin folio funds if applicable
        if (tx.getPaymentMethod() == PaymentMethod.CABIN_FOLIO && tx.getCabinNumber() != null) {
            folioService.refundFolio(tx.getCabinNumber(), tx.getTotalAmount());
        }

        tx.setStatus(TransactionStatus.VOIDED);
        tx.setVoidReason(request.getVoidReason());
        PosTransaction updatedTx = transactionRepository.save(tx);

        // Adjust shift metrics
        if (tx.getShiftId() != null) {
            shiftRepository.findById(tx.getShiftId()).ifPresent(shift -> {
                shift.addRefund(tx.getTotalAmount());
                shiftRepository.save(shift);
            });
        }

        // Audit Log
        PosAuditLog auditLog = PosAuditLog.builder()
                .transactionId(tx.getId())
                .action("VOID")
                .performedBy(request.getPerformedBy())
                .reason(request.getVoidReason())
                .details(String.format("Voided same-shift transaction ID %d for amount $%.2f", tx.getId(), tx.getTotalAmount()))
                .build();
        auditLogRepository.save(auditLog);

        return toResponse(updatedTx);
    }

    @Transactional
    public TransactionResponse refundTransaction(Long transactionId, RefundTransactionRequest request) {
        PosTransaction tx = transactionRepository.findByIdWithLock(transactionId)
                .orElseThrow(() -> new ResourceNotFoundException("Không tìm thấy giao dịch ID: " + transactionId));

        if (tx.getStatus() == TransactionStatus.VOIDED) {
            throw new BusinessRuleException("Không thể hoàn tiền cho giao dịch đã bị Void!");
        }

        // Manager PIN Verification
        if (request.getManagerId() == null || request.getManagerId().isBlank()
                || !managerAuthService.verifyRefundApproval(request.getManagerId(), request.getManagerPin())) {
            throw new UnauthorizedManagerOverrideException("Mã PIN Quản lý không hợp lệ hoặc không có quyền phê duyệt Hoàn tiền!");
        }

        BigDecimal refundAmt = request.getRefundAmount();
        BigDecimal remaining = tx.getRemainingRefundableAmount();

        if (refundAmt.compareTo(remaining) > 0) {
            throw new BusinessRuleException(String.format(
                    "Số tiền hoàn ($%.2f) vượt quá số dư còn lại của giao dịch gốc ($%.2f)!",
                    refundAmt, remaining
            ));
        }

        tx.setRefundedAmount(tx.getRefundedAmount().add(refundAmt));
        if (tx.getRefundedAmount().compareTo(tx.getTotalAmount()) >= 0) {
            tx.setStatus(TransactionStatus.FULLY_REFUNDED);
        } else {
            tx.setStatus(TransactionStatus.PARTIALLY_REFUNDED);
        }

        PosTransaction updatedTx = transactionRepository.save(tx);

        // Refund Folio if applicable
        if (tx.getPaymentMethod() == PaymentMethod.CABIN_FOLIO && tx.getCabinNumber() != null) {
            folioService.refundFolio(tx.getCabinNumber(), refundAmt);
        }

        // Adjust shift metrics
        if (tx.getShiftId() != null) {
            shiftRepository.findById(tx.getShiftId()).ifPresent(shift -> {
                shift.addRefund(refundAmt);
                shiftRepository.save(shift);
            });
        }

        // Audit Log
        PosAuditLog auditLog = PosAuditLog.builder()
                .transactionId(tx.getId())
                .action("REFUND")
                .performedBy(request.getPerformedBy())
                .managerId(request.getManagerId())
                .reason(request.getRefundReason())
                .details(String.format("Refunded $%.2f. New status: %s", refundAmt, tx.getStatus()))
                .build();
        auditLogRepository.save(auditLog);

        return toResponse(updatedTx);
    }

    @Transactional(readOnly = true)
    public TransactionResponse getTransactionById(Long id) {
        PosTransaction tx = transactionRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Không tìm thấy giao dịch ID: " + id));
        return toResponse(tx);
    }

    @Transactional(readOnly = true)
    public TransactionResponse getTransactionByUuid(String uuid) {
        PosTransaction tx = transactionRepository.findByTransactionUuid(uuid)
                .orElseThrow(() -> new ResourceNotFoundException("Không tìm thấy giao dịch UUID: " + uuid));
        return toResponse(tx);
    }

    @Transactional(readOnly = true)
    public Page<TransactionResponse> getTransactionsWithFilters(
            String stationId,
            String cashierId,
            TransactionStatus status,
            PaymentMethod paymentMethod,
            String cabinNumber,
            Instant startDate,
            Instant endDate,
            Pageable pageable
    ) {
        return transactionRepository.findWithFilters(
            stationId, cashierId, status, paymentMethod, cabinNumber, startDate, endDate, pageable
        ).map(this::toResponse);
    }

    public BatchSyncResponse processBatchSync(BatchSyncRequest batch) {
        int received = (batch != null && batch.getTransactions() != null) ? batch.getTransactions().size() : 0;
        int processed = 0;
        int duplicates = 0;
        int failed = 0;
        List<String> processedUuids = new ArrayList<>();
        List<String> duplicateUuids = new ArrayList<>();
        List<String> failedReasons = new ArrayList<>();

        if (batch != null && batch.getTransactions() != null) {
            for (CreateTransactionRequest txReq : batch.getTransactions()) {
                if (txReq.getTransactionUuid() == null || txReq.getTransactionUuid().isBlank()) {
                    failed++;
                    failedReasons.add("UUID null: transactionUuid bắt buộc khi đồng bộ ngoại tuyến");
                    continue;
                }

                try {
                    if (transactionRepository.findByTransactionUuid(txReq.getTransactionUuid()).isPresent()) {
                        duplicates++;
                        duplicateUuids.add(txReq.getTransactionUuid());
                        continue;
                    }

                    TransactionResponse res;
                    if (transactionTemplate != null) {
                        res = transactionTemplate.execute(status ->
                                doProcessChargeTransaction(txReq.getTransactionUuid(), txReq)
                        );
                    } else {
                        res = doProcessChargeTransaction(txReq.getTransactionUuid(), txReq);
                    }

                    if (res != null) {
                        processedUuids.add(res.getTransactionUuid());
                        processed++;
                    }
                } catch (Exception ex) {
                    failed++;
                    failedReasons.add(String.format("UUID %s: %s", txReq.getTransactionUuid(), ex.getMessage()));
                }
            }
        }

        return BatchSyncResponse.builder()
                .deviceId(batch != null ? batch.getDeviceId() : null)
                .totalReceived(received)
                .successfullyProcessed(processed)
                .duplicateCount(duplicates)
                .failedCount(failed)
                .processedUuids(processedUuids)
                .duplicateUuids(duplicateUuids)
                .failedReasons(failedReasons)
                .build();
    }

    public TransactionResponse toResponse(PosTransaction tx) {
        List<TransactionItemResponse> itemResponses = new ArrayList<>();
        if (tx.getItems() != null) {
            for (PosTransactionItem item : tx.getItems()) {
                itemResponses.add(TransactionItemResponse.builder()
                        .id(item.getId())
                        .itemCode(item.getItemCode())
                        .itemName(item.getItemName())
                        .category(item.getCategory())
                        .quantity(item.getQuantity())
                        .unitPrice(item.getUnitPrice())
                        .totalPrice(item.getTotalPrice())
                        .build());
            }
        }

        return TransactionResponse.builder()
                .id(tx.getId())
                .transactionUuid(tx.getTransactionUuid())
                .idempotencyKey(tx.getIdempotencyKey())
                .shiftId(tx.getShiftId())
                .stationId(tx.getStationId())
                .cashierId(tx.getCashierId())
                .passengerId(tx.getPassengerId())
                .cabinNumber(tx.getCabinNumber())
                .guestName(tx.getGuestName())
                .paymentMethod(tx.getPaymentMethod())
                .type(tx.getType())
                .status(tx.getStatus())
                .subtotal(tx.getSubtotal())
                .tax(tx.getTax())
                .gratuity(tx.getGratuity())
                .discount(tx.getDiscount())
                .totalAmount(tx.getTotalAmount())
                .refundedAmount(tx.getRefundedAmount())
                .remainingRefundableAmount(tx.getRemainingRefundableAmount())
                .currency(tx.getCurrency())
                .settled(tx.getSettled())
                .originalTransactionId(tx.getOriginalTransactionId())
                .voidReason(tx.getVoidReason())
                .notes(tx.getNotes())
                .createdAt(tx.getCreatedAt())
                .items(itemResponses)
                .build();
    }
}
