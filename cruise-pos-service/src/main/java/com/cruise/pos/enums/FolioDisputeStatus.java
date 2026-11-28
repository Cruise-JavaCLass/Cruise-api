package com.cruise.pos.enums;

/**
 * State machine khiếu nại giao dịch: PENDING -> IN_REVIEW -> (APPROVED | REJECTED)
 */
public enum FolioDisputeStatus {
    PENDING,
    IN_REVIEW,
    APPROVED,
    REJECTED
}
