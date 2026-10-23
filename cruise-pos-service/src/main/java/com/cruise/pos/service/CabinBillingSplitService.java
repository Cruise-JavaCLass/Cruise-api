package com.cruise.pos.service;

import com.cruise.pos.dto.*;
import com.cruise.pos.entity.*;
import com.cruise.pos.enums.PaymentMethod;
import com.cruise.pos.enums.TransactionStatus;
import com.cruise.pos.enums.TransactionType;
import com.cruise.pos.exception.BusinessRuleException;
import com.cruise.pos.exception.DuplicateResourceException;
import com.cruise.pos.exception.InsufficientFolioBalanceException;
import com.cruise.pos.exception.ResourceNotFoundException;
import com.cruise.pos.repository.*;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Instant;
import java.util.*;

@Service
@RequiredArgsConstructor
@Slf4j
public class CabinBillingSplitService {

    private final CabinFolioAccountRepository folioRepository;
    private final CabinMemberRepository memberRepository;
    private final PosSignatureRepository signatureRepository;
    private final PosTransactionRepository transactionRepository;
    private final PosAuditLogRepository auditLogRepository;
    private final PosFolioService folioService;

    @Transactional
    public List<CabinMemberDto> getCabinMembers(String cabinNumber) {
        ensureSeededCabinMembers(cabinNumber);
        List<CabinMember> members = memberRepository.findByCabinNumber(cabinNumber);
        return members.stream().map(this::toMemberDto).toList();
    }

    @Transactional
    public SignatureResponse archiveSignature(ArchiveSignatureRequest request) {
        if (signatureRepository.existsByTransactionUuid(request.getTransactionUuid())) {
            throw new DuplicateResourceException("Signature already archived for transaction: " + request.getTransactionUuid());
        }

        String rawBase64 = request.getSignatureImageBase64().trim();
        if (rawBase64.isBlank() || rawBase64.length() < 30 || rawBase64.length() > 2_000_000) {
            throw new BusinessRuleException("Chữ ký số không hợp lệ hoặc để trống!");
        }

        // Strip data URI prefix if present (e.g., data:image/png;base64,...)
        String base64 = rawBase64.replaceFirst("^data:image/[^;]+;base64,", "");
        try {
            java.util.Base64.getDecoder().decode(base64);
        } catch (IllegalArgumentException e) {
            throw new BusinessRuleException("Định dạng dữ liệu chữ ký Base64 không hợp lệ!");
        }

        String checksum = calculateSha256(base64);

        PosSignature signature = PosSignature.builder()
                .transactionUuid(request.getTransactionUuid())
                .transactionId(request.getTransactionId())
                .cabinNumber(request.getCabinNumber())
                .passengerId(request.getPassengerId())
                .signerName(request.getSignerName())
                .signatureImageBase64(base64)
                .sha256Checksum(checksum)
                .capturedBy(request.getCapturedBy())
                .amount(request.getAmount())
                .build();

        PosSignature saved = signatureRepository.save(signature);

        PosAuditLog auditLog = PosAuditLog.builder()
                .transactionId(saved.getTransactionId())
                .action("SIGNATURE_ARCHIVED")
                .performedBy(request.getCapturedBy() != null ? request.getCapturedBy() : "POS-TERMINAL")
                .reason("Archive digital signature for transaction verification")
                .details(String.format("Signer: %s, Cabin: %s, Checksum: %s",
                        request.getSignerName(), request.getCabinNumber(), checksum))
                .build();
        auditLogRepository.save(auditLog);

        log.info("Archived signature for txUuid={}, signer={}, checksum={}",
                request.getTransactionUuid(), request.getSignerName(), checksum);

        return toSignatureResponse(saved);
    }

    @Transactional(readOnly = true)
    public SignatureResponse getSignatureByTransactionUuid(String transactionUuid) {
        PosSignature signature = signatureRepository.findByTransactionUuid(transactionUuid)
                .orElseThrow(() -> new ResourceNotFoundException("Không tìm thấy chữ ký số cho giao dịch: " + transactionUuid));
        return toSignatureResponse(signature);
    }

    @Transactional
    public CabinSplitBillingResponse processSplitBilling(CabinSplitBillingRequest request) {
        String cabinNumber = request.getCabinNumber();
        CabinFolioAccount folio = folioRepository.findByCabinNumberWithLock(cabinNumber)
                .orElseThrow(() -> new ResourceNotFoundException("Không tìm thấy tài khoản Folio cho cabin: " + cabinNumber));

        ensureSeededCabinMembers(cabinNumber);

        BigDecimal totalSplitAmount = BigDecimal.ZERO;
        for (CabinSplitShareRequest share : request.getSplitShares()) {
            if (share.getAmount() == null || share.getAmount().compareTo(BigDecimal.ZERO) <= 0) {
                throw new BusinessRuleException("Số tiền chia cho từng khách phải lớn hơn 0");
            }
            totalSplitAmount = totalSplitAmount.add(share.getAmount());
        }

        // Check overall folio capacity
        if (!folio.canDebit(totalSplitAmount)) {
            throw new InsufficientFolioBalanceException(String.format(
                    "Tài khoản cabin %s không đủ hạn mức tín dụng! Hạn mức còn lại: $%.2f, Tổng yêu cầu chia bill: $%.2f",
                    cabinNumber, folio.getAvailableLimit(), totalSplitAmount
            ));
        }

        List<CabinSplitBillingResponse.SplitShareResult> splitResults = new ArrayList<>();

        for (CabinSplitShareRequest share : request.getSplitShares()) {
            CabinMember member = memberRepository.findByCabinNumberAndPassengerId(cabinNumber, share.getPassengerId())
                    .orElseThrow(() -> new BusinessRuleException(String.format(
                            "Hành khách ID %d không thuộc phòng %s!", share.getPassengerId(), cabinNumber
                    )));

            // 1. Authorized Signer & Minor Lock Check
            if (Boolean.TRUE.equals(member.getIsMinor()) || !Boolean.TRUE.equals(member.getIsAuthorizedSigner())) {
                throw new BusinessRuleException(String.format(
                        "Hành khách %s (ID: %d) là trẻ vị thành niên hoặc chưa được cấp quyền ký nhận hóa đơn (Authorized Signer Lock)!",
                        member.getGuestName(), member.getPassengerId()
                ));
            }

            // 2. Individual Credit Limit Check (Real-time Over-limit Lock)
            if (member.getIndividualCreditLimit() != null) {
                BigDecimal memberAvail = member.getIndividualCreditLimit().subtract(member.getIndividualBalance());
                if (memberAvail.compareTo(share.getAmount()) < 0) {
                    throw new InsufficientFolioBalanceException(String.format(
                            "Hành khách %s đã vượt hạn mức chi tiêu cá nhân! Hạn mức còn lại: $%.2f, Yêu cầu: $%.2f (Over-limit Lock)",
                            member.getGuestName(), memberAvail, share.getAmount()
                    ));
                }
                member.setIndividualBalance(member.getIndividualBalance().add(share.getAmount()));
                memberRepository.save(member);
            }

            // 3. Create Child Transaction Record for audit & non-repudiation
            String childTxUuid = (request.getTransactionUuid() != null ? request.getTransactionUuid() : UUID.randomUUID().toString())
                    + "-G" + member.getPassengerId();

            PosTransaction splitTx = PosTransaction.builder()
                    .transactionUuid(childTxUuid)
                    .shiftId(request.getShiftId())
                    .stationId(request.getStationId())
                    .cashierId(request.getCashierId())
                    .passengerId(member.getPassengerId())
                    .cabinNumber(cabinNumber)
                    .guestName(member.getGuestName())
                    .paymentMethod(PaymentMethod.CABIN_FOLIO)
                    .type(TransactionType.CHARGE)
                    .status(TransactionStatus.COMPLETED)
                    .subtotal(share.getAmount())
                    .tax(BigDecimal.ZERO)
                    .gratuity(BigDecimal.ZERO)
                    .discount(BigDecimal.ZERO)
                    .totalAmount(share.getAmount())
                    .refundedAmount(BigDecimal.ZERO)
                    .currency("USD")
                    .settled(false)
                    .notes(request.getNotes() != null ? request.getNotes() : "Cabin Split Charge for " + member.getGuestName())
                    .build();

            PosTransaction savedTx = transactionRepository.save(splitTx);

            // 4. Archive signature if provided in split share
            boolean signatureArchived = false;
            if (share.getSignatureBase64() != null && !share.getSignatureBase64().isBlank()) {
                ArchiveSignatureRequest sigReq = ArchiveSignatureRequest.builder()
                        .transactionUuid(savedTx.getTransactionUuid())
                        .transactionId(savedTx.getId())
                        .cabinNumber(cabinNumber)
                        .passengerId(member.getPassengerId())
                        .signerName(member.getGuestName())
                        .signatureImageBase64(share.getSignatureBase64())
                        .capturedBy(request.getCashierId())
                        .amount(share.getAmount())
                        .build();
                archiveSignature(sigReq);
                signatureArchived = true;
            }

            BigDecimal remainingLimit = member.getIndividualCreditLimit() != null
                    ? member.getIndividualCreditLimit().subtract(member.getIndividualBalance())
                    : folio.getAvailableLimit().subtract(totalSplitAmount);

            splitResults.add(CabinSplitBillingResponse.SplitShareResult.builder()
                    .passengerId(member.getPassengerId())
                    .guestName(member.getGuestName())
                    .amount(share.getAmount())
                    .transactionId(savedTx.getId())
                    .transactionUuid(savedTx.getTransactionUuid())
                    .signatureArchived(signatureArchived)
                    .remainingMemberLimit(remainingLimit)
                    .build());
        }

        // 5. Debit the overall Cabin Folio Account
        folio.debit(totalSplitAmount);
        folioRepository.save(folio);

        log.info("Successfully split bill for cabin {}: total=${}, shares={}",
                cabinNumber, totalSplitAmount, splitResults.size());

        return CabinSplitBillingResponse.builder()
                .cabinNumber(cabinNumber)
                .totalAmount(totalSplitAmount)
                .splitCount(splitResults.size())
                .splits(splitResults)
                .build();
    }

    public void ensureSeededCabinMembers(String cabinNumber) {
        if (!memberRepository.existsByCabinNumber(cabinNumber)) {
            CabinFolioAccount folio = folioRepository.findByCabinNumber(cabinNumber).orElse(null);
            if (folio == null || folio.getPassengerId() == null) {
                return;
            }
            String primaryName = folio.getPrimaryGuestName() != null ? folio.getPrimaryGuestName() : "Primary Guest";
            Long primaryPid = folio.getPassengerId();

            memberRepository.save(CabinMember.builder()
                    .cabinNumber(cabinNumber)
                    .passengerId(primaryPid)
                    .guestName(primaryName)
                    .age(38)
                    .isMinor(false)
                    .isAuthorizedSigner(true)
                    .individualCreditLimit(BigDecimal.valueOf(1000.00))
                    .individualBalance(BigDecimal.ZERO)
                    .build());
        }
    }

    private CabinMemberDto toMemberDto(CabinMember member) {
        BigDecimal avail = member.getIndividualCreditLimit() != null
                ? member.getIndividualCreditLimit().subtract(member.getIndividualBalance())
                : null;

        return CabinMemberDto.builder()
                .id(member.getId())
                .cabinNumber(member.getCabinNumber())
                .passengerId(member.getPassengerId())
                .guestName(member.getGuestName())
                .age(member.getAge())
                .isMinor(member.getIsMinor())
                .isAuthorizedSigner(member.getIsAuthorizedSigner())
                .individualCreditLimit(member.getIndividualCreditLimit())
                .individualBalance(member.getIndividualBalance())
                .availableLimit(avail)
                .build();
    }

    private SignatureResponse toSignatureResponse(PosSignature sig) {
        return SignatureResponse.builder()
                .id(sig.getId())
                .transactionUuid(sig.getTransactionUuid())
                .transactionId(sig.getTransactionId())
                .cabinNumber(sig.getCabinNumber())
                .passengerId(sig.getPassengerId())
                .signerName(sig.getSignerName())
                .signatureImageBase64(sig.getSignatureImageBase64())
                .sha256Checksum(sig.getSha256Checksum())
                .capturedBy(sig.getCapturedBy())
                .amount(sig.getAmount())
                .createdAt(sig.getCreatedAt() != null ? sig.getCreatedAt() : Instant.now())
                .build();
    }

    private String calculateSha256(String data) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(data.getBytes(StandardCharsets.UTF_8));
            StringBuilder hexString = new StringBuilder();
            for (byte b : hash) {
                String hex = Integer.toHexString(0xff & b);
                if (hex.length() == 1) hexString.append('0');
                hexString.append(hex);
            }
            return hexString.toString();
        } catch (NoSuchAlgorithmException e) {
            throw new RuntimeException("SHA-256 algorithm not available", e);
        }
    }
}
