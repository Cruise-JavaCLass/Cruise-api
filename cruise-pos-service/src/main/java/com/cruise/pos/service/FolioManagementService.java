package com.cruise.pos.service;

import com.cruise.pos.entity.CabinFolio;
import com.cruise.pos.entity.PreAuthorizationHold;
import com.cruise.pos.enums.CabinFolioStatus;
import com.cruise.pos.enums.PreAuthHoldStatus;
import com.cruise.pos.exception.BusinessRuleException;
import com.cruise.pos.exception.FolioLimitExceededException;
import com.cruise.pos.exception.ResourceNotFoundException;
import com.cruise.pos.repository.CabinFolioRepository;
import com.cruise.pos.repository.PreAuthorizationHoldRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Duration;
import java.time.Instant;
import java.util.UUID;

/**
 * Kiểm soát hạn mức chi tiêu của folio cabin: tạm giữ tiền (Pre-Authorization Hold)
 * và chống chi tiêu vượt hạn mức (Overdraft Protection).
 *
 * <p>Quy tắc: một khoản chi bị từ chối nếu
 * {@code currentBalance + chargeAmount > creditLimit + totalHoldAmount},
 * trong đó totalHoldAmount là tổng các khoản tạm giữ đang ACTIVE và chưa hết hạn.
 */
@Service
@RequiredArgsConstructor
@Slf4j
@Transactional
public class FolioManagementService {

    /** Thời hạn mặc định của một khoản tạm giữ */
    public static final Duration DEFAULT_HOLD_DURATION = Duration.ofDays(14);

    private final CabinFolioRepository folioRepository;
    private final PreAuthorizationHoldRepository holdRepository;

    /**
     * Kết quả kiểm tra một khoản chi được phép.
     */
    public record SpendAuthorization(
            Long folioId,
            BigDecimal chargeAmount,
            BigDecimal creditLimit,
            BigDecimal totalHoldAmount,
            BigDecimal projectedBalance,
            BigDecimal remainingHeadroom,
            boolean overdraftApproved) {
    }

    /**
     * Tạo giao dịch tạm giữ tiền khi khách lên tàu (Check-in).
     */
    public PreAuthorizationHold createPreAuthorization(Long folioId, Long passengerId, BigDecimal amount) {
        if (folioId == null) {
            throw new BusinessRuleException("Mã folio không được để trống");
        }
        if (passengerId == null) {
            throw new BusinessRuleException("Mã hành khách không được để trống");
        }
        BigDecimal holdAmount = BillingMoney.requirePositive("Số tiền tạm giữ", amount);

        // Khóa folio để các thao tác trên cùng folio được xử lý tuần tự
        CabinFolio folio = folioRepository.findByIdForUpdate(folioId)
                .orElseThrow(() -> new ResourceNotFoundException("Không tìm thấy folio ID: " + folioId));
        if (!folio.isOpen()) {
            throw new BusinessRuleException(
                    "Folio " + folio.getCabinNumber() + " đang ở trạng thái " + folio.getStatus()
                            + ", không thể tạo khoản tạm giữ");
        }

        PreAuthorizationHold hold = PreAuthorizationHold.builder()
                .folio(folio)
                .passengerId(passengerId)
                .holdAmount(holdAmount)
                .holdReferenceCode(generateReferenceCode())
                .status(PreAuthHoldStatus.ACTIVE)
                .expiresAt(Instant.now().plus(DEFAULT_HOLD_DURATION))
                .build();
        PreAuthorizationHold saved = holdRepository.save(hold);
        log.info("Đã tạo khoản tạm giữ {} số tiền {} cho folio {}", saved.getHoldReferenceCode(), holdAmount, folioId);
        return saved;
    }

    /**
     * Kiểm tra một khoản chi có nằm trong hạn mức hay không (không có cờ cho phép thấu chi).
     */
    @Transactional(readOnly = true)
    public SpendAuthorization validateSpendAuthorization(Long folioId, BigDecimal chargeAmount) {
        return validateSpendAuthorization(folioId, chargeAmount, false);
    }

    /**
     * Kiểm tra một khoản chi. Khi {@code overdraftApproved = true} (quản lý đã cho phép thấu chi)
     * thì bỏ qua kiểm tra hạn mức.
     *
     * @throws FolioLimitExceededException mã lỗi FOLIO_LIMIT_EXCEEDED khi vượt hạn mức
     */
    @Transactional(readOnly = true)
    public SpendAuthorization validateSpendAuthorization(Long folioId, BigDecimal chargeAmount,
                                                         boolean overdraftApproved) {
        BigDecimal charge = BillingMoney.requirePositive("Số tiền chi tiêu", chargeAmount);
        CabinFolio folio = folioRepository.findById(folioId)
                .orElseThrow(() -> new ResourceNotFoundException("Không tìm thấy folio ID: " + folioId));

        if (folio.getStatus() != CabinFolioStatus.OPEN) {
            throw new BusinessRuleException("FOLIO_NOT_OPEN: Folio " + folio.getCabinNumber()
                    + " đang ở trạng thái " + folio.getStatus() + ", không thể ghi nhận chi tiêu");
        }

        BigDecimal totalHold = BillingMoney.nullSafe(
                holdRepository.sumActiveHoldAmount(folioId, PreAuthHoldStatus.ACTIVE, Instant.now()));
        BigDecimal projectedBalance = folio.getCurrentBalance().add(charge);
        BigDecimal allowedMaximum = folio.getCreditLimit().add(totalHold);

        if (projectedBalance.compareTo(allowedMaximum) > 0 && !overdraftApproved) {
            throw new FolioLimitExceededException(String.format(
                    "Chi tiêu %s làm số dư vượt hạn mức (số dư hiện tại %s, hạn mức %s, tạm giữ %s)",
                    charge, folio.getCurrentBalance(), folio.getCreditLimit(), totalHold));
        }

        return new SpendAuthorization(folioId, charge, folio.getCreditLimit(), totalHold,
                projectedBalance, allowedMaximum.subtract(projectedBalance), overdraftApproved);
    }

    /**
     * Quyết toán khoản tạm giữ khi kết thúc hải trình: số tiền thực thu giảm công nợ của folio.
     */
    public PreAuthorizationHold captureHold(Long holdId, BigDecimal finalAmount) {
        BigDecimal captured = BillingMoney.requirePositive("Số tiền quyết toán", finalAmount);

        PreAuthorizationHold hold = holdRepository.findByIdForUpdate(holdId)
                .orElseThrow(() -> new ResourceNotFoundException("Không tìm thấy khoản tạm giữ ID: " + holdId));
        Instant now = Instant.now();

        if (hold.getStatus() == PreAuthHoldStatus.ACTIVE && hold.isExpired(now)) {
            throw new BusinessRuleException("Khoản tạm giữ " + hold.getHoldReferenceCode() + " đã hết hạn");
        }
        if (captured.compareTo(hold.getHoldAmount()) > 0) {
            throw new BusinessRuleException(String.format(
                    "Số tiền quyết toán %s vượt số tiền tạm giữ %s", captured, hold.getHoldAmount()));
        }

        CabinFolio folio = folioRepository.findByIdForUpdate(hold.getFolio().getId())
                .orElseThrow(() -> new ResourceNotFoundException("Không tìm thấy folio của khoản tạm giữ"));
        if (folio.getStatus() == CabinFolioStatus.SETTLED) {
            throw new BusinessRuleException("Folio " + folio.getCabinNumber() + " đã tất toán");
        }

        hold.capture(captured, now); // kiểm tra trạng thái ACTIVE nằm trong entity
        folio.credit(captured);
        folioRepository.save(folio);
        PreAuthorizationHold saved = holdRepository.save(hold);
        log.info("Đã quyết toán khoản tạm giữ {} số tiền {}", saved.getHoldReferenceCode(), captured);
        return saved;
    }

    /**
     * Hủy khoản tạm giữ khi khách thanh toán bằng tiền mặt hoặc chuyển khoản VietQR.
     */
    public PreAuthorizationHold releaseHold(Long holdId) {
        PreAuthorizationHold hold = holdRepository.findByIdForUpdate(holdId)
                .orElseThrow(() -> new ResourceNotFoundException("Không tìm thấy khoản tạm giữ ID: " + holdId));
        hold.release(Instant.now());
        PreAuthorizationHold saved = holdRepository.save(hold);
        log.info("Đã hủy khoản tạm giữ {}", saved.getHoldReferenceCode());
        return saved;
    }

    private static String generateReferenceCode() {
        return "PAH-" + UUID.randomUUID().toString().replace("-", "").substring(0, 16).toUpperCase();
    }
}
