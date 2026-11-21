package com.cruise.pos.service;

import com.cruise.pos.exception.BusinessRuleException;

import java.math.BigDecimal;
import java.math.RoundingMode;

/**
 * Tiện ích kiểm tra số tiền dùng chung cho các service billing của cabin
 */
final class BillingMoney {

    private BillingMoney() {
    }

    /** Số tiền phải > 0 và tối đa 2 chữ số thập phân; trả về giá trị đã chuẩn hóa về scale 2 */
    static BigDecimal requirePositive(String fieldLabel, BigDecimal amount) {
        if (amount == null || amount.signum() <= 0) {
            throw new BusinessRuleException(fieldLabel + " phải lớn hơn 0");
        }
        if (amount.stripTrailingZeros().scale() > 2) {
            throw new BusinessRuleException(fieldLabel + " chỉ được có tối đa 2 chữ số thập phân");
        }
        return amount.setScale(2, RoundingMode.UNNECESSARY);
    }

    static BigDecimal nullSafe(BigDecimal value) {
        return value == null ? BigDecimal.ZERO : value;
    }
}
