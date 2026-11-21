package com.cruise.pos.dto;

import java.math.BigDecimal;
import java.util.List;

/**
 * Sao kê riêng của từng khách sau khi chia hóa đơn cabin.
 *
 * @param passengerId           khách sở hữu sao kê
 * @param personalTransactionIds các giao dịch gán đích danh cho khách (chi tiêu cá nhân)
 * @param personalTotal         tổng chi tiêu cá nhân
 * @param sharedTotal           phần phí chung được phân bổ cho khách
 * @param grandTotal            tổng phải thanh toán = personalTotal + sharedTotal
 * @param currency              đơn vị tiền tệ
 */
public record SubFolioStatement(
        Long passengerId,
        List<Long> personalTransactionIds,
        BigDecimal personalTotal,
        BigDecimal sharedTotal,
        BigDecimal grandTotal,
        String currency) {
}
