package com.cruise.pos.service;

import com.cruise.pos.entity.CabinFolio;
import com.cruise.pos.exception.ResourceNotFoundException;
import com.cruise.pos.repository.CabinFolioRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.OptimisticLockingFailureException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;

import java.math.BigDecimal;

/**
 * Ghi nhận chi tiêu POS vào folio cabin với khóa lạc quan (@Version).
 *
 * <p>Mỗi lần thử chạy trong một transaction riêng: kiểm tra hạn mức rồi ghi nợ.
 * Nếu có giao dịch khác vừa sửa số dư (xung đột phiên bản) thì thử lại với số dư mới,
 * nên nhiều lần quẹt thẻ cùng lúc không bao giờ làm mất một khoản chi hay vượt hạn mức.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class FolioChargeService {

    /** Số lần thử tối đa khi gặp xung đột phiên bản */
    public static final int MAX_ATTEMPTS = 25;

    private final CabinFolioRepository folioRepository;
    private final FolioManagementService folioManagementService;
    private final TransactionTemplate transactionTemplate;

    public CabinFolio charge(Long folioId, BigDecimal amount) {
        return charge(folioId, amount, false);
    }

    /**
     * @param overdraftApproved true nếu quản lý đã cho phép thấu chi
     * @throws com.cruise.pos.exception.FolioLimitExceededException khi vượt hạn mức mà chưa được phép
     * @throws OptimisticLockingFailureException khi vẫn xung đột sau {@link #MAX_ATTEMPTS} lần thử
     */
    public CabinFolio charge(Long folioId, BigDecimal amount, boolean overdraftApproved) {
        OptimisticLockingFailureException lastConflict = null;
        for (int attempt = 1; attempt <= MAX_ATTEMPTS; attempt++) {
            try {
                return transactionTemplate.execute(status -> chargeOnce(folioId, amount, overdraftApproved));
            } catch (OptimisticLockingFailureException conflict) {
                lastConflict = conflict;
                log.debug("Xung đột phiên bản khi ghi nợ folio {} (lần {}/{})", folioId, attempt, MAX_ATTEMPTS);
            }
        }
        throw lastConflict;
    }

    private CabinFolio chargeOnce(Long folioId, BigDecimal amount, boolean overdraftApproved) {
        FolioManagementService.SpendAuthorization authorization =
                folioManagementService.validateSpendAuthorization(folioId, amount, overdraftApproved);
        CabinFolio folio = folioRepository.findById(folioId)
                .orElseThrow(() -> new ResourceNotFoundException("Không tìm thấy folio ID: " + folioId));
        folio.debit(authorization.chargeAmount());
        // flush ngay trong transaction để xung đột phiên bản lộ ra tại đây
        return folioRepository.saveAndFlush(folio);
    }
}
