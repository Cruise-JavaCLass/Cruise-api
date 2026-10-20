package com.cruise.pos.service;

import java.math.BigDecimal;

/**
 * Kết quả tra cứu các khoản đang bị giữ do khiếu nại của một cabin.
 *
 * @param heldAmount tổng tiền bị giữ lại, không thu trong lần tất toán này
 * @param openDisputeCount số khiếu nại chưa kết thúc
 */
public record DisputeHold(BigDecimal heldAmount, int openDisputeCount) {

    public static DisputeHold none() {
        return new DisputeHold(BigDecimal.ZERO, 0);
    }
}
